package object;

import entity.Entity;
import main.Gamepanel;

/** A trophy material using the existing crystal icon, separate from the well's essence. */
public final class OBJ_royalslimecore extends Entity {
    public OBJ_royalslimecore(Gamepanel gp){
        super(gp);name="Royal Slime Core";stackable=true;maxStackSize=99;
        down1=new OBJ_crystal(gp).down1;description="(Royal Slime Core)\nA trophy from the Crowned Slime.\nNot required for basic crafting.";
    }
}
