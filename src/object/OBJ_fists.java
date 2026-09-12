package object;

import entity.Entity;
import main.Gamepanel;

public class OBJ_fists extends Entity {
    public OBJ_fists(Gamepanel gp) {
        super(gp);
        name = "Fists";
        type = type_sword; // treated as a sword-slot weapon so attack code paths work unchanged
        attackValue = 0;
        attackArea.width = 24;
        attackArea.height = 24;
        description = "(Fists)\nUnarmed.";
        isPlaceholder = true;
    }
}