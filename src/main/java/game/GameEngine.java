package game;

import audio.AudioPlayer;
import model.Card;
import model.Deck;
import model.GameState;
import model.Song;

public class GameEngine {
    private final GameState gameState;
    private final AudioPlayer audioPlayer;
    private final GameRules gameRules;

    private GameListener listener;
    private Thread playbackThread;
    private long playbackSessionId;

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

    public GameEngine(AudioPlayer audioPlayer, GameRules gameRules) {
        this.audioPlayer = audioPlayer;
        this.gameRules = gameRules;
        this.gameState = new GameState();
    }

    public void initializeGame(Deck deck, int totalRounds) {
        gameState.initializeGame(deck, totalRounds);
    }

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

    public void startNewRound() {
        if (gameState.isGameOver()) {
            endGame();
            return;
        }

        gameState.startNewRound();
        startInnerRound();
    }

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
        }
    }

    private void playMusicWithLimit(Song song) {
        try {
            gameState.startMusicPlayback();
            int duration = gameRules.getPlaybackDuration();
            long sessionId = ++playbackSessionId;

            if (listener != null) {
                listener.onMusicStart(song);
            }

            playbackThread = new Thread(() -> {
                try {
                    audioPlayer.playLimited(song, duration);
                    Thread.sleep(duration * 1000L + 100L);
                    if (sessionId != playbackSessionId || gameState.getRoundState() != GameState.RoundState.MUSIC_PLAYING) {
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

    public void stopCurrentMusic() {
        cancelPlaybackThread();
        audioPlayer.stop();
        if (listener != null) {
            listener.onMusicInterrupted();
        }
    }

    public void submitRoundResult(GameState.Result result) {
        cancelPlaybackThread();
        audioPlayer.stop();
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

    private void enterRestState() {
        try {
            gameState.enterRestMusic();
            if (listener != null) {
                listener.onRestMusicStart();
            }

            Song restSong = gameRules.getRestMusic();
            if (shouldPlayRestMusic() && restSong != null && restSong.fileExists()) {
                audioPlayer.play(restSong);
            }
        } catch (Exception e) {
            if (listener != null) {
                listener.onError("Unable to start rest music: " + e.getMessage());
            }
        }
    }

    private boolean shouldPlayRestMusic() {
        return gameRules.isRestMusicEnabled()
            && gameRules.getRestIntervalRounds() > 0
            && gameState.getCurrentRound() % gameRules.getRestIntervalRounds() == 0
            && gameState.getCurrentRound() < gameState.getTotalRounds()
            && gameState.getCurrentDeck() != null
            && gameState.getCurrentDeck().hasActiveCards();
    }

    public void resumeFromRest() {
        cancelPlaybackThread();
        audioPlayer.stop();
        gameState.resumeFromRest();

        if (listener != null) {
            listener.onRestMusicComplete();
        }

        startNewRound();
    }

    public void endGame() {
        cancelPlaybackThread();
        audioPlayer.stop();
        gameState.endGame();
        if (listener != null) {
            listener.onGameOver(gameState);
        }
    }

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

    public void dispose() {
        audioPlayer.dispose();
        cancelPlaybackThread();
    }

    private void cancelPlaybackThread() {
        playbackSessionId++;
        if (playbackThread != null && playbackThread.isAlive()) {
            playbackThread.interrupt();
        }
    }
}
