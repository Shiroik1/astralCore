package tiles_interactive;

import entity.Entity;

import main.Gamepanel;

import java.awt.*;

public class IT_drytree extends InteractiveTile {

    /** Creates a IT_drytree instance and initializes the state required by its collaborators. */
    public IT_drytree(Gamepanel gp, int col, int row) {
        super(gp, col, row);
        HP = 3;

        this.worldX = gp.tileSize * col;
        this.worldY = gp.tileSize * row;

        down1 = setup("/tiles_Interactive/drytree", gp.tileSize, gp.tileSize);
        destructible = true;
    }

    /** Reports whether correct item for IT_drytree. */
    public boolean isCorrectItem(Entity entity) {
        boolean isCorrectItem = false;
        if (entity.currentWeapon.type == type_axe) {
            isCorrectItem = true;
        }
        return isCorrectItem;
    }

    /** Returns the destroyed form for IT_drytree. */
    public InteractiveTile getDestroyedForm() {
        InteractiveTile tile = new IT_trunk(gp, worldX / gp.tileSize, worldY / gp.tileSize);
        return tile;
    }

    /** Performs the play se operation for IT_drytree. */
    public void playSE() {
        gp.playSE(10);
    }

    /** Returns the particle color for IT_drytree. */
    public Color getParticleColor() {
        Color color = new Color(65, 50, 50);
        return color;
    }

    /** Returns the particle size for IT_drytree. */
    public int getParticleSize() {
        int size = 5;
        return size;
    }

    /** Returns the particle speed for IT_drytree. */
    public int getParticleSpeed() {
        int speed = 1;
        return speed;
    }

    /** Returns the particle max hp for IT_drytree. */
    public int getParticleMaxHP() {
        int maxHP = 20;
        return maxHP;
    }
}
