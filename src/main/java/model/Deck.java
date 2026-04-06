package model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 卡组类
 * 代表一个卡组，包含多张卡牌
 */
public class Deck {
    private String deckName;        // 卡组名称
    private String configFile;      // 配置文件路径
    private List<Card> cards;       // 卡组中的所有卡牌
    private List<Card> activeCards; // 当前在场上的卡牌（用于游戏中）
    
    private static final Random RANDOM = new Random();
    
    public Deck(String deckName) {
        this.deckName = deckName;
        this.cards = new ArrayList<>();
        this.activeCards = new ArrayList<>();
    }
    
    public Deck(String deckName, String configFile) {
        this.deckName = deckName;
        this.configFile = configFile;
        this.cards = new ArrayList<>();
        this.activeCards = new ArrayList<>();
    }
    
    /**
     * 添加卡牌
     */
    public void addCard(Card card) {
        if (card != null && !cards.contains(card)) {
            cards.add(card);
        }
    }
    
    /**
     * 移除卡牌
     */
    public void removeCard(Card card) {
        cards.remove(card);
        activeCards.remove(card);
    }
    
    /**
     * 获取所有卡牌
     */
    public List<Card> getCards() {
        return new ArrayList<>(cards);
    }
    
    /**
     * 获取在场上的卡牌
     */
    public List<Card> getActiveCards() {
        return new ArrayList<>(activeCards);
    }
    
    /**
     * 初始化游戏时，将所有卡牌加入场上
     */
    public void initializeGame() {
        activeCards.clear();
        for (Card card : cards) {
            if (card.isActive()) {
                activeCards.add(card);
            }
        }
    }
    
    /**
     * 随机获取一张在场的卡牌
     */
    public Card getRandomActiveCard() {
        if (activeCards.isEmpty()) {
            return null;
        }
        return activeCards.get(RANDOM.nextInt(activeCards.size()));
    }
    
    /**
     * 从场上移除卡牌（对局成功时调用）
     */
    public boolean removeActiveCard(Card card) {
        return activeCards.remove(card);
    }
    
    /**
     * 将卡牌添加回场上（后台管理用）
     */
    public void addActiveCard(Card card) {
        if (card != null && !activeCards.contains(card) && cards.contains(card)) {
            activeCards.add(card);
        }
    }
    
    /**
     * 获取卡牌数量
     */
    public int getCardCount() {
        return cards.size();
    }

    public Deck createLimitedDeck(int cardLimit) {
        Deck limitedDeck = new Deck(deckName, configFile);
        List<Card> shuffledCards = new ArrayList<>(cards);
        Collections.shuffle(shuffledCards, RANDOM);

        int limit = Math.max(1, Math.min(cardLimit, shuffledCards.size()));
        for (int i = 0; i < limit; i++) {
            limitedDeck.addCard(shuffledCards.get(i));
        }

        return limitedDeck;
    }

    public Deck createDeckFromCards(List<Card> selectedCards) {
        Deck customDeck = new Deck(deckName, configFile);
        if (selectedCards == null) {
            return customDeck;
        }

        for (Card card : selectedCards) {
            if (cards.contains(card)) {
                customDeck.addCard(card);
            }
        }

        return customDeck;
    }
    
    /**
     * 获取在场卡牌数量
     */
    public int getActiveCardCount() {
        return activeCards.size();
    }
    
    /**
     * 检查是否还有卡牌在场上
     */
    public boolean hasActiveCards() {
        return !activeCards.isEmpty();
    }
    
    /**
     * 获取已出局的卡牌列表
     */
    public List<Card> getInactiveCards() {
        List<Card> inactive = new ArrayList<>(cards);
        inactive.removeAll(activeCards);
        return inactive;
    }
    
    // Getters
    public String getDeckName() {
        return deckName;
    }
    
    public String getConfigFile() {
        return configFile;
    }
    
    public void setConfigFile(String configFile) {
        this.configFile = configFile;
    }
    
    @Override
    public String toString() {
        return deckName + " (总卡数: " + cards.size() + ", 在场: " + activeCards.size() + ")";
    }
}
