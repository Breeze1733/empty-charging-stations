# -*- coding: utf-8 -*-
"""
智能充电柜/桩 API 客户端
提供 DES 加解密封装、网络通信及充电桩可用性查询工具。
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
import requests
import urllib3
import psutil
from Crypto.Cipher import DES
from Crypto.Util.Padding import pad

urllib3.disable_warnings()

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DEFAULT_SESSION_FILE = os.path.join(SCRIPT_DIR, "session.json")


class ChargeBoxClient:
    """智能充电柜/桩 API 客户端"""

    BASE_URL = "https://hgcms.gzyzinfo.com:442/ChargeBoxService/"
    DES_KEY = b"yz_cbox\x00"

    def __init__(self, token: str = None, session_file: str = None):
        self.session_file = session_file or DEFAULT_SESSION_FILE
        self.token = token
        self.user_id = None
        self.phone = None

        if not self.token and os.path.exists(self.session_file):
            self.load_session()

    def load_session(self):
        """从本地 JSON 文件恢复会话信息"""
        try:
            with open(self.session_file, "r", encoding="utf-8") as f:
                data = json.load(f)
                self.token = data.get("token")
                self.user_id = data.get("userId")
                self.phone = data.get("phone")
        except Exception as e:
            print(f"[!] 读取本地会话失败: {e}")

    def save_session(self, token: str, user_id: int = None, phone: str = None):
        """持久化保存 Token 等会话信息"""
        self.token = token
        self.user_id = user_id
        self.phone = phone
        data = {
            "token": self.token,
            "userId": self.user_id,
            "phone": self.phone,
            "update_time": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        }
        with open(self.session_file, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        print(f"[*] Session 已保存至 {self.session_file}")

    def encrypt(self, data_dict: dict) -> str:
        """DES-ECB PKCS7 加密，输出十六进制字符串"""
        cipher = DES.new(self.DES_KEY, DES.MODE_ECB)
        raw = json.dumps(data_dict, separators=(",", ":")).encode("utf-8")
        return cipher.encrypt(pad(raw, DES.block_size, style="pkcs7")).hex()

    def post(self, endpoint: str, payload_dict: dict = None, use_token: bool = True) -> dict:
        """通用接口 POST 请求封装"""
        now_str = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        payload = payload_dict.copy() if payload_dict else {}
        para = self.encrypt(payload)

        data = {"para": para, "mobileTime": now_str}
        if use_token and self.token:
            data["token"] = self.token

        url = f"{self.BASE_URL}{endpoint}"
        resp = requests.post(url, data=data, verify=False, timeout=10)
        resp.encoding = "utf-8"
        return resp.json()

    def check_login(self) -> bool:
        """检测当前配置的 Token 是否有效"""
        if not self.token:
            print("[-] 当前未配置 Token，请先登录或设置 Token")
            return False
        res = self.post("mobile/chargeLocker/stationList.do", {})
        if res.get("state") == "true":
            stations = res.get("content", [])
            print(f"[+] Token 验证成功! 当前可用，共查询到 {len(stations)} 个充电站。")
            return True
        else:
            print(f"[-] Token 验证失败: {res.get('descriptions')}")
            return False

    def get_stations(self) -> list:
        """获取所有充电站列表"""
        res = self.post("mobile/chargeLocker/stationList.do", {})
        if res.get("state") == "true":
            return res.get("content", [])
        raise RuntimeError(res.get("descriptions") or "获取充电站列表失败")

    def get_station_boxes(self, code: str) -> list:
        """查询指定充电桩编码的所有格口状态"""
        now_str = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        res = self.post("mobile/chargeLocker/chargeBoxList.do", {"code": code, "mobileTime": now_str})
        if res.get("state") == "true":
            content = res.get("content", {})
            return content.get("list", [])
        return []

    def get_my_records(self) -> list:
        """查询用户历史充电订单记录"""
        if not self.user_id:
            raise RuntimeError("缺少 userId，请先运行 grab 抓取完整会话")
        res = self.post("mobile/chargeRecord/list.do", {"userId": self.user_id, "state": "1"})
        if res.get("state") == "true":
            return res.get("content", [])
        return []


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="智能充电桩可用性检测客户端")
    subparsers = parser.add_subparsers(dest="command")

    subparsers.add_parser("check", help="检测本地保存的 Token 是否有效")

    p_set = subparsers.add_parser("set-token", help="手动设置/更新抓包获取的 Token")
    p_set.add_argument("token", help="Token 字符串")

    subparsers.add_parser("records", help="查询当前用户的充电历史记录及充电桩名称")
    subparsers.add_parser("stations", help="查询所有开通的充电站网点列表")

    args = parser.parse_args()
    client = ChargeBoxClient()

    if args.command == "check":
        client.check_login()
    elif args.command == "set-token":
        client.save_session(args.token)
        client.check_login()
    elif args.command == "records":
        records = client.get_my_records()
        print(f"[*] 共检索到 {len(records)} 条充电历史记录:")
        for r in records:
            print(f"[{r.get('startTime')}] {r.get('clName')} ({r.get('address')}) - 格口:{r.get('boxName')}号 - 状态:{r.get('stateStr')}")
    elif args.command == "stations":
        stations = client.get_stations()
        print(f"[*] 共检索到 {len(stations)} 个网点:")
        for s in stations:
            print(f"[{s.get('id')}] {s.get('name')} (编码: {s.get('code')}) - 地址: {s.get('address')}")
    else:
        parser.print_help()
