package ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.converter.IntegerStringConverter;
import model.Card;
import model.Deck;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Modal dialog for selecting which cards participate in the next game.
 */
public class CardSelectionDialog {
    private final Stage stage;
    private final List<Card> allCards;
    private final int cardLimit;
    private final Set<Card> selectedCards = new LinkedHashSet<>();
    private final Map<Card, CardTile> cardTiles = new LinkedHashMap<>();
    private final Label selectionLabel = new Label();
    private final Label emptyStateLabel = new Label(
            "\u5F53\u524D\u6CA1\u6709\u5339\u914D\u7684\u5361\u9762\u6807\u9898");
    private final Label emptyCardModeLabel = new Label();

    private FlowPane cardsPane;
    private TextField searchField;
    private ComboBox<SortMode> sortComboBox;
    private Spinner<Integer> randomCountSpinner;
    private Button randomPickButton;
    private CheckBox emptyCardModeCheckBox;

    private boolean confirmed;

    /**
     * Initializes the dialog and preselects cards up to the configured limit.
     */
    public CardSelectionDialog(Stage owner, Deck deck, int cardLimit) {
        this.allCards = deck.getCards();
        this.cardLimit = Math.max(1, Math.min(cardLimit, Math.max(1, allCards.size())));
        preselectCards();

        stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("\u9009\u62E9\u53C2\u8D5B\u5361\u724C");
        stage.setWidth(1120);
        stage.setHeight(760);
        stage.setScene(buildScene());
        refreshSelectionLabel();
    }

    /**
     * Blocks until the dialog closes and returns selected/unselected cards.
     */
    public SelectionResult showAndWait() {
        stage.showAndWait();
        if (!confirmed) {
            return new SelectionResult(List.of(), List.of(), List.of());
        }

        List<Card> selected = new ArrayList<>(selectedCards);
        List<Card> unselected = new ArrayList<>();
        for (Card card : allCards) {
            if (!selectedCards.contains(card)) {
                unselected.add(card);
            }
        }
        return new SelectionResult(selected, unselected, createEmptySourceCards(unselected));
    }

    /**
     * Builds the dialog scene.
     */
    private Scene buildScene() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(24));
        root.setStyle(
                "-fx-background-color: linear-gradient(to bottom right, #f8f1e6, #eef4fb);" +
                        "-fx-font-family: 'Microsoft YaHei';");

        VBox header = new VBox(8);
        Label titleLabel = new Label("\u9009\u62E9\u672C\u5C40\u53C2\u8D5B\u5361\u724C");
        titleLabel.setStyle("-fx-font-size: 28; -fx-font-weight: bold; -fx-text-fill: #22324b;");

        Label subtitleLabel = new Label(
                "\u53EF\u4EE5\u6309\u6807\u9898\u641C\u7D22\u3001\u6392\u5E8F\uff0c\u4E5F\u53EF\u4EE5\u4ECE\u5F53\u524D\u7ED3\u679C\u4E2D\u968F\u673A\u62BD\u53D6\u5361\u724C\u3002");
        subtitleLabel.setWrapText(true);
        subtitleLabel.setStyle("-fx-font-size: 14; -fx-text-fill: #68788d;");

        selectionLabel.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #31415f;");
        header.getChildren().addAll(titleLabel, subtitleLabel, selectionLabel);

        VBox filterPanel = new VBox(12);
        filterPanel.setPadding(new Insets(16));
        filterPanel.setStyle(
                "-fx-background-color: rgba(255,255,255,0.80);" +
                        "-fx-background-radius: 18;" +
                        "-fx-border-radius: 18;" +
                        "-fx-border-color: rgba(36,50,74,0.08);");

        HBox searchRow = new HBox(12);
        searchRow.setAlignment(Pos.CENTER_LEFT);

        Label searchLabel = new Label("\u6807\u9898\u641C\u7D22");
        searchLabel.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #31415f;");

        searchField = new TextField();
        searchField.setPromptText("\u8F93\u5165\u5361\u9762\u6807\u9898\u5173\u952E\u5B57");
        searchField.setPrefWidth(320);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchField.textProperty().addListener((obs, oldValue, newValue) -> refreshCardList());

        Label sortLabel = new Label("\u6392\u5E8F");
        sortLabel.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #31415f;");

        sortComboBox = new ComboBox<>();
        sortComboBox.getItems().addAll(SortMode.ORIGINAL, SortMode.TITLE_ASC, SortMode.TITLE_DESC);
        sortComboBox.setValue(SortMode.ORIGINAL);
        sortComboBox.setOnAction(event -> refreshCardList());
        sortComboBox.setPrefWidth(160);

        searchRow.getChildren().addAll(searchLabel, searchField, sortLabel, sortComboBox);

        HBox actionRow = new HBox(12);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        Label randomLabel = new Label("\u968F\u673A\u5F20\u6570");
        randomLabel.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #31415f;");

        int defaultRandomCount = Math.max(1, Math.min(cardLimit, Math.max(1, allCards.size())));
        randomCountSpinner = new Spinner<>();
        randomCountSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                1,
                defaultRandomCount,
                defaultRandomCount));
        randomCountSpinner.setEditable(true);
        randomCountSpinner.setPrefWidth(110);
        configureRandomCountEditor();

        randomPickButton = new Button("\u968F\u673A\u9009\u62E9");
        randomPickButton.setStyle(createSecondaryButtonStyle());
        randomPickButton.setOnAction(event -> randomSelectVisibleCards());

        Button pickVisibleButton = new Button(
                "\u9009\u62E9\u5F53\u524D\u7ED3\u679C\u524D " + cardLimit + " \u5F20");
        pickVisibleButton.setStyle(createSecondaryButtonStyle());
        pickVisibleButton.setOnAction(event -> selectVisibleCardsUpToLimit());

        Button clearButton = new Button("\u6E05\u7A7A");
        clearButton.setStyle(createSecondaryButtonStyle());
        clearButton.setOnAction(event -> {
            selectedCards.clear();
            refreshSelectionView();
        });

        actionRow.getChildren().addAll(randomLabel, randomCountSpinner, randomPickButton, pickVisibleButton,
                clearButton);

        emptyCardModeCheckBox = new CheckBox("\u7A7A\u724C\u5F00\u59CB\u6A21\u5F0F");
        emptyCardModeCheckBox.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #31415f;");
        emptyCardModeCheckBox.selectedProperty().addListener((obs, oldValue, newValue) -> updateEmptyCardModeLabel());

        emptyCardModeLabel.setWrapText(true);
        emptyCardModeLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #6d7686;");

        filterPanel.getChildren().addAll(searchRow, actionRow, emptyCardModeCheckBox, emptyCardModeLabel);

        cardsPane = new FlowPane();
        cardsPane.setHgap(16);
        cardsPane.setVgap(16);
        cardsPane.setPadding(new Insets(4));
        cardsPane.setPrefWrapLength(980);

        ScrollPane scrollPane = new ScrollPane(cardsPane);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setStyle("-fx-background-color: transparent;");

        emptyStateLabel.setStyle("-fx-font-size: 15; -fx-text-fill: #6d7686;");
        emptyStateLabel.setVisible(false);
        emptyStateLabel.setManaged(false);

        StackPane cardsContainer = new StackPane(scrollPane, emptyStateLabel);
        StackPane.setAlignment(emptyStateLabel, Pos.CENTER);

        HBox bottomBar = new HBox(12);
        bottomBar.setAlignment(Pos.CENTER_RIGHT);

        Button cancelButton = new Button("\u53D6\u6D88");
        cancelButton.setStyle(createSecondaryButtonStyle());
        cancelButton.setOnAction(event -> stage.close());

        Button confirmButton = new Button("\u5F00\u59CB\u6E38\u620F");
        confirmButton.setStyle(createPrimaryButtonStyle());
        confirmButton.setOnAction(event -> confirmSelection());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        bottomBar.getChildren().addAll(spacer, cancelButton, confirmButton);

        VBox mainBox = new VBox(18, header, filterPanel, cardsContainer, bottomBar);
        VBox.setVgrow(cardsContainer, Priority.ALWAYS);
        root.setCenter(mainBox);

        refreshCardList();
        return new Scene(root);
    }

    /**
     * Creates a clickable tile for a single card.
     */
    private CardTile createCardTile(Card card) {
        VBox tile = new VBox(10);
        tile.setPrefWidth(180);
        tile.setPadding(new Insets(14));
        tile.setAlignment(Pos.TOP_CENTER);

        ImageView imageView = new ImageView();
        imageView.setFitWidth(140);
        imageView.setFitHeight(180);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);

        Label imageFallback = new Label("\u65E0\u56FE\u7247");
        imageFallback.setStyle("-fx-font-size: 12; -fx-text-fill: #8b93a3;");
        if (card.getImageFile() != null && card.getImageFile().exists()) {
            try (FileInputStream inputStream = new FileInputStream(card.getImageFile())) {
                imageView.setImage(new Image(inputStream));
                imageFallback.setVisible(false);
                imageFallback.setManaged(false);
            } catch (Exception e) {
                imageView.setImage(null);
            }
        }

        Label selectedBadge = new Label("\u2713");
        selectedBadge.setStyle(
                "-fx-background-color: #2e7d4f;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 16;" +
                        "-fx-font-weight: bold;" +
                        "-fx-padding: 4 10;" +
                        "-fx-background-radius: 999;");
        selectedBadge.setVisible(selectedCards.contains(card));
        selectedBadge.setManaged(selectedCards.contains(card));

        StackPane imageStack = new StackPane(imageView, imageFallback, selectedBadge);
        StackPane.setAlignment(selectedBadge, Pos.TOP_RIGHT);
        StackPane.setMargin(selectedBadge, new Insets(8));

        Label titleLabel = new Label(card.getWorkName());
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(150);
        titleLabel.setAlignment(Pos.CENTER);
        titleLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #24324a;");

        Label songCountLabel = new Label(card.getSongCount() + " songs");
        songCountLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #6d7686;");

        updateCardTileStyle(tile, selectedCards.contains(card));
        tile.setOnMouseClicked(event -> {
            if (toggleCardSelection(card)) {
                refreshCardTile(card);
            }
        });

        tile.getChildren().addAll(imageStack, titleLabel, songCountLabel);
        return new CardTile(tile, selectedBadge);
    }

    /**
     * Re-renders cards based on current search and sort state.
     */
    private void refreshCardList() {
        if (cardsPane == null) {
            return;
        }

        List<Card> visibleCards = getVisibleCards();
        List<javafx.scene.Node> visibleNodes = new ArrayList<>(visibleCards.size());
        for (Card card : visibleCards) {
            CardTile tile = cardTiles.computeIfAbsent(card, this::createCardTile);
            visibleNodes.add(tile.root());
        }

        cardsPane.getChildren().setAll(visibleNodes);
        boolean hasVisibleCards = !visibleCards.isEmpty();
        emptyStateLabel.setVisible(!hasVisibleCards);
        emptyStateLabel.setManaged(!hasVisibleCards);
        updateRandomControls(visibleCards.size());
        refreshSelectionLabel();
    }

    /**
     * Computes the cards visible under the current search and sort settings.
     */
    private List<Card> getVisibleCards() {
        List<Card> visibleCards = new ArrayList<>();
        String keyword = normalizeSearchText(searchField == null ? "" : searchField.getText());

        for (Card card : allCards) {
            if (keyword.isEmpty() || normalizeSearchText(card.getWorkName()).contains(keyword)) {
                visibleCards.add(card);
            }
        }

        SortMode sortMode = sortComboBox == null || sortComboBox.getValue() == null
                ? SortMode.ORIGINAL
                : sortComboBox.getValue();

        if (sortMode == SortMode.TITLE_ASC) {
            visibleCards.sort(createTitleComparator());
        } else if (sortMode == SortMode.TITLE_DESC) {
            visibleCards.sort(createTitleComparator().reversed());
        }

        return visibleCards;
    }

    private Comparator<Card> createTitleComparator() {
        return Comparator.comparing(
                (Card card) -> safeText(card.getWorkName()),
                String.CASE_INSENSITIVE_ORDER).thenComparing(
                        (Card card) -> safeText(card.getImageName()),
                        String.CASE_INSENSITIVE_ORDER);
    }

    private void updateRandomControls(int visibleCount) {
        if (randomCountSpinner == null || randomPickButton == null) {
            return;
        }

        SpinnerValueFactory.IntegerSpinnerValueFactory valueFactory =
                (SpinnerValueFactory.IntegerSpinnerValueFactory) randomCountSpinner.getValueFactory();
        int maxSelectable = Math.max(1, Math.min(cardLimit, Math.max(1, visibleCount)));
        valueFactory.setMax(maxSelectable);
        int normalizedValue = normalizeRandomCountValue(maxSelectable);
        if (normalizedValue != valueFactory.getValue()) {
            valueFactory.setValue(normalizedValue);
        }

        boolean disableControls = visibleCount == 0;
        randomCountSpinner.setDisable(disableControls);
        randomPickButton.setDisable(disableControls);
    }

    /**
     * Selects cards randomly from the current visible result set.
     */
    private void randomSelectVisibleCards() {
        List<Card> visibleCards = getVisibleCards();
        if (visibleCards.isEmpty()) {
            showError("\u65E0\u53EF\u9009\u5361\u724C", "\u5F53\u524D\u6CA1\u6709\u53EF\u4EE5\u968F\u673A\u9009\u62E9\u7684\u5361\u724C\u3002");
            return;
        }

        int randomCount = normalizeRandomCountValue(Math.min(cardLimit, visibleCards.size()));
        if (randomCount > visibleCards.size()) {
            showError(
                    "\u6570\u91CF\u4E0D\u8DB3",
                    "\u5F53\u524D\u7ED3\u679C\u53EA\u6709 " + visibleCards.size() + " \u5F20\u5361\u724C\uff0c\u65E0\u6CD5\u968F\u673A\u9009\u62E9 "
                            + randomCount + " \u5F20\u3002");
            return;
        }

        List<Card> shuffledCards = new ArrayList<>(visibleCards);
        Collections.shuffle(shuffledCards);

        selectedCards.clear();
        selectedCards.addAll(shuffledCards.subList(0, randomCount));
        refreshSelectionView();
    }

    /**
     * Selects the first cards from the current visible result set, up to the limit.
     */
    private void selectVisibleCardsUpToLimit() {
        List<Card> visibleCards = getVisibleCards();
        if (visibleCards.isEmpty()) {
            showError("\u65E0\u5339\u914D\u7ED3\u679C", "\u5F53\u524D\u6CA1\u6709\u53EF\u4EE5\u9009\u62E9\u7684\u5361\u724C\u3002");
            return;
        }

        selectedCards.clear();
        for (Card card : visibleCards) {
            if (selectedCards.size() >= cardLimit) {
                break;
            }
            selectedCards.add(card);
        }
        refreshSelectionView();
    }

    /**
     * Toggles a single card while honoring the selection limit.
     */
    private boolean toggleCardSelection(Card card) {
        if (!selectedCards.contains(card)) {
            if (selectedCards.size() >= cardLimit) {
                showError(
                        "\u5DF2\u5230\u4E0A\u9650",
                        "\u6700\u591A\u53EA\u80FD\u9009\u62E9 " + cardLimit + " \u5F20\u5361\u724C\u3002");
                return false;
            }
            selectedCards.add(card);
        } else {
            selectedCards.remove(card);
        }
        refreshSelectionLabel();
        return true;
    }

    /**
     * Refreshes selection state after batch operations.
     */
    private void refreshSelectionView() {
        refreshSelectionLabel();
        for (Card card : allCards) {
            refreshCardTile(card);
        }
    }

    private void refreshCardTile(Card card) {
        CardTile tile = cardTiles.get(card);
        if (tile == null) {
            return;
        }

        boolean selected = selectedCards.contains(card);
        tile.badge().setVisible(selected);
        tile.badge().setManaged(selected);
        updateCardTileStyle(tile.root(), selected);
    }

    /**
     * Applies selected/unselected visual state to a card tile.
     */
    private void updateCardTileStyle(VBox tile, boolean selected) {
        if (selected) {
            tile.setStyle(
                    "-fx-background-color: rgba(255,255,255,0.96);" +
                            "-fx-background-radius: 18;" +
                            "-fx-border-radius: 18;" +
                            "-fx-border-color: #2e7d4f;" +
                            "-fx-border-width: 2;" +
                            "-fx-effect: dropshadow(gaussian, rgba(46,125,79,0.20), 18, 0.18, 0, 6);" +
                            "-fx-cursor: hand;");
            tile.setScaleX(1.03);
            tile.setScaleY(1.03);
        } else {
            tile.setStyle(
                    "-fx-background-color: rgba(255,255,255,0.88);" +
                            "-fx-background-radius: 18;" +
                            "-fx-border-radius: 18;" +
                            "-fx-border-color: rgba(36,50,74,0.08);" +
                            "-fx-border-width: 1;" +
                            "-fx-cursor: hand;");
            tile.setScaleX(1.0);
            tile.setScaleY(1.0);
        }
    }

    /**
     * Preselects cards from the original order up to the limit.
     */
    private void preselectCards() {
        for (Card card : allCards) {
            if (selectedCards.size() >= cardLimit) {
                break;
            }
            selectedCards.add(card);
        }
    }

    private void refreshSelectionLabel() {
        int visibleCount = cardsPane == null ? allCards.size() : getVisibleCards().size();
        selectionLabel.setText(
                "\u5DF2\u9009 " + selectedCards.size() + " / " + cardLimit + " \u5F20\uff0c\u5F53\u524D\u663E\u793A "
                        + visibleCount + " / " + allCards.size() + " \u5F20\u3002");
        updateEmptyCardModeLabel();
    }

    /**
     * Verifies at least one card is selected before closing.
     */
    private void confirmSelection() {
        if (selectedCards.isEmpty()) {
            showError("\u672A\u9009\u62E9\u5361\u724C", "\u8BF7\u81F3\u5C11\u9009\u62E9 1 \u5F20\u5361\u724C\u518D\u5F00\u59CB\u3002");
            return;
        }
        if (isEmptyCardModeEnabled() && getAvailableEmptyCardCount() < selectedCards.size()) {
            showError(
                    "\u7A7A\u724C\u6570\u91CF\u4E0D\u8DB3",
                    "\u7A7A\u724C\u5F00\u59CB\u6A21\u5F0F\u9700\u8981\u4ECE\u672A\u9009\u4E2D\u5361\u724C\u91CC\u968F\u673A\u62BD\u53D6 "
                            + selectedCards.size() + " \u5F20\u7A7A\u724C\uff0c\u4F46\u5F53\u524D\u53EA\u5269 "
                            + getAvailableEmptyCardCount() + " \u5F20\u672A\u9009\u4E2D\u5361\u724C\u3002");
            return;
        }
        confirmed = true;
        stage.close();
    }

    private boolean isEmptyCardModeEnabled() {
        return emptyCardModeCheckBox != null && emptyCardModeCheckBox.isSelected();
    }

    private int getAvailableEmptyCardCount() {
        return allCards.size() - selectedCards.size();
    }

    private void updateEmptyCardModeLabel() {
        if (emptyCardModeLabel == null) {
            return;
        }

        if (!isEmptyCardModeEnabled()) {
            emptyCardModeLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #6d7686;");
            emptyCardModeLabel.setText(
                    "\u5F00\u542F\u540E\uff0c\u4F1A\u4ECE\u672A\u9009\u4E2D\u5361\u724C\u91CC\u968F\u673A\u52A0\u5165\u4E0E\u5DF2\u9009\u6570\u91CF\u76F8\u540C\u7684\u7A7A\u724C\u3002");
            return;
        }

        int selectedCount = selectedCards.size();
        int availableCount = getAvailableEmptyCardCount();
        if (availableCount >= selectedCount) {
            emptyCardModeLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #2e7d4f;");
            emptyCardModeLabel.setText(
                    "\u672C\u5C40\u5C06\u4ECE\u672A\u9009\u4E2D\u5361\u724C\u91CC\u968F\u673A\u62BD\u53D6 " + selectedCount
                            + " \u5F20\u4F5C\u4E3A\u7A7A\u724C\u3002");
            return;
        }

        emptyCardModeLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #c65b48;");
        emptyCardModeLabel.setText(
                "\u5269\u4F59\u5361\u6C60\u4E0D\u8DB3\uff1A\u5F53\u524D\u5DF2\u9009 " + selectedCount + " \u5F20\uff0c\u4F46\u672A\u9009\u4E2D\u5361\u724C\u53EA\u5269 "
                        + availableCount + " \u5F20\uff0c\u65E0\u6CD5\u751F\u6210\u540C\u7B49\u6570\u91CF\u7684\u7A7A\u724C\u3002");
    }

    private List<Card> createEmptySourceCards(List<Card> unselectedCards) {
        if (!isEmptyCardModeEnabled() || unselectedCards.isEmpty()) {
            return List.of();
        }

        List<Card> shuffledCards = new ArrayList<>(unselectedCards);
        Collections.shuffle(shuffledCards);
        return new ArrayList<>(shuffledCards.subList(0, selectedCards.size()));
    }

    private String createPrimaryButtonStyle() {
        return "-fx-background-color: #2e7d4f;" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 14;" +
                "-fx-font-weight: bold;" +
                "-fx-padding: 12 22;" +
                "-fx-background-radius: 12;" +
                "-fx-cursor: hand;";
    }

    private String createSecondaryButtonStyle() {
        return "-fx-background-color: white;" +
                "-fx-text-fill: #31415f;" +
                "-fx-font-size: 13;" +
                "-fx-font-weight: bold;" +
                "-fx-padding: 10 18;" +
                "-fx-background-radius: 12;" +
                "-fx-border-radius: 12;" +
                "-fx-border-color: #d8deea;" +
                "-fx-cursor: hand;";
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(stage);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void configureRandomCountEditor() {
        SpinnerValueFactory.IntegerSpinnerValueFactory valueFactory =
                (SpinnerValueFactory.IntegerSpinnerValueFactory) randomCountSpinner.getValueFactory();
        TextFormatter<Integer> formatter = new TextFormatter<>(
                new IntegerStringConverter(),
                valueFactory.getValue(),
                change -> change.getControlNewText().matches("\\d*") ? change : null);
        randomCountSpinner.getEditor().setTextFormatter(formatter);
        formatter.valueProperty().bindBidirectional(valueFactory.valueProperty());
        randomCountSpinner.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                valueFactory.setValue(normalizeRandomCountValue(valueFactory.getMax()));
            }
        });
    }

    private int normalizeRandomCountValue(int maxAllowed) {
        SpinnerValueFactory.IntegerSpinnerValueFactory valueFactory =
                (SpinnerValueFactory.IntegerSpinnerValueFactory) randomCountSpinner.getValueFactory();
        int upperBound = Math.max(1, maxAllowed);
        Integer currentValue = valueFactory.getValue();

        if (currentValue == null) {
            String editorText = randomCountSpinner.getEditor().getText();
            if (editorText != null && !editorText.isBlank()) {
                try {
                    currentValue = Integer.parseInt(editorText.trim());
                } catch (NumberFormatException ignored) {
                    currentValue = null;
                }
            }
        }

        if (currentValue == null) {
            currentValue = upperBound;
        }

        return Math.max(1, Math.min(upperBound, currentValue));
    }

    private String normalizeSearchText(String value) {
        return safeText(value).toLowerCase(Locale.ROOT).trim();
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    /**
     * Returns the final selected and unselected cards.
     */
    public record SelectionResult(List<Card> selectedCards, List<Card> unselectedCards, List<Card> emptySourceCards) {
    }

    /**
     * Small view model for a card tile.
     */
    private record CardTile(VBox root, Label badge) {
    }

    private enum SortMode {
        ORIGINAL("\u9ED8\u8BA4\u987A\u5E8F"),
        TITLE_ASC("\u6807\u9898\u5347\u5E8F"),
        TITLE_DESC("\u6807\u9898\u964D\u5E8F");

        private final String label;

        SortMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
