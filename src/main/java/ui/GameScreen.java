package ui;

import game.GameEngine;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import model.Card;
import model.Deck;
import model.GameState;
import model.Song;

import java.io.FileInputStream;
import java.util.stream.Collectors;

public class GameScreen {
    private final MainWindow mainWindow;
    private final GameEngine gameEngine;

    private Deck currentDeck;
    private Scene scene;

    private ImageView cardImageView;
    private Label imagePlaceholderLabel;
    private Label cardNameLabel;
    private Label songTitleLabel;
    private Label songMetaLabel;
    private Label roundLabel;
    private Label deckInfoLabel;
    private Label statusLabel;
    private Label statsLabel;
    private Label queueLabel;
    private ProgressBar playbackProgressBar;
    private Button successButton;
    private Button failureButton;
    private Button prepareButton;
    private FlowPane centerPane;

    public GameScreen(MainWindow mainWindow, GameEngine gameEngine) {
        this.mainWindow = mainWindow;
        this.gameEngine = gameEngine;
    }

    public void initialize(Deck deck, int totalRounds) {
        currentDeck = deck;
        createUI();
        setupGameEngine();
        gameEngine.startGame(deck, totalRounds);
        refreshDeckSummary();
    }

    private void createUI() {
        BorderPane root = new BorderPane();
        root.setStyle(
            "-fx-background-color: linear-gradient(to bottom right, #f4ede3, #eef4fb);" +
            "-fx-font-family: 'Segoe UI';"
        );

        root.setTop(createTopBar());
        root.setCenter(createResponsiveCenter());
        root.setBottom(createButtonBar());

        scene = new Scene(root, 1280, 820);
        scene.widthProperty().addListener((obs, oldValue, newValue) -> updateCenterLayout(newValue.doubleValue()));
        updateCenterLayout(1280);
    }

    private HBox createTopBar() {
        HBox topBar = new HBox(18);
        topBar.setPadding(new Insets(24, 28, 20, 28));
        topBar.setAlignment(Pos.CENTER_LEFT);

        VBox titleBlock = new VBox(4);
        Label titleLabel = new Label("Live Round");
        titleLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #7a8598;");

        roundLabel = new Label("Round 1 / 10");
        roundLabel.setStyle("-fx-font-size: 28; -fx-font-weight: bold; -fx-text-fill: #1d2a44;");

        deckInfoLabel = new Label("Deck ready");
        deckInfoLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #6d7686;");
        titleBlock.getChildren().addAll(titleLabel, roundLabel, deckInfoLabel);

        VBox statsCard = new VBox(6);
        statsCard.setPadding(new Insets(14, 20, 14, 20));
        statsCard.setStyle(createGlassCardStyle());

        statsLabel = new Label("Success 0 | Failure 0 | Rate 0%");
        statsLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #24324a;");

        queueLabel = new Label("Active cards 0");
        queueLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #6d7686;");
        statsCard.getChildren().addAll(statsLabel, queueLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        topBar.getChildren().addAll(titleBlock, spacer, statsCard);
        return topBar;
    }

    private ScrollPane createResponsiveCenter() {
        centerPane = new FlowPane(Orientation.HORIZONTAL, 24, 24);
        centerPane.setPadding(new Insets(0, 28, 24, 28));

        centerPane.getChildren().addAll(createArtworkPanel(), createRoundInfoPanel(), createQueuePanel());

        ScrollPane scrollPane = new ScrollPane(centerPane);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        return scrollPane;
    }

    private VBox createArtworkPanel() {
        VBox artworkPanel = new VBox(16);
        artworkPanel.setAlignment(Pos.TOP_CENTER);
        artworkPanel.setPadding(new Insets(24));
        artworkPanel.setPrefWidth(420);
        artworkPanel.setMinWidth(320);
        artworkPanel.setStyle(createPanelStyle("#fffaf3"));

        Label artworkHeader = new Label("Artwork");
        artworkHeader.setStyle("-fx-font-size: 15; -fx-font-weight: bold; -fx-text-fill: #7a5c2e;");

        StackPane imageFrame = new StackPane();
        imageFrame.setPadding(new Insets(18));
        imageFrame.setMinHeight(440);
        imageFrame.setStyle(
            "-fx-background-color: linear-gradient(to bottom, #ffffff, #f7efe4);" +
            "-fx-background-radius: 28;" +
            "-fx-border-radius: 28;" +
            "-fx-border-color: rgba(122,92,46,0.16);"
        );

        cardImageView = new ImageView();
        cardImageView.setFitWidth(320);
        cardImageView.setFitHeight(420);
        cardImageView.setPreserveRatio(true);
        cardImageView.setSmooth(true);

        imagePlaceholderLabel = new Label("Artwork preview area");
        imagePlaceholderLabel.setStyle("-fx-font-size: 16; -fx-text-fill: #8b93a3;");
        imageFrame.getChildren().addAll(cardImageView, imagePlaceholderLabel);

        cardNameLabel = new Label("Ready");
        cardNameLabel.setWrapText(true);
        cardNameLabel.setAlignment(Pos.CENTER);
        cardNameLabel.setStyle("-fx-font-size: 28; -fx-font-weight: bold; -fx-text-fill: #1d2a44;");

        Label artworkHint = new Label("Main visual area for the current card.");
        artworkHint.setWrapText(true);
        artworkHint.setAlignment(Pos.CENTER);
        artworkHint.setStyle("-fx-font-size: 12; -fx-text-fill: #7a8598;");

        artworkPanel.getChildren().addAll(artworkHeader, imageFrame, cardNameLabel, artworkHint);
        return artworkPanel;
    }

    private VBox createRoundInfoPanel() {
        VBox infoPanel = new VBox(18);
        infoPanel.setPadding(new Insets(24));
        infoPanel.setPrefWidth(420);
        infoPanel.setMinWidth(320);
        infoPanel.setStyle(createPanelStyle("#f8fbff"));

        Label infoHeader = new Label("Round Status");
        infoHeader.setStyle("-fx-font-size: 15; -fx-font-weight: bold; -fx-text-fill: #4a6589;");

        VBox stateCard = new VBox(12);
        stateCard.setPadding(new Insets(20));
        stateCard.setStyle(createGlassCardStyle());

        statusLabel = new Label("Press Prepare to start.");
        statusLabel.setWrapText(true);
        statusLabel.setStyle("-fx-font-size: 20; -fx-font-weight: bold; -fx-text-fill: #24324a;");

        songTitleLabel = new Label("Waiting");
        songTitleLabel.setWrapText(true);
        songTitleLabel.setStyle("-fx-font-size: 24; -fx-font-weight: bold; -fx-text-fill: #24324a;");

        songMetaLabel = new Label("Selected card and song information will appear here.");
        songMetaLabel.setWrapText(true);
        songMetaLabel.setStyle("-fx-font-size: 14; -fx-text-fill: #6d7686;");

        playbackProgressBar = new ProgressBar(0);
        playbackProgressBar.setPrefWidth(Double.MAX_VALUE);
        playbackProgressBar.setStyle(
            "-fx-accent: #2f7d4a;" +
            "-fx-control-inner-background: rgba(47,125,74,0.12);" +
            "-fx-background-radius: 999;"
        );

        stateCard.getChildren().addAll(statusLabel, songTitleLabel, songMetaLabel, playbackProgressBar);

        VBox helperCard = new VBox(10);
        helperCard.setPadding(new Insets(20));
        helperCard.setStyle(createGlassCardStyle());

        Label helperTitle = new Label("Flow");
        helperTitle.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #24324a;");

        Label helperText = new Label("Prepare starts a round. Success or Failure moves into rest. Prepare Next continues.");
        helperText.setWrapText(true);
        helperText.setStyle("-fx-font-size: 13; -fx-text-fill: #6d7686;");
        helperCard.getChildren().addAll(helperTitle, helperText);

        infoPanel.getChildren().addAll(infoHeader, stateCard, helperCard);
        return infoPanel;
    }

    private VBox createQueuePanel() {
        VBox queuePanel = new VBox(18);
        queuePanel.setPadding(new Insets(24));
        queuePanel.setPrefWidth(320);
        queuePanel.setMinWidth(280);
        queuePanel.setStyle(createPanelStyle("#f7fbf5"));

        Label queueHeader = new Label("Card Pool");
        queueHeader.setStyle("-fx-font-size: 15; -fx-font-weight: bold; -fx-text-fill: #3c6f4d;");

        VBox activeCard = new VBox(10);
        activeCard.setPadding(new Insets(20));
        activeCard.setStyle(createGlassCardStyle());

        Label activeTitle = new Label("Current Songs");
        activeTitle.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #24324a;");

        Label activeText = new Label("Songs linked to the current card will be shown here.");
        activeText.setWrapText(true);
        activeText.setStyle("-fx-font-size: 13; -fx-text-fill: #6d7686;");

        activeCard.getChildren().addAll(activeTitle, activeText);
        queuePanel.getChildren().addAll(queueHeader, activeCard);
        return queuePanel;
    }

    private void updateCenterLayout(double width) {
        if (centerPane == null) {
            return;
        }
        centerPane.setPrefWrapLength(Math.max(680, width - 120));
    }

    private VBox createButtonBar() {
        VBox buttonBar = new VBox(15);
        buttonBar.setPadding(new Insets(0, 28, 28, 28));
        buttonBar.setAlignment(Pos.CENTER);

        HBox roundButtonsBox = new HBox(20);
        roundButtonsBox.setAlignment(Pos.CENTER);

        successButton = createButton("Success", "#2f7d4a", e -> submitSuccess());
        failureButton = createButton("Failure", "#c65b48", e -> submitFailure());
        successButton.setDisable(true);
        failureButton.setDisable(true);
        roundButtonsBox.getChildren().addAll(successButton, failureButton);

        prepareButton = createButton("Prepare", "#3d6cb5", e -> resumeFromRest());
        prepareButton.setDisable(false);
        prepareButton.setVisible(true);

        Button returnButton = createButton("Back to Menu", "#6f7480", e -> returnToMenu());
        buttonBar.getChildren().addAll(roundButtonsBox, prepareButton, returnButton);
        return buttonBar;
    }

    private Button createButton(String text, String color, javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        Button button = new Button(text);
        button.setStyle(
            "-fx-background-color: " + color + ";" +
            "-fx-text-fill: white;" +
            "-fx-font-size: 15;" +
            "-fx-font-weight: bold;" +
            "-fx-padding: 14 28;" +
            "-fx-border-radius: 14;" +
            "-fx-background-radius: 14;" +
            "-fx-cursor: hand;"
        );
        button.setOnAction(handler);
        return button;
    }

    private void setupGameEngine() {
        gameEngine.setGameListener(new GameEngine.GameListener() {
            @Override
            public void onAwaitingStart() {
                Platform.runLater(() -> {
                    statusLabel.setText("Press Prepare to start the game.");
                    songTitleLabel.setText("Waiting to start");
                    songMetaLabel.setText("The selected cards are ready. Start when everyone is prepared.");
                    prepareButton.setText("Prepare");
                    prepareButton.setDisable(false);
                    successButton.setDisable(true);
                    failureButton.setDisable(true);
                    playbackProgressBar.setProgress(0);
                    refreshDeckSummary();
                });
            }

            @Override
            public void onRoundStart(Card card) {
                Platform.runLater(() -> {
                    cardNameLabel.setText(card.getWorkName());
                    songTitleLabel.setText("Song clip queued");
                    songMetaLabel.setText(buildSongList(card));
                    statusLabel.setText("Card selected. Waiting for playback.");
                    updateCardImage(card);
                    prepareButton.setDisable(true);
                    prepareButton.setText("Prepare Next");
                    successButton.setDisable(true);
                    failureButton.setDisable(true);
                    playbackProgressBar.setProgress(0);
                    refreshDeckSummary();
                });
            }

            @Override
            public void onMusicStart(Song song) {
                Platform.runLater(() -> {
                    songTitleLabel.setText(song.getDisplayName());
                    songMetaLabel.setText(String.format("Format %s | File %s",
                        song.getFileFormat().toUpperCase(), song.getFileName()));
                    statusLabel.setText("Playback is active. You can end this round at any time.");
                    successButton.setDisable(false);
                    failureButton.setDisable(false);
                });
            }

            @Override
            public void onMusicComplete() {
                Platform.runLater(() -> {
                    statusLabel.setText("Playback finished. Choose Success or Failure.");
                    successButton.setDisable(false);
                    failureButton.setDisable(false);
                    playbackProgressBar.setProgress(1.0);
                });
            }

            @Override
            public void onMusicInterrupted() {
                Platform.runLater(() -> statusLabel.setText("Playback stopped."));
            }

            @Override
            public void onRoundComplete(GameState.Result result) {
                Platform.runLater(() -> {
                    GameState state = gameEngine.getGameState();
                    roundLabel.setText(String.format("Round %d / %d",
                        state.getCurrentRound(), state.getTotalRounds()));
                    updateStats(state);
                    refreshDeckSummary();
                    prepareButton.setDisable(false);
                    prepareButton.setText("Prepare Next");
                    statusLabel.setText("Round complete. Press Prepare Next to continue.");
                    successButton.setDisable(true);
                    failureButton.setDisable(true);
                });
            }

            @Override
            public void onRestMusicStart() {
                Platform.runLater(() -> {
                    statusLabel.setText("Rest time. Break music will play if enabled.");
                    songTitleLabel.setText("Rest Time");
                    songMetaLabel.setText("Press Prepare Next when the table is ready.");
                    prepareButton.setDisable(false);
                    prepareButton.setText("Prepare Next");
                });
            }

            @Override
            public void onRestMusicComplete() {
            }

            @Override
            public void onGameOver(GameState state) {
                Platform.runLater(() -> {
                    refreshDeckSummary();
                    showGameOverDialog(state);
                });
            }

            @Override
            public void onError(String error) {
                Platform.runLater(() -> mainWindow.showErrorDialog("Error", error));
            }
        });
    }

    private void submitSuccess() {
        gameEngine.submitRoundResult(GameState.Result.SUCCESS);
    }

    private void submitFailure() {
        gameEngine.submitRoundResult(GameState.Result.FAILURE);
    }

    private void resumeFromRest() {
        gameEngine.prepareNextRound();
    }

    private void updateCardImage(Card card) {
        if (card == null || card.getImageFile() == null || !card.getImageFile().exists()) {
            cardImageView.setImage(null);
            imagePlaceholderLabel.setVisible(true);
            return;
        }

        try (FileInputStream inputStream = new FileInputStream(card.getImageFile())) {
            cardImageView.setImage(new Image(inputStream));
            imagePlaceholderLabel.setVisible(false);
        } catch (Exception e) {
            cardImageView.setImage(null);
            imagePlaceholderLabel.setVisible(true);
        }
    }

    private void updateStats(GameState state) {
        statsLabel.setText(String.format(
            "Success %d | Failure %d | Rate %.1f%%",
            state.getSuccessCount(),
            state.getFailureCount(),
            state.getSuccessRate()
        ));
    }

    private void refreshDeckSummary() {
        GameState state = gameEngine.getGameState();
        Deck deck = state != null ? state.getCurrentDeck() : currentDeck;
        if (deck == null) {
            deckInfoLabel.setText("Deck unavailable");
            queueLabel.setText("Active cards 0");
            return;
        }

        deckInfoLabel.setText(String.format("%s  |  Card pool up to %d", deck.getDeckName(), deck.getCardCount()));
        queueLabel.setText(String.format("Active cards %d | Inactive cards %d",
            deck.getActiveCardCount(), deck.getInactiveCards().size()));
    }

    private String buildSongList(Card card) {
        if (card == null || card.getSongs().isEmpty()) {
            return "No playable songs linked to this card.";
        }
        return card.getSongs().stream()
            .limit(4)
            .map(Song::getDisplayName)
            .collect(Collectors.joining(" / "));
    }

    private void showGameOverDialog(GameState state) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Game Over");
        alert.setContentText(String.format(
            "Game finished%n%nRounds: %d%nSuccess: %d%nFailure: %d%nRate: %.1f%%",
            state.getCurrentRound(),
            state.getSuccessCount(),
            state.getFailureCount(),
            state.getSuccessRate()
        ));
        alert.showAndWait();
        returnToMenu();
    }

    private void returnToMenu() {
        gameEngine.abortGame();
        mainWindow.returnToDeckSelection();
    }

    private String createPanelStyle(String color) {
        return "-fx-background-color: " + color + ";" +
            "-fx-background-radius: 24;" +
            "-fx-border-radius: 24;" +
            "-fx-border-color: rgba(29,42,68,0.08);" +
            "-fx-effect: dropshadow(gaussian, rgba(22,33,58,0.10), 24, 0.18, 0, 8);";
    }

    private String createGlassCardStyle() {
        return "-fx-background-color: rgba(255,255,255,0.84);" +
            "-fx-background-radius: 20;" +
            "-fx-border-radius: 20;" +
            "-fx-border-color: rgba(36,50,74,0.08);";
    }

    public Scene getScene() {
        return scene;
    }
}
