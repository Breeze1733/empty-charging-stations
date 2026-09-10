# 大学城充电桩监测 (University City Charging Station Monitor)

针对广州大学城（华工大学城 C15 网点等）智能充电桩实时可用性的轻量检测工具套件。

[![GitHub Release](https://img.shields.io/github/v/release/Breeze1733/empty-charging-stations?color=10B981&label=Release&logo=github)](https://github.com/Breeze1733/empty-charging-stations/releases/latest)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20Windows-blue)](https://github.com/Breeze1733/empty-charging-stations)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

> ### 📦 **开箱即用下载 (无需配置环境，双击即用)**
> - 📱 **安卓手机 App 安装包**：[👉 点击下载 ChargingStationMonitor-v1.0.0.apk (约 5.2MB)](https://github.com/Breeze1733/empty-charging-stations/releases/download/v1.0.0/ChargingStationMonitor-v1.0.0.apk)
> - 💻 **电脑端 Token 一键提取器 (免Python环境独立程序)**：[👉 点击下载 TokenExtractor-v1.0.0.exe (约 19.7MB)](https://github.com/Breeze1733/empty-charging-stations/releases/download/v1.0.0/TokenExtractor-v1.0.0.exe)
> - 🔗 [前往 GitHub Releases 页面查看所有版本发行](https://github.com/Breeze1733/empty-charging-stations/releases/latest)

---

## 📖 极简使用指南 (三步上手)

### 第一步：电脑端提取 Token
1. 在电脑微信中打开 **“智能充电”** 小程序并进入首页；
2. 运行电脑端提取工具（**二选一**）：
   - **方式 A (推荐)**：直接双击下载的 [`TokenExtractor.exe`](https://github.com/Breeze1733/empty-charging-stations/releases/download/v1.0.0/TokenExtractor-v1.0.0.exe) 独立可执行程序（电脑无需安装 Python）；
   - **方式 B (源码)**：在仓库根目录双击运行 [`运行Token提取器.bat`](运行Token提取器.bat) 或执行 `python token_extractor.py`；
3. 点击 **“🚀 一键提取最新 Token”**，程序在 1 秒内自动扫描内存，提取 Token 并**自动复制到系统剪贴板**；
4. 通过微信“文件传输助手”发送给手机并复制该 Token。

### 第二步：手机安装 App 并保存 Token
1. 在安卓手机上下载并安装 [**ChargingStationMonitor-v1.0.0.apk**](https://github.com/Breeze1733/empty-charging-stations/releases/download/v1.0.0/ChargingStationMonitor-v1.0.0.apk)；
2. 打开 App，点击右上角 ⚙️ **设置** 图标；
3. 点击 **“📋 粘贴剪贴板”**（或长按手动粘贴），再点击 **“💾 验证并保存 Token”**；
4. 提示“验证成功”后，Token 会持久化保存在手机中（杀后台或手机重启均不丢失）。

### 第三步：随时查看实时空闲状态
1. 返回 App 主界面，即时呈现华工大学城 C15 全部 4 台电桩（共 48 个插口）的占用情况：
   - 🟢 **绿色高亮**：插口空闲可用（例如 `03 空闲`）；
   - ⚪ **浅灰色**：已被占用（例如 `01 占用`）；
2. 支持随时**下拉页面刷新**或点击右上角 **🔄 刷新** 按钮；
3. **若 Token 后续失效**：界面顶部会自动弹出醒目的红色提醒条，点击即可直接进入设置更新。

---

## 📱 软件组成与特性

### 1. 手机安卓端 App《大学城充电桩监测》
- **纯原生轻量设计**：仅约 5.2MB，内置 Java 原生 DES-ECB 解密与 HTTPS 通信，兼容 Android 7.0 至 Android 15。
- **持久化配置**：通过 Android 原生 `SharedPreferences` 本地管理 Token，安全可靠。
- **总览看板**：汇总统计当前 4 桩总可用空闲口（如 `7 / 48 口可用`）及精准更新时间。
- **12 格卡片矩阵**：1~4号桩各一张卡片，以 6列 x 2行 的网格清晰排列 `01` 到 `12` 号插口，空闲与占用一目了然。

### 2. 电脑端 Token 一键提取器
- **独立打包**：提供已打包为单文件的 Windows 可执行程序 `TokenExtractor.exe`，免去安装 Python 与第三方依赖的麻烦。
- **零配置提取**：无需配置代理抓包或伪装，自动定位 `WeChatAppEx.exe` 进程内存堆栈。
- **在线自检**：提取同时向服务端发送心跳探测包，提示“有效可用 ✅”或“已过期 ❌”。
- **双模式支持**：默认提供精美图形界面；源码模式下亦支持无头命令行提取：`python token_extractor.py --cli`。

---

## 🔌 华工大学城 C15 专属电桩拓扑

经过服务端完整网点拓扑逆向与校验，已精确定位华工 C15 站点的 4 台充电桩：

| 电桩名称 | 设备硬件编码 | 插口数 | 对应格口 |
| :--- | :--- | :--- | :--- |
| **1号充电桩** | `861714054442714` | 12 | 01 ~ 12 号口 |
| **2号充电桩** | `861714054100585` | 12 | 01 ~ 12 号口 |
| **3号充电桩** | `861714054436518` | 12 | 01 ~ 12 号口 |
| **4号充电桩** | `863488056590576` | 12 | 01 ~ 12 号口 |

---

## 🛠️ 逆向协议与技术细节

- **服务基址**：`https://hgcms.gzyzinfo.com:442/ChargeBoxService/`
- **加密机制**：DES-ECB，PKCS5/PKCS7 Padding，固定密钥为 8 字节 `yz_cbox\0`
- **请求格式**：
  - `POST`，`Content-Type: application/x-www-form-urlencoded`
  - Body：`para=<HEX_CIPHERTEXT>&mobileTime=<YYYY-MM-DD HH:mm:ss>&token=<TOKEN>`
- **核心接口**：
  - `mobile/chargeLocker/stationList.do`：站点列表及 Token 有效性自检探测
  - `mobile/chargeLocker/chargeBoxList.do`：查询指定电桩各格口状态（入参 `{"code": "<pileCode>"}`）

---

## 📂 项目结构

```text
├── 运行Token提取器.bat      # 电脑端 Token 提取器双击启动脚本 (Python环境)
├── token_extractor.py     # 电脑端 Token 提取器源码 (Tkinter GUI / CLI)
├── charge_client.py       # Python 协议客户端与接口调试工具
├── session.example.json   # 会话凭据配置示例模板
├── README.md              # 项目文档与使用指南
└── android_app/           # Android Studio 原生安卓工程完整源码
    ├── app/
    │   └── src/main/
    │       ├── AndroidManifest.xml
    │       ├── java/com/charging/c15station/
    │       │   ├── MainActivity.java     # 监控看板主界面
    │       │   ├── SettingsActivity.java # Token 设置与验证界面
    │       │   ├── ChargeClient.java     # DES 加密与 HTTP 通信
    │       │   ├── TokenManager.java     # SharedPreferences 本地存储
    │       │   └── PileInfo.java         # C15 桩位模型定义
    │       └── res/                      # 布局、样式与矢量图标资源
    ├── build.gradle
    └── settings.gradle
```

---

## ⚠️ 免责声明

本项目仅供高校师生个人学习、充电便捷检测及逆向协议学术研究所用，不作任何商业用途。
