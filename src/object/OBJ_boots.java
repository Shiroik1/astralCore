package object;

import art.CharacterOutfit;

import main.Gamepanel;

public class OBJ_boots extends OBJ_apparel {
    /** Creates a OBJ_boots instance and initializes the state required by its collaborators. */
    public OBJ_boots(Gamepanel gp) {
        super(gp, ItemType.BOOTS, CharacterOutfit.Slot.BOOTS, "Leather Boots");
    }
}
