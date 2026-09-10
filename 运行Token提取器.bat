@echo off
chcp 65001 >nul
title 智能充电 - Token提取器
echo ========================================================
echo   智能充电 - PC微信小程序 Token 一键提取器
echo ========================================================
echo.
echo 正在启动 Token 提取器图形界面...
python token_extractor.py
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [提示] 启动图形界面失败，正在尝试命令行模式...
    python token_extractor.py --cli
    pause
)
