package config;

import model.Card;
import model.Deck;
import model.Song;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * 加载项目配置，并将数据集 CSV 转换为领域对象。
 */
public class ConfigManager {
    private static final String CONFIG_FILE = "config.toml";
    private static final String LEGACY_CONFIG_FILE = "config.txt";

    private static final String DEFAULT_CONFIG_TOML = """
            # 歌牌点歌系统配置

            [paths]
            music = "music/"
            images = "images/"
            decks = "decks/"

            [audio]
            rest_music = "rest.mp3"
            min_duration = 10
            max_duration = 30

            [game]
            default_deck = "decks/deck1.csv"
            failure_mode = "PASS"
            enable_rest_music = true
            auto_continue_after_rest = false
            """;

    private final File baseDir;
    private final Map<String, String> rawConfig;

    private String musicFolder;
    private String imagesFolder;
    private String restMusic;
    private int minDuration;
    private int maxDuration;
    private String defaultDeck;
    private String failureMode;

    /**
     * 解析配置根目录，确保配置文件和子目录存在，然后加载并校验。
     */
    public ConfigManager(String baseDir) throws IOException {
        this.baseDir = resolveBaseDir(baseDir);
        this.rawConfig = new HashMap<>();
        ensureConfigExists();
        loadConfigFile();
        validateConfig();
    }

    /**
     * 支持直接路径、资源路径以及打包后的资源目录。
     */
    private File resolveBaseDir(String requestedBaseDir) throws IOException {
        File directPath = new File(requestedBaseDir);
        if (containsConfigFile(directPath)) {
            return directPath.getCanonicalFile();
        }

        File sourceResourcesPath = new File("src/main/resources", requestedBaseDir);
        if (containsConfigFile(sourceResourcesPath)) {
            return sourceResourcesPath.getCanonicalFile();
        }

        File jpackageAppDir = resolveFromJpackageAppDir(requestedBaseDir);
        if (jpackageAppDir != null) {
            return jpackageAppDir.getCanonicalFile();
        }

        if (directPath.isDirectory()) {
            return directPath.getCanonicalFile();
        }

        if (sourceResourcesPath.isDirectory()) {
            return sourceResourcesPath.getCanonicalFile();
        }

        File codeSourceDirectory = resolveFromCodeSource(requestedBaseDir);
        if (codeSourceDirectory != null) {
            return codeSourceDirectory.getCanonicalFile();
        }

        ClassLoader classLoader = ConfigManager.class.getClassLoader();
        File bundledDirectory = resolveBundledConfigDirectory(classLoader, requestedBaseDir);
        if (bundledDirectory != null) {
            return bundledDirectory.getCanonicalFile();
        }

        throw new FileNotFoundException(
                "Config directory not found: " + requestedBaseDir
                + " (CWD=" + new File(".").getAbsolutePath()
                + ", code-source=" + getCodeSourcePath() + ")");
    }

    /**
     * jpackage 安装时，配置目录位于应用 JAR 所在的 app/ 目录内。
     * <p>
     * jpackage 启动器设置系统属性 {@code jpackage.app-path} 指向可执行文件路径，
     * 其同级 {@code app/} 子目录包含所有通过 {@code --input} 打包的文件。
     * 同时也通过代码源位置做回退检查。
     */
    private File resolveFromJpackageAppDir(String requestedBaseDir) {
        File candidate = resolveViaJpackageProperty(requestedBaseDir);
        if (candidate != null) {
            return candidate;
        }

        return resolveViaCodeSourceParent(requestedBaseDir);
    }

    private File resolveViaJpackageProperty(String requestedBaseDir) {
        String appPath = System.getProperty("jpackage.app-path");
        if (appPath == null) {
            return null;
        }

        File appExe = new File(appPath);
        File installDir = appExe.getParentFile();
        if (installDir == null) {
            return null;
        }

        File appDir = new File(installDir, "app");
        File candidate = new File(appDir, requestedBaseDir);
        if (containsConfigFile(candidate)) {
            return candidate;
        }

        return null;
    }

    private File resolveViaCodeSourceParent(String requestedBaseDir) {
        try {
            URL codeSourceUrl = ConfigManager.class.getProtectionDomain()
                    .getCodeSource().getLocation();
            if (codeSourceUrl == null) {
                return null;
            }

            File codeSource = new File(codeSourceUrl.toURI());
            File appDir = codeSource.isFile() ? codeSource.getParentFile() : codeSource;
            if (appDir == null) {
                return null;
            }

            File candidate = new File(appDir, requestedBaseDir);
            if (containsConfigFile(candidate)) {
                return candidate;
            }
        } catch (Exception ignored) {
            // Fall through to other strategies
        }

        return null;
    }

    /**
     * 用于诊断日志的辅助方法，返回代码源路径。
     */
    private String getCodeSourcePath() {
        try {
            URL url = ConfigManager.class.getProtectionDomain()
                    .getCodeSource().getLocation();
            return url != null ? url.toString() : "null";
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    /**
     * 优先查找 jar 或应用程序所在目录旁边的 config 目录，适配 jpackage 安装包。
     */
    private File resolveFromCodeSource(String requestedBaseDir) throws IOException {
        try {
            URL codeSourceUrl = ConfigManager.class.getProtectionDomain().getCodeSource().getLocation();
            if (codeSourceUrl == null) {
                return null;
            }

            File codeSource = new File(codeSourceUrl.toURI());
            File rootDirectory = codeSource.isFile() ? codeSource.getParentFile() : codeSource;
            if (rootDirectory == null) {
                return null;
            }

            File siblingConfig = new File(rootDirectory, requestedBaseDir);
            if (containsConfigFile(siblingConfig)) {
                return siblingConfig;
            }

            if (requestedBaseDir.startsWith("config/")) {
                File nestedConfig = new File(rootDirectory, requestedBaseDir);
                if (nestedConfig.isDirectory()) {
                    return nestedConfig;
                }
            }

            File directConfigFile = new File(rootDirectory, CONFIG_FILE);
            if (directConfigFile.isFile()) {
                return directConfigFile.getParentFile();
            }

            return null;
        } catch (URISyntaxException e) {
            throw new IOException("Failed to resolve config from code source.", e);
        }
    }

    /**
     * 尝试从 classpath 中解析配置目录，支持目录资源和配置文件资源。
     */
    private File resolveBundledConfigDirectory(ClassLoader classLoader, String requestedBaseDir) throws IOException {
        for (String resourceName : buildResourceCandidates(requestedBaseDir)) {
            URL resourceUrl = classLoader.getResource(resourceName);
            if (resourceUrl == null) {
                continue;
            }

            try {
                if ("file".equals(resourceUrl.getProtocol())) {
                    File resourceFile = new File(resourceUrl.toURI());
                    if (resourceFile.isDirectory()) {
                        return resourceFile;
                    }

                    if (CONFIG_FILE.equals(resourceFile.getName())) {
                        return resourceFile.getParentFile();
                    }
                }

                if ("jar".equals(resourceUrl.getProtocol())) {
                    return extractBundledDirectory(resourceUrl, resourceName);
                }
            } catch (Exception e) {
                throw new IOException("Failed to resolve config directory: " + requestedBaseDir, e);
            }
        }

        return null;
    }

    /**
     * 生成 classpath 的候选资源名，兼容目录和配置文件两种布局。
     */
    private List<String> buildResourceCandidates(String requestedBaseDir) {
        List<String> candidates = new ArrayList<>();
        String normalizedBaseDir = requestedBaseDir.replace('\\', '/');

        if (!normalizedBaseDir.isEmpty()) {
            candidates.add(normalizedBaseDir);
            if (!normalizedBaseDir.endsWith("/")) {
                candidates.add(normalizedBaseDir + "/");
            }
            candidates.add(normalizedBaseDir + "/" + CONFIG_FILE);
        }

        candidates.add(CONFIG_FILE);
        candidates.add("config/" + CONFIG_FILE);
        return candidates;
    }

    /**
     * 将 jar 内的配置资源抽取到临时目录，便于后续继续使用文件系统访问。
     */
    private File extractBundledDirectory(URL resourceUrl, String resourceName) throws IOException {
        JarURLConnection jarConnection = (JarURLConnection) resourceUrl.openConnection();
        try (JarFile jarFile = jarConnection.getJarFile()) {
            Path tempDirectory = Files.createTempDirectory("karuta-config-");
            String entryPrefix;

            if (resourceName.endsWith(CONFIG_FILE)) {
                entryPrefix = resourceName.substring(0, resourceName.length() - CONFIG_FILE.length());
            } else {
                entryPrefix = resourceName.endsWith("/") ? resourceName : resourceName + "/";
            }

            jarFile.stream()
                    .filter(entry -> !entry.isDirectory() && entry.getName().startsWith(entryPrefix))
                    .forEach(entry -> copyJarEntry(jarFile, entry, tempDirectory, entryPrefix));

            return tempDirectory.toFile();
        }
    }

    /**
     * 复制单个 jar 条目到临时配置目录。
     */
    private void copyJarEntry(JarFile jarFile, JarEntry entry, Path tempDirectory, String entryPrefix) {
        Path targetFile = tempDirectory.resolve(entry.getName().substring(entryPrefix.length()));
        try {
            Path parent = targetFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (var inputStream = jarFile.getInputStream(entry)) {
                Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract config resource: " + entry.getName(), e);
        }
    }

    /**
     * 检查目录中是否存在配置文件（TOML 或旧版 txt）。
     */
    private boolean containsConfigFile(File directory) {
        if (!directory.isDirectory()) {
            return false;
        }
        return new File(directory, CONFIG_FILE).isFile()
                || new File(directory, LEGACY_CONFIG_FILE).isFile();
    }

    /**
     * 首次运行时自动生成配置文件和子目录；如果存在旧版 config.txt，自动迁移。
     */
    private void ensureConfigExists() throws IOException {
        File configFile = new File(baseDir, CONFIG_FILE);
        File legacyFile = new File(baseDir, LEGACY_CONFIG_FILE);

        if (!configFile.exists()) {
            if (legacyFile.exists()) {
                migrateFromLegacy(legacyFile, configFile);
            } else {
                Files.writeString(configFile.toPath(), DEFAULT_CONFIG_TOML, StandardCharsets.UTF_8);
            }
        }

        String[] subDirs = {"music", "images", "decks"};
        for (String dir : subDirs) {
            File subDir = new File(baseDir, dir);
            if (!subDir.exists()) {
                Files.createDirectories(subDir.toPath());
            }
        }
    }

    /**
     * 将旧版 key=value 格式的 config.txt 迁移为 TOML 格式。
     */
    private void migrateFromLegacy(File legacyFile, File tomlFile) throws IOException {
        Map<String, String> legacy = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(legacyFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    legacy.put(parts[0].trim(), parts[1].trim());
                }
            }
        }

        String toml = String.format("""
                # 歌牌点歌系统配置（从 config.txt 自动迁移）

                [paths]
                music = "%s"
                images = "%s"
                decks = "decks/"

                [audio]
                rest_music = "%s"
                min_duration = %s
                max_duration = %s

                [game]
                default_deck = "%s"
                failure_mode = "%s"
                enable_rest_music = %s
                auto_continue_after_rest = %s
                """,
                stripConfigPrefix(legacy.getOrDefault("music_folder", "music/")),
                stripConfigPrefix(legacy.getOrDefault("images_folder", "images/")),
                legacy.getOrDefault("rest_music", "rest.mp3"),
                legacy.getOrDefault("min_duration", "10"),
                legacy.getOrDefault("max_duration", "30"),
                stripConfigPrefix(legacy.getOrDefault("default_deck", "decks/deck1.csv")),
                legacy.getOrDefault("failure_mode", "PASS"),
                legacy.getOrDefault("enable_rest_music", "true"),
                legacy.getOrDefault("auto_continue_after_rest", "false"));

        Files.writeString(tomlFile.toPath(), toml, StandardCharsets.UTF_8);
    }

    private String stripConfigPrefix(String path) {
        String normalized = path.replace('\\', '/');
        return normalized.startsWith("config/") ? normalized.substring("config/".length()) : normalized;
    }

    /**
     * 使用 tomlj 解析 TOML 配置文件，并扁平化为 dotted key map。
     */
    private void loadConfigFile() throws IOException {
        File configFile = new File(baseDir, CONFIG_FILE);
        if (!configFile.exists()) {
            throw new FileNotFoundException("Config file not found: " + configFile.getAbsolutePath());
        }

        TomlParseResult toml = Toml.parse(configFile.toPath());
        if (toml.hasErrors()) {
            throw new IOException("TOML parse error in " + configFile.getAbsolutePath()
                    + ": " + toml.errors().get(0).toString());
        }

        flattenToml(toml, "", rawConfig);
    }

    private void flattenToml(TomlTable table, String prefix, Map<String, String> target) {
        for (String key : table.keySet()) {
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = table.get(key);
            if (value instanceof TomlTable nested) {
                flattenToml(nested, fullKey, target);
            } else {
                target.put(fullKey, String.valueOf(value));
            }
        }
    }

    /**
     * 在读取 TOML 配置后解析路径并校验时长参数。
     */
    private void validateConfig() {
        musicFolder = resolveConfiguredPath(getTomlValue("paths.music", "music/")).getPath();
        imagesFolder = resolveConfiguredPath(getTomlValue("paths.images", "images/")).getPath();
        restMusic = getTomlValue("audio.rest_music", "rest.mp3");
        minDuration = Integer.parseInt(getTomlValue("audio.min_duration", "10"));
        maxDuration = Integer.parseInt(getTomlValue("audio.max_duration", "30"));
        defaultDeck = resolveConfiguredPath(getTomlValue("game.default_deck", "decks/deck1.csv")).getPath();
        failureMode = getTomlValue("game.failure_mode", "PASS");

        if (minDuration <= 0 || maxDuration <= 0 || minDuration > maxDuration) {
            throw new IllegalArgumentException(
                    String.format("Invalid duration config: min=%d, max=%d", minDuration, maxDuration));
        }
    }

    private String getTomlValue(String dottedKey, String defaultValue) {
        return rawConfig.getOrDefault(dottedKey, defaultValue);
    }

    /**
     * 将配置中的相对路径解析为基于配置目录的绝对路径。
     */
    private File resolveConfiguredPath(String configuredPath) {
        File directPath = new File(configuredPath);
        if (directPath.isAbsolute() && directPath.exists()) {
            return directPath;
        }
        return new File(baseDir, configuredPath);
    }

    /**
     * 将数据集 CSV 解析为完整的牌组对象。
     */
    public Deck loadDeck(String deckFilePath) throws IOException {
        File deckFile = new File(deckFilePath);
        if (!deckFile.exists()) {
            throw new FileNotFoundException("Deck file not found: " + deckFile.getAbsolutePath());
        }

        String deckName = deckFile.getName().replace(".csv", "");
        Deck deck = new Deck(deckName, deckFilePath);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(deckFile), StandardCharsets.UTF_8))) {
            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                parseDeckLine(line, deck);
            }
        }

        return deck;
    }

    /**
     * 将一行 CSV 转换为一张卡牌及其关联歌曲，并跳过无效资源。
     */
    private void parseDeckLine(String line, Deck deck) {
        List<String> parts = parseCsvLine(line);
        if (parts.size() < 3) {
            System.err.println("Invalid deck row: " + line);
            return;
        }

        String imageName = parts.get(0).trim();
        String workName = parts.get(1).trim();
        String songsList = parts.get(2).trim();
        String songDisplayNames = parts.size() >= 4 ? parts.get(3).trim() : "";

        Card card = new Card(imageName, workName);

        File imageFile = new File(imagesFolder, imageName);
        card.setImageFile(imageFile);
        if (!imageFile.exists()) {
            System.err.println("Image file not found: " + imageFile.getAbsolutePath());
        }

        String[] songs = songsList.split("\\|");
        String[] displayNames = songDisplayNames.isEmpty() ? new String[0] : songDisplayNames.split("\\|");

        for (int i = 0; i < songs.length; i++) {
            String trimmedSongName = songs[i].trim();
            if (trimmedSongName.isEmpty()) {
                continue;
            }

            File musicFile = new File(musicFolder, trimmedSongName);
            if (!musicFile.exists()) {
                System.err.println("Audio file not found: " + musicFile.getAbsolutePath());
                continue;
            }

            String displayName = i < displayNames.length ? displayNames[i].trim() : "";
            card.addSong(new Song(trimmedSongName, musicFile, displayName));
        }

        if (card.getSongCount() > 0) {
            deck.addCard(card);
        }
    }

    /**
     * 处理带引号的 CSV 字段和转义双引号。
     */
    private List<String> parseCsvLine(String line) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char currentChar = line.charAt(i);

            if (currentChar == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }

            if (currentChar == ',' && !inQuotes) {
                parts.add(current.toString());
                current.setLength(0);
                continue;
            }

            current.append(currentChar);
        }

        parts.add(current.toString());
        return parts;
    }

    public boolean isSupportedAudioFormat(String fileName) {
        String format = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        return format.matches("mp3|wav|m4a|aif|aiff");
    }

    public String[] getSupportedFormats() {
        return new String[] { "mp3", "wav", "m4a", "aif", "aiff" };
    }

    public String getMusicFolder() {
        return musicFolder;
    }

    public String getImagesFolder() {
        return imagesFolder;
    }

    public String getRestMusic() {
        return restMusic;
    }

    public int getMinDuration() {
        return minDuration;
    }

    public int getMaxDuration() {
        return maxDuration;
    }

    public String getDefaultDeck() {
        return defaultDeck;
    }

    public String getFailureMode() {
        return failureMode;
    }

    /**
     * 根据配置的音乐目录解析休息音乐文件。
     */
    public File getRestMusicFile() {
        return new File(musicFolder, restMusic);
    }

    public Map<String, String> getRawConfig() {
        return new HashMap<>(rawConfig);
    }
}
