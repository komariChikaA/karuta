package model;

import java.util.ArrayList;
import java.util.List;

/**
 * 游戏状态类
 * 管理整个游戏的当前状态
 */
public class GameState {
    
    public enum RoundState {
        IDLE,               // 空闲状态
        CARD_SELECTED,      // 已选择卡牌
        MUSIC_PLAYING,      // 音乐播放中
        WAITING_RESULT,     // 等待结果
        ROUND_COMPLETE,     // 回合完成
        REST_MUSIC,         // 中场休息
        GAME_OVER           // 游戏结束
    }
    
    public enum Result {
        SUCCESS,            // 对局成功
        FAILURE,            // 对局失败
        PASS,               // 轮空
        SKIP                // 弃牌
    }
    
    private Deck currentDeck;           // 当前使用的卡组
    private Card currentCard;           // 当前演唱的卡牌
    private Song currentSong;           // 当前播放的歌曲
    private RoundState roundState;      // 当前回合状态
    private int currentRound;           // 当前回合数
    private int totalRounds;            // 总回合数
    private long roundStartTime;        // 回合开始时间
    private List<Card> usedCards;       // 已使用过的卡牌
    private List<Result> roundResults;  // 每个回合的结果记录
    
    public GameState() {
        this.roundState = RoundState.IDLE;
        this.currentRound = 0;
        this.totalRounds = 0;
        this.usedCards = new ArrayList<>();
        this.roundResults = new ArrayList<>();
    }
    
    /**
     * 初始化游戏
     */
    public void initializeGame(Deck deck, int totalRounds) {
        this.currentDeck = deck;
        this.totalRounds = totalRounds;
        this.currentRound = 0;
        this.usedCards.clear();
        this.roundResults.clear();
        this.roundState = RoundState.IDLE;
        
        // 初始化卡组，将所有卡加入场上
        deck.initializeGame();
    }
    
    /**
     * 开始新的回合
     */
    public void startNewRound() {
        currentRound++;
        currentCard = null;
        currentSong = null;
        roundStartTime = System.currentTimeMillis();
        roundState = RoundState.IDLE;
    }
    
    /**
     * 选择卡牌并随机抽取歌曲
     */
    public void selectCard() {
        if (currentDeck == null || !currentDeck.hasActiveCards()) {
            roundState = RoundState.GAME_OVER;
            return;
        }
        
        currentCard = currentDeck.getRandomActiveCard();
        if (currentCard != null) {
            currentSong = currentCard.getRandomSong();
            roundState = RoundState.CARD_SELECTED;
        }
    }
    
    /**
     * 开始播放音乐
     */
    public void startMusicPlayback() {
        if (currentSong != null) {
            roundState = RoundState.MUSIC_PLAYING;
        }
    }
    
    /**
     * 音乐播放完成
     */
    public void musicPlaybackComplete() {
        roundState = RoundState.WAITING_RESULT;
    }
    
    /**
     * 提交对局结果
     */
    public void submitResult(Result result) {
        roundResults.add(result);
        
        if (result == Result.SUCCESS && currentCard != null) {
            // 对局成功，将卡牌从场上移除
            currentDeck.removeActiveCard(currentCard);
            usedCards.add(currentCard);
        }
        
        roundState = RoundState.ROUND_COMPLETE;
    }
    
    /**
     * 进入中场休息
     */
    public void enterRestMusic() {
        roundState = RoundState.REST_MUSIC;
    }
    
    /**
     * 从中场休息返回准备状态
     */
    public void resumeFromRest() {
        roundState = RoundState.IDLE;
    }
    
    /**
     * 检查游戏是否结束
     */
    public boolean isGameOver() {
        return currentDeck == null || !currentDeck.hasActiveCards();
    }
    
    /**
     * 结束游戏
     */
    public void endGame() {
        roundState = RoundState.GAME_OVER;
    }
    
    /**
     * 获取游戏进度（百分比）
     */
    public int getProgress() {
        if (currentDeck == null || totalRounds == 0) {
            return 0;
        }
        int completedCards = totalRounds - currentDeck.getActiveCardCount();
        if (completedCards <= 0) {
            return 0;
        }
        return Math.min(100, (completedCards * 100) / totalRounds);
    }

    public int getRemainingRounds() {
        if (currentDeck == null) {
            return 0;
        }
        return currentDeck.getActiveCardCount();
    }
    
    /**
     * 获取成功率
     */
    public double getSuccessRate() {
        if (roundResults.isEmpty()) return 0;
        long successCount = roundResults.stream()
            .filter(r -> r == Result.SUCCESS)
            .count();
        return (double) successCount / roundResults.size() * 100;
    }
    
    // Getters
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
