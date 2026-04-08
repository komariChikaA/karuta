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
        stage.setTitle("管理面板");
        stage.setWidth(800);
        stage.setHeight(600);

        VBox root = new VBox(15);
        root.setPadding(new Insets(15));
        root.setStyle("-fx-font-size: 12;");

        Label titleLabel = new Label("牌组状态");
        titleLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold;");

        statusLabel = new Label("游戏状态：等待中");
        statusLabel.setStyle(
            "-fx-padding: 10;" +
            "-fx-background-color: #fff3cd;" +
            "-fx-border-radius: 5;" +
            "-fx-text-fill: #856404;"
        );

        HBox mainContent = new HBox(15);
        VBox activeCardsBox = createCardsPanel("在场卡牌", activeCardsView = new ListView<>(), true);
        VBox inactiveCardsBox = createCardsPanel("离场卡牌", inactiveCardsView = new ListView<>(), false);
        mainContent.getChildren().addAll(activeCardsBox, inactiveCardsBox);
        HBox.setHgrow(activeCardsBox, Priority.ALWAYS);
        HBox.setHgrow(inactiveCardsBox, Priority.ALWAYS);

        Label infoLabel = new Label("详细信息：");
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

        Label countLabel = new Label("数量：0");
        countLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11;");

        listView.getItems().addListener((javafx.collections.ListChangeListener<? super Card>) change ->
            countLabel.setText("数量：" + listView.getItems().size())
        );

        box.getChildren().addAll(titleLabel, listView, countLabel);
        return box;
    }

    private HBox createButtonBox() {
        HBox buttonBox = new HBox(10);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));

        Button refreshButton = new Button("刷新");
        refreshButton.setOnAction(e -> updateUI());

        Button moveToActiveButton = new Button("移回在场");
        moveToActiveButton.setOnAction(e -> moveSelectedToActive());

        Button moveToInactiveButton = new Button("移出在场");
        moveToInactiveButton.setOnAction(e -> moveSelectedToInactive());

        Button exportButton = new Button("导出");
        exportButton.setOnAction(e -> exportState());

        Button clearButton = new Button("重置离场");
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
            case IDLE -> status = "空闲";
            case CARD_SELECTED -> status = "已选卡";
            case MUSIC_PLAYING -> status = "播放中";
            case WAITING_RESULT -> status = "等待结果";
            case ROUND_COMPLETE -> status = "回合结束";
            case REST_MUSIC -> status = "休息时间";
            case GAME_OVER -> status = "游戏结束";
            default -> status = "未知";
        }

        statusLabel.setText(String.format(
            "状态：%s | 已进行：%d | 剩余：%d | 在场：%d | 成功率：%.1f%%",
            status,
            state.getCurrentRound(),
            state.getRemainingRounds(),
            state.getCurrentDeck().getActiveCardCount(),
            state.getSuccessRate()
        ));
    }

    private void updateInfoArea(GameState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("已进行回合：").append(state.getCurrentRound()).append('\n');
        sb.append("剩余回合：").append(state.getRemainingRounds()).append('\n');
        sb.append("成功：").append(state.getSuccessCount()).append('\n');
        sb.append("失败：").append(state.getFailureCount()).append('\n');
        sb.append("在场卡牌：").append(state.getCurrentDeck().getActiveCardCount()).append('\n');
        sb.append("离场卡牌：").append(state.getCurrentDeck().getInactiveCards().size()).append('\n');
        if (state.getCurrentCard() != null) {
            sb.append("当前卡牌：").append(state.getCurrentCard().getWorkName()).append('\n');
        }
        infoArea.setText(sb.toString());
    }

    private void moveSelectedToActive() {
        Card selectedCard = inactiveCardsView.getSelectionModel().getSelectedItem();
        if (selectedCard != null) {
            gameEngine.getGameState().getCurrentDeck().addActiveCard(selectedCard);
            updateUI();
            inactiveCardsView.getSelectionModel().clearSelection();
            showInfo("已移回在场。");
        }
    }

    private void moveSelectedToInactive() {
        Card selectedCard = activeCardsView.getSelectionModel().getSelectedItem();
        if (selectedCard != null) {
            gameEngine.getGameState().getCurrentDeck().removeActiveCard(selectedCard);
            updateUI();
            activeCardsView.getSelectionModel().clearSelection();
            showInfo("已移出在场。");
        }
    }

    private void clearInactiveCards() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("确认");
        alert.setContentText("是否将所有离场卡牌移回在场？");

        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            Deck deck = gameEngine.getGameState().getCurrentDeck();
            for (Card card : deck.getInactiveCards()) {
                deck.addActiveCard(card);
            }
            updateUI();
            showInfo("离场卡牌已重置。");
        }
    }

    private void exportState() {
        GameState state = gameEngine.getGameState();
        StringBuilder sb = new StringBuilder();
        sb.append("已进行回合：").append(state.getCurrentRound()).append('\n');
        sb.append("剩余回合：").append(state.getRemainingRounds()).append('\n');
        sb.append("成功：").append(state.getSuccessCount()).append('\n');
        sb.append("失败：").append(state.getFailureCount()).append('\n');
        for (Card card : state.getCurrentDeck().getActiveCards()) {
            sb.append("- ").append(card.getWorkName()).append('\n');
        }
        infoArea.setText(sb.toString());
        showInfo("状态已导出到面板。");
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("提示");
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
