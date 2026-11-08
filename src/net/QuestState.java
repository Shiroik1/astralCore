package net;

/** Host-owned tutorial progress and scene version; no world assets travel over the network. */
public class QuestState {
    public int stage;
    public int kills;
    public boolean inWell;
    public int areaVersion;
    public int introPlayers;
    public int encounterVersion;
    public object.ItemType[] pendingRewards;
}
