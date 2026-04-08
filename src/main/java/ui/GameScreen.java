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
import javafx.scene.control.Slider;
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
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
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
    private Label volumeValueLabel;
    private ProgressBar playbackProgressBar;
    private Slider volumeSlider;
    private Timeline playbackTimeline;
    private Button successButton;
    private Button failureButton;
    private Button prepareButton;
    private Button restMusicToggleButton;
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
                        "-fx-font-family: 'Segoe UI';");

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
        Label titleLabel = new Label("对战中");
        titleLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #7a8598;");

        roundLabel = new Label("已进行 0 | 剩余 0");
        roundLabel.setStyle("-fx-font-size: 28; -fx-font-weight: bold; -fx-text-fill: #1d2a44;");

        deckInfoLabel = new Label("数据集已就绪");
        deckInfoLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #6d7686;");
        titleBlock.getChildren().addAll(titleLabel, roundLabel, deckInfoLabel);

        VBox statsCard = new VBox(6);
        statsCard.setPadding(new Insets(14, 20, 14, 20));
        statsCard.setStyle(createGlassCardStyle());

        statsLabel = new Label("成功 0 | 失败 0 | 成功率 0%");
        statsLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #24324a;");

        queueLabel = new Label("在场卡牌 0");
        queueLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #6d7686;");
        statsCard.getChildren().addAll(statsLabel, queueLabel);

        VBox volumeCard = new VBox(8);
        volumeCard.setPadding(new Insets(14, 20, 14, 20));
        volumeCard.setStyle(createGlassCardStyle());

        Label volumeLabel = new Label("音量");
        volumeLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #24324a;");

        volumeValueLabel = new Label("80%");
        volumeValueLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #6d7686;");

        volumeSlider = new Slider(0, 100, Math.round(gameEngine.getPlaybackVolume() * 100.0));
        volumeSlider.setPrefWidth(220);
        volumeSlider.setBlockIncrement(1);
        volumeSlider.valueProperty().addListener((obs, oldValue, newValue) -> {
            double normalizedVolume = newValue.doubleValue() / 100.0;
            volumeValueLabel.setText(String.format("%d%%", Math.round(newValue.doubleValue())));
            gameEngine.setPlaybackVolume(normalizedVolume);
        });

        volumeCard.getChildren().addAll(volumeLabel, volumeSlider, volumeValueLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        topBar.getChildren().addAll(titleBlock, spacer, statsCard, volumeCard);
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

        Label artworkHeader = new Label("卡面");
        artworkHeader.setStyle("-fx-font-size: 15; -fx-font-weight: bold; -fx-text-fill: #7a5c2e;");

        StackPane imageFrame = new StackPane();
        imageFrame.setPadding(new Insets(18));
        imageFrame.setMinHeight(440);
        imageFrame.setStyle(
                "-fx-background-color: linear-gradient(to bottom, #ffffff, #f7efe4);" +
                        "-fx-background-radius: 28;" +
                        "-fx-border-radius: 28;" +
                        "-fx-border-color: rgba(122,92,46,0.16);");

        cardImageView = new ImageView();
        cardImageView.setFitWidth(320);
        cardImageView.setFitHeight(420);
        cardImageView.setPreserveRatio(true);
        cardImageView.setSmooth(true);

        imagePlaceholderLabel = new Label("卡面预览区域");
        imagePlaceholderLabel.setStyle("-fx-font-size: 16; -fx-text-fill: #8b93a3;");
        imageFrame.getChildren().addAll(cardImageView, imagePlaceholderLabel);

        cardNameLabel = new Label("准备中");
        cardNameLabel.setWrapText(true);
        cardNameLabel.setAlignment(Pos.CENTER);
        cardNameLabel.setStyle("-fx-font-size: 28; -fx-font-weight: bold; -fx-text-fill: #1d2a44;");

        Label artworkHint = new Label("当前卡牌的主视觉区域。");
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

        Label infoHeader = new Label("回合状态");
        infoHeader.setStyle("-fx-font-size: 15; -fx-font-weight: bold; -fx-text-fill: #4a6589;");

        VBox stateCard = new VBox(12);
        stateCard.setPadding(new Insets(20));
        stateCard.setStyle(createGlassCardStyle());

        statusLabel = new Label("点击准备开始。");
        statusLabel.setWrapText(true);
        statusLabel.setStyle("-fx-font-size: 20; -fx-font-weight: bold; -fx-text-fill: #24324a;");

        songTitleLabel = new Label("等待中");
        songTitleLabel.setWrapText(true);
        songTitleLabel.setStyle("-fx-font-size: 24; -fx-font-weight: bold; -fx-text-fill: #24324a;");

        songMetaLabel = new Label("所选卡牌与歌曲信息会显示在这里。");
        songMetaLabel.setWrapText(true);
        songMetaLabel.setStyle("-fx-font-size: 14; -fx-text-fill: #6d7686;");

        playbackProgressBar = new ProgressBar(0);
        playbackProgressBar.setPrefWidth(Double.MAX_VALUE);
        playbackProgressBar.setStyle(
                "-fx-accent: #2f7d4a;" +
                        "-fx-control-inner-background: rgba(47,125,74,0.12);" +
                        "-fx-background-radius: 999;");

        stateCard.getChildren().addAll(statusLabel, songTitleLabel, songMetaLabel, playbackProgressBar);

        VBox helperCard = new VBox(10);
        helperCard.setPadding(new Insets(20));
        helperCard.setStyle(createGlassCardStyle());

        Label helperTitle = new Label("流程");
        helperTitle.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #24324a;");

        Label helperText = new Label("点击准备开始回合；提交成功或失败后进入休息；再点击准备进入下一回合。");
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

        Label queueHeader = new Label("卡池");
        queueHeader.setStyle("-fx-font-size: 15; -fx-font-weight: bold; -fx-text-fill: #3c6f4d;");

        VBox activeCard = new VBox(10);
        activeCard.setPadding(new Insets(20));
        activeCard.setStyle(createGlassCardStyle());

        Label activeTitle = new Label("当前歌曲");
        activeTitle.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #24324a;");

        Label activeText = new Label("当前卡牌关联的歌曲将显示在这里。");
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

        successButton = createButton("成功", "#2f7d4a", e -> submitSuccess());
        failureButton = createButton("失败", "#c65b48", e -> submitFailure());
        restMusicToggleButton = createButton("暂停休息音乐", "#5f6f86", e -> toggleRestMusicPlayback());
        successButton.setDisable(true);
        failureButton.setDisable(true);
        restMusicToggleButton.setDisable(true);
        roundButtonsBox.getChildren().addAll(successButton, failureButton, restMusicToggleButton);

        prepareButton = createButton("准备", "#3d6cb5", e -> resumeFromRest());
        prepareButton.setDisable(false);
        prepareButton.setVisible(true);

        Button returnButton = createButton("返回菜单", "#6f7480", e -> returnToMenu());
        buttonBar.getChildren().addAll(roundButtonsBox, prepareButton, returnButton);
        return buttonBar;
    }

    private Button createButton(String text, String color,
            javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        Button button = new Button(text);
        button.setStyle(
                "-fx-background-color: " + color + ";" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 15;" +
                        "-fx-font-weight: bold;" +
                        "-fx-padding: 14 28;" +
                        "-fx-border-radius: 14;" +
                        "-fx-background-radius: 14;" +
                        "-fx-cursor: hand;");
        button.setOnAction(handler);
        return button;
    }

    private void setupGameEngine() {
        gameEngine.setGameListener(new GameEngine.GameListener() {
            @Override
            public void onAwaitingStart() {
                Platform.runLater(() -> {
                    statusLabel.setText("点击准备开始游戏。");
                    songTitleLabel.setText("等待开始");
                    songMetaLabel.setText("已完成卡牌选择，准备好后开始。");
                    prepareButton.setText("准备");
                    prepareButton.setDisable(false);
                    successButton.setDisable(true);
                    failureButton.setDisable(true);
                    updateRestMusicToggleButton();
                    playbackProgressBar.setProgress(0);
                    stopPlaybackTimeline();
                    refreshDeckSummary();
                });
            }

            @Override
            public void onRoundStart(Card card) {
                Platform.runLater(() -> {
                    cardNameLabel.setText(card.getWorkName());
                    songTitleLabel.setText("歌曲片段已就绪");
                    songMetaLabel.setText(buildSongList(card));
                    statusLabel.setText("卡牌已选中，等待播放。");
                    updateCardImage(card);
                    prepareButton.setDisable(true);
                    prepareButton.setText("准备下一回合");
                    successButton.setDisable(true);
                    failureButton.setDisable(true);
                    updateRestMusicToggleButton();
                    playbackProgressBar.setProgress(0);
                    stopPlaybackTimeline();
                    refreshDeckSummary();
                });
            }

            @Override
            public void onMusicStart(Song song) {
                Platform.runLater(() -> {
                    songTitleLabel.setText(song.getDisplayName());
                    songMetaLabel.setText(String.format("格式 %s | 文件 %s",
                            song.getFileFormat().toUpperCase(), song.getFileName()));
                    statusLabel.setText("正在播放，可随时结束本回合。");
                    successButton.setDisable(false);
                    failureButton.setDisable(false);
                    startPlaybackTimeline();
                });
            }

            @Override
            public void onMusicComplete() {
                Platform.runLater(() -> {
                    statusLabel.setText("播放完成，请选择成功或失败。");
                    successButton.setDisable(false);
                    failureButton.setDisable(false);
                    playbackProgressBar.setProgress(1.0);
                    stopPlaybackTimeline();
                });
            }

            @Override
            public void onMusicInterrupted() {
                Platform.runLater(() -> {
                    statusLabel.setText("播放已停止。");
                    stopPlaybackTimeline();
                });
            }

            @Override
            public void onRoundComplete(GameState.Result result) {
                Platform.runLater(() -> {
                    GameState state = gameEngine.getGameState();
                    roundLabel.setText(String.format("已进行 %d | 剩余 %d",
                            state.getCurrentRound(), state.getRemainingRounds()));
                    updateStats(state);
                    refreshDeckSummary();
                    prepareButton.setDisable(false);
                    prepareButton.setText("准备下一回合");
                    statusLabel.setText("回合完成，点击准备下一回合继续。");
                    successButton.setDisable(true);
                    failureButton.setDisable(true);
                    stopPlaybackTimeline();
                });
            }

            @Override
            public void onRestMusicStart() {
                Platform.runLater(() -> {
                    statusLabel.setText("休息时间，若已开启将播放休息音乐。");
                    songTitleLabel.setText("休息时间");
                    songMetaLabel.setText("准备好后点击准备下一回合。");
                    prepareButton.setDisable(false);
                    prepareButton.setText("准备下一回合");
                    updateRestMusicToggleButton();
                });
            }

            @Override
            public void onRestMusicComplete() {
                Platform.runLater(() -> updateRestMusicToggleButton());
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
                Platform.runLater(() -> mainWindow.showErrorDialog("错误", error));
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

    private void toggleRestMusicPlayback() {
        GameState state = gameEngine.getGameState();
        boolean inRestState = state != null && state.getRoundState() == GameState.RoundState.REST_MUSIC;
        if (!inRestState) {
            return;
        }

        if (gameEngine.isCurrentMusicPlaying()) {
            gameEngine.pauseCurrentMusic();
            statusLabel.setText("休息音乐已暂停。");
            restMusicToggleButton.setText("播放休息音乐");
        } else if (gameEngine.isCurrentMusicPaused()) {
            gameEngine.resumeCurrentMusic();
            statusLabel.setText("休息音乐已继续播放。");
            restMusicToggleButton.setText("暂停休息音乐");
        } else {
            statusLabel.setText("当前没有可控制的休息音乐。");
        }
        updateRestMusicToggleButton();
    }

    private void updateRestMusicToggleButton() {
        if (restMusicToggleButton == null) {
            return;
        }

        GameState state = gameEngine.getGameState();
        boolean inRestState = state != null && state.getRoundState() == GameState.RoundState.REST_MUSIC;
        if (!inRestState) {
            restMusicToggleButton.setDisable(true);
            restMusicToggleButton.setText("暂停休息音乐");
            return;
        }

        if (gameEngine.isCurrentMusicPlaying()) {
            restMusicToggleButton.setDisable(false);
            restMusicToggleButton.setText("暂停休息音乐");
            return;
        }

        if (gameEngine.isCurrentMusicPaused()) {
            restMusicToggleButton.setDisable(false);
            restMusicToggleButton.setText("播放休息音乐");
            return;
        }

        restMusicToggleButton.setDisable(false);
        restMusicToggleButton.setText("暂停休息音乐");
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
                "成功 %d | 失败 %d | 成功率 %.1f%%",
                state.getSuccessCount(),
                state.getFailureCount(),
                state.getSuccessRate()));
    }

    private void refreshDeckSummary() {
        GameState state = gameEngine.getGameState();
        Deck deck = state != null ? state.getCurrentDeck() : currentDeck;
        if (deck == null) {
            deckInfoLabel.setText("数据集不可用");
            roundLabel.setText("已进行 0 | 剩余 0");
            queueLabel.setText("在场卡牌 0");
            return;
        }

        int playedRounds = state != null ? state.getCurrentRound() : 0;
        int remainingRounds = state != null ? state.getRemainingRounds() : deck.getActiveCardCount();
        roundLabel.setText(String.format("已进行 %d | 剩余 %d", playedRounds, remainingRounds));
        deckInfoLabel.setText(String.format("%s  |  剩余回合 %d", deck.getDeckName(), remainingRounds));
        queueLabel.setText(String.format("在场卡牌 %d | 离场卡牌 %d",
                deck.getActiveCardCount(), deck.getInactiveCards().size()));
    }

    private String buildSongList(Card card) {
        if (card == null || card.getSongs().isEmpty()) {
            return "该卡牌没有可播放歌曲。";
        }
        return card.getSongs().stream()
                .limit(4)
                .map(Song::getDisplayName)
                .collect(Collectors.joining(" / "));
    }

    private void showGameOverDialog(GameState state) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("游戏结束");
        alert.setContentText(String.format(
                "对战结束%n%n回合数：%d%n成功：%d%n失败：%d%n成功率：%.1f%%",
                state.getCurrentRound(),
                state.getSuccessCount(),
                state.getFailureCount(),
                state.getSuccessRate()));
        alert.showAndWait();
        returnToMenu();
    }

    private void returnToMenu() {
        gameEngine.abortGame();
        stopPlaybackTimeline();
        mainWindow.returnToDeckSelection();
    }

    private void startPlaybackTimeline() {
        stopPlaybackTimeline();

        int durationSeconds = Math.max(1, gameEngine.getCurrentPlaybackDurationSeconds());
        playbackTimeline = new Timeline();
        playbackTimeline.setCycleCount(Timeline.INDEFINITE);

        final long startTime = System.nanoTime();
        KeyFrame frame = new KeyFrame(Duration.millis(100), event -> {
            double elapsedSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0;
            double progress = Math.min(1.0, elapsedSeconds / durationSeconds);
            playbackProgressBar.setProgress(progress);
            if (progress >= 1.0) {
                stopPlaybackTimeline();
            }
        });

        playbackTimeline.getKeyFrames().setAll(frame);
        playbackTimeline.playFromStart();
    }

    private void stopPlaybackTimeline() {
        if (playbackTimeline != null) {
            playbackTimeline.stop();
            playbackTimeline = null;
        }
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
