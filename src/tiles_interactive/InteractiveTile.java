package tiles_interactive;

import entity.Entity;

import main.Gamepanel;

import java.awt.*;

public class InteractiveTile extends Entity {

    public boolean destructible = false;

    /**
     * Creates a InteractiveTile instance and initializes the state required by its collaborators.
     */
    public InteractiveTile(Gamepanel gp, int col, int row) {
        super(gp);
    }

    /** Reports whether correct item for InteractiveTile. */
    public boolean isCorrectItem(Entity entity) {
        boolean isCorrectItem = false;
        return isCorrectItem;
    }

    /** Returns the destroyed form for InteractiveTile. */
    public InteractiveTile getDestroyedForm() {
        InteractiveTile tile = null;
        return tile;
    }

    /** Performs the play se operation for InteractiveTile. */
    public void playSE() {}

    /** Advances this component for one simulation tick. */
    public void update() {
        if (invincible) {
            invincibleCounter++;
            if (invincibleCounter > 20) {
                invincible = false;
                invincibleCounter = 0;
            }
        }
    }

    /** Renders this component into the supplied Java2D graphics context. */
    public void draw(Graphics2D g2) {
        int screenX = worldX - gp.localPlayer().worldX + gp.localPlayer().screenX;
        int screenY = worldY - gp.localPlayer().worldY + gp.localPlayer().screenY;

        if (worldX + gp.tileSize > gp.localPlayer().worldX - gp.localPlayer().screenX
                && worldX - gp.tileSize < gp.localPlayer().worldX + gp.localPlayer().screenX
                && worldY + gp.tileSize > gp.localPlayer().worldY - gp.localPlayer().screenY
                && worldY - gp.tileSize < gp.localPlayer().worldY + gp.localPlayer().screenY) {

            g2.drawImage(down1, screenX, screenY, null);
        }

        if (gp.keyH.showDebug) {
            drawHitbox(g2);
        }
    }
}
