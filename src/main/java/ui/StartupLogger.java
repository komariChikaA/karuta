package ui;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

/**
 * 将启动期关键信息写入本地日志文件，便于排查安装包运行失败。
 */
public final class StartupLogger {
    private static final Path LOG_FILE = resolveLogFile();

    private StartupLogger() {
    }

    public static synchronized void log(String message) {
        writeLine("[INFO] " + message);
    }

    public static synchronized void logException(String context, Throwable throwable) {
        StringWriter buffer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(buffer));
        writeLine("[ERROR] " + context + System.lineSeparator() + buffer);
    }

    public static String getLogFilePath() {
        return LOG_FILE.toAbsolutePath().toString();
    }

    private static Path resolveLogFile() {
        String userHome = System.getProperty("user.home", ".");
        return Paths.get(userHome, ".karuta-jukebox", "logs", "startup.log");
    }

    private static void writeLine(String line) {
        try {
            Path parent = LOG_FILE.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            String withTimestamp = String.format("%s %s%n", LocalDateTime.now(), line);
            Files.writeString(
                    LOG_FILE,
                    withTimestamp,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (Exception ignored) {
            // 日志记录失败时不影响主流程。
        }
    }
}