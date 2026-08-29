package object;

import entity.Entity;
import main.Gamepanel;

public class OBJ_potion_red extends Entity {

    public OBJ_potion_red(Gamepanel gp) {
        super(gp);
        name = "Red Potion";
        value = 5;
        type = type_consumable;
        down1 = setup("/object/potion_red", gp.tileSize, gp.tileSize);
        description = "(" + name + ")\nA potion to restore\nyour health.";
    }

    public void use(Entity entity){
        gp.gameState = gp.dialogueState;
        gp.ui.currentDialogue = "You drink the " + name + ".\nYour HP is restored by " + value + "!";
        entity.HP += value;
        if(entity.HP > entity.maxHP){
            entity.HP = entity.maxHP;
        }
        gp.playSE(2);
    }
}
