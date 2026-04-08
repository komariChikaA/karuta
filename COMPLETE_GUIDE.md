# COMPLETE GUIDE

## 1. 文档定位

本文件是当前版本的完整能力说明，面向使用者与维护者。  
快速上手请先看 [QUICKSTART.md](./QUICKSTART.md)。

## 2. 功能总览

### 2.1 数据层

- CSV 加载/保存，支持引号与逗号内容。
- 写出 UTF-8 BOM，提升 Windows/Excel 兼容性。
- 单作品多歌曲，支持独立显示名。

### 2.2 导入层

- GUI 中逐作品导入和编辑。
- 支持导入/导出 ZIP 数据包。
- `flac` 自动转 `mp3`（依赖 ffmpeg）。

### 2.3 对局层

- `Start` 后先选卡。
- 第一回合和每回合结束后都要手动 `Prepare`。
- 回合中可即时提交 `Success` / `Failure`。
- 失败处理支持 `PASS` / `SKIP`。

### 2.4 休息音乐

- 来源优先：未参赛卡牌歌曲池。
- 若为空，回退到 `config.txt` 的 `rest_music`。
- 休息阶段支持暂停/恢复控制。

### 2.5 管理层

- 管理员面板显示 active/inactive 池。
- 支持手动移卡与状态导出。

## 3. 目录结构

```text
src/main/java/
  audio/
  config/
  game/
  model/
  ui/

src/main/resources/config/
  config.txt
  decks/
  images/
  music/
```

## 4. 运行与构建

```bash
mvn clean javafx:run
mvn clean package
```

## 5. 已知约束

- 构建目标是 JDK 17。
- JavaFX 播放侧不直接使用 flac，需导入时转码。
- 素材规模较大时，建议将资源目录放在仓库外并通过配置指向。
