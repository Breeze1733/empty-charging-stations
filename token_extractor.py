# -*- coding: utf-8 -*-
"""
智能充电 - PC微信小程序 Token 提取器
双击直接运行图形界面，也可使用命令行模式 (--cli)。
"""
import sys
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")

import os
import re
import json
import datetime
import argparse
import ctypes
from ctypes import wintypes
import tkinter as tk
from tkinter import ttk, messagebox
import requests
import urllib3
import psutil
from Crypto.Cipher import DES
from Crypto.Util.Padding import pad

urllib3.disable_warnings()

BASE_URL = "https://hgcms.gzyzinfo.com:442/ChargeBoxService/"
DES_KEY = b"yz_cbox\x00"

kernel32 = ctypes.windll.kernel32
user32 = ctypes.windll.user32

class MEMORY_BASIC_INFORMATION(ctypes.Structure):
    _fields_ = [
        ("BaseAddress", ctypes.c_void_p),
        ("AllocationBase", ctypes.c_void_p),
        ("AllocationProtect", wintypes.DWORD),
        ("RegionSize", ctypes.c_size_t),
        ("State", wintypes.DWORD),
        ("Protect", wintypes.DWORD),
        ("Type", wintypes.DWORD),
    ]

kernel32.GlobalAlloc.restype = ctypes.c_void_p
kernel32.GlobalAlloc.argtypes = [ctypes.c_uint, ctypes.c_size_t]
kernel32.GlobalLock.restype = ctypes.c_void_p
kernel32.GlobalLock.argtypes = [ctypes.c_void_p]
kernel32.GlobalUnlock.argtypes = [ctypes.c_void_p]
user32.SetClipboardData.restype = ctypes.c_void_p
user32.SetClipboardData.argtypes = [ctypes.c_uint, ctypes.c_void_p]

def copy_to_clipboard(text: str) -> bool:
    """直接调用 Windows 底层 API 写入系统剪贴板（支持 64 位指针）"""
    try:
        if not user32.OpenClipboard(None):
            return False
        user32.EmptyClipboard()
        text_bytes = text.encode("utf-16le") + b"\x00\x00"
        h_mem = kernel32.GlobalAlloc(0x0002, len(text_bytes))  # GMEM_MOVEABLE
        if h_mem:
            p_mem = kernel32.GlobalLock(h_mem)
            ctypes.memmove(p_mem, text_bytes, len(text_bytes))
            kernel32.GlobalUnlock(h_mem)
            user32.SetClipboardData(13, h_mem)  # CF_UNICODETEXT
        user32.CloseClipboard()
        return True
    except Exception:
        return False

def encrypt_des(data_dict: dict) -> str:
    """DES-ECB PKCS7 加密，输出十六进制字符串"""
    cipher = DES.new(DES_KEY, DES.MODE_ECB)
    raw = json.dumps(data_dict, separators=(",", ":")).encode("utf-8")
    return cipher.encrypt(pad(raw, DES.block_size, style="pkcs7")).hex()

def verify_token(token: str) -> bool:
    """向服务端发起测试请求验证 Token 是否有效"""
    try:
        now_str = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        para = encrypt_des({})
        data = {"para": para, "mobileTime": now_str, "token": token}
        r = requests.post(f"{BASE_URL}mobile/chargeLocker/stationList.do", data=data, verify=False, timeout=6)
        r.encoding = "utf-8"
        res = r.json()
        return res.get("state") == "true"
    except Exception:
        return False

def grab_session_from_wechat():
    """
    从正在运行的 PC 微信小程序进程 (WeChatAppEx.exe) 内存中提取 Token、UserId 和 Phone
    优先扫描最新启动的小程序进程，通常耗时只需 0.5 ~ 1 秒。
    """
    procs = [p for p in psutil.process_iter(["pid", "name", "create_time"]) if p.info["name"] and "WeChatAppEx" in p.info["name"]]
    if not procs:
        return None, "未检测到运行中的 PC 微信小程序 (WeChatAppEx.exe)，请先打开“智能充电”小程序！"

    # 按创建时间倒序排，最新运行的小程序窗口排在最前面
    procs.sort(key=lambda x: x.info["create_time"] or 0, reverse=True)

    mbi = MEMORY_BASIC_INFORMATION()
    mbi_size = ctypes.sizeof(mbi)

    for proc in procs:
        pid = proc.info["pid"]
        h = kernel32.OpenProcess(0x0410, False, pid)  # PROCESS_VM_READ | PROCESS_QUERY_INFORMATION
        if not h:
            continue
        addr = 0
        found_data = None

        while kernel32.VirtualQueryEx(h, ctypes.c_void_p(addr), ctypes.byref(mbi), mbi_size):
            # MEM_COMMIT (0x1000) 且有读权限，排除不可读/PAGE_GUARD
            if mbi.State == 0x1000 and (mbi.Protect & 0xEE) and not (mbi.Protect & 0x100):
                # 过滤过大的映射段，加快速度
                if mbi.RegionSize <= 32 * 1024 * 1024:
                    buf = ctypes.create_string_buffer(mbi.RegionSize)
                    bytesRead = ctypes.c_size_t()
                    if kernel32.ReadProcessMemory(h, ctypes.c_void_p(mbi.BaseAddress), buf, mbi.RegionSize, ctypes.byref(bytesRead)):
                        data = buf.raw[:bytesRead.value]
                        if b'"token":' in data and b'"userId":' in data:
                            # 匹配 JSON 格式的 LOGININFO
                            # 形如: {"image":"","phone":"18929712079",...,"userId":12193,...,"token":"d01fb845-..."}
                            idx = data.find(b'"token":')
                            while idx != -1:
                                start = data.rfind(b"{", max(0, idx - 400), idx)
                                end = data.find(b"}", idx)
                                if start != -1 and end != -1 and end - start < 1000:
                                    snippet = data[start:end+1]
                                    m_tok = re.search(rb'"token":\s*"([a-zA-Z0-9_\-]{16,64})"', snippet)
                                    if m_tok:
                                        tok = m_tok.group(1).decode()
                                        m_uid = re.search(rb'"userId":\s*(\d+)', snippet)
                                        uid = int(m_uid.group(1).decode()) if m_uid else 0
                                        m_phone = re.search(rb'"phone":\s*"(\d{11})"', snippet)
                                        phone = m_phone.group(1).decode() if m_phone else ""
                                        found_data = (tok, uid, phone)
                                        break
                                idx = data.find(b'"token":', idx + 10)
                            if found_data:
                                break
            addr = (mbi.BaseAddress or 0) + mbi.RegionSize

        kernel32.CloseHandle(h)
        if found_data:
            return found_data, ""

    return None, "已检测到小程序进程，但未在内存中找到登录凭证，请在小程序中进入一次首页后再试！"


class ExtractorGUI:
    def __init__(self, root):
        self.root = root
        self.root.title("⚡ 智能充电 - Token 一键提取器")
        self.root.geometry("580x440")
        self.root.resizable(False, False)

        # 尝试设置界面主题
        style = ttk.Style()
        try:
            style.theme_use("clam")
        except Exception:
            pass

        self.root.configure(bg="#F4F6F9")

        # 顶部绿色标题栏
        header_frame = tk.Frame(root, bg="#10B981", height=75)
        header_frame.pack(fill=tk.X)

        title_label = tk.Label(
            header_frame,
            text="⚡ 智能充电 Token 一键提取器",
            font=("Microsoft YaHei", 15, "bold"),
            fg="white",
            bg="#10B981"
        )
        title_label.pack(pady=(12, 2))

        sub_label = tk.Label(
            header_frame,
            text="打开电脑微信“智能充电”小程序，点击下方按钮即可一键提取并自动复制",
            font=("Microsoft YaHei", 9),
            fg="#D1FAE5",
            bg="#10B981"
        )
        sub_label.pack(pady=(0, 10))

        # 主卡片面板
        card = tk.Frame(root, bg="white", bd=0, highlightthickness=1, highlightbackground="#E5E7EB")
        card.pack(fill=tk.BOTH, expand=True, padx=20, pady=15)

        # 状态提示条
        self.status_var = tk.StringVar(value="准备就绪：请确保电脑微信已打开“智能充电”小程序")
        self.status_label = tk.Label(
            card,
            textvariable=self.status_var,
            font=("Microsoft YaHei", 9),
            fg="#4B5563",
            bg="white"
        )
        self.status_label.pack(pady=(15, 8))

        # 提取按钮
        self.extract_btn = tk.Button(
            card,
            text="🚀 一键提取最新 Token",
            font=("Microsoft YaHei", 11, "bold"),
            bg="#10B981",
            fg="white",
            activebackground="#059669",
            activeforeground="white",
            relief=tk.FLAT,
            padx=25,
            pady=9,
            cursor="hand2",
            command=self.do_extract
        )
        self.extract_btn.pack(pady=6)

        # 用户信息展示栏
        info_frame = tk.Frame(card, bg="#F9FAFB", bd=1, relief=tk.SOLID)
        info_frame.pack(fill=tk.X, padx=15, pady=10)

        self.user_info_var = tk.StringVar(value="手机号: --    |    用户ID: --    |    Token状态: --")
        info_lbl = tk.Label(
            info_frame,
            textvariable=self.user_info_var,
            font=("Microsoft YaHei", 9),
            bg="#F9FAFB",
            fg="#374151"
        )
        info_lbl.pack(pady=8)

        # Token 标签与输入框
        token_label = tk.Label(
            card,
            text="当前最新 Token (已自动复制到系统剪贴板)：",
            font=("Microsoft YaHei", 9, "bold"),
            bg="white",
            fg="#1F2937"
        )
        token_label.pack(anchor=tk.W, padx=15, pady=(5, 3))

        token_input_frame = tk.Frame(card, bg="white")
        token_input_frame.pack(fill=tk.X, padx=15, pady=(0, 10))

        self.token_entry = tk.Entry(
            token_input_frame,
            font=("Consolas", 10),
            bg="#F3F4F6",
            fg="#111827",
            relief=tk.FLAT,
            bd=5
        )
        self.token_entry.pack(side=tk.LEFT, fill=tk.X, expand=True, ipady=4)

        copy_btn = tk.Button(
            token_input_frame,
            text="📋 重新复制",
            font=("Microsoft YaHei", 9),
            bg="#3B82F6",
            fg="white",
            activebackground="#2563EB",
            activeforeground="white",
            relief=tk.FLAT,
            padx=12,
            pady=3,
            cursor="hand2",
            command=self.copy_token
        )
        copy_btn.pack(side=tk.LEFT, padx=(8, 0))

        # 底部提示文字
        self.tip_var = tk.StringVar(value="💡 提取成功后直接在微信发给手机“文件传输助手”，在手机App设置中粘贴即可。")
        tip_lbl = tk.Label(
            card,
            textvariable=self.tip_var,
            font=("Microsoft YaHei", 8),
            bg="white",
            fg="#9CA3AF"
        )
        tip_lbl.pack(pady=(0, 10))

    def copy_token(self):
        tok = self.token_entry.get().strip()
        if tok:
            copy_to_clipboard(tok)
            self.tip_var.set("✅ Token 已成功复制到系统剪贴板！可以直接 Ctrl+V 粘贴！")
            messagebox.showinfo("提示", "Token 已成功复制到系统剪贴板！")
        else:
            messagebox.showwarning("提示", "当前没有可复制的 Token，请先点击提取！")

    def do_extract(self):
        self.status_var.set("⏳ 正在快速扫描微信小程序内存...")
        self.root.update()

        session, err = grab_session_from_wechat()
        if not session:
            self.status_var.set("❌ " + err)
            messagebox.showerror("提取失败", err)
            return

        token, user_id, phone = session
        self.token_entry.delete(0, tk.END)
        self.token_entry.insert(0, token)

        # 自动写入 Windows 剪贴板
        copy_to_clipboard(token)

        # 验证有效性
        self.status_var.set("🔍 正在连接服务端验证 Token 有效性...")
        self.root.update()

        is_valid = verify_token(token)
        status_str = "有效可用 ✅" if is_valid else "已过期 ❌"

        masked_phone = phone[:3] + "****" + phone[7:] if len(phone) == 11 else (phone or "未绑定")
        self.user_info_var.set(f"手机号: {masked_phone}    |    用户ID: {user_id}    |    状态: {status_str}")

        if is_valid:
            self.status_var.set("🎉 Token 提取成功且已通过服务端验证！已自动复制到剪贴板！")
            self.tip_var.set(f"更新时间: {datetime.datetime.now().strftime("%H:%M:%S")} (已保存到本地 session.json)")
            # 持久化到本地文件
            try:
                with open("session.json", "w", encoding="utf-8") as f:
                    json.dump({
                        "token": token,
                        "userId": user_id,
                        "phone": phone,
                        "update_time": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                    }, f, ensure_ascii=False, indent=2)
            except Exception:
                pass
            messagebox.showinfo(
                "提取成功",
                f"✅ Token 提取成功！\n\n已自动复制到剪贴板，请通过微信发送给手机！\n\n用户手机: {masked_phone}\nToken: {token}"
            )
        else:
            self.status_var.set("⚠️ 成功提取到 Token，但在云端校验未通过，可能已过期。")
            messagebox.showwarning("提示", "提取到的 Token 经测试已失效，请在微信小程序里点击任意页面刷新后再试。")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="PC 微信 Token 提取器")
    parser.add_argument("--cli", action="store_true", help="命令行模式直接提取并输出")
    args = parser.parse_args()

    if args.cli:
        print("[*] 正在扫描 PC 微信小程序内存...")
        session, err = grab_session_from_wechat()
        if session:
            token, user_id, phone = session
            copy_to_clipboard(token)
            valid = verify_token(token)
            print(f"[+] 提取成功!")
            print(f"    Token: {token}")
            print(f"    User ID: {user_id}")
            print(f"    Phone: {phone}")
            print(f"    云端校验: {"有效可用" if valid else "无效或已过期"}")
            print(f"[+] 已自动将 Token 复制到系统剪贴板！")
            # 持久化到 session.json
            with open("session.json", "w", encoding="utf-8") as f:
                json.dump({
                    "token": token,
                    "userId": user_id,
                    "phone": phone,
                    "update_time": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                }, f, ensure_ascii=False, indent=2)
        else:
            print("[-] " + err)
    else:
        root = tk.Tk()
        app = ExtractorGUI(root)
        root.mainloop()
