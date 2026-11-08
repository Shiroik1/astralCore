package net;

public class MonsterState {
    public int worldX, worldY;
    public String direction;
    public int HP, maxHP;
    public boolean dying;
    public boolean alive;
    public boolean isBoss;
    public String bossState;
    public int bossStateTimer;
    public double chargeX,chargeY;
    public boolean lastAttackWasSlam;
    public monster.MonsterType monsterType;
}