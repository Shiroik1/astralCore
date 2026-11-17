package net;

public class MonsterState {
    public String skeletonState,skeletonAttack;
    public String skeletonPose,skeletonRecoveryAttack;
    public double skeletonGait,skeletonIdle;
    public int skeletonWind,skeletonGap,skeletonRecovery;
    public int skeletonDay,skeletonBaseHealth,skeletonSwipe,skeletonLunge,skeletonRake,skeletonTimer,skeletonCooldown,skeletonLungeCooldown,skeletonRakeCooldown,skeletonDaylight,skeletonGoalX,skeletonGoalY,skeletonInvestigation;
    public boolean skeletonEnraged,skeletonDeparting;
    public int slimeInvestigationX,slimeInvestigationY,slimeInvestigationTicks,slimeStalledTicks;
    public boolean slimeDirectorSpawn,slimeInvestigationFailed;
    public String slimeState,nestId;
    public int slimeTimer,slimeCooldown,homeX,homeY,slimeStartX,slimeStartY,slimeGoalX,slimeGoalY,slimeHitMask,slimeAdds;
    public int slimeArrivalGrace;
    public boolean slimePhaseTwo,slimeNextSlam,slimeReturning,slimeRadialRecovery;
    public int worldX, worldY;
    public String direction;
    public int HP, maxHP;
    public int comboStacks,assassinPairMask=-1;
    public int hitSequence, criticalHitRgb;
    public String hitDirection;
    public boolean dying, rangerPinned, assassinBleeding, berserkerSundered, paladinChallenged;
    public boolean alive;
    public boolean isBoss;
    public String bossState;
    public int bossStateTimer;
    public double chargeX,chargeY;
    public boolean lastAttackWasSlam;
    public monster.MonsterType monsterType;
}
