package game;

import audio.AudioPlayer;
import model.Card;
import model.Deck;
import model.GameState;
import model.Song;

/**
 * 负责协调回合推进、播放控制与界面回调。
 */
public class GameEngine {
    private static final double REST_VOLUME_DB = -12.0;
    private static final double REST_VOLUME_FACTOR = Math.pow(10.0, REST_VOLUME_DB / 20.0);

    private final GameState gameState;
    private final AudioPlayer audioPlayer;
    private final GameRules gameRules;

    private GameListener listener;
    private Thread playbackThread;
    private long playbackSessionId;
    private double normalPlaybackVolume;
    private int currentPlaybackDurationSeconds;

    /**
     * 供 JavaFX 界面响应引擎状态变化的监听器。
     */
    public interface GameListener {
        void onRoundStart(Card card);

        void onMusicStart(Song song);

        void onMusicComplete();

        void onMusicInterrupted();

        void onRoundComplete(GameState.Result result);

        void onAwaitingStart();

        void onRestMusicStart();

        void onRestMusicComplete();

        void onGameOver(GameState state);

        void onError(String error);
    }

    /**
     * 使用一个播放器和一套规则创建游戏引擎。
     */
    public GameEngine(AudioPlayer audioPlayer, GameRules gameRules) {
        this.audioPlayer = audioPlayer;
        this.gameRules = gameRules;
        this.gameState = new GameState();
        this.normalPlaybackVolume = audioPlayer.getVolume();
    }

    /**
     * 将牌组载入状态对象，但不通知界面。
     */
    public void initializeGame(Deck deck, int totalRounds) {
        gameState.initializeGame(deck, totalRounds);
    }

    /**
     * 初始化游戏，并通知界面可以准备第一回合。
     */
    public void startGame(Deck deck, int totalRounds) {
        try {
            initializeGame(deck, totalRounds);
            if (listener != null) {
                listener.onAwaitingStart();
            }
        } catch (Exception e) {
            if (listener != null) {
                listener.onError("Failed to initialize game: " + e.getMessage());
            }
        }
    }

    /**
     * 从开始、回合结束或休息状态推进状态机。
     */
    public void prepareNextRound() {
        GameState.RoundState state = gameState.getRoundState();
        if (state == GameState.RoundState.GAME_OVER) {
            endGame();
            return;
        }

        if (gameState.getCurrentRound() == 0 && state == GameState.RoundState.IDLE) {
            startNewRound();
            return;
        }

        if (state == GameState.RoundState.REST_MUSIC || state == GameState.RoundState.ROUND_COMPLETE) {
            resumeFromRest();
        }
    }

    /**
     * 在牌组尚未耗尽时开始新回合。
     */
    public void startNewRound() {
        if (gameState.isGameOver()) {
            endGame();
            return;
        }

        gameState.startNewRound();
        startInnerRound();
    }

    /**
     * 选出下一张卡牌，并在播放前通知界面。
     */
    private void startInnerRound() {
        gameState.selectCard();
        Card currentCard = gameState.getCurrentCard();
        if (currentCard == null) {
            endGame();
            return;
        }

        if (listener != null) {
            listener.onRoundStart(currentCard);
        }

        Song song = gameState.getCurrentSong();
        if (song != null) {
            playMusicWithLimit(song);
        } else if (currentCard.isEmptyCard()) {
            currentPlaybackDurationSeconds = 0;
        }
    }

    /**
     * 启动受保护的播放线程，避免过期回调修改当前回合状态。
     */
    private void playMusicWithLimit(Song song) {
        try {
            audioPlayer.setVolume(normalPlaybackVolume);
            gameState.startMusicPlayback();
            int duration = gameRules.getPlaybackDuration();
            currentPlaybackDurationSeconds = duration;
            long sessionId = ++playbackSessionId;

            if (listener != null) {
                listener.onMusicStart(song);
            }

            playbackThread = new Thread(() -> {
                try {
                    audioPlayer.playLimited(song, duration);
                    Thread.sleep(duration * 1000L + 100L);
                    if (sessionId != playbackSessionId
                            || gameState.getRoundState() != GameState.RoundState.MUSIC_PLAYING) {
                        return;
                    }
                    gameState.musicPlaybackComplete();
                    if (listener != null) {
                        listener.onMusicComplete();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    if (listener != null) {
                        listener.onError("Failed to play music: " + e.getMessage());
                    }
                }
            });
            playbackThread.setDaemon(true);
            playbackThread.start();
        } catch (Exception e) {
            if (listener != null) {
                listener.onError("Unable to start playback: " + e.getMessage());
            }
        }
    }

    /**
     * 停止当前播放，并通知界面播放已被中断。
     */
    public void stopCurrentMusic() {
        cancelPlaybackThread();
        audioPlayer.stop();
        if (listener != null) {
            listener.onMusicInterrupted();
        }
    }

    /**
     * 记录判定结果，并切换到休息或游戏结束状态。
     */
    public void submitRoundResult(GameState.Result result) {
        cancelPlaybackThread();
        audioPlayer.stop();
        if (gameState.getCurrentCard() != null && gameState.getCurrentCard().isEmptyCard()) {
            result = GameState.Result.SUCCESS;
        }
        gameState.submitResult(result);
        if (result == GameState.Result.FAILURE && gameState.getCurrentCard() != null
                && gameRules.getFailureMode() == GameRules.FailureMode.SKIP) {
            gameState.getCurrentDeck().removeActiveCard(gameState.getCurrentCard());
        }

        if (listener != null) {
            listener.onRoundComplete(result);
        }

        if (gameState.isGameOver()) {
            endGame();
        } else {
            enterRestState();
        }
    }

    /**
     * 进入休息状态，并在需要时播放较低音量的休息音乐。
     */
    private void enterRestState() {
        try {
            gameState.enterRestMusic();
            if (listener != null) {
                listener.onRestMusicStart();
            }

            Song restSong = gameRules.getRestMusic();
            if (shouldPlayRestMusic() && restSong != null && restSong.fileExists()) {
                audioPlayer.setVolume(normalPlaybackVolume * REST_VOLUME_FACTOR);
                audioPlayer.play(restSong);
            }
        } catch (Exception e) {
            if (listener != null) {
                listener.onError("Unable to start rest music: " + e.getMessage());
            }
        }
    }

    /**
     * 只有在游戏仍进行且已启用休息音乐时才播放休息音乐。
     */
    private boolean shouldPlayRestMusic() {
        return gameRules.isRestMusicEnabled()
                && gameState.getCurrentDeck() != null
                && gameState.getCurrentDeck().hasActiveRealCards();
    }

    /**
     * 离开休息状态并立即开始下一回合。
     */
    public void resumeFromRest() {
        cancelPlaybackThread();
        audioPlayer.stop();
        gameState.resumeFromRest();

        if (listener != null) {
            listener.onRestMusicComplete();
        }

        startNewRound();
    }

    /**
     * 停止播放并将最终状态发送给界面。
     */
    public void endGame() {
        cancelPlaybackThread();
        audioPlayer.stop();
        gameState.endGame();
        if (listener != null) {
            listener.onGameOver(gameState);
        }
    }

    /**
     * 中止当前游戏，但不触发游戏结束回调。
     */
    public void abortGame() {
        cancelPlaybackThread();
        audioPlayer.stop();
        gameState.endGame();
    }

    public void setGameListener(GameListener listener) {
        this.listener = listener;
    }

    public GameState getGameState() {
        return gameState;
    }

    public Deck getCurrentDeck() {
        return gameState.getCurrentDeck();
    }

    public void pauseCurrentMusic() {
        audioPlayer.pause();
    }

    public void resumeCurrentMusic() {
        audioPlayer.resume();
    }

    public boolean isCurrentMusicPlaying() {
        return audioPlayer.isPlaying();
    }

    public boolean isCurrentMusicPaused() {
        return audioPlayer.isPaused();
    }

    /**
     * 释放媒体资源，并中断等待中的播放监控线程。
     */
    public void dispose() {
        audioPlayer.dispose();
        cancelPlaybackThread();
    }

    public double getPlaybackVolume() {
        return normalPlaybackVolume;
    }

    /**
     * 将归一化音量同时应用到正常播放和休息音乐。
     */
    public void setPlaybackVolume(double volume) {
        if (volume < 0.0 || volume > 1.0) {
            throw new IllegalArgumentException("Volume must be between 0.0 and 1.0.");
        }

        normalPlaybackVolume = volume;

        GameState.RoundState roundState = gameState.getRoundState();
        if (roundState == GameState.RoundState.REST_MUSIC) {
            audioPlayer.setVolume(volume * REST_VOLUME_FACTOR);
        } else {
            audioPlayer.setVolume(volume);
        }
    }

    public int getCurrentPlaybackDurationSeconds() {
        return currentPlaybackDurationSeconds;
    }

    /**
     * 使当前播放会话失效，并在需要时中断监控线程。
     */
    private void cancelPlaybackThread() {
        playbackSessionId++;
        if (playbackThread != null && playbackThread.isAlive()) {
            playbackThread.interrupt();
        }
    }
}
