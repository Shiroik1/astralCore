package entity;

/**
 * Base-class stat table: level-1 values and automatic growth per level.
 * Subclasses use their base class's row exactly (Berserker/Paladin = WARRIOR, Ranger/Hunter(Assassin) = HUNTER, Mage/Priest = MAGE).
 * Equipment, buffs and skill augments are added on top of this elsewhere.
 */
public enum ClassStats {
    //          start HP, start MP, HP/level, MP/level, primary stat
    WARRIOR(120,   0,  8, 0, Primary.STR),
    HUNTER (100,   0,  6, 0, Primary.DEX),
    MAGE   ( 80, 100,  4, 2, Primary.INT);

    public enum Primary { STR, DEX, INT }

    public static final int MAX_LEVEL = 25;
    public static final int START_PRIMARY = 5;   // level-1 value of the class's primary stat
    public static final int START_OTHER = 1;     // level-1 value of the other two attributes
    public static final int START_VIT = 1;
    public static final int VIT_EVERY = 5;       // +1 VIT every 5 levels (5, 10, 15, 20, 25)

    public final int startHP, startMP, hpPerLevel, mpPerLevel;
    public final Primary primary;

    ClassStats(int startHP, int startMP, int hpPerLevel, int mpPerLevel, Primary primary) {
        this.startHP = startHP; this.startMP = startMP;
        this.hpPerLevel = hpPerLevel; this.mpPerLevel = mpPerLevel;
        this.primary = primary;
    }

    public static ClassStats of(String classId) {
        return switch (CharacterClass.fromId(classId)) {
            case WARRIOR, BERSERKER, PALADIN -> WARRIOR;
            case HUNTER, RANGER -> HUNTER;
            case MAGE, PRIEST -> MAGE;
        };
    }

    /** Level-1 value of one attribute. */
    public int startStat(Primary stat) { return stat == primary ? START_PRIMARY : START_OTHER; }
}