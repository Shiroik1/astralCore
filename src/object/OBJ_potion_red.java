package object;

import entity.Entity;
import entity.Player;

import main.Gamepanel;

import net.GameEventType;

import java.awt.Color;

public class OBJ_potion_red extends Entity {

    /**
     * Creates a OBJ_potion_red instance and initializes the state required by its collaborators.
     */
    public OBJ_potion_red(Gamepanel gp) {
        super(gp);
        name = "Red Potion";
        value = 5;
        type = type_consumable;
        down1 = setup("/object/potion_red", gp.tileSize, gp.tileSize);
        description = "(" + name + ")\nA potion to restore\nyour health.";
        stackable = true;
        maxStackSize = 5;
    }

    /** Performs the use operation for OBJ_potion_red. */
    public void use(Entity entity) {
        int before = entity.HP;
        entity.HP = Math.min(entity.maxHP, entity.HP + value);
        int healed = entity.HP - before;
        gp.playSE(2);

        if (entity instanceof Player p) {
            gp.broadcastPlayerEvent(
                    p.playerId,
                    GameEventType.HEAL,
                    "Player " + (p.playerId + 1) + " drank a " + name + " (+" + healed + " HP)",
                    null);
            if (healed > 0 && p == gp.localPlayer()) {
                gp.spawnFloatingText(
                        p.worldX + gp.tileSize / 2,
                        p.worldY,
                        "+" + healed,
                        new Color(120, 220, 120));
            }
        }
    }
}
