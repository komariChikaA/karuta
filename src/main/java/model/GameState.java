package model;

import java.util.ArrayList;
import java.util.List;

/**
 * 表示一局进行中游戏的核心可变状态。
 */
public class GameState {

    /**
     * 引擎与界面共用的高层回合状态。
     */
    public enum RoundState {
        IDLE,
        CARD_SELECTED,
        EMPTY_CARD,
        MUSIC_PLAYING,
        WAITING_RESULT,
        ROUND_COMPLETE,
        REST_MUSIC,
        GAME_OVER
    }

    /**
     * 操作员在回合结束时提交的判定结果。
     */
    public enum Result {
        SUCCESS,
        FAILURE,
        PASS,
        SKIP
    }

    private Deck currentDeck;
    private Card currentCard;
    private Song currentSong;
    private RoundState roundState;
    private int currentRound;
    private int totalRounds;
    private long roundStartTime;
    private List<Card> usedCards;
    private List<Result> roundResults;

    /**
     * 创建一个空的状态对象，等待引擎初始化。
     */
    public GameState() {
        this.roundState = RoundState.IDLE;
        this.currentRound = 0;
        this.totalRounds = 0;
        this.usedCards = new ArrayList<>();
        this.roundResults = new ArrayList<>();
    }

    /**
     * 重置所有计数器，并让选中的牌组进入可玩状态。
     */
    public void initializeGame(Deck deck, int totalRounds) {
        this.currentDeck = deck;
        this.totalRounds = totalRounds;
        this.currentRound = 0;
        this.usedCards.clear();
        this.roundResults.clear();
        this.roundState = RoundState.IDLE;

        deck.initializeGame();
    }

    /**
     * 推进回合计数，并清空上一回合的选择结果。
     */
    public void startNewRound() {
        currentRound++;
        currentCard = null;
        currentSong = null;
        roundStartTime = System.currentTimeMillis();
        roundState = RoundState.IDLE;
    }

    /**
     * 从在场卡牌中随机选一张，并从该卡牌中随机选一首歌。
     */
    public void selectCard() {
        if (currentDeck == null || !currentDeck.hasActiveRealCards()) {
            roundState = RoundState.GAME_OVER;
            return;
        }

        currentCard = currentDeck.getRandomActiveCard();
        if (currentCard != null) {
            if (currentCard.isEmptyCard()) {
                currentSong = currentCard.getRandomSong();
                roundState = RoundState.EMPTY_CARD;
            } else {
                currentSong = currentCard.getRandomSong();
                roundState = RoundState.CARD_SELECTED;
            }
        }
    }

    /**
     * 将当前回合切换到播放状态。
     */
    public void startMusicPlayback() {
        if (currentSong != null) {
            roundState = RoundState.MUSIC_PLAYING;
        }
    }

    /**
     * 将回合标记为可手动提交成功或失败结果。
     */
    public void musicPlaybackComplete() {
        roundState = RoundState.WAITING_RESULT;
    }

    /**
     * 保存回合结果，并在成功时移除对应卡牌。
     */
    public void submitResult(Result result) {
        roundResults.add(result);

        if (result == Result.SUCCESS && currentCard != null) {
            currentDeck.removeActiveCard(currentCard);
            usedCards.add(currentCard);
        }

        roundState = RoundState.ROUND_COMPLETE;
    }

    /**
     * 将状态机切换到休息阶段。
     */
    public void enterRestMusic() {
        roundState = RoundState.REST_MUSIC;
    }

    /**
     * 在下一回合开始前，从休息状态返回空闲状态。
     */
    public void resumeFromRest() {
        roundState = RoundState.IDLE;
    }

    /**
     * 判断当前游戏是否已经耗尽所有在场卡牌。
     */
    public boolean isGameOver() {
        return currentDeck == null || !currentDeck.hasActiveRealCards();
    }

    /**
     * 强制将游戏置为结束状态。
     */
    public void endGame() {
        roundState = RoundState.GAME_OVER;
    }

    /**
     * 将已完成的卡牌数量转换为 0 到 100 的进度值。
     */
    public int getProgress() {
        if (currentDeck == null || totalRounds == 0) {
            return 0;
        }
        int completedCards = totalRounds - currentDeck.getActiveRealCardCount();
        if (completedCards <= 0) {
            return 0;
        }
        return Math.min(100, (completedCards * 100) / totalRounds);
    }

    /**
     * 返回在场列表中剩余的卡牌数量。
     */
    public int getRemainingRounds() {
        if (currentDeck == null) {
            return 0;
        }
        return currentDeck.getActiveRealCardCount();
    }

    /**
     * 根据已提交的回合结果计算成功率。
     */
    public double getSuccessRate() {
        if (roundResults.isEmpty()) return 0;
        long successCount = roundResults.stream()
            .filter(r -> r == Result.SUCCESS)
            .count();
        return (double) successCount / roundResults.size() * 100;
    }

    public Deck getCurrentDeck() {
        return currentDeck;
    }

    public Card getCurrentCard() {
        return currentCard;
    }

    public Song getCurrentSong() {
        return currentSong;
    }

    public RoundState getRoundState() {
        return roundState;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public int getTotalRounds() {
        return totalRounds;
    }

    public long getRoundStartTime() {
        return roundStartTime;
    }

    public List<Card> getUsedCards() {
        return new ArrayList<>(usedCards);
    }

    public List<Result> getRoundResults() {
        return new ArrayList<>(roundResults);
    }

    public int getSuccessCount() {
        return (int) roundResults.stream()
            .filter(r -> r == Result.SUCCESS)
            .count();
    }

    public int getFailureCount() {
        return (int) roundResults.stream()
            .filter(r -> r == Result.FAILURE)
            .count();
    }

    @Override
    public String toString() {
        return String.format(
            "GameState{round=%d/%d, state=%s, card=%s, successRate=%.1f%%}",
            currentRound, totalRounds, roundState, currentCard, getSuccessRate()
        );
    }
}
