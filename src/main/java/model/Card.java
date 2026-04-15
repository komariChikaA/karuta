package model;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 表示一张可用于游戏的卡牌，包含卡面和一首或多首关联歌曲。
 */
public class Card {
    private String imageName;
    private File imageFile;
    private String workName;
    private List<Song> songs;
    private boolean isActive;
    private boolean emptyCard;

    private static final Random RANDOM = new Random();

    /**
     * 创建一张默认处于启用状态的卡牌。
     */
    public Card(String imageName, String workName) {
        this.imageName = imageName;
        this.workName = workName;
        this.songs = new ArrayList<>();
        this.isActive = true;
        this.emptyCard = false;
    }

    /**
     * Creates a playable empty-card variant that keeps the original artwork and metadata.
     */
    public Card createEmptyVariant() {
        Card emptyVariant = new Card(imageName + "#empty", workName);
        emptyVariant.setImageFile(imageFile);
        emptyVariant.emptyCard = true;
        for (Song song : songs) {
            emptyVariant.addSong(song);
        }
        return emptyVariant;
    }

    /**
     * 只添加一次歌曲，避免卡牌出现重复曲目。
     */
    public void addSong(Song song) {
        if (song != null && !songs.contains(song)) {
            songs.add(song);
        }
    }

    /**
     * 从卡牌中移除一首关联歌曲。
     */
    public void removeSong(Song song) {
        songs.remove(song);
    }

    /**
     * 返回关联歌曲列表的防御性拷贝。
     */
    public List<Song> getSongs() {
        return new ArrayList<>(songs);
    }

    /**
     * 为当前回合随机选择一首歌曲。
     */
    public Song getRandomSong() {
        if (songs.isEmpty()) {
            return null;
        }
        return songs.get(RANDOM.nextInt(songs.size()));
    }

    /**
     * 安全地按索引获取歌曲。
     */
    public Song getSong(int index) {
        if (index >= 0 && index < songs.size()) {
            return songs.get(index);
        }
        return null;
    }

    /**
     * 返回卡牌关联的歌曲数量。
     */
    public int getSongCount() {
        return songs.size();
    }

    /**
     * 判断卡面图片文件是否存在。
     */
    public boolean imageExists() {
        return imageFile != null && imageFile.exists();
    }

    public String getImageName() {
        return imageName;
    }

    public File getImageFile() {
        return imageFile;
    }

    public void setImageFile(File file) {
        this.imageFile = file;
    }

    public String getWorkName() {
        return workName;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public boolean isEmptyCard() {
        return emptyCard;
    }

    @Override
    public String toString() {
        return workName + " (图片: " + imageName + ", 歌曲数: " + songs.size() + ")";
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Card)) {
            return false;
        }
        Card other = (Card) obj;
        return this.imageName.equals(other.imageName);
    }

    @Override
    public int hashCode() {
        return imageName.hashCode();
    }
}
