module karuta.jukebox {
    requires transitive javafx.controls;
    requires javafx.media;

    exports audio;
    exports config;
    exports game;
    exports model;
    exports ui;
}
