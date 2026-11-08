package quest;

/** Content data is independent of the player's current quest progress. */
public record QuestDefinition(String id,String title,int requiredKills,int rewardCoins,int rewardExperience) {
    public static final QuestDefinition WELL = new QuestDefinition("well_cleanup","Trouble Below the Well",10,20,15);
}
