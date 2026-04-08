# DEVELOPMENT

## 1. 技术栈

- Java 17
- JavaFX（controls/media）
- Maven

## 2. 模块结构

```text
audio/   # 播放控制
config/  # 配置与 CSV 加载
game/    # 对局规则与状态流转
model/   # Card/Deck/Song/GameState
ui/      # JavaFX 界面
```

## 3. 关键流程

1. `ConfigManager` 解析 `config.txt` 与数据集 CSV。
2. `DeckSelectionScreen` 选择数据集与开局参数。
3. `CardSelectionDialog` 选择本局参赛卡牌。
4. `GameEngine` 驱动回合：`Prepare -> 播放 -> 提交结果 -> 休息`。
5. `AdminPanel` 对 active/inactive 池做实时干预。

## 4. 构建与运行

```bash
mvn clean compile
mvn clean javafx:run
mvn clean package
```

## 5. 开发约定

- CSV 表头保持为 `image_name,work_name,songs,song_display_names`。
- 新增音频格式时，需同时更新：
- `ConfigManager#isSupportedAudioFormat`
- `AudioPlayer#isPlayableFormat`
- 导入界面的文件过滤逻辑
- 修改对局流程时，同时检查 `GameScreen` 与 `GameEngine` 的状态同步。

## 6. 常见维护点

- 配置目录解析：优先检查 `config.txt` 真实所在目录。
- 媒体文件缺失：加载会给出警告并跳过不可用条目。
- `flac` 转码失败：优先排查 `ffmpeg` 可执行与 PATH。
