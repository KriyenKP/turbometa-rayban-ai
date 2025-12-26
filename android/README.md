# TurboMeta Android

Ray-Ban Meta 智能眼镜 AI 助手 Android 版

## 功能特性

### Live AI
- 实时语音对话，支持阿里云百炼 API
- 眼镜摄像头实时画面传输
- 多轮对话记录保存

### LeanEat
- 食物拍照识别
- 卡路里和营养成分分析
- 健康评分和饮食建议

### 直播
- 眼镜摄像头实时预览
- 支持推流到抖音、快手、小红书等平台（开发中）

### 实时翻译（开发中）
- 语音实时翻译

### WordLearn（开发中）
- 单词学习功能

## 系统要求

- Android 8.0 (API 26) 或更高版本
- 需要安装 Meta AI 应用并完成眼镜配对
- 需要阿里云百炼 API Key（用于 Live AI 功能）

## 安装

1. 下载 APK 文件
2. 在 Android 设备上安装 APK
3. 授予必要权限（蓝牙、麦克风）
4. 打开 Meta AI 应用完成眼镜注册

## 使用说明

### 首次使用

1. 确保 Ray-Ban Meta 眼镜已通过 Meta AI 应用配对
2. 打开 TurboMeta 应用
3. 点击首页的"连接眼镜"按钮
4. 应用会自动跳转到 Meta AI 进行设备注册

### 配置 API Key

1. 进入设置页面
2. 点击"API Key"
3. 输入阿里云百炼 API Key
4. 保存

获取 API Key: https://bailian.console.aliyun.com/?apiKey=1

### 使用 Live AI

1. 确保眼镜已连接且 API Key 已配置
2. 点击首页"Live AI"卡片
3. 应用会请求眼镜摄像头权限（首次使用）
4. 开始与 AI 对话

### 使用 LeanEat

1. 确保眼镜已连接
2. 点击首页"LeanEat"卡片
3. 对准食物拍照
4. 等待 AI 分析营养成分

## 技术架构

- **UI 框架**: Jetpack Compose + Material 3
- **架构模式**: MVVM
- **眼镜 SDK**: Meta Wearables DAT SDK v0.3.0
- **AI 接口**: 阿里云百炼 Omni Realtime API
- **本地存储**: Room Database

## 权限说明

| 权限 | 用途 |
|------|------|
| BLUETOOTH | 连接 Ray-Ban Meta 眼镜 |
| BLUETOOTH_CONNECT | 蓝牙设备连接 |
| BLUETOOTH_SCAN | 扫描蓝牙设备 |
| RECORD_AUDIO | 语音对话录音 |
| INTERNET | API 通信 |

## 构建

```bash
# Debug 构建
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug
```

## 项目结构

```
app/src/main/java/com/turbometa/rayban/
├── MainActivity.kt           # 主入口
├── ui/
│   ├── navigation/          # 导航配置
│   ├── screens/             # 各功能页面
│   └── theme/               # 主题配置
├── viewmodels/
│   ├── WearablesViewModel   # 眼镜连接和流管理
│   └── OmniRealtimeViewModel # AI 对话管理
├── data/
│   ├── database/            # Room 数据库
│   └── models/              # 数据模型
└── utils/                   # 工具类
```

## 版本历史

### v1.0.0
- 首个发布版本
- Live AI 实时语音对话
- LeanEat 营养分析
- 直播预览功能
- 对话记录保存
- 中英文本地化

## License

MIT License
