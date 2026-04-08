package ui;

import javafx.application.Application;

/**
 * 纯 Java 启动器，避免把 {@link MainWindow} 直接作为 JAR/jpackage 入口。
 */
public final class Launcher {
    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(MainWindow.class, args);
    }
}