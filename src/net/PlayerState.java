package net;

import object.ItemType;

public class PlayerState {
    public boolean merchantOpen;
    public boolean inWell;
    public int resolve;
    public int playerId;
    public String playerName;
    public int worldX, worldY;
    public String direction;
    public int HP, maxHP, MP, maxMP;
    public String animState;
    public boolean attacking;
    public long ackTick; // the sender's last input tick this position reflects
    public ItemType[] inventoryTypes;
    public int[] inventoryStackCounts;
    public ItemType weaponType;
    public ItemType shieldType;
    public ItemType[] apparelTypes;
    public boolean isDead;
    public String playerClass;
    public String gender;
    public int hairStyle=-1,hairColor=-1;
    public int level, exp, nextLevelExp;
    public int strength, dexterity, intelligence;
    public int coin;
    public int rage, maxRage;
    public int arcaneCharge;

    // host-owned combat state, mirrored to clients
    public int[] skillCooldowns;
    public String[] effectTypes;
    public int[] effectDurations;
    public int[] effectValues;
    public int auraRgb = -1; // -1 = no aura
    public int auraTicks;
    public int[] effectIntervals,effectCounters;
    public boolean powerStrikePending,rendPending,armorBreakPending;
    public int powerStrikeBonus;
}
