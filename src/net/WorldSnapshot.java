package net;

public class WorldSnapshot {
    public long tick;
    public boolean inWellArea;
    public WorldSnapshot otherArea;
    public QuestState quest;
    public SlimeProjectileState[] slimeProjectiles;
    public PlayerState[] players;
    public MonsterState[] monsters;
    public ObjectState[] objects;
    public NpcState[] npcs;
    public byte[] sceneryState;
}