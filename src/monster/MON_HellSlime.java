package monster;

import main.Gamepanel;

/** Red, repeatable well boss: slam, three radial volleys, then a telegraphed charge. */
public final class MON_HellSlime extends MON_SlimeBoss {
    /** Creates a MON_HellSlime instance and initializes the state required by its collaborators. */
    public MON_HellSlime(Gamepanel gp) {
        super(gp, true);
    }
}
