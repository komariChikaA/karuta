package ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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
import javafx.stage.Modality;
import javafx.stage.Stage;
import model.Card;
import model.Deck;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class CardSelectionDialog {
    private final Stage stage;
    private final Deck deck;
    private final int cardLimit;
    private final Set<Card> selectedCards = new LinkedHashSet<>();
    private final Label selectionLabel = new Label();

    private boolean confirmed;

    public CardSelectionDialog(Stage owner, Deck deck, int cardLimit) {
        this.deck = deck;
        this.cardLimit = Math.max(1, cardLimit);
        preselectCards();

        stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("选择参赛卡牌");
        stage.setWidth(1120);
        stage.setHeight(760);
        stage.setScene(buildScene());
        refreshSelectionLabel();
    }

    public SelectionResult showAndWait() {
        stage.showAndWait();
        if (!confirmed) {
            return new SelectionResult(List.of(), List.of());
        }

        List<Card> selected = new ArrayList<>(selectedCards);
        List<Card> unselected = new ArrayList<>();
        for (Card card : deck.getCards()) {
            if (!selectedCards.contains(card)) {
                unselected.add(card);
            }
        }
        return new SelectionResult(selected, unselected);
    }

    private Scene buildScene() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(24));
        root.setStyle(
            "-fx-background-color: linear-gradient(to bottom right, #f8f1e6, #eef4fb);" +
            "-fx-font-family: 'Microsoft YaHei';"
        );

        VBox header = new VBox(8);
        Label titleLabel = new Label("选择本局参赛卡牌");
        titleLabel.setStyle("-fx-font-size: 28; -fx-font-weight: bold; -fx-text-fill: #22324b;");

        Label subtitleLabel = new Label("开始前手动筛选要参与本局的卡牌。这里显示的是卡牌图片和标题。");
        subtitleLabel.setWrapText(true);
        subtitleLabel.setStyle("-fx-font-size: 14; -fx-text-fill: #68788d;");

        selectionLabel.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #31415f;");
        header.getChildren().addAll(titleLabel, subtitleLabel, selectionLabel);

        FlowPane cardsPane = new FlowPane();
        cardsPane.setHgap(16);
        cardsPane.setVgap(16);
        cardsPane.setPadding(new Insets(4));

        for (Card card : deck.getCards()) {
            cardsPane.getChildren().add(createCardTile(card));
        }

        ScrollPane scrollPane = new ScrollPane(cardsPane);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setStyle("-fx-background-color: transparent;");

        HBox buttonBar = new HBox(12);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);

        Button autoPickButton = new Button("自动选前 " + cardLimit + " 张");
        autoPickButton.setStyle(createSecondaryButtonStyle());
        autoPickButton.setOnAction(event -> {
            selectedCards.clear();
            preselectCards();
            rebuild();
        });

        Button clearButton = new Button("清空");
        clearButton.setStyle(createSecondaryButtonStyle());
        clearButton.setOnAction(event -> {
            selectedCards.clear();
            rebuild();
        });

        Button cancelButton = new Button("取消");
        cancelButton.setStyle(createSecondaryButtonStyle());
        cancelButton.setOnAction(event -> stage.close());

        Button confirmButton = new Button("开始游戏");
        confirmButton.setStyle(createPrimaryButtonStyle());
        confirmButton.setOnAction(event -> confirmSelection());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        buttonBar.getChildren().addAll(autoPickButton, clearButton, spacer, cancelButton, confirmButton);

        VBox mainBox = new VBox(18, header, scrollPane, buttonBar);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        root.setCenter(mainBox);
        return new Scene(root);
    }

    private VBox createCardTile(Card card) {
        VBox tile = new VBox(10);
        tile.setPrefWidth(180);
        tile.setPadding(new Insets(14));
        tile.setAlignment(Pos.TOP_CENTER);

        ImageView imageView = new ImageView();
        imageView.setFitWidth(140);
        imageView.setFitHeight(180);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);

        Label imageFallback = new Label("No Image");
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

        Label selectedBadge = new Label("✓");
        selectedBadge.setStyle(
            "-fx-background-color: #2e7d4f;" +
            "-fx-text-fill: white;" +
            "-fx-font-size: 16;" +
            "-fx-font-weight: bold;" +
            "-fx-padding: 4 10;" +
            "-fx-background-radius: 999;"
        );
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
                boolean selected = selectedCards.contains(card);
                selectedBadge.setVisible(selected);
                selectedBadge.setManaged(selected);
                updateCardTileStyle(tile, selected);
            }
        });

        tile.getChildren().addAll(imageStack, titleLabel, songCountLabel);
        return tile;
    }

    private boolean toggleCardSelection(Card card) {
        if (!selectedCards.contains(card)) {
            if (selectedCards.size() >= cardLimit) {
                showError("已到上限", "最多只能选择 " + cardLimit + " 张卡牌。");
                return false;
            }
            selectedCards.add(card);
        } else {
            selectedCards.remove(card);
        }
        refreshSelectionLabel();
        return true;
    }

    private void updateCardTileStyle(VBox tile, boolean selected) {
        if (selected) {
            tile.setStyle(
                "-fx-background-color: rgba(255,255,255,0.96);" +
                "-fx-background-radius: 18;" +
                "-fx-border-radius: 18;" +
                "-fx-border-color: #2e7d4f;" +
                "-fx-border-width: 2;" +
                "-fx-effect: dropshadow(gaussian, rgba(46,125,79,0.20), 18, 0.18, 0, 6);"
            );
            tile.setScaleX(1.03);
            tile.setScaleY(1.03);
        } else {
            tile.setStyle(
                "-fx-background-color: rgba(255,255,255,0.88);" +
                "-fx-background-radius: 18;" +
                "-fx-border-radius: 18;" +
                "-fx-border-color: rgba(36,50,74,0.08);" +
                "-fx-border-width: 1;"
            );
            tile.setScaleX(1.0);
            tile.setScaleY(1.0);
        }
    }

    private void preselectCards() {
        for (Card card : deck.getCards()) {
            if (selectedCards.size() >= cardLimit) {
                break;
            }
            selectedCards.add(card);
        }
    }

    private void refreshSelectionLabel() {
        selectionLabel.setText("已选 " + selectedCards.size() + " / " + cardLimit + " 张，本局实际只使用你勾选的卡牌。");
    }

    private void confirmSelection() {
        if (selectedCards.isEmpty()) {
            showError("未选择卡牌", "请至少选择 1 张卡牌再开始。");
            return;
        }
        confirmed = true;
        stage.close();
    }

    private void rebuild() {
        stage.setScene(buildScene());
        stage.sizeToScene();
        stage.setWidth(Math.max(stage.getWidth(), 1120));
        stage.setHeight(Math.max(stage.getHeight(), 760));
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

    public record SelectionResult(List<Card> selectedCards, List<Card> unselectedCards) {
    }
}
