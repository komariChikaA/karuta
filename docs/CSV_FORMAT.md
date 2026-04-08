# CSV FORMAT

## 1. 数据集文件

文件位置：`config/decks/*.csv`  
推荐编码：UTF-8（程序保存时会写入 UTF-8 BOM）

标准表头：

```csv
image_name,work_name,songs,song_display_names
```

字段说明：

- `image_name`：图片文件名（位于 `images_folder`）。
- `work_name`：作品名。
- `songs`：歌曲文件名列表，使用 `|` 分隔。
- `song_display_names`：歌曲显示名列表，使用 `|` 分隔；可留空。

## 2. 示例

```csv
image_name,work_name,songs,song_display_names
gal_demo.jpg,Demo Work,demo_1.mp3|demo_2.mp3,Demo Song 1|Demo Song 2
gal_demo2.jpg,Demo Work 2,demo_3.mp3,Demo Song 3
```

## 3. 规则与注意事项

- 文件名只写“文件名”，不要写完整路径。
- `songs` 和 `song_display_names` 建议一一对应。
- 含逗号或引号的文本应使用 CSV 引号转义。
- 某条记录如果所有歌曲都不存在，会被加载时忽略。

## 4. 资源目录

默认由 `config.txt` 指定：

```properties
music_folder=config/music/
images_folder=config/images/
default_deck=config/decks/deck1.csv
```

## 5. 音频格式支持

播放支持：

- `mp3`
- `wav`
- `m4a`
- `aif`
- `aiff`

导入时允许选择 `flac`，保存时会自动转成 `mp3`。
