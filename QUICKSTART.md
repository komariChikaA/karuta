# QUICKSTART

目标：5 分钟内启动并跑通一局。

## 1. 准备环境

```bash
java -version
mvn -version
ffmpeg -version
```

需要：
- JDK 17
- Maven 3.9+
- `ffmpeg`（导入 `flac` 时需要）

## 2. 启动应用

```bash
mvn clean javafx:run
```

## 3. 选数据集并开局

1. 在主界面选择数据集。
2. 设置 `Cards`（本局最多参赛卡牌数）。
3. 设置 `On Failure`（`PASS` 或 `SKIP`）。
4. 点击 `Start`。
5. 在选卡弹窗确认参赛卡牌。
6. 进入对局后点击 `Prepare`。

## 4. 回合操作

- 点击 `Success`：本回合成功，当前卡牌离开 active 池。
- 点击 `Failure`：本回合失败，按 `PASS/SKIP` 规则处理。
- 回合结束后会进入休息状态，再点 `Prepare Next` 开始下一回合。

## 5. 常见问题

- 启动报找不到 `config.txt`：确认配置目录中存在该文件。
- 音频无法播放：确认是 `mp3/wav/m4a/aif/aiff` 之一。
- 选了 `flac` 但失败：确认 `ffmpeg` 在系统 PATH 中可执行。
