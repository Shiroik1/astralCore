package net;

public class PlayerState {
    public int playerId;
    public String playerName;
    public int worldX, worldY;
    public String direction;
    public int HP, maxHP, MP, maxMP;
    public String animState;
    public boolean attacking;
    public long ackTick; // the sender's last input tick this position reflects
    public String[] inventoryTypeIds;
    public int[] inventoryStackCounts;
    public String weaponTypeId;
    public String shieldTypeId;
    public boolean isDead;
    public String playerClass;
    public int level, exp, nextLevelExp;
    public int strength, dexterity, intelligence;
    public int coin;
    public int rage, maxRage;
    public int arcaneCharge;
}