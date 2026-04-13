/**
 * 歌牌点歌应用的模块声明。
 */
module karuta.jukebox {
    requires java.desktop;
    requires transitive javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires org.tomlj;

    exports audio;
    exports config;
    exports game;
    exports model;
    exports ui;

    opens ui to javafx.fxml;
}
