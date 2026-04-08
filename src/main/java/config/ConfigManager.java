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

/**
 * 加载项目配置，并将数据集 CSV 转换为领域对象。
 */
public class ConfigManager {
    private static final String CONFIG_FILE = "config.txt";

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
     * 解析配置根目录，然后加载并校验配置文件。
     */
    public ConfigManager(String baseDir) throws IOException {
        this.baseDir = resolveBaseDir(baseDir);
        this.rawConfig = new HashMap<>();
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

        throw new FileNotFoundException("Config directory not found: " + requestedBaseDir);
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
     * 尝试从 classpath 中解析配置目录，支持目录资源和单个 config.txt 文件资源。
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
     * 生成 classpath 的候选资源名，兼容目录和 config.txt 两种布局。
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
     * 检查目录中是否存在预期的配置入口文件。
     */
    private boolean containsConfigFile(File directory) {
        return directory.isDirectory() && new File(directory, CONFIG_FILE).isFile();
    }

    /**
     * 从 UTF-8 配置文件读取键值对，并跳过空行和注释行。
     */
    private void loadConfigFile() throws IOException {
        File configFile = new File(baseDir, CONFIG_FILE);
        if (!configFile.exists()) {
            throw new FileNotFoundException("Config file not found: " + configFile.getAbsolutePath());
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    rawConfig.put(parts[0].trim(), parts[1].trim());
                }
            }
        }
    }

    /**
     * 在读取原始配置后解析路径并校验时长参数。
     */
    private void validateConfig() {
        musicFolder = resolveConfiguredPath(getConfigValue("music_folder", "config/music/")).getPath();
        imagesFolder = resolveConfiguredPath(getConfigValue("images_folder", "config/images/")).getPath();
        restMusic = getConfigValue("rest_music", "rest.mp3");
        minDuration = Integer.parseInt(getConfigValue("min_duration", "10"));
        maxDuration = Integer.parseInt(getConfigValue("max_duration", "30"));
        defaultDeck = resolveConfiguredPath(getConfigValue("default_deck", "config/decks/deck1.csv")).getPath();
        failureMode = getConfigValue("failure_mode", "PASS");

        if (minDuration <= 0 || maxDuration <= 0 || minDuration > maxDuration) {
            throw new IllegalArgumentException(
                    String.format("Invalid duration config: min=%d, max=%d", minDuration, maxDuration));
        }
    }

    private String getConfigValue(String key, String defaultValue) {
        return rawConfig.getOrDefault(key, defaultValue);
    }

    /**
     * 在需要时相对于当前配置目录解析配置路径。
     */
    private File resolveConfiguredPath(String configuredPath) {
        File directPath = new File(configuredPath);
        if (directPath.exists()) {
            return directPath;
        }

        String normalized = configuredPath.replace('\\', '/');
        if (normalized.startsWith("config/")) {
            return new File(baseDir, normalized.substring("config/".length()));
        }

        return new File(baseDir, normalized);
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
