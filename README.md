# 大学城充电桩监测 (University City Charging Station Monitor)

针对广州大学城（华工大学城 C15 网点等）智能充电桩实时空闲情况的检测工具套件。

本项目包含两个核心软件：
1. **电脑端 Token 一键提取器 (Python GUI / CLI)**：微信电脑端打开“智能充电”小程序后，1秒内自动从进程内存提取最新 Token 并写入系统剪贴板。
2. **手机安卓端 App《大学城充电桩监测》 (Android Native)**：提供 Token 设置/持久化保存，实时监控华工 C15 的 1号、2号、3号、4号充电桩（共48个插口）的空闲与占用情况。

---

## 📱 软件一览

### 1. 手机安卓 APP（大学城充电桩监测）
- **安装包直接下载**：根目录下 [`大学城充电桩监测.apk`](大学城充电桩监测.apk) (约 5.2MB)
- **源码工程**：[`android_app/`](android_app/)
- **功能特性**：
  - **设置栏**：支持一键从剪贴板粘贴 Token、清空、测试并保存。采用 Android 原生 `SharedPreferences` 存储，退出或重启手机不丢失。Token 过期可随时在设置中修改。
  - **总览看板**：直观展示当前 4 桩总空闲插口数（如 `7 / 48 口可用`）以及最近数据更新时间。
  - **4 桩独立卡片**：1号桩、2号桩、3号桩、4号桩各一张卡片，显示设备编码与空闲数统计。
  - **12 格插口矩阵**：每桩以 6列 x 2行 直观呈现 `01` 到 `12` 号插口：
    - 🟢 **绿色高亮**：空闲可用（如 `11 空闲`）
    - ⚪ **浅灰色**：已被占用（如 `01 占用`）
  - **刷新交互**：支持下拉刷新（`SwipeRefreshLayout`）与右上角刷新按钮；Token 失效时顶部自动弹出红色提醒条，点击一键跳转设置。

### 2. 电脑端 Token 一键提取器
- **双击运行脚本**：[`运行Token提取器.bat`](运行Token提取器.bat)
- **源码文件**：[`token_extractor.py`](token_extractor.py)
- **功能特性**：
  - 自动扫描运行中的 PC 微信小程序进程（`WeChatAppEx.exe`）内存，无需手动抓包或代理配置，约 1 秒提取出 Token、UserId 及绑定手机号。
  - 自动写入 Windows 剪贴板，支持重新复制。
  - 发起轻量云端心跳自动检测 Token 有效性，提示“有效可用 ✅”或“已失效 ❌”。
  - 支持命令行模式：`python token_extractor.py --cli`。

---

## 🛠️ 逆向协议与技术细节

- **服务基址**：`https://hgcms.gzyzinfo.com:442/ChargeBoxService/`
- **加密机制**：DES-ECB，PKCS5/PKCS7 Padding，密钥为 8 字节 `yz_cbox\0`
- **请求格式**：
  - `POST`，`Content-Type: application/x-www-form-urlencoded`
  - 请求体：`para=<HEX_CIPHERTEXT>&mobileTime=<YYYY-MM-DD HH:mm:ss>&token=<TOKEN>`
- **核心接口**：
  - `mobile/chargeLocker/stationList.do`：站点列表及 Token 有效性探测
  - `mobile/chargeLocker/chargeBoxList.do`：查询指定电桩各格口状态（入参 `{"code": "<pileCode>"}`）
- **华工大学城 C15 电桩编码**：
  - **1号充电桩**：`861714054442714`（12口）
  - **2号充电桩**：`861714054100585`（12口）
  - **3号充电桩**：`861714054436518`（12口）
  - **4号充电桩**：`863488056590576`（12口）

---

## 🚀 极简使用指南

1. **获取 Token**：
   - 电脑微信打开“智能充电”小程序进入首页；
   - 双击运行根目录下的 `运行Token提取器.bat`（或在终端运行 `python token_extractor.py`）；
   - 点击“🚀 一键提取最新 Token”，Token 会自动复制到剪贴板；
   - 通过微信将 Token 发送给手机（如发送到微信“文件传输助手”）。

2. **手机端使用**：
   - 将根目录下的 `大学城充电桩监测.apk` 发送至手机安装；
   - 打开 App，点击右上角 ⚙️ **设置** 图标；
   - 点击 **“📋 粘贴剪贴板”**，然后点击 **“💾 验证并保存 Token”**；
   - 返回主页，即可随时下拉刷新查看华工 C15 的 1、2、3、4 号电桩各个插口的实时空闲状态。

---

## 📂 项目结构

```text
├── 大学城充电桩监测.apk     # 已编译的 Android 手机安装包
├── 运行Token提取器.bat      # 电脑端 Token 提取器一键启动脚本
├── token_extractor.py     # 电脑端 Token 提取器源码 (Tkinter GUI / CLI)
├── charge_client.py       # Python 版协议客户端及测试命令行工具
├── session.example.json   # 会话凭据配置示例文件
├── README.md              # 项目说明文档
└── android_app/           # Android Studio 原生安卓工程源码
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
