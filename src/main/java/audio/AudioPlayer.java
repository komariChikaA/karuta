package audio;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;
import model.Song;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class AudioPlayer {
    private static final double DEFAULT_VOLUME = 0.8;

    private MediaPlayer mediaPlayer;
    private PauseTransition stopTimer;
    private Song currentSong;
    private boolean isPaused;
    private double volume;
    private PlaybackListener listener;

    public interface PlaybackListener {
        void onPlaybackComplete();
        void onPlaybackError(String error);
        void onPlaybackStarted(Song song);
    }

    public AudioPlayer() {
        this.volume = DEFAULT_VOLUME;
        this.isPaused = false;
    }

    public void play(Song song) throws Exception {
        if (song == null || !song.fileExists()) {
            throw new IllegalArgumentException("Song file does not exist.");
        }
        if (!isPlayableFormat(song.getFileName())) {
            throw new IllegalArgumentException("Unsupported audio format. Use mp3, wav, m4a, aif, or aiff.");
        }

        currentSong = song;
        File audioFile = song.getFile();
        String mediaUrl = audioFile.toURI().toString();
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        runOnFxThreadAndWait(() -> {
            try {
                stopInternal();

                Media media = new Media(mediaUrl);
                mediaPlayer = new MediaPlayer(media);
                mediaPlayer.setVolume(volume);
                mediaPlayer.setOnEndOfMedia(() -> {
                    isPaused = false;
                    if (listener != null) {
                        listener.onPlaybackComplete();
                    }
                });
                mediaPlayer.setOnError(() -> {
                    if (listener != null) {
                        String error = mediaPlayer.getError() != null
                            ? mediaPlayer.getError().getMessage()
                            : "Unknown playback error";
                        listener.onPlaybackError(error);
                    }
                });
                mediaPlayer.play();
                isPaused = false;
                if (listener != null) {
                    listener.onPlaybackStarted(song);
                }
            } catch (Exception e) {
                errorRef.set(e);
            }
        });

        if (errorRef.get() != null) {
            throw new RuntimeException("Unable to play file: " + audioFile.getAbsolutePath(), errorRef.get());
        }
    }

    public void playLimited(Song song, int durationSeconds) throws Exception {
        play(song);
        runOnFxThreadAndWait(() -> {
            if (stopTimer != null) {
                stopTimer.stop();
            }
            stopTimer = new PauseTransition(Duration.seconds(durationSeconds));
            stopTimer.setOnFinished(event -> {
                if (mediaPlayer != null && mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
                    stop();
                }
            });
            stopTimer.playFromStart();
        });
    }

    public void pause() {
        runOnFxThreadAndWait(() -> {
            if (mediaPlayer != null && mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
                mediaPlayer.pause();
                isPaused = true;
            }
        });
    }

    public void resume() {
        runOnFxThreadAndWait(() -> {
            if (mediaPlayer != null && isPaused) {
                mediaPlayer.play();
                isPaused = false;
            }
        });
    }

    public void stop() {
        runOnFxThreadAndWait(this::stopInternal);
    }

    private void stopInternal() {
        if (stopTimer != null) {
            stopTimer.stop();
            stopTimer = null;
        }
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
        isPaused = false;
        currentSong = null;
    }

    public void setVolume(double volume) {
        if (volume < 0.0 || volume > 1.0) {
            throw new IllegalArgumentException("Volume must be between 0.0 and 1.0.");
        }
        this.volume = volume;
        runOnFxThreadAndWait(() -> {
            if (mediaPlayer != null) {
                mediaPlayer.setVolume(volume);
            }
        });
    }

    public double getVolume() {
        return volume;
    }

    public void seek(double seconds) {
        runOnFxThreadAndWait(() -> {
            if (mediaPlayer != null) {
                mediaPlayer.seek(Duration.seconds(seconds));
            }
        });
    }

    public double getCurrentTime() {
        return mediaPlayer != null ? mediaPlayer.getCurrentTime().toSeconds() : 0;
    }

    public double getTotalDuration() {
        return mediaPlayer != null && mediaPlayer.getTotalDuration() != null
            ? mediaPlayer.getTotalDuration().toSeconds()
            : 0;
    }

    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING;
    }

    public boolean isPaused() {
        return isPaused;
    }

    public void setPlaybackListener(PlaybackListener listener) {
        this.listener = listener;
    }

    public Song getCurrentSong() {
        return currentSong;
    }

    public void dispose() {
        stop();
    }

    private boolean isPlayableFormat(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0) {
            return false;
        }
        String extension = fileName.substring(lastDot + 1).toLowerCase();
        return extension.matches("mp3|wav|m4a|aif|aiff");
    }

    private void runOnFxThreadAndWait(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } finally {
                latch.countDown();
            }
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("FX thread execution interrupted", e);
        }
    }
}
