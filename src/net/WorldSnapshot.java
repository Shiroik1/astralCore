package net;

public class WorldSnapshot {
    public String generationSettings;
    public long tick;
    public boolean inWellArea;
    public WorldSnapshot otherArea;
    public QuestState quest;
    public SlimeProjectileState[] slimeProjectiles;
    public RangerEffectState[] rangerEffects;
    public MageEffectState[] mageEffects;
    public PlayerState[] players;
    public MonsterState[] monsters;
    public ObjectState[] objects;
    public NpcState[] npcs;
    public byte[] sceneryState;
    public float dayTime;
    public int worldDay,moonWarningDay;
}