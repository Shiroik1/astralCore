package monster;

/** Existing 60 Hz simulation; JVM properties are read once, before assets/entities are built. */
public final class SkeletonConfig {
    private SkeletonConfig(){}
    public static final int HEALTH=i("health",35,1,10000),SWIPE=i("swipeDamage",6,1,100),LUNGE=i("lungeDamage",9,1,100),RAKE=i("rakeDamage",5,1,100);
    public static final int SPEED=i("speed",2,1,5),DETECTION=i("detectionTiles",6,2,12),EMERGE=t("emergenceSeconds",1.6),INTERVAL=t("spawnSeconds",16),DAYLIGHT_GRACE=t("daylightGraceSeconds",12);
    public static final int SWIPE_WIND=t("swipeWindup",.30),SWIPE_ACTIVE=t("swipeActive",.12),SWIPE_RECOVERY=t("swipeRecovery",.45),SWIPE_COOLDOWN=t("swipeCooldown",1.4);
    public static final int LUNGE_WIND=t("lungeWindup",.6),LUNGE_TIME=t("lungeSeconds",.3),LUNGE_RECOVERY=t("lungeRecovery",.75),LUNGE_COOLDOWN=t("lungeCooldown",5);
    public static final int RAKE_WIND=t("rakeWindup",.4),RAKE_GAP=t("rakeGap",.25),RAKE_RECOVERY=t("rakeRecovery",.8),RAKE_COOLDOWN=t("rakeCooldown",6);
    public static final int LUNGE_DAY=i("lungeDay",4,1,1000),RAKE_DAY=i("rakeDay",8,1,1000),AGGRESSIVE_DAY=i("aggressiveDay",15,1,1000),VETERAN_DAY=i("veteranDay",22,1,1000),SCALE_DAYS=i("scalingDays",20,0,100),MOON_DAYS=i("moonDays",8,2,1000);
    public static final int CAP=i("nearbyCap",4,1,10),MOON_CAP=i("moonCap",16,1,16),COMBINED_CAP=i("combinedCap",10,2,20),FOREST_RADIUS=i("forestRadius",4,2,6),ATTACKERS=i("attackers",1,1,2);
    public static final double HP_GROWTH=n("dailyHealthGrowth",.03,0,.2),DAMAGE_GROWTH=n("dailyDamageGrowth",.02,0,.2),FOREST_COVER=n("forestCoverage",.60,.1,1),CHANCE=n("spawnChance",.65,0,1);
    public static final double MOON_HP=n("moonHealthBonus",.25,0,1),MOON_DAMAGE=n("moonDamageBonus",.35,0,2),MOON_SPEED=n("moonSpeedBonus",.20,0,1),MOON_FREQUENCY=n("moonSpawnMultiplier",1.5,1,3),MOON_COOLDOWN=n("moonCooldownReduction",.15,0,.5);
    public static final int NIGHT_INTERVAL=t("nightSpawnSeconds",6),NIGHT_CAP=i("nightCap",12,1,16),NIGHT_MOON_CAP=i("nightMoonCap",MOON_CAP,1,16);
    public static final int NIGHT_COMBINED_CAP=i("nightCombinedCap",18,2,20),NIGHT_DETECTION=i("nightDetectionTiles",10,2,16),HUNT_TIME=t("huntSeconds",60);
    public static final int NIGHT_GROUP_MIN=i("nightGroupMin",4,1,10),NIGHT_GROUP_MAX=Math.max(NIGHT_GROUP_MIN,i("nightGroupMax",6,1,10)),NIGHT_ATTACKERS=i("nightAttackers",2,1,3);
    public static final int EMERGE_MIN=i("emergeMinTiles",4,3,10),EMERGE_MAX=i("emergeMaxTiles",8,4,16);
    public static final double NIGHT_CHANCE=n("nightSpawnChance",.90,0,1),NIGHT_DAMAGE=n("nightDamageBonus",.20,0,1);
    public static double hpMultiplier(int day){return 1+Math.min(SCALE_DAYS,Math.max(0,day-1))*HP_GROWTH;}
    public static double damageMultiplier(int day){return 1+Math.min(SCALE_DAYS,Math.max(0,day-1))*DAMAGE_GROWTH;}
    private static int i(String key,int d,int min,int max){int v=Integer.getInteger("astral.skeleton."+key,d);if(v<min||v>max)throw new IllegalArgumentException(key);return v;}
    private static double n(String key,double d,double min,double max){double v=Double.parseDouble(System.getProperty("astral.skeleton."+key,""+d));if(!Double.isFinite(v)||v<min||v>max)throw new IllegalArgumentException(key);return v;}
    private static int t(String key,double d){return (int)Math.round(n(key,d,.05,120)*60);}
}
