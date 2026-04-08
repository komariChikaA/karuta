package ui;

import javafx.application.Application;

import javax.swing.JOptionPane;

/**
 * 纯 Java 启动器，避免把 {@link MainWindow} 直接作为 JAR/jpackage 入口。
 */
public final class Launcher {
    private Launcher() {
    }

    public static void main(String[] args) {
        StartupLogger.log("Launcher started.");
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            throwable.printStackTrace();
            StartupLogger.logException("Uncaught exception in thread: " + thread.getName(), throwable);
        });

        try {
            Application.launch(MainWindow.class, args);
        } catch (Throwable throwable) {
            StartupLogger.logException("Application.launch failed", throwable);
            JOptionPane.showMessageDialog(
                    null,
                    "应用启动失败。\n日志文件: " + StartupLogger.getLogFilePath(),
                    "Karuta 启动失败",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}