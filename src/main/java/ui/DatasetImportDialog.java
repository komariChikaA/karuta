package ui;

import config.ConfigManager;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class DatasetImportDialog {
    private static final String CSV_HEADER = "image_name,work_name,songs,song_display_names";
    private static final String UTF8_BOM = "\uFEFF";

    private final Stage stage;
    private final ConfigManager configManager;
    private final Consumer<String> onSaved;

    private final ComboBox<String> datasetComboBox = new ComboBox<>();
    private final TextField newDatasetField = new TextField();
    private final ListView<String> worksListView = new ListView<>();
    private final TextField workTitleField = new TextField();
    private final TextField imagePathField = new TextField();
    private final ImageView imagePreview = new ImageView();
    private final Label imageHintLabel = new Label("未选择图片");
    private final VBox songsBox = new VBox(10);
    private final Label statusLabel = new Label("先选择数据集，再逐个作品导入或修改。");

    private final List<WorkEntry> currentWorks = new ArrayList<>();
    private final List<SongItem> currentSongs = new ArrayList<>();

    private String activeDatasetName;
    private int editingIndex = -1;
    private File selectedImageSource;
    private String currentImageFileName;
    private boolean updatingDatasetSelection;

    public DatasetImportDialog(Stage owner, ConfigManager configManager, String initialDatasetName, Consumer<String> onSaved) {
        this.configManager = configManager;
        this.onSaved = onSaved;

        stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("导入数据集");
        stage.setWidth(1180);
        stage.setHeight(960);
        stage.setScene(buildScene());

        refreshDatasetChoices(null);
        if (initialDatasetName != null && !initialDatasetName.isBlank()) {
            newDatasetField.clear();
            loadDataset(initialDatasetName);
            refreshDatasetChoices(sanitizeName(initialDatasetName));
        } else {
            resetEditorForNewWork();
        }
    }

    public void showAndWait() {
        stage.showAndWait();
    }

    private Scene buildScene() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(24));
        root.setStyle(
            "-fx-background-color: linear-gradient(to bottom right, #f7f2e7, #eef5fb);" +
            "-fx-font-family: 'Microsoft YaHei';"
        );

        VBox header = new VBox(8);
        Label titleLabel = new Label("数据集编辑");
        titleLabel.setStyle("-fx-font-size: 30; -fx-font-weight: bold; -fx-text-fill: #22324b;");
        Label subtitleLabel = new Label("先选择一个数据集，再一部作品一部作品地新增或修改。");
        subtitleLabel.setWrapText(true);
        subtitleLabel.setStyle("-fx-font-size: 14; -fx-text-fill: #68788d;");
        header.getChildren().addAll(titleLabel, subtitleLabel);

        HBox content = new HBox(22, buildDatasetPanel(), buildEditorPanel());
        HBox.setHgrow(content.getChildren().get(1), Priority.ALWAYS);

        HBox buttonBar = new HBox(12);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);

        Button newWorkButton = new Button("新建作品");
        newWorkButton.setStyle(createSecondaryButtonStyle());
        newWorkButton.setOnAction(event -> resetEditorForNewWork());

        Button saveWorkButton = createPrimaryButton("保存作品");
        saveWorkButton.setOnAction(event -> saveWork());

        Button closeButton = new Button("关闭");
        closeButton.setStyle(createSecondaryButtonStyle());
        closeButton.setOnAction(event -> stage.close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        buttonBar.getChildren().addAll(newWorkButton, spacer, closeButton, saveWorkButton);

        statusLabel.setWrapText(true);
        statusLabel.setStyle(
            "-fx-padding: 12 14;" +
            "-fx-background-color: rgba(255,255,255,0.82);" +
            "-fx-background-radius: 14;" +
            "-fx-text-fill: #4e5c70;"
        );

        VBox mainBox = new VBox(20, header, content, statusLabel, buttonBar);
        VBox.setVgrow(content, Priority.ALWAYS);
        root.setCenter(mainBox);
        return new Scene(root);
    }

    private VBox buildDatasetPanel() {
        VBox leftPanel = new VBox(16);
        leftPanel.setPrefWidth(360);
        leftPanel.setPadding(new Insets(22));
        leftPanel.setStyle(createPanelStyle("#fffaf3"));

        Label sectionTitle = new Label("数据集");
        sectionTitle.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #22324b;");

        datasetComboBox.setPrefWidth(Double.MAX_VALUE);
        datasetComboBox.setPromptText("选择已有数据集");
        datasetComboBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (!updatingDatasetSelection && newValue != null && !newValue.isBlank()) {
                newDatasetField.clear();
                loadDataset(newValue);
            }
        });

        newDatasetField.setPromptText("或输入新数据集名称");

        Button useDatasetButton = createPrimaryButton("使用这个数据集");
        useDatasetButton.setOnAction(event -> applyDatasetSelection());

        Button importPackageButton = new Button("导入数据包");
        importPackageButton.setStyle(createSecondaryButtonStyle());
        importPackageButton.setOnAction(event -> importDatasetPackage());

        Button exportPackageButton = new Button("导出数据包");
        exportPackageButton.setStyle(createSecondaryButtonStyle());
        exportPackageButton.setOnAction(event -> exportDatasetPackage());

        Button deleteDatasetButton = new Button("删除数据集");
        deleteDatasetButton.setStyle(createSecondaryButtonStyle());
        deleteDatasetButton.setOnAction(event -> deleteDataset());

        HBox packageActionBar = new HBox(10, importPackageButton, exportPackageButton, deleteDatasetButton);
        packageActionBar.setAlignment(Pos.CENTER_LEFT);

        worksListView.setPrefHeight(520);
        worksListView.getSelectionModel().selectedIndexProperty().addListener((obs, oldValue, newValue) -> {
            int index = newValue == null ? -1 : newValue.intValue();
            if (index >= 0 && index < currentWorks.size()) {
                loadWorkIntoEditor(index);
            }
        });

        leftPanel.getChildren().addAll(
            sectionTitle,
            createFieldBlock("已有数据集", "下拉选择后可直接编辑里面的作品。", datasetComboBox),
            createFieldBlock("新数据集名称", "没有的话就在这里新建。", newDatasetField),
            useDatasetButton,
            packageActionBar,
            createFieldBlock("作品列表", "左侧选中某个作品即可进入编辑状态。", worksListView)
        );
        VBox.setVgrow(worksListView, Priority.ALWAYS);

        return leftPanel;
    }

    private VBox buildEditorPanel() {
        VBox rightPanel = new VBox(18);
        rightPanel.setPadding(new Insets(22));
        rightPanel.setStyle(createPanelStyle("#f8fbff"));

        Label editorTitle = new Label("作品编辑");
        editorTitle.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #22324b;");

        workTitleField.setPromptText("作品标题");
        imagePathField.setEditable(false);
        imagePathField.setPromptText("选择作品图片");

        Button chooseImageButton = new Button("选择图片");
        chooseImageButton.setStyle(createSecondaryButtonStyle());
        chooseImageButton.setOnAction(event -> chooseImage());

        imagePreview.setFitWidth(240);
        imagePreview.setFitHeight(320);
        imagePreview.setPreserveRatio(true);
        imagePreview.setSmooth(true);

        imageHintLabel.setWrapText(true);
        imageHintLabel.setMaxWidth(240);
        imageHintLabel.setStyle("-fx-text-fill: #74839a; -fx-font-size: 13;");

        VBox imageCard = new VBox(12, imagePreview, imageHintLabel);
        imageCard.setAlignment(Pos.TOP_CENTER);
        imageCard.setPadding(new Insets(16));
        imageCard.setPrefWidth(300);
        imageCard.setStyle(createInsetCardStyle());

        Button chooseSongsButton = new Button("添加曲子");
        chooseSongsButton.setStyle(createSecondaryButtonStyle());
        chooseSongsButton.setOnAction(event -> chooseSongs());

        Button clearSongsButton = new Button("清空曲子");
        clearSongsButton.setStyle(createSecondaryButtonStyle());
        clearSongsButton.setOnAction(event -> {
            currentSongs.clear();
            refreshSongsBox();
        });

        HBox songActionBar = new HBox(12, chooseSongsButton, clearSongsButton);
        songActionBar.setAlignment(Pos.CENTER_LEFT);

        songsBox.setFillWidth(true);
        ScrollPane songsScrollPane = new ScrollPane(songsBox);
        songsScrollPane.setFitToWidth(true);
        songsScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        songsScrollPane.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(songsScrollPane, Priority.ALWAYS);

        HBox editorBody = new HBox(20,
            imageCard,
            new VBox(16,
                createFieldBlock("作品标题", "这里是作品名，不是数据集名。", workTitleField),
                createFieldBlock("作品图片", null, buildInlineRow(imagePathField, chooseImageButton)),
                createFieldBlock("作品曲目", "每首曲子都可以单独改显示名。", songActionBar),
                songsScrollPane
            )
        );
        HBox.setHgrow(editorBody.getChildren().get(1), Priority.ALWAYS);
        VBox.setVgrow((VBox) editorBody.getChildren().get(1), Priority.ALWAYS);

        rightPanel.getChildren().addAll(editorTitle, editorBody);
        VBox.setVgrow(editorBody, Priority.ALWAYS);
        return rightPanel;
    }

    private void applyDatasetSelection() {
        String newDatasetName = sanitizeName(newDatasetField.getText());
        if (!newDatasetName.equals("dataset") || isMeaningful(newDatasetField.getText())) {
            loadDataset(newDatasetName);
            refreshDatasetChoices(newDatasetName);
            return;
        }

        String selected = datasetComboBox.getValue();
        if (selected == null || selected.isBlank()) {
            showError("未选择数据集", "请选择已有数据集，或者输入一个新数据集名称。");
            return;
        }

        loadDataset(selected);
    }

    private void loadDataset(String datasetName) {
        try {
            activeDatasetName = sanitizeName(datasetName);
            currentWorks.clear();
            currentWorks.addAll(readWorks(getDeckFile(activeDatasetName)));
            refreshWorksList();
            resetEditorForNewWork();
            statusLabel.setText("当前数据集: " + activeDatasetName + "，可逐条新增或修改作品。");
            if (onSaved != null) {
                onSaved.accept(activeDatasetName);
            }
        } catch (Exception e) {
            showError("读取数据集失败", e.getMessage());
        }
    }

    private void refreshDatasetChoices(String preferredDataset) {
        try {
            List<String> datasetNames = listDatasetNames();
            updatingDatasetSelection = true;
            datasetComboBox.setItems(FXCollections.observableArrayList(datasetNames));
            if (preferredDataset != null && datasetNames.contains(preferredDataset)) {
                datasetComboBox.setValue(preferredDataset);
            } else if (activeDatasetName != null && datasetNames.contains(activeDatasetName)) {
                datasetComboBox.setValue(activeDatasetName);
            } else if (!datasetNames.isEmpty()) {
                datasetComboBox.setValue(datasetNames.get(0));
            } else {
                datasetComboBox.setValue(null);
            }
            updatingDatasetSelection = false;
        } catch (IOException e) {
            updatingDatasetSelection = false;
            showError("读取数据集列表失败", e.getMessage());
        }
    }

    private List<String> listDatasetNames() throws IOException {
        Path deckDirectory = getDeckDirectory();
        Files.createDirectories(deckDirectory);

        List<String> names = new ArrayList<>();
        try (java.util.stream.Stream<Path> stream = Files.list(deckDirectory)) {
            stream.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv"))
                .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                .forEach(path -> names.add(stripExtension(path.getFileName().toString())));
        }
        return names;
    }

    private void refreshWorksList() {
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < currentWorks.size(); i++) {
            WorkEntry entry = currentWorks.get(i);
            labels.add((i + 1) + ". " + entry.workName);
        }
        worksListView.setItems(FXCollections.observableArrayList(labels));
    }

    private void loadWorkIntoEditor(int index) {
        WorkEntry entry = currentWorks.get(index);
        editingIndex = index;
        workTitleField.setText(entry.workName);

        selectedImageSource = null;
        currentImageFileName = entry.imageName;
        updateImageFieldsForExistingAsset(entry.imageName);

        currentSongs.clear();
        for (int i = 0; i < entry.songFileNames.size(); i++) {
            String displayName = i < entry.songDisplayNames.size() ? entry.songDisplayNames.get(i) : stripExtension(entry.songFileNames.get(i));
            currentSongs.add(SongItem.existing(entry.songFileNames.get(i), displayName));
        }
        refreshSongsBox();
        statusLabel.setText("正在编辑作品: " + entry.workName);
    }

    private void resetEditorForNewWork() {
        editingIndex = -1;
        workTitleField.clear();
        selectedImageSource = null;
        currentImageFileName = null;
        imagePathField.clear();
        imagePreview.setImage(null);
        imageHintLabel.setText("未选择图片");
        currentSongs.clear();
        refreshSongsBox();
        worksListView.getSelectionModel().clearSelection();
        statusLabel.setText(activeDatasetName == null
            ? "先选择数据集，再逐个作品导入或修改。"
            : "当前是新作品模式，保存后会继续让你录入下一部作品。");
    }

    private void chooseImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择图片");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("图片文件", "*.png", "*.jpg", "*.jpeg", "*.webp", "*.bmp")
        );

        File file = fileChooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }

        selectedImageSource = file;
        currentImageFileName = null;
        imagePathField.setText(file.getAbsolutePath());
        imageHintLabel.setText(file.getName());
        if (workTitleField.getText().isBlank()) {
            workTitleField.setText(stripExtension(file.getName()));
        }
        loadPreviewImage(file);
    }

    private void chooseSongs() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择曲子");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("音频文件", buildAudioExtensions())
        );

        List<File> files = fileChooser.showOpenMultipleDialog(stage);
        if (files == null || files.isEmpty()) {
            return;
        }

        Set<String> existingKeys = new LinkedHashSet<>();
        for (SongItem item : currentSongs) {
            existingKeys.add(item.uniqueKey());
        }

        for (File file : files) {
            SongItem newItem = SongItem.fromSource(file, stripExtension(file.getName()));
            if (existingKeys.add(newItem.uniqueKey())) {
                currentSongs.add(newItem);
            }
        }

        refreshSongsBox();
    }

    private void refreshSongsBox() {
        songsBox.getChildren().clear();

        if (currentSongs.isEmpty()) {
            Label emptyLabel = new Label("当前作品还没有曲子。");
            emptyLabel.setStyle("-fx-font-size: 14; -fx-text-fill: #74839a;");
            songsBox.getChildren().add(emptyLabel);
            return;
        }

        for (SongItem songItem : new ArrayList<>(currentSongs)) {
            Label fileLabel = new Label(songItem.fileLabel());
            fileLabel.setWrapText(true);
            fileLabel.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #31415f;");

            Button removeButton = new Button("移除");
            removeButton.setStyle(createSecondaryButtonStyle());
            removeButton.setOnAction(event -> {
                currentSongs.remove(songItem);
                refreshSongsBox();
            });

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox titleRow = new HBox(10, fileLabel, spacer, removeButton);
            titleRow.setAlignment(Pos.CENTER_LEFT);

            VBox card = new VBox(10,
                titleRow,
                createFieldBlock("曲名", "这里可以改游戏里显示的曲子名字。", songItem.displayNameField)
            );
            card.setPadding(new Insets(16));
            card.setStyle(createInsetCardStyle());
            songsBox.getChildren().add(card);
        }
    }

    private void saveWork() {
        try {
            String datasetName = resolveDatasetNameForSave();
            String workTitle = requireText(workTitleField, "请输入作品标题。");

            if (selectedImageSource == null && currentImageFileName == null) {
                throw new IllegalArgumentException("请为这个作品选择图片。");
            }
            if (currentSongs.isEmpty()) {
                throw new IllegalArgumentException("请至少选择一首曲子。");
            }

            Files.createDirectories(getDeckDirectory());
            Files.createDirectories(Path.of(configManager.getImagesFolder()));
            Files.createDirectories(Path.of(configManager.getMusicFolder()));

            String imageFileName = resolveImageFileName(datasetName);
            List<String> songFileNames = new ArrayList<>();
            List<String> songDisplayNames = new ArrayList<>();

            for (SongItem songItem : currentSongs) {
                String displayName = requireText(songItem.displayNameField, "曲名不能为空。");
                songFileNames.add(resolveSongFileName(datasetName, songItem));
                songDisplayNames.add(displayName);
            }

            WorkEntry entry = new WorkEntry(imageFileName, workTitle, songFileNames, songDisplayNames);
            boolean wasEditingExistingWork = editingIndex >= 0 && editingIndex < currentWorks.size();
            if (wasEditingExistingWork) {
                currentWorks.set(editingIndex, entry);
            } else {
                currentWorks.add(entry);
                editingIndex = currentWorks.size() - 1;
            }

            writeWorks(getDeckFile(datasetName), currentWorks);
            activeDatasetName = datasetName;
            refreshDatasetChoices(datasetName);
            refreshWorksList();
            if (wasEditingExistingWork) {
                worksListView.getSelectionModel().select(editingIndex);
                statusLabel.setText("已更新当前作品，仍然停留在这个数据集里。");
            } else {
                resetEditorForNewWork();
                statusLabel.setText("已新增一个作品，现在可以继续导入下一个作品。");
            }
            if (onSaved != null) {
                onSaved.accept(datasetName);
            }
        } catch (Exception e) {
            showError("保存失败", e.getMessage());
        }
    }

    private void importDatasetPackage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("导入数据包");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP 数据包", "*.zip"));
        File packageFile = fileChooser.showOpenDialog(stage);
        if (packageFile == null) {
            return;
        }

        try {
            Files.createDirectories(getDeckDirectory());
            Files.createDirectories(Path.of(configManager.getImagesFolder()));
            Files.createDirectories(Path.of(configManager.getMusicFolder()));

            String importedDatasetName = importDatasetFromZip(packageFile.toPath());
            loadDataset(importedDatasetName);
            refreshDatasetChoices(importedDatasetName);
            statusLabel.setText("数据包导入成功：" + importedDatasetName);
            if (onSaved != null) {
                onSaved.accept(importedDatasetName);
            }
        } catch (Exception e) {
            showError("导入失败", e.getMessage());
        }
    }

    private void exportDatasetPackage() {
        try {
            String datasetName = resolveDatasetNameForExport();
            Path deckFile = getDeckFile(datasetName);
            if (!Files.exists(deckFile)) {
                throw new IllegalArgumentException("未找到数据集 CSV：" + deckFile.getFileName());
            }

            List<WorkEntry> works = readWorks(deckFile);
            if (works.isEmpty()) {
                throw new IllegalArgumentException("数据集为空，无法导出。");
            }

            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("导出数据包");
            fileChooser.setInitialFileName(datasetName + ".zip");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP 数据包", "*.zip"));
            File targetFile = fileChooser.showSaveDialog(stage);
            if (targetFile == null) {
                return;
            }

            Path targetZip = ensureZipExtension(targetFile.toPath());
            exportDatasetToZip(datasetName, works, targetZip);
            statusLabel.setText("数据包导出成功：" + targetZip.getFileName());
        } catch (Exception e) {
            showError("导出失败", e.getMessage());
        }
    }

    private void deleteDataset() {
        try {
            String datasetName = resolveDatasetNameForExport();
            Path deckFile = getDeckFile(datasetName);
            if (!Files.exists(deckFile)) {
                throw new IllegalArgumentException("未找到数据集 CSV：" + deckFile.getFileName());
            }

            ButtonType csvOnlyButton = new ButtonType("仅删除 CSV");
            ButtonType withAssetsButton = new ButtonType("删除 CSV + 独占资源");
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.initOwner(stage);
            confirm.setTitle("删除数据集");
            confirm.setHeaderText(null);
            confirm.setContentText("确认删除数据集 \"" + datasetName + "\"？");
            confirm.getButtonTypes().setAll(csvOnlyButton, withAssetsButton, ButtonType.CANCEL);

            ButtonType decision = confirm.showAndWait().orElse(ButtonType.CANCEL);
            if (decision == ButtonType.CANCEL) {
                return;
            }

            boolean deleteExclusiveAssets = decision == withAssetsButton;
            int deletedImages = 0;
            int deletedSongs = 0;

            if (deleteExclusiveAssets) {
                Set<String> targetImages = new LinkedHashSet<>();
                Set<String> targetSongs = new LinkedHashSet<>();
                collectReferencedAssets(deckFile, targetImages, targetSongs);

                Set<String> otherImages = new LinkedHashSet<>();
                Set<String> otherSongs = new LinkedHashSet<>();
                collectReferencedAssetsFromOtherDecks(deckFile, otherImages, otherSongs);

                deletedImages = deleteUnreferencedAssets(
                    targetImages,
                    otherImages,
                    Path.of(configManager.getImagesFolder())
                );
                deletedSongs = deleteUnreferencedAssets(
                    targetSongs,
                    otherSongs,
                    Path.of(configManager.getMusicFolder())
                );
            }

            Files.delete(deckFile);

            if (datasetName.equals(activeDatasetName)) {
                activeDatasetName = null;
                currentWorks.clear();
                refreshWorksList();
                resetEditorForNewWork();
            }

            refreshDatasetChoices(null);
            if (deleteExclusiveAssets) {
                statusLabel.setText(String.format(
                    "数据集已删除：%s | 删除图片 %d | 删除歌曲 %d",
                    datasetName, deletedImages, deletedSongs
                ));
            } else {
                statusLabel.setText("数据集已删除：" + datasetName);
            }
            if (onSaved != null) {
                onSaved.accept(null);
            }
        } catch (Exception e) {
            showError("删除失败", e.getMessage());
        }
    }

    private void collectReferencedAssets(Path deckFile, Set<String> imageNames, Set<String> songNames) throws IOException {
        List<WorkEntry> works = readWorks(deckFile);
        for (WorkEntry work : works) {
            if (work.imageName != null && !work.imageName.isBlank()) {
                imageNames.add(work.imageName);
            }
            for (String songFileName : work.songFileNames) {
                if (songFileName != null && !songFileName.isBlank()) {
                    songNames.add(songFileName);
                }
            }
        }
    }

    private void collectReferencedAssetsFromOtherDecks(
        Path deletedDeckFile,
        Set<String> imageNames,
        Set<String> songNames
    ) throws IOException {
        Path deckDirectory = getDeckDirectory();
        if (!Files.isDirectory(deckDirectory)) {
            return;
        }

        try (var stream = Files.list(deckDirectory)) {
            List<Path> deckFiles = stream
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv"))
                .filter(path -> !path.equals(deletedDeckFile))
                .collect(Collectors.toList());

            for (Path deckFile : deckFiles) {
                collectReferencedAssets(deckFile, imageNames, songNames);
            }
        }
    }

    private int deleteUnreferencedAssets(Set<String> targetAssets, Set<String> referencedByOtherDecks, Path baseDirectory) throws IOException {
        int deletedCount = 0;
        for (String assetName : targetAssets) {
            if (referencedByOtherDecks.contains(assetName)) {
                continue;
            }
            Path assetPath = baseDirectory.resolve(assetName);
            if (Files.deleteIfExists(assetPath)) {
                deletedCount++;
            }
        }
        return deletedCount;
    }

    private String resolveDatasetNameForExport() {
        if (activeDatasetName != null && !activeDatasetName.isBlank()) {
            return activeDatasetName;
        }

        String selected = datasetComboBox.getValue();
        if (selected != null && !selected.isBlank()) {
            return sanitizeName(selected);
        }

        throw new IllegalArgumentException("请先选择数据集。");
    }

    private Path ensureZipExtension(Path targetPath) {
        String fileName = targetPath.getFileName().toString();
        if (fileName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            return targetPath;
        }
        Path parent = targetPath.getParent();
        String zippedName = fileName + ".zip";
        return parent == null ? Path.of(zippedName) : parent.resolve(zippedName);
    }

    private void exportDatasetToZip(String datasetName, List<WorkEntry> works, Path targetZip) throws IOException {
        Path deckFile = getDeckFile(datasetName);
        if (!Files.exists(deckFile)) {
            throw new IOException("未找到数据集 CSV：" + deckFile.getFileName());
        }

        Set<String> imageNames = new LinkedHashSet<>();
        Set<String> songNames = new LinkedHashSet<>();
        for (WorkEntry work : works) {
            if (work.imageName != null && !work.imageName.isBlank()) {
                imageNames.add(work.imageName);
            }
            for (String songFileName : work.songFileNames) {
                if (songFileName != null && !songFileName.isBlank()) {
                    songNames.add(songFileName);
                }
            }
        }

        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(targetZip))) {
            addFileToZip(zipOutputStream, deckFile, "decks/" + datasetName + ".csv");

            Path imagesDir = Path.of(configManager.getImagesFolder());
            for (String imageName : imageNames) {
                Path imagePath = imagesDir.resolve(imageName);
                if (!Files.isRegularFile(imagePath)) {
                    throw new IOException("缺少图片资源：" + imageName);
                }
                addFileToZip(zipOutputStream, imagePath, "images/" + imageName);
            }

            Path musicDir = Path.of(configManager.getMusicFolder());
            for (String songName : songNames) {
                Path songPath = musicDir.resolve(songName);
                if (!Files.isRegularFile(songPath)) {
                    throw new IOException("缺少音频资源：" + songName);
                }
                addFileToZip(zipOutputStream, songPath, "music/" + songName);
            }
        }
    }

    private void addFileToZip(ZipOutputStream zipOutputStream, Path sourceFile, String zipEntryName) throws IOException {
        ZipEntry zipEntry = new ZipEntry(zipEntryName.replace('\\', '/'));
        zipOutputStream.putNextEntry(zipEntry);
        Files.copy(sourceFile, zipOutputStream);
        zipOutputStream.closeEntry();
    }

    private String importDatasetFromZip(Path zipPath) throws IOException {
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            ZipEntry csvEntry = findCsvEntry(zipFile);
            if (csvEntry == null) {
                throw new IOException("ZIP 中未找到 CSV 数据集文件。");
            }

            String sourceDatasetName = sanitizeName(stripExtension(Path.of(csvEntry.getName()).getFileName().toString()));
            String targetDatasetName = resolveUniqueDatasetName(sourceDatasetName);

            List<WorkEntry> importedWorks;
            try (InputStream csvStream = zipFile.getInputStream(csvEntry)) {
                importedWorks = readWorks(csvStream);
            }
            if (importedWorks.isEmpty()) {
                throw new IOException("CSV 中没有可用作品数据。");
            }

            Path imagesDir = Path.of(configManager.getImagesFolder());
            Path musicDir = Path.of(configManager.getMusicFolder());

            Map<String, ZipEntry> entriesByFullName = new HashMap<>();
            Map<String, List<ZipEntry>> entriesByBaseName = new HashMap<>();
            zipFile.stream()
                .filter(entry -> !entry.isDirectory())
                .forEach(entry -> {
                    String normalized = normalizeEntryPath(entry.getName());
                    entriesByFullName.put(normalized, entry);
                    entriesByFullName.put(normalized.toLowerCase(Locale.ROOT), entry);
                    String baseName = Path.of(normalized).getFileName().toString().toLowerCase(Locale.ROOT);
                    entriesByBaseName.computeIfAbsent(baseName, key -> new ArrayList<>()).add(entry);
                });

            List<WorkEntry> rewrittenWorks = new ArrayList<>();
            for (WorkEntry work : importedWorks) {
                String importedImageName = copyAssetFromZip(
                    zipFile,
                    entriesByFullName,
                    entriesByBaseName,
                    work.imageName,
                    imagesDir,
                    targetDatasetName
                );

                List<String> importedSongNames = new ArrayList<>();
                for (String songName : work.songFileNames) {
                    importedSongNames.add(copyAssetFromZip(
                        zipFile,
                        entriesByFullName,
                        entriesByBaseName,
                        songName,
                        musicDir,
                        targetDatasetName
                    ));
                }

                rewrittenWorks.add(new WorkEntry(importedImageName, work.workName, importedSongNames, work.songDisplayNames));
            }

            writeWorks(getDeckFile(targetDatasetName), rewrittenWorks);
            return targetDatasetName;
        }
    }

    private ZipEntry findCsvEntry(ZipFile zipFile) {
        List<ZipEntry> csvEntries = zipFile.stream()
            .filter(entry -> !entry.isDirectory())
            .filter(entry -> entry.getName().toLowerCase(Locale.ROOT).endsWith(".csv"))
            .sorted(Comparator.comparing(ZipEntry::getName, String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());
        if (csvEntries.isEmpty()) {
            return null;
        }
        return csvEntries.get(0);
    }

    private String resolveUniqueDatasetName(String baseName) throws IOException {
        String candidate = sanitizeName(baseName);
        int counter = 2;
        while (Files.exists(getDeckFile(candidate))) {
            candidate = sanitizeName(baseName) + "_" + counter;
            counter++;
        }
        return candidate;
    }

    private List<WorkEntry> readWorks(InputStream inputStream) throws IOException {
        List<WorkEntry> works = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }

        if (!lines.isEmpty()) {
            lines.set(0, stripBom(lines.get(0)));
        }
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            List<String> parts = parseCsvLine(line);
            if (parts.size() < 3) {
                continue;
            }
            String imageName = parts.get(0).trim();
            String workName = parts.get(1).trim();
            List<String> songFileNames = splitPipeValues(parts.get(2));
            List<String> songDisplayNames = parts.size() >= 4 ? splitPipeValues(parts.get(3)) : new ArrayList<>();
            works.add(new WorkEntry(imageName, workName, songFileNames, songDisplayNames));
        }
        return works;
    }

    private String copyAssetFromZip(
        ZipFile zipFile,
        Map<String, ZipEntry> entriesByFullName,
        Map<String, List<ZipEntry>> entriesByBaseName,
        String referencedName,
        Path targetDirectory,
        String datasetName
    ) throws IOException {
        if (referencedName == null || referencedName.isBlank()) {
            throw new IOException("CSV 中存在空的资源引用。");
        }

        ZipEntry sourceEntry = findAssetEntry(entriesByFullName, entriesByBaseName, referencedName);
        if (sourceEntry == null) {
            throw new IOException("ZIP 中未找到资源：" + referencedName);
        }

        String originalName = Path.of(sourceEntry.getName()).getFileName().toString();
        String extension = "";
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex >= 0) {
            extension = originalName.substring(dotIndex).toLowerCase(Locale.ROOT);
        }
        String baseName = dotIndex >= 0 ? originalName.substring(0, dotIndex) : originalName;
        Path targetPath = buildUniqueAssetPath(targetDirectory, datasetName, baseName, extension);

        try (InputStream inputStream = zipFile.getInputStream(sourceEntry)) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
        return targetPath.getFileName().toString();
    }

    private ZipEntry findAssetEntry(
        Map<String, ZipEntry> entriesByFullName,
        Map<String, List<ZipEntry>> entriesByBaseName,
        String referencedName
    ) {
        String normalizedReference = normalizeEntryPath(referencedName);
        ZipEntry exact = entriesByFullName.get(normalizedReference);
        if (exact != null) {
            return exact;
        }

        String lowerReference = normalizedReference.toLowerCase(Locale.ROOT);
        exact = entriesByFullName.get(lowerReference);
        if (exact != null) {
            return exact;
        }

        String baseName = Path.of(normalizedReference).getFileName().toString().toLowerCase(Locale.ROOT);
        List<ZipEntry> matches = entriesByBaseName.get(baseName);
        if (matches == null || matches.isEmpty()) {
            return null;
        }
        return matches.get(0);
    }

    private String normalizeEntryPath(String value) {
        String normalized = value.replace('\\', '/');
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private String resolveDatasetNameForSave() {
        if (activeDatasetName != null && !activeDatasetName.isBlank()) {
            return activeDatasetName;
        }
        throw new IllegalArgumentException("请先选择或创建一个数据集。");
    }

    private String resolveImageFileName(String datasetName) throws IOException {
        if (selectedImageSource == null) {
            return currentImageFileName;
        }
        String copiedName = copyAsset(selectedImageSource.toPath(), Path.of(configManager.getImagesFolder()), datasetName);
        currentImageFileName = copiedName;
        selectedImageSource = null;
        imageHintLabel.setText(copiedName);
        imagePathField.setText(copiedName);
        return copiedName;
    }

    private String resolveSongFileName(String datasetName, SongItem songItem) throws IOException {
        if (songItem.sourceFile == null) {
            return songItem.storedFileName;
        }

        String copiedName;
        if (isFlacFile(songItem.sourceFile.getName())) {
            copiedName = transcodeFlacToMp3(songItem.sourceFile.toPath(), Path.of(configManager.getMusicFolder()), datasetName);
        } else {
            copiedName = copyAsset(songItem.sourceFile.toPath(), Path.of(configManager.getMusicFolder()), datasetName);
        }
        songItem.storedFileName = copiedName;
        songItem.sourceFile = null;
        return copiedName;
    }

    private void updateImageFieldsForExistingAsset(String imageFileName) {
        currentImageFileName = imageFileName;
        imagePathField.setText(imageFileName);
        imageHintLabel.setText(imageFileName);
        File existingImage = Path.of(configManager.getImagesFolder()).resolve(imageFileName).toFile();
        if (existingImage.isFile()) {
            loadPreviewImage(existingImage);
        } else {
            imagePreview.setImage(null);
        }
    }

    private void loadPreviewImage(File file) {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            imagePreview.setImage(new Image(inputStream));
        } catch (IOException e) {
            imagePreview.setImage(null);
            showError("图片读取失败", e.getMessage());
        }
    }

    private List<WorkEntry> readWorks(Path deckFile) throws IOException {
        List<WorkEntry> works = new ArrayList<>();
        if (!Files.exists(deckFile)) {
            return works;
        }

        List<String> lines = Files.readAllLines(deckFile, StandardCharsets.UTF_8);
        if (!lines.isEmpty()) {
            lines.set(0, stripBom(lines.get(0)));
        }
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }

            List<String> parts = parseCsvLine(line);
            if (parts.size() < 3) {
                continue;
            }

            String imageName = parts.get(0).trim();
            String workName = parts.get(1).trim();
            List<String> songFileNames = splitPipeValues(parts.get(2));
            List<String> songDisplayNames = parts.size() >= 4 ? splitPipeValues(parts.get(3)) : new ArrayList<>();
            works.add(new WorkEntry(imageName, workName, songFileNames, songDisplayNames));
        }
        return works;
    }

    private void writeWorks(Path deckFile, List<WorkEntry> works) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(UTF8_BOM + CSV_HEADER);
        for (WorkEntry work : works) {
            lines.add(String.join(",",
                toCsvValue(work.imageName),
                toCsvValue(work.workName),
                toCsvValue(String.join("|", work.songFileNames)),
                toCsvValue(String.join("|", work.songDisplayNames))
            ));
        }
        Files.write(deckFile, lines, StandardCharsets.UTF_8);
    }

    private List<String> parseCsvLine(String line) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char currentChar = line.charAt(i);

            if (currentChar == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }

            if (currentChar == ',' && !inQuotes) {
                parts.add(current.toString());
                current.setLength(0);
                continue;
            }

            current.append(currentChar);
        }

        parts.add(current.toString());
        return parts;
    }

    private List<String> splitPipeValues(String value) {
        List<String> result = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return result;
        }

        String[] items = value.split("\\|");
        for (String item : items) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private Path getDeckDirectory() {
        File defaultDeck = new File(configManager.getDefaultDeck());
        File parent = defaultDeck.getParentFile();
        return parent != null ? parent.toPath() : Path.of("config", "decks");
    }

    private Path getDeckFile(String datasetName) {
        return getDeckDirectory().resolve(sanitizeName(datasetName) + ".csv");
    }

    private String copyAsset(Path source, Path targetDirectory, String prefix) throws IOException {
        String originalFileName = source.getFileName().toString();
        String extension = "";
        int dotIndex = originalFileName.lastIndexOf('.');
        if (dotIndex >= 0) {
            extension = originalFileName.substring(dotIndex).toLowerCase(Locale.ROOT);
        }

        String baseName = dotIndex >= 0 ? originalFileName.substring(0, dotIndex) : originalFileName;
        Path candidate = buildUniqueAssetPath(targetDirectory, prefix, baseName, extension);
        Files.copy(source, candidate, StandardCopyOption.REPLACE_EXISTING);
        return candidate.getFileName().toString();
    }

    private String transcodeFlacToMp3(Path source, Path targetDirectory, String prefix) throws IOException {
        String originalFileName = source.getFileName().toString();
        int dotIndex = originalFileName.lastIndexOf('.');
        String baseName = dotIndex >= 0 ? originalFileName.substring(0, dotIndex) : originalFileName;
        Path targetFile = buildUniqueAssetPath(targetDirectory, prefix, baseName, ".mp3");

        ProcessBuilder processBuilder = new ProcessBuilder(
            "ffmpeg",
            "-y",
            "-i",
            source.toString(),
            "-codec:a",
            "libmp3lame",
            "-q:a",
            "2",
            targetFile.toString()
        );
        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();
            try (var output = process.getInputStream()) {
                output.transferTo(OutputStream.nullOutputStream());
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                Files.deleteIfExists(targetFile);
                throw new IOException("ffmpeg 转换 flac 失败，退出码: " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Files.deleteIfExists(targetFile);
            throw new IOException("ffmpeg 转换 flac 时被中断", e);
        }

        return targetFile.getFileName().toString();
    }

    private Path buildUniqueAssetPath(Path targetDirectory, String prefix, String baseName, String extension) {
        String normalizedBase = sanitizeName(prefix + "_" + baseName);
        Path candidate = targetDirectory.resolve(normalizedBase + extension);
        int counter = 2;

        while (Files.exists(candidate)) {
            candidate = targetDirectory.resolve(normalizedBase + "_" + counter + extension);
            counter++;
        }
        return candidate;
    }

    private String requireText(TextField field, String message) {
        String value = field.getText() == null ? "" : field.getText().trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private boolean isMeaningful(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String[] buildAudioExtensions() {
        String[] formats = configManager.getSupportedFormats();
        String[] extensions = new String[formats.length + 1];
        for (int i = 0; i < formats.length; i++) {
            extensions[i] = "*." + formats[i];
        }
        extensions[formats.length] = "*.flac";
        return extensions;
    }

    private boolean isFlacFile(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 && fileName.substring(dotIndex + 1).equalsIgnoreCase("flac");
    }

    private String sanitizeName(String value) {
        String source = value == null ? "" : value;
        String normalized = Normalizer.normalize(source, Normalizer.Form.NFKC)
            .replaceAll("[\\\\/:*?\"<>|]", " ")
            .replaceAll("\\s+", "_")
            .trim();

        if (normalized.isEmpty()) {
            return "dataset";
        }
        return normalized;
    }

    private String stripExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    private String toCsvValue(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String stripBom(String value) {
        if (value != null && value.startsWith(UTF8_BOM)) {
            return value.substring(1);
        }
        return value;
    }

    private VBox createFieldBlock(String title, String hint, javafx.scene.Node input) {
        VBox block = new VBox(6);
        Label label = new Label(title);
        label.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #31415f;");
        block.getChildren().add(label);

        if (hint != null && !hint.isBlank()) {
            Label hintLabel = new Label(hint);
            hintLabel.setWrapText(true);
            hintLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #74839a;");
            block.getChildren().add(hintLabel);
        }

        block.getChildren().add(input);
        return block;
    }

    private HBox buildInlineRow(javafx.scene.Node growNode, javafx.scene.Node fixedNode) {
        HBox row = new HBox(10, growNode, fixedNode);
        HBox.setHgrow(growNode, Priority.ALWAYS);
        return row;
    }

    private Button createPrimaryButton(String text) {
        Button button = new Button(text);
        button.setStyle(
            "-fx-background-color: #2e7d4f;" +
            "-fx-text-fill: white;" +
            "-fx-font-size: 14;" +
            "-fx-font-weight: bold;" +
            "-fx-padding: 12 22;" +
            "-fx-background-radius: 12;" +
            "-fx-cursor: hand;"
        );
        return button;
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

    private String createPanelStyle(String background) {
        return "-fx-background-color: " + background + ";" +
            "-fx-background-radius: 22;" +
            "-fx-border-radius: 22;" +
            "-fx-border-color: rgba(29,42,68,0.08);" +
            "-fx-effect: dropshadow(gaussian, rgba(22,33,58,0.10), 24, 0.18, 0, 8);";
    }

    private String createInsetCardStyle() {
        return "-fx-background-color: rgba(255,255,255,0.84);" +
            "-fx-background-radius: 18;" +
            "-fx-border-radius: 18;" +
            "-fx-border-color: rgba(36,50,74,0.08);";
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(stage);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private static final class WorkEntry {
        private final String imageName;
        private final String workName;
        private final List<String> songFileNames;
        private final List<String> songDisplayNames;

        private WorkEntry(String imageName, String workName, List<String> songFileNames, List<String> songDisplayNames) {
            this.imageName = imageName;
            this.workName = workName;
            this.songFileNames = new ArrayList<>(songFileNames);
            this.songDisplayNames = new ArrayList<>(songDisplayNames);
        }
    }

    private static final class SongItem {
        private File sourceFile;
        private String storedFileName;
        private final TextField displayNameField;

        private SongItem(File sourceFile, String storedFileName, String displayName) {
            this.sourceFile = sourceFile;
            this.storedFileName = storedFileName;
            this.displayNameField = new TextField(displayName);
            this.displayNameField.setPromptText("曲子显示名");
        }

        private static SongItem fromSource(File sourceFile, String displayName) {
            return new SongItem(sourceFile, null, displayName);
        }

        private static SongItem existing(String storedFileName, String displayName) {
            return new SongItem(null, storedFileName, displayName);
        }

        private String fileLabel() {
            return sourceFile != null ? sourceFile.getName() : storedFileName;
        }

        private String uniqueKey() {
            return sourceFile != null ? sourceFile.getAbsolutePath() : "stored:" + storedFileName;
        }
    }
}
