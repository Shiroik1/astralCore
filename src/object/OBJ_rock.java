package object;

import entity.Projectile;

import main.Gamepanel;

import java.awt.*;

public class OBJ_rock extends Projectile {
    /** Creates a OBJ_rock instance and initializes the state required by its collaborators. */
    public OBJ_rock(Gamepanel gp) {
        super(gp);
        name = "Rock";
        speed = 5;
        maxHP = 80;
        HP = maxHP;
        attack = 2;
        useCost = 1;
        alive = false;

        getImage();
    }

    /** Returns the image for OBJ_rock. */
    private void getImage() {
        up1 = setup("/projectile/rock_down_1", gp.tileSize, gp.tileSize);
        up2 = setup("/projectile/rock_down_1", gp.tileSize, gp.tileSize);
        down1 = setup("/projectile/rock_down_1", gp.tileSize, gp.tileSize);
        down2 = setup("/projectile/rock_down_1", gp.tileSize, gp.tileSize);
        left1 = setup("/projectile/rock_down_1", gp.tileSize, gp.tileSize);
        left2 = setup("/projectile/rock_down_1", gp.tileSize, gp.tileSize);
        right1 = setup("/projectile/rock_down_1", gp.tileSize, gp.tileSize);
        right2 = setup("/projectile/rock_down_1", gp.tileSize, gp.tileSize);
    }

    /** Returns the particle color for OBJ_rock. */
    public Color getParticleColor() {
        Color color = new Color(65, 50, 50);
        return color;
    }

    /** Returns the particle size for OBJ_rock. */
    public int getParticleSize() {
        int size = 8;
        return size;
    }

    /** Returns the particle speed for OBJ_rock. */
    public int getParticleSpeed() {
        int speed = 1;
        return speed;
    }

    /** Returns the particle max hp for OBJ_rock. */
    public int getParticleMaxHP() {
        int maxHP = 20;
        return maxHP;
    }
}
