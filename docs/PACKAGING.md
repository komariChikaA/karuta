# 打包说明

## 项目结构

- `src/main/java/audio`：音频播放与媒体控制。
- `src/main/java/config`：配置读取和数据集解析。
- `src/main/java/game`：对局引擎与规则。
- `src/main/java/model`：卡牌、牌组、歌曲等领域模型。
- `src/main/java/ui`：JavaFX 界面、主窗口和弹窗。
- `src/main/resources/config`：内置默认配置入口。
- `target/`：Maven 构建输出和安装包产物。

## 构建结果

- 当前 macOS 环境已成功生成 `target/package/KarutaJukebox-1.0.0.dmg`。
- Windows `exe` 不能在 macOS 上直接产出，必须在 Windows 环境执行相同的打包命令。
- 打包前会把 `src/main/resources/config` 复制到 `target/config`，让安装包内也保留一个可读的配置目录。

## macOS 打包为 dmg

先构建项目：

```bash
mvn clean package
```

再生成安装包：

```bash
rm -rf target/package
mkdir -p target/package
jpackage \
  --type dmg \
  --input target \
  --main-jar karuta-jukebox-1.0.0.jar \
  --main-class ui.Launcher \
  --name KarutaJukebox \
  --app-version 1.0.0 \
  --dest target/package \
  --java-options --enable-native-access=ALL-UNNAMED
```

产物路径：

```text
target/package/KarutaJukebox-1.0.0.dmg
```

## Windows 打包为 exe

在 Windows 机器上先构建项目，再执行：

```powershell
mvn clean package
jpackage `
  --type exe `
  --input target `
  --main-jar karuta-jukebox-1.0.0.jar `
  --main-class ui.Launcher `
  --name KarutaJukebox `
  --app-version 1.0.0 `
  --dest target/package `
  --java-options --enable-native-access=ALL-UNNAMED
```

产物路径：

```text
target/package/KarutaJukebox-1.0.0.exe
```

## 运行时资源注意事项

当前仓库只内置了 `src/main/resources/config/config.txt`。图像、音频和数据集默认仍按外部 `config/` 目录查找，因此打包后如果要直接开局，需要把以下目录放到应用可访问的位置：

- `config/decks/`
- `config/images/`
- `config/music/`

如果这些目录不存在，应用仍可启动，但会在加载数据集时找不到资源。
