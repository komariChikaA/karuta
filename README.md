# karuta

A karuta music player 
=======
# 歌牌点歌系统（Karuta Jukebox）

一个Android+Windows双端的歌牌点歌程序，结合卡牌游戏机制。

### 本项目大部分内容使用Vibe Coding

## 📋 功能概述

### 核心功能
- 🎵 从文件夹读取歌曲和对应的图片（一个图片可对应多首歌曲）
- 🃏 卡组管理，支持多个卡组配置
- ▶️ 对局流程：选择卡组→随机抽取歌曲→播放歌曲→成功/失败操作
- 🎶 中场休息音乐
- 👨‍💼 后台操作面板：实时管理场上卡组状态，防止误操作

### 对局规则
- 从卡组中随机选取一首歌曲播放（10-30秒）
- **对局成功**：该卡对应的歌曲从队列中移除
- **对局失败**：按规则处理（轮空/弃牌）
- **中场休息**：播放休息音乐后，操作者点击"准备"开始下一回合

## 📁 项目结构

```
karuta/
├── src/
│   ├── config/              # 配置管理
│   │   ├── ConfigManager.java
│   │   └── constants.txt
│   ├── model/                # 数据模型
│   │   ├── Card.java         # 卡牌类
│   │   ├── Song.java         # 歌曲类
│   │   ├── Deck.java         # 卡组类
│   │   └── GameState.java    # 游戏状态
│   ├── audio/                # 音频处理
│   │   ├── AudioPlayer.java  # 音乐播放器
│   │   └── AudioFormat.java  # 格式支持
│   ├── game/                 # 游戏逻辑
│   │   ├── GameEngine.java   # 游戏引擎
│   │   ├── RoundManager.java # 回合管理
│   │   └── GameRules.java    # 游戏规则
│   ├── ui/                   # UI界面（JavaFX）
│   │   ├── MainWindow.java   # 主窗口
│   │   ├── GameScreen.java   # 游戏界面
│   │   ├── AdminPanel.java   # 后台管理面板
│   │   └── styles.css        # 样式表
│   └── Main.java             # 启动类
├── config/                   # 配置文件
│   ├── decks/                # 卡组配置
│   │   └── deck1.csv         # 卡组1配置
│   ├── music/                # 音乐文件夹
│   ├── images/               # 图片文件夹
│   └── config.txt            # 主配置文件
├── pom.xml                   # Maven项目配置（如果使用Maven）
└── docs/                     # 文档
    ├── CSV_FORMAT.md         # CSV格式说明
    └── ARCHITECTURE.md       # 架构设计文档
```

## 🚀 快速开始

### 环境要求
- Java 11+
- JavaFX SDK 11+（Windows）
- Android SDK 28+（Android，后续）

### 配置文件格式

#### 卡组配置文件 (deck1.csv)
```csv
image_name,work_name,songs
card1.png,作品A,song1.mp3|song2.mp3|song3.mp3
card2.png,作品B,song4.mp3
card3.png,作品C,song5.mp3|song6.mp3
```

#### 主配置文件 (config.txt)
```txt
music_folder=config/music/
images_folder=config/images/
rest_music=rest.mp3
min_duration=10
max_duration=30
default_deck=config/decks/deck1.csv
```

### 编译运行
```bash
# 编译
javac -cp .:javafx-sdk/lib/* src/Main.java

# 运行
java -cp .:javafx-sdk/lib/* --add-modules javafx.controls,javafx.media Main
```

## 📦 依赖

- **JavaFX**：GUI框架
- **VLC-j** 或 **xuggle**：多格式音频支持
- **Apache Commons Lang**：工具类

## 🎮 使用流程

### 游戏流程
1. **启动** → 选择卡组
2. **对局开始** → 系统随机从卡组中选取一张卡
3. **播放歌曲** → 随机选取该卡对应的一首歌曲播放（10-30秒）
4. **操作** → 点击"成功"或"失败"按钮
5. **中场休息** → 播放休息音乐
6. **点击"准备"** → 开始下一回合

### 后台管理面板
- 实时显示当前场上的卡牌状态
- 可以手动移除/添加卡牌
- 查看已播放歌曲列表
- 调整播放音量和时长

## 🛠️ 可扩展性（后续版本）

- [ ] Android应用（使用Android Studio）
- [ ] 网络对战模式
- [ ] 数据统计和排行榜
- [ ] 自定义游戏规则
- [ ] 歌曲评分系统

## 📝 许可证

MIT

>>>>>>> cf830c4 (feat: FIRST COMMIT)
