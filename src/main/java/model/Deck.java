package model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 保存一个数据集的全部卡牌，并跟踪仍在场上的卡牌。
 */
public class Deck {
    private String deckName;
    private String configFile;
    private List<Card> cards;
    private List<Card> activeCards;

    private static final Random RANDOM = new Random();

    /**
     * 创建一个仅存在于内存中的牌组，不绑定配置文件。
     */
    public Deck(String deckName) {
        this.deckName = deckName;
        this.cards = new ArrayList<>();
        this.activeCards = new ArrayList<>();
    }

    /**
     * 创建一个会记住来源配置文件的牌组。
     */
    public Deck(String deckName, String configFile) {
        this.deckName = deckName;
        this.configFile = configFile;
        this.cards = new ArrayList<>();
        this.activeCards = new ArrayList<>();
    }

    /**
     * 仅添加一次卡牌，保证牌组成员唯一。
     */
    public void addCard(Card card) {
        if (card != null && !cards.contains(card)) {
            cards.add(card);
        }
    }

    /**
     * 同时从总列表和在场列表中移除卡牌。
     */
    public void removeCard(Card card) {
        cards.remove(card);
        activeCards.remove(card);
    }

    /**
     * 返回牌组中的全部卡牌。
     */
    public List<Card> getCards() {
        return new ArrayList<>(cards);
    }

    /**
     * 返回当前仍在场上的卡牌。
     */
    public List<Card> getActiveCards() {
        return new ArrayList<>(activeCards);
    }

    /**
     * 根据已启用的卡牌重新构建在场列表。
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
     * 为下一回合随机选择一张在场卡牌。
     */
    public Card getRandomActiveCard() {
        if (activeCards.isEmpty()) {
            return null;
        }
        return activeCards.get(RANDOM.nextInt(activeCards.size()));
    }

    /**
     * 在卡牌被处理完后，将其从当前回合可用池中移除。
     */
    public boolean removeActiveCard(Card card) {
        return activeCards.remove(card);
    }

    /**
     * 如果卡牌属于当前牌组，则将其重新加入在场列表。
     */
    public void addActiveCard(Card card) {
        if (card != null && !activeCards.contains(card) && cards.contains(card)) {
            activeCards.add(card);
        }
    }

    /**
     * 返回数据集中的卡牌总数。
     */
    public int getCardCount() {
        return cards.size();
    }

    /**
     * 按指定卡牌数量随机抽样生成一个牌组。
     */
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

    /**
     * 根据明确选择的卡牌构建新牌组，并保留原始元数据。
     */
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
     * 返回当前仍然在场的卡牌数量。
     */
    public int getActiveCardCount() {
        return activeCards.size();
    }

    /**
     * Returns the number of active non-empty cards still in play.
     */
    public int getActiveRealCardCount() {
        return (int) activeCards.stream()
                .filter(card -> !card.isEmptyCard())
                .count();
    }

    /**
     * 判断是否至少还有一张卡牌在场。
     */
    public boolean hasActiveCards() {
        return !activeCards.isEmpty();
    }

    /**
     * Returns whether at least one non-empty card is still active.
     */
    public boolean hasActiveRealCards() {
        return activeCards.stream().anyMatch(card -> !card.isEmptyCard());
    }

    /**
     * 返回已经离场的卡牌。
     */
    public List<Card> getInactiveCards() {
        List<Card> inactive = new ArrayList<>(cards);
        inactive.removeAll(activeCards);
        return inactive;
    }

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
