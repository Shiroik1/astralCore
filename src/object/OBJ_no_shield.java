package object;

import entity.Entity;
import main.Gamepanel;

public class OBJ_no_shield extends Entity {
    public OBJ_no_shield(Gamepanel gp) {
        super(gp);
        name = "No Shield";
        type = type_shield;
        defenseValue = 0;
        description = "(No Shield)\nNothing equipped.";
        isPlaceholder = true;
    }
}