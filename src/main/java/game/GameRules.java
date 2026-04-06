package game;

import config.ConfigManager;
import model.Song;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameRules {
    public enum FailureMode {
        PASS,
        SKIP
    }

    private static final int DEFAULT_REST_INTERVAL = 5;
    private static final Random RANDOM = new Random();

    private final List<Song> restMusicPool = new ArrayList<>();

    private int minPlaybackDuration;
    private int maxPlaybackDuration;
    private int restIntervalRounds;
    private Song restMusic;
    private FailureMode failureMode;
    private boolean enableRestMusic;

    public GameRules(ConfigManager config) {
        this.minPlaybackDuration = config.getMinDuration();
        this.maxPlaybackDuration = config.getMaxDuration();
        this.restIntervalRounds = DEFAULT_REST_INTERVAL;
        this.enableRestMusic = true;

        File restMusicFile = config.getRestMusicFile();
        if (restMusicFile.exists()) {
            this.restMusic = new Song(restMusicFile.getName(), restMusicFile);
        }

        String mode = config.getFailureMode();
        this.failureMode = FailureMode.valueOf(mode.toUpperCase());
    }

    public int getPlaybackDuration() {
        if (minPlaybackDuration >= maxPlaybackDuration) {
            return minPlaybackDuration;
        }
        return minPlaybackDuration + (int) (Math.random() * (maxPlaybackDuration - minPlaybackDuration + 1));
    }

    public int getRestIntervalRounds() {
        return restIntervalRounds;
    }

    public void setRestIntervalRounds(int interval) {
        if (interval < 0) {
            throw new IllegalArgumentException("Rest interval cannot be negative.");
        }
        this.restIntervalRounds = interval;
    }

    public Song getRestMusic() {
        if (!restMusicPool.isEmpty()) {
            return restMusicPool.get(RANDOM.nextInt(restMusicPool.size()));
        }
        return restMusic;
    }

    public void setRestMusic(Song music) {
        this.restMusic = music;
    }

    public void setRestMusicPool(List<Song> songs) {
        restMusicPool.clear();
        if (songs != null) {
            restMusicPool.addAll(songs);
        }
    }

    public FailureMode getFailureMode() {
        return failureMode;
    }

    public void setFailureMode(FailureMode mode) {
        this.failureMode = mode;
    }

    public boolean isRestMusicEnabled() {
        return enableRestMusic && (restMusic != null || !restMusicPool.isEmpty());
    }

    public void setRestMusicEnabled(boolean enabled) {
        this.enableRestMusic = enabled;
    }

    public boolean isValidDuration(int seconds) {
        return seconds >= minPlaybackDuration && seconds <= maxPlaybackDuration;
    }

    @Override
    public String toString() {
        return String.format(
            "GameRules{duration=%d-%ds, restInterval=%d, failureMode=%s}",
            minPlaybackDuration,
            maxPlaybackDuration,
            restIntervalRounds,
            failureMode
        );
    }
}
