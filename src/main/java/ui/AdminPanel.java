package ui;

import game.GameEngine;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import model.Card;
import model.Deck;
import model.GameState;

import java.util.List;

public class AdminPanel {
    private final GameEngine gameEngine;

    private Stage stage;
    private ListView<Card> activeCardsView;
    private ListView<Card> inactiveCardsView;
    private TextArea infoArea;
    private Label statusLabel;
    private boolean autoUpdateStarted;

    public AdminPanel(GameEngine gameEngine) {
        this.gameEngine = gameEngine;
        createUI();
    }

    private void createUI() {
        stage = new Stage();
        stage.setTitle("Admin Panel");
        stage.setWidth(800);
        stage.setHeight(600);

        VBox root = new VBox(15);
        root.setPadding(new Insets(15));
        root.setStyle("-fx-font-size: 12;");

        Label titleLabel = new Label("Deck State");
        titleLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold;");

        statusLabel = new Label("Game state: waiting");
        statusLabel.setStyle(
            "-fx-padding: 10;" +
            "-fx-background-color: #fff3cd;" +
            "-fx-border-radius: 5;" +
            "-fx-text-fill: #856404;"
        );

        HBox mainContent = new HBox(15);
        VBox activeCardsBox = createCardsPanel("Active cards", activeCardsView = new ListView<>(), true);
        VBox inactiveCardsBox = createCardsPanel("Inactive cards", inactiveCardsView = new ListView<>(), false);
        mainContent.getChildren().addAll(activeCardsBox, inactiveCardsBox);
        HBox.setHgrow(activeCardsBox, Priority.ALWAYS);
        HBox.setHgrow(inactiveCardsBox, Priority.ALWAYS);

        Label infoLabel = new Label("Details:");
        infoLabel.setStyle("-fx-font-weight: bold;");

        infoArea = new TextArea();
        infoArea.setEditable(false);
        infoArea.setWrapText(true);
        infoArea.setPrefHeight(120);
        infoArea.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 11;");

        HBox buttonBox = createButtonBox();

        root.getChildren().addAll(
            titleLabel,
            statusLabel,
            mainContent,
            infoLabel,
            infoArea,
            buttonBox
        );

        stage.setScene(new Scene(root));
    }

    private VBox createCardsPanel(String title, ListView<Card> listView, boolean isActive) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(10));
        box.setStyle(
            "-fx-border-color: #ddd;" +
            "-fx-border-radius: 5;" +
            "-fx-background-color: " + (isActive ? "#e8f5e9" : "#f5f5f5") + ";"
        );

        Label titleLabel = new Label(title);
        titleLabel.setStyle(
            "-fx-font-weight: bold;" +
            "-fx-font-size: 13;" +
            "-fx-text-fill: " + (isActive ? "#2e7d32" : "#666") + ";"
        );

        listView.setPrefHeight(250);
        listView.setCellFactory(param -> new CardListCell(isActive));

        Label countLabel = new Label("Count: 0");
        countLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11;");

        listView.getItems().addListener((javafx.collections.ListChangeListener<? super Card>) change ->
            countLabel.setText("Count: " + listView.getItems().size())
        );

        box.getChildren().addAll(titleLabel, listView, countLabel);
        return box;
    }

    private HBox createButtonBox() {
        HBox buttonBox = new HBox(10);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));

        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> updateUI());

        Button moveToActiveButton = new Button("Move To Active");
        moveToActiveButton.setOnAction(e -> moveSelectedToActive());

        Button moveToInactiveButton = new Button("Move To Inactive");
        moveToInactiveButton.setOnAction(e -> moveSelectedToInactive());

        Button exportButton = new Button("Export");
        exportButton.setOnAction(e -> exportState());

        Button clearButton = new Button("Reset Inactive");
        clearButton.setStyle("-fx-text-fill: #d32f2f;");
        clearButton.setOnAction(e -> clearInactiveCards());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        buttonBox.getChildren().addAll(
            refreshButton,
            new Separator(Orientation.VERTICAL),
            moveToActiveButton,
            moveToInactiveButton,
            spacer,
            exportButton,
            clearButton
        );

        return buttonBox;
    }

    private void startAutoUpdate() {
        Thread updateThread = new Thread(() -> {
            while (stage.isShowing()) {
                try {
                    Thread.sleep(1000);
                    Platform.runLater(this::updateUI);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        updateThread.setDaemon(true);
        updateThread.start();
    }

    private void updateUI() {
        try {
            GameState state = gameEngine.getGameState();
            Deck deck = state.getCurrentDeck();
            if (deck == null) {
                return;
            }

            Card selectedActive = activeCardsView.getSelectionModel().getSelectedItem();
            Card selectedInactive = inactiveCardsView.getSelectionModel().getSelectedItem();

            List<Card> activeCards = deck.getActiveCards();
            List<Card> inactiveCards = deck.getInactiveCards();

            activeCardsView.getItems().setAll(activeCards);
            inactiveCardsView.getItems().setAll(inactiveCards);

            restoreSelection(activeCardsView, activeCards, selectedActive);
            restoreSelection(inactiveCardsView, inactiveCards, selectedInactive);

            updateStatusLabel(state);
            updateInfoArea(state);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void restoreSelection(ListView<Card> listView, List<Card> cards, Card selectedCard) {
        if (selectedCard == null) {
            return;
        }
        int index = cards.indexOf(selectedCard);
        if (index >= 0) {
            listView.getSelectionModel().select(index);
        }
    }

    private void updateStatusLabel(GameState state) {
        String status;
        switch (state.getRoundState()) {
            case IDLE -> status = "Idle";
            case CARD_SELECTED -> status = "Card Selected";
            case MUSIC_PLAYING -> status = "Music Playing";
            case WAITING_RESULT -> status = "Waiting Result";
            case ROUND_COMPLETE -> status = "Round Complete";
            case REST_MUSIC -> status = "Rest Music";
            case GAME_OVER -> status = "Game Over";
            default -> status = "Unknown";
        }

        statusLabel.setText(String.format(
            "State: %s | Round: %d/%d | Active: %d | Success: %.1f%%",
            status,
            state.getCurrentRound(),
            state.getTotalRounds(),
            state.getCurrentDeck().getActiveCardCount(),
            state.getSuccessRate()
        ));
    }

    private void updateInfoArea(GameState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("Round: ").append(state.getCurrentRound()).append("/").append(state.getTotalRounds()).append('\n');
        sb.append("Success: ").append(state.getSuccessCount()).append('\n');
        sb.append("Failure: ").append(state.getFailureCount()).append('\n');
        sb.append("Active cards: ").append(state.getCurrentDeck().getActiveCardCount()).append('\n');
        sb.append("Inactive cards: ").append(state.getCurrentDeck().getInactiveCards().size()).append('\n');
        if (state.getCurrentCard() != null) {
            sb.append("Current card: ").append(state.getCurrentCard().getWorkName()).append('\n');
        }
        infoArea.setText(sb.toString());
    }

    private void moveSelectedToActive() {
        Card selectedCard = inactiveCardsView.getSelectionModel().getSelectedItem();
        if (selectedCard != null) {
            gameEngine.getGameState().getCurrentDeck().addActiveCard(selectedCard);
            updateUI();
            inactiveCardsView.getSelectionModel().clearSelection();
            showInfo("Card moved back to active.");
        }
    }

    private void moveSelectedToInactive() {
        Card selectedCard = activeCardsView.getSelectionModel().getSelectedItem();
        if (selectedCard != null) {
            gameEngine.getGameState().getCurrentDeck().removeActiveCard(selectedCard);
            updateUI();
            activeCardsView.getSelectionModel().clearSelection();
            showInfo("Card moved to inactive.");
        }
    }

    private void clearInactiveCards() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm");
        alert.setContentText("Move all inactive cards back to active?");

        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            Deck deck = gameEngine.getGameState().getCurrentDeck();
            for (Card card : deck.getInactiveCards()) {
                deck.addActiveCard(card);
            }
            updateUI();
            showInfo("Inactive cards reset.");
        }
    }

    private void exportState() {
        GameState state = gameEngine.getGameState();
        StringBuilder sb = new StringBuilder();
        sb.append("Round: ").append(state.getCurrentRound()).append("/").append(state.getTotalRounds()).append('\n');
        sb.append("Success: ").append(state.getSuccessCount()).append('\n');
        sb.append("Failure: ").append(state.getFailureCount()).append('\n');
        for (Card card : state.getCurrentDeck().getActiveCards()) {
            sb.append("- ").append(card.getWorkName()).append('\n');
        }
        infoArea.setText(sb.toString());
        showInfo("State exported to panel.");
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Info");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private static class CardListCell extends ListCell<Card> {
        private final boolean isActive;

        CardListCell(boolean isActive) {
            this.isActive = isActive;
        }

        @Override
        protected void updateItem(Card item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
            } else {
                setText(item.getWorkName() + " (" + item.getSongCount() + ")");
                setStyle(isActive
                    ? "-fx-text-fill: #2e7d32; -fx-font-weight: bold;"
                    : "-fx-text-fill: #666; -fx-font-style: italic;");
            }
        }
    }

    public void show() {
        updateUI();
        if (!autoUpdateStarted) {
            autoUpdateStarted = true;
            startAutoUpdate();
        }
        stage.show();
    }

    public void close() {
        stage.close();
    }
}
