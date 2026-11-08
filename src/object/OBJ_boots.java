package object;

import art.CharacterOutfit;
import main.Gamepanel;

public class OBJ_boots extends OBJ_apparel {
    public OBJ_boots(Gamepanel gp){
        super(gp, ItemType.BOOTS, CharacterOutfit.Slot.BOOTS, "Leather Boots");
    }
}
