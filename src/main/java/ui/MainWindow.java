package ui;

import audio.AudioPlayer;
import config.ConfigManager;
import game.GameEngine;
import game.GameRules;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import model.Deck;
import model.Song;

import java.io.IOException;
import java.util.List;

/**
 * 应用入口，负责串联配置、游戏服务和 JavaFX 界面。
 */
public class MainWindow extends Application {
    private Stage primaryStage;
    private ConfigManager configManager;
    private GameEngine gameEngine;
    private GameRules gameRules;

    private GameScreen gameScreen;
    private AdminPanel adminPanel;
    private DeckSelectionScreen deckSelectionScreen;

    /**
     * 启动应用并显示牌组选择界面。
     */
    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        primaryStage.setTitle("歌牌对战");
        primaryStage.setWidth(1200);
        primaryStage.setHeight(800);

        try {
            configManager = new ConfigManager("config");
            gameRules = new GameRules(configManager);
            gameEngine = new GameEngine(new AudioPlayer(), gameRules);
            deckSelectionScreen = new DeckSelectionScreen(this);
            showDeckSelectionScreen();
        } catch (Exception e) {
            e.printStackTrace();
            StartupLogger.logException("MainWindow.start failed", e);
            showErrorDialog("配置加载失败", e.getMessage() + "\n日志文件: " + StartupLogger.getLogFilePath());
        }

        primaryStage.show();
    }

    /**
     * 将用户带回数据集选择界面。
     */
    public void showDeckSelectionScreen() {
        Scene scene = deckSelectionScreen.getScene();
        primaryStage.setScene(scene);
    }

    /**
     * 应用所选游戏选项并打开游戏界面。
     */
    public void startGame(
            Deck selectedDeck,
            int totalRounds,
            boolean enableRestMusic,
            GameRules.FailureMode failureMode,
            List<Song> restMusicPool) {
        try {
            gameRules.setRestMusicEnabled(enableRestMusic);
            gameRules.setFailureMode(failureMode);
            gameRules.setRestMusicPool(restMusicPool);
            gameScreen = new GameScreen(this, gameEngine);
            gameScreen.initialize(selectedDeck, totalRounds);
            primaryStage.setWidth(Math.max(primaryStage.getWidth(), 1540));
            primaryStage.setHeight(Math.max(primaryStage.getHeight(), 950));
            primaryStage.setScene(gameScreen.getScene());
            openAdminPanel();
        } catch (Exception e) {
            showErrorDialog("启动游戏失败", e.getMessage());
        }
    }

    /**
     * 打开用于监控实时牌组状态的辅助管理窗口。
     */
    private void openAdminPanel() {
        adminPanel = new AdminPanel(gameEngine);
        adminPanel.show();
    }

    /**
     * 重新创建引擎，确保下一局从干净的播放状态开始。
     */
    public void returnToDeckSelection() {
        gameEngine.dispose();
        gameEngine = new GameEngine(new AudioPlayer(), gameRules);

        if (adminPanel != null) {
            adminPanel.close();
            adminPanel = null;
        }

        showDeckSelectionScreen();
    }

    /**
     * 显示由主窗口持有的错误对话框。
     */
    public void showErrorDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * 显示简单的信息提示对话框。
     */
    public void showInfoDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public GameEngine getGameEngine() {
        return gameEngine;
    }

    public GameRules getGameRules() {
        return gameRules;
    }

    /**
     * 在应用退出时释放音频资源。
     */
    @Override
    public void stop() {
        if (gameEngine != null) {
            gameEngine.dispose();
        }
    }

    /**
     * 标准的 JavaFX 启动入口。
     */
    public static void main(String[] args) {
        launch(args);
    }
}
