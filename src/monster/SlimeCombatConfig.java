package monster;

/** Runtime combat balance, in fixed 60 Hz ticks. World layout settings are saved separately. */
public final class SlimeCombatConfig {
    public static final int SUMMONS=5,RADIAL_COUNT=8;
    public static final int RADIAL_WARNING=ticks("radialWarningSeconds",.9),RADIAL_LIFE=ticks("projectileSeconds",3);
    public static final int SPIT_WARNING=ticks("spitWarningSeconds",.6),SUMMON_WARNING=ticks("summonWarningSeconds",.75);
    public static final int SUMMON_ATTACK_GRACE=ticks("summonGraceSeconds",1.25),RADIAL_RECOVERY=ticks("radialRecoverySeconds",1.4);
    public static final int RADIAL_DAMAGE=integer("radialDamage",10,1,50),SPIT_DAMAGE=integer("spitDamage",12,1,50);
    public static final int BOSS_HP=integer("bossHP",220,1,10000),SLAM_DAMAGE=integer("slamDamage",18,1,100);
    public static final int PROJECTILE_SPEED=integer("projectileSpeed",3,1,12);
    private SlimeCombatConfig(){}
    private static int ticks(String key,double fallback){
        double v=Double.parseDouble(System.getProperty("astral.slime."+key,Double.toString(fallback)));
        if(!Double.isFinite(v) || v<.1 || v>10)throw new IllegalArgumentException("Invalid slime timing: "+key);return (int)Math.round(v*60);
    }
    private static int integer(String key,int fallback,int min,int max){int v=Integer.parseInt(System.getProperty("astral.slime."+key,Integer.toString(fallback)));if(v<min || v>max)throw new IllegalArgumentException("Invalid slime setting: "+key);return v;}
}
