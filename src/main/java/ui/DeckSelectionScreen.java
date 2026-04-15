package ui;

import config.ConfigManager;
import game.GameRules;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextFormatter;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.converter.IntegerStringConverter;
import model.Card;
import model.Deck;
import model.Song;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 起始界面，用于预览可用数据集并收集游戏选项。
 */
public class DeckSelectionScreen {
    private final MainWindow mainWindow;
    private final ConfigManager configManager;
    private final List<File> deckFiles;

    private Scene scene;
    private ListView<String> deckListView;
    private Spinner<Integer> cardsSpinner;
    private CheckBox restMusicCheckBox;
    private ComboBox<GameRules.FailureMode> failureModeComboBox;
    private Label deckNameLabel;
    private Label deckMetaLabel;
    private Label previewTitleLabel;
    private Label previewSongsLabel;
    private Label emptyPreviewLabel;
    private ImageView previewImageView;
    private ListView<String> previewCardListView;

    /**
     * 创建选择界面，并立即构建其 UI。
     */
    public DeckSelectionScreen(MainWindow mainWindow) {
        this.mainWindow = mainWindow;
        this.configManager = mainWindow.getConfigManager();
        this.deckFiles = new ArrayList<>();
        createUI();
    }

    /**
     * 构建双栏数据集浏览区和开始按钮区域。
     */
    private void createUI() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(28));
        root.setStyle(
                "-fx-background-color: linear-gradient(to bottom right, #f7efe4, #f2f5fb);" +
                        "-fx-font-family: 'Segoe UI';");

        VBox hero = new VBox(8);
        hero.setPadding(new Insets(0, 0, 24, 0));

        Label titleLabel = new Label("点歌对战");
        titleLabel.setStyle("-fx-font-size: 36; -fx-font-weight: bold; -fx-text-fill: #1d2a44;");

        Label subtitleLabel = new Label("选择数据集，预览卡面，然后开始对战。");
        subtitleLabel.setStyle("-fx-font-size: 15; -fx-text-fill: #6d7686;");

        hero.getChildren().addAll(titleLabel, subtitleLabel);
        root.setTop(hero);

        HBox content = new HBox(24);

        VBox leftPanel = new VBox(18);
        leftPanel.setPrefWidth(360);
        leftPanel.setPadding(new Insets(26));
        leftPanel.setStyle(createPanelStyle("#fffaf3"));

        Label deckLabel = new Label("可用数据集");
        deckLabel.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #24324a;");

        Label deckHintLabel = new Label("左侧选择数据集，右侧查看卡面与歌曲预览。");
        deckHintLabel.setWrapText(true);
        deckHintLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #7a8598;");

        Button importButton = new Button("导入数据集");
        importButton.setStyle(createSecondaryButtonStyle());
        importButton.setOnAction(e -> openImportDialog());

        Button refreshButton = new Button("刷新列表");
        refreshButton.setStyle(createSecondaryButtonStyle());
        refreshButton.setOnAction(e -> refreshDeckList(getSelectedDeckName()));

        HBox deckActionBar = new HBox(12, importButton, refreshButton);
        deckActionBar.setAlignment(Pos.CENTER_LEFT);

        deckListView = new ListView<>();
        deckListView.setPrefHeight(320);
        deckListView.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-control-inner-background: #fffdf8;" +
                        "-fx-background-insets: 0;" +
                        "-fx-border-color: #eadfca;" +
                        "-fx-border-radius: 16;" +
                        "-fx-background-radius: 16;" +
                        "-fx-padding: 8;");
        loadAvailableDecks();
        deckListView.getSelectionModel().selectedIndexProperty()
                .addListener((obs, oldValue, newValue) -> updateDeckPreview(newValue.intValue()));

        VBox roundsCard = new VBox(10);
        roundsCard.setPadding(new Insets(18));
        roundsCard.setStyle(createInsetCardStyle());

        Label roundsLabel = new Label("对战设置");
        roundsLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #24324a;");

        Label roundsHintLabel = new Label("设置本局使用的卡牌数量。");
        roundsHintLabel.setWrapText(true);
        roundsHintLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #7a8598;");

        SpinnerValueFactory.IntegerSpinnerValueFactory cardCountFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(
                1, 100, 50);
        cardsSpinner = new Spinner<>();
        cardsSpinner.setValueFactory(cardCountFactory);
        cardsSpinner.setEditable(true);
        configureCardCountEditor(cardCountFactory);
        cardsSpinner.setStyle("-fx-font-size: 14;");
        cardsSpinner.setPrefWidth(160);

        HBox roundsBox = new HBox(12, new Label("卡牌数"), cardsSpinner);
        roundsBox.setAlignment(Pos.CENTER_LEFT);

        restMusicCheckBox = new CheckBox("休息时间播放歌曲");
        restMusicCheckBox.setSelected(true);
        restMusicCheckBox.setStyle("-fx-font-size: 13; -fx-text-fill: #31415f;");

        failureModeComboBox = new ComboBox<>();
        failureModeComboBox
                .setItems(FXCollections.observableArrayList(GameRules.FailureMode.PASS, GameRules.FailureMode.SKIP));
        failureModeComboBox.setValue(mainWindow.getGameRules().getFailureMode());
        failureModeComboBox.setPrefWidth(180);

        HBox failureModeBox = new HBox(12, new Label("失败处理"), failureModeComboBox);
        failureModeBox.setAlignment(Pos.CENTER_LEFT);

        Button startButton = createButton("开始", "#2f7d4a");
        startButton.setOnAction(e -> startGame());

        Button exitButton = new Button("退出");
        exitButton.setStyle(createSecondaryButtonStyle());
        exitButton.setOnAction(e -> System.exit(0));

        HBox buttonBox = new HBox(15, startButton, exitButton);
        buttonBox.setAlignment(Pos.CENTER_LEFT);

        roundsCard.getChildren().addAll(roundsLabel, roundsHintLabel, roundsBox, failureModeBox, restMusicCheckBox,
                buttonBox);
        leftPanel.getChildren().addAll(deckLabel, deckHintLabel, deckActionBar, deckListView, roundsCard);

        VBox rightPanel = new VBox(18);
        rightPanel.setPadding(new Insets(26));
        rightPanel.setStyle(createPanelStyle("#f8fbff"));

        deckNameLabel = new Label("未选择数据集");
        deckNameLabel.setStyle("-fx-font-size: 24; -fx-font-weight: bold; -fx-text-fill: #1d2a44;");

        deckMetaLabel = new Label("卡牌 0 | 歌曲 0");
        deckMetaLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #6d7686;");

        HBox previewContent = new HBox(20);

        VBox artworkCard = new VBox(12);
        artworkCard.setAlignment(Pos.TOP_CENTER);
        artworkCard.setPadding(new Insets(18));
        artworkCard.setPrefWidth(320);
        artworkCard.setStyle(createInsetCardStyle());

        previewImageView = new ImageView();
        previewImageView.setFitWidth(240);
        previewImageView.setFitHeight(320);
        previewImageView.setPreserveRatio(true);
        previewImageView.setSmooth(true);

        emptyPreviewLabel = new Label("卡面预览区域");
        emptyPreviewLabel.setWrapText(true);
        emptyPreviewLabel.setMaxWidth(220);
        emptyPreviewLabel.setAlignment(Pos.CENTER);
        emptyPreviewLabel.setStyle("-fx-font-size: 14; -fx-text-fill: #7a8598;");

        previewTitleLabel = new Label("请选择数据集");
        previewTitleLabel.setWrapText(true);
        previewTitleLabel.setAlignment(Pos.CENTER);
        previewTitleLabel.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #24324a;");

        previewSongsLabel = new Label("歌曲信息将显示在这里。");
        previewSongsLabel.setWrapText(true);
        previewSongsLabel.setMaxWidth(240);
        previewSongsLabel.setAlignment(Pos.CENTER);
        previewSongsLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #6d7686;");

        artworkCard.getChildren().addAll(previewImageView, emptyPreviewLabel, previewTitleLabel, previewSongsLabel);

        VBox listCard = new VBox(12);
        listCard.setPadding(new Insets(18));
        listCard.setStyle(createInsetCardStyle());
        HBox.setHgrow(listCard, Priority.ALWAYS);

        Label listTitle = new Label("卡牌预览");
        listTitle.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #24324a;");

        Label listHint = new Label("展示卡面、作品标题和歌曲数量。");
        listHint.setWrapText(true);
        listHint.setStyle("-fx-font-size: 12; -fx-text-fill: #7a8598;");

        previewCardListView = new ListView<>();
        previewCardListView.setPrefHeight(360);
        previewCardListView.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-control-inner-background: white;" +
                        "-fx-background-insets: 0;" +
                        "-fx-border-color: #dfe6f2;" +
                        "-fx-border-radius: 14;" +
                        "-fx-background-radius: 14;" +
                        "-fx-padding: 8;");

        listCard.getChildren().addAll(listTitle, listHint, previewCardListView);
        previewContent.getChildren().addAll(artworkCard, listCard);

        Region contentSpacer = new Region();
        VBox.setVgrow(contentSpacer, Priority.ALWAYS);
        rightPanel.getChildren().addAll(deckNameLabel, deckMetaLabel, new Separator(), previewContent, contentSpacer);

        content.getChildren().addAll(leftPanel, rightPanel);
        HBox.setHgrow(rightPanel, Priority.ALWAYS);
        root.setCenter(content);

        scene = new Scene(root, 1120, 720);
        updateDeckPreview(deckListView.getSelectionModel().getSelectedIndex());
    }

    /**
     * 扫描配置中的牌组目录，并填充可见的数据集列表。
     */
    private void loadAvailableDecks() {
        deckFiles.clear();
        deckListView.getItems().clear();

        File decksFolder = new File(configManager.getDefaultDeck()).getParentFile();
        if (decksFolder != null && decksFolder.exists() && decksFolder.isDirectory()) {
            File[] files = decksFolder.listFiles((dir, name) -> name.endsWith(".csv"));
            if (files != null) {
                java.util.Arrays.sort(files,
                        java.util.Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
                for (File file : files) {
                    deckFiles.add(file);
                    deckListView.getItems().add(file.getName().replace(".csv", ""));
                }
            }
        }

        if (deckListView.getItems().isEmpty()) {
            deckListView.getItems().add("未找到数据集文件");
            deckListView.setDisable(true);
        } else {
            deckListView.setDisable(false);
            deckListView.getSelectionModel().selectFirst();
        }
    }

    /**
     * 重新加载数据集，并尽量保持之前选中的牌组高亮。
     */
    private void refreshDeckList(String preferredDeckName) {
        loadAvailableDecks();
        if (deckFiles.isEmpty()) {
            updateDeckPreview(-1);
            return;
        }

        int indexToSelect = 0;
        if (preferredDeckName != null && !preferredDeckName.isBlank()) {
            for (int i = 0; i < deckFiles.size(); i++) {
                String deckName = deckFiles.get(i).getName().replace(".csv", "");
                if (deckName.equals(preferredDeckName)) {
                    indexToSelect = i;
                    break;
                }
            }
        }

        deckListView.getSelectionModel().select(indexToSelect);
        updateDeckPreview(indexToSelect);
    }

    /**
     * 打开数据集导入对话框，并在关闭后刷新列表。
     */
    private void openImportDialog() {
        String selectedDeckName = getSelectedDeckName();
        DatasetImportDialog dialog = new DatasetImportDialog(
                scene.getWindow() instanceof javafx.stage.Stage stage ? stage : null,
                configManager,
                selectedDeckName,
                this::refreshDeckList);
        dialog.showAndWait();
        refreshDeckList(selectedDeckName);
    }

    private String getSelectedDeckName() {
        int selectedIndex = deckListView.getSelectionModel().getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= deckFiles.size()) {
            return null;
        }
        return deckFiles.get(selectedIndex).getName().replace(".csv", "");
    }

    /**
     * 加载选中的数据集，并用代表性内容更新预览组件。
     */
    private void updateDeckPreview(int selectedIndex) {
        if (selectedIndex < 0 || selectedIndex >= deckFiles.size()) {
            deckNameLabel.setText("未选择数据集");
            deckMetaLabel.setText("卡牌 0 | 歌曲 0");
            previewTitleLabel.setText("请选择数据集");
            previewSongsLabel.setText("歌曲信息将显示在这里。");
            previewImageView.setImage(null);
            emptyPreviewLabel.setVisible(true);
            previewCardListView.setItems(FXCollections.observableArrayList());
            return;
        }

        try {
            Deck deck = configManager.loadDeck(deckFiles.get(selectedIndex).getAbsolutePath());
            int totalSongs = deck.getCards().stream().mapToInt(Card::getSongCount).sum();
            deckNameLabel.setText(deck.getDeckName());
            deckMetaLabel.setText(String.format("卡牌 %d | 歌曲 %d", deck.getCardCount(), totalSongs));
            cardsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1,
                    Math.max(1, deck.getCardCount()), Math.min(10, Math.max(1, deck.getCardCount()))));

            Card heroCard = deck.getCards().isEmpty() ? null : deck.getCards().get(0);
            if (heroCard != null) {
                previewTitleLabel.setText(heroCard.getWorkName());
                previewSongsLabel.setText(buildSongPreview(heroCard));
                updatePreviewImage(heroCard);
            } else {
                previewTitleLabel.setText("该数据集没有可用卡牌");
                previewSongsLabel.setText("请检查 csv 内容和音频文件。");
                previewImageView.setImage(null);
                emptyPreviewLabel.setVisible(true);
            }

            previewCardListView.setItems(FXCollections.observableArrayList(
                    deck.getCards().stream()
                            .limit(12)
                            .map(this::buildCardListLabel)
                            .collect(Collectors.toList())));
        } catch (Exception e) {
            deckNameLabel.setText("预览失败");
            deckMetaLabel.setText(e.getMessage());
            previewTitleLabel.setText("无法读取数据集");
            previewSongsLabel.setText("请检查配置路径和资源文件。");
            previewImageView.setImage(null);
            emptyPreviewLabel.setVisible(true);
            previewCardListView.setItems(FXCollections.observableArrayList());
        }
    }

    /**
     * 当选中的卡牌拥有有效文件时，加载其图片预览。
     */
    private void updatePreviewImage(Card card) {
        if (card == null || card.getImageFile() == null || !card.getImageFile().exists()) {
            previewImageView.setImage(null);
            emptyPreviewLabel.setVisible(true);
            return;
        }

        try (FileInputStream inputStream = new FileInputStream(card.getImageFile())) {
            previewImageView.setImage(new Image(inputStream));
            emptyPreviewLabel.setVisible(false);
        } catch (Exception e) {
            previewImageView.setImage(null);
            emptyPreviewLabel.setVisible(true);
        }
    }

    /**
     * 生成前几首歌曲名称的简短可读预览。
     */
    private String buildSongPreview(Card card) {
        List<Song> songs = card.getSongs();
        if (songs.isEmpty()) {
            return "该卡牌没有关联歌曲。";
        }

        return songs.stream()
                .limit(3)
                .map(Song::getDisplayName)
                .collect(Collectors.joining(" / "));
    }

    private String buildCardListLabel(Card card) {
        String firstSong = card.getSongs().isEmpty()
                ? "无歌曲"
                : card.getSongs().get(0).getDisplayName();
        return card.getWorkName() + "  |  " + firstSong;
    }

    /**
     * 构建所选牌组，应用当前选项，并启动游戏。
     */
    private void startGame() {
        int selectedIndex = deckListView.getSelectionModel().getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= deckFiles.size()) {
            mainWindow.showErrorDialog("错误", "请先选择数据集。");
            return;
        }

        try {
            File selectedDeckFile = deckFiles.get(selectedIndex);
            Deck deck = configManager.loadDeck(selectedDeckFile.getAbsolutePath());
            int selectedCardCount = cardsSpinner.getValue();
            CardSelectionDialog cardSelectionDialog = new CardSelectionDialog(
                    scene.getWindow() instanceof javafx.stage.Stage stage ? stage : null,
                    deck,
                    selectedCardCount);
            CardSelectionDialog.SelectionResult selectionResult = cardSelectionDialog.showAndWait();
            if (selectionResult.selectedCards().isEmpty()) {
                return;
            }

            Set<Card> selectedCards = new LinkedHashSet<>(selectionResult.selectedCards());
            Set<Card> excludedRestCards = new LinkedHashSet<>(selectedCards);
            excludedRestCards.addAll(selectionResult.emptySourceCards());

            Deck limitedDeck = new Deck(deck.getDeckName(), deck.getConfigFile());
            for (Card selectedCard : selectionResult.selectedCards()) {
                limitedDeck.addCard(selectedCard);
            }
            for (Card emptySourceCard : selectionResult.emptySourceCards()) {
                limitedDeck.addCard(emptySourceCard.createEmptyVariant());
            }

            List<Song> restSongs = deck.getCards().stream()
                    .filter(card -> !excludedRestCards.contains(card))
                    .flatMap(card -> card.getSongs().stream())
                    .toList();
            mainWindow.startGame(
                    limitedDeck,
                    selectionResult.selectedCards().size(),
                    restMusicCheckBox.isSelected(),
                    failureModeComboBox.getValue(),
                    restSongs);
        } catch (Exception e) {
            mainWindow.showErrorDialog("加载数据集失败", e.getMessage());
        }
    }

    private Button createButton(String text, String color) {
        Button button = new Button(text);
        button.setStyle(createPrimaryButtonStyle(color));
        return button;
    }

    private String createPanelStyle(String background) {
        return "-fx-background-color: " + background + ";" +
                "-fx-background-radius: 24;" +
                "-fx-border-radius: 24;" +
                "-fx-border-color: rgba(29,42,68,0.08);" +
                "-fx-effect: dropshadow(gaussian, rgba(22,33,58,0.10), 24, 0.18, 0, 8);";
    }

    private String createInsetCardStyle() {
        return "-fx-background-color: rgba(255,255,255,0.78);" +
                "-fx-background-radius: 18;" +
                "-fx-border-radius: 18;" +
                "-fx-border-color: rgba(36,50,74,0.08);";
    }

    private String createPrimaryButtonStyle(String color) {
        return "-fx-background-color: " + color + ";" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 15;" +
                "-fx-font-weight: bold;" +
                "-fx-padding: 14 28;" +
                "-fx-background-radius: 14;" +
                "-fx-border-radius: 14;" +
                "-fx-cursor: hand;";
    }

    private String createSecondaryButtonStyle() {
        return "-fx-background-color: white;" +
                "-fx-text-fill: #31415f;" +
                "-fx-font-size: 14;" +
                "-fx-font-weight: bold;" +
                "-fx-padding: 13 24;" +
                "-fx-background-radius: 14;" +
                "-fx-border-radius: 14;" +
                "-fx-border-color: #d8deea;" +
                "-fx-cursor: hand;";
    }

    /**
     * 限制可编辑数字框只能输入整数，并在失焦时提交数值。
     */
    private void configureCardCountEditor(SpinnerValueFactory.IntegerSpinnerValueFactory valueFactory) {
        TextFormatter<Integer> formatter = new TextFormatter<>(
                new IntegerStringConverter(),
                valueFactory.getValue(),
                change -> change.getControlNewText().matches("\\d*") ? change : null);
        cardsSpinner.getEditor().setTextFormatter(formatter);
        formatter.valueProperty().bindBidirectional(valueFactory.valueProperty());
        cardsSpinner.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                cardsSpinner.increment(0);
            }
        });
    }

    /**
     * 将构建好的场景提供给主窗口使用。
     */
    public Scene getScene() {
        return scene;
    }
}
