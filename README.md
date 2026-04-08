# Karuta Jukebox

Karuta Jukebox 是一个基于 JavaFX 的桌面应用，用于组织“听歌猜作品”对局。  
它提供数据集管理、开局选卡、回合控制、休息音乐和后台管理能力。

## 核心功能

- 通过 CSV 管理数据集（每个作品支持多首歌曲）。
- 在 GUI 中新增/编辑作品，不必手工维护 CSV。
- 导入 `flac` 时自动调用 `ffmpeg` 转码为 `mp3`。
- `Start` 后进入可视化选卡窗口，手动决定本局参赛卡牌。
- 回合中可随时点击 `Success` / `Failure` 结束当前回合。
- 休息阶段可播放休息音乐，来源优先为“未参赛卡牌歌曲池”。
- 管理员面板可实时查看并调整 active/inactive 卡牌。

## 环境要求

- JDK 17
- Maven 3.9+
- `ffmpeg`（仅在导入 `flac` 时必需）

## 快速运行

```bash
mvn clean javafx:run
```

## 打包

```bash
mvn clean package
```

## 目录约定

- 代码内置配置：`src/main/resources/config/`
- 运行时常用资源目录：
- `config/decks/`（数据集 CSV）
- `config/images/`（图片）
- `config/music/`（音频）

应用启动时会优先寻找包含 `config.txt` 的配置目录。

## 数据集格式

CSV 表头：

```csv
image_name,work_name,songs,song_display_names
```

示例：

```csv
image_name,work_name,songs,song_display_names
demo_a.jpg,作品A,song_a_1.mp3|song_a_2.mp3,曲名A1|曲名A2
demo_b.jpg,作品B,song_b_1.mp3,曲名B1
```

支持播放格式：`mp3`、`wav`、`m4a`、`aif`、`aiff`。  
导入时可选 `flac`，保存时会转为 `mp3`。

## 文档导航

- [QUICKSTART.md](./QUICKSTART.md)：5 分钟跑通。
- [GET_STARTED.md](./GET_STARTED.md)：从零准备素材到开局。
- [USER_MANUAL.md](./USER_MANUAL.md)：面向使用者的完整操作手册。
- [COMPLETE_GUIDE.md](./COMPLETE_GUIDE.md)：功能总览与系统说明。
- [docs/CSV_FORMAT.md](./docs/CSV_FORMAT.md)：CSV 与资源规范。
- [docs/DEVELOPMENT.md](./docs/DEVELOPMENT.md)：开发与维护说明。
