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

public class MainWindow extends Application {
    private Stage primaryStage;
    private ConfigManager configManager;
    private GameEngine gameEngine;
    private GameRules gameRules;

    private GameScreen gameScreen;
    private AdminPanel adminPanel;
    private DeckSelectionScreen deckSelectionScreen;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        primaryStage.setTitle("Karuta Jukebox");
        primaryStage.setWidth(1200);
        primaryStage.setHeight(800);

        try {
            configManager = new ConfigManager("config");
            gameRules = new GameRules(configManager);
            gameEngine = new GameEngine(new AudioPlayer(), gameRules);
            deckSelectionScreen = new DeckSelectionScreen(this);
            showDeckSelectionScreen();
        } catch (IOException e) {
            showErrorDialog("Failed to load config", e.getMessage());
        }

        primaryStage.show();
    }

    public void showDeckSelectionScreen() {
        Scene scene = deckSelectionScreen.getScene();
        primaryStage.setScene(scene);
    }

    public void startGame(
        Deck selectedDeck,
        int totalRounds,
        boolean enableRestMusic,
        GameRules.FailureMode failureMode,
        List<Song> restMusicPool
    ) {
        try {
            gameRules.setRestMusicEnabled(enableRestMusic);
            gameRules.setFailureMode(failureMode);
            gameRules.setRestMusicPool(restMusicPool);
            gameScreen = new GameScreen(this, gameEngine);
            gameScreen.initialize(selectedDeck, totalRounds);
            primaryStage.setScene(gameScreen.getScene());
            openAdminPanel();
        } catch (Exception e) {
            showErrorDialog("Failed to start game", e.getMessage());
        }
    }

    private void openAdminPanel() {
        adminPanel = new AdminPanel(gameEngine);
        adminPanel.show();
    }

    public void returnToDeckSelection() {
        gameEngine.dispose();
        gameEngine = new GameEngine(new AudioPlayer(), gameRules);

        if (adminPanel != null) {
            adminPanel.close();
            adminPanel = null;
        }

        showDeckSelectionScreen();
    }

    public void showErrorDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

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

    @Override
    public void stop() {
        if (gameEngine != null) {
            gameEngine.dispose();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
