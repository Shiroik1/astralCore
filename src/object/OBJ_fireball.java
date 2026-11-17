package object;

import entity.Entity;
import entity.Projectile;

import main.Gamepanel;

import java.awt.*;

public class OBJ_fireball extends Projectile {
    /** Creates a OBJ_fireball instance and initializes the state required by its collaborators. */
    public OBJ_fireball(Gamepanel gp) {
        super(gp);
        name = "Fireball";
        speed = 10;
        maxHP = 80;
        HP = maxHP;
        attack = 2;
        useCost = 1;
        alive = false;

        getImage();
    }

    /** Returns the image for OBJ_fireball. */
    private void getImage() {
        up1 = setup("/projectile/fireball_up_1", gp.tileSize, gp.tileSize);
        up2 = setup("/projectile/fireball_up_2", gp.tileSize, gp.tileSize);
        down1 = setup("/projectile/fireball_down_1", gp.tileSize, gp.tileSize);
        down2 = setup("/projectile/fireball_down_2", gp.tileSize, gp.tileSize);
        left1 = setup("/projectile/fireball_left_1", gp.tileSize, gp.tileSize);
        left2 = setup("/projectile/fireball_left_2", gp.tileSize, gp.tileSize);
        right1 = setup("/projectile/fireball_right_1", gp.tileSize, gp.tileSize);
        right2 = setup("/projectile/fireball_right_2", gp.tileSize, gp.tileSize);
    }

    /** Reports whether this component has resource for OBJ_fireball. */
    public boolean hasResource(Entity user) {
        boolean hasResource = false;
        if (user.MP >= useCost) {
            hasResource = true;
        }
        return hasResource;
    }

    /** Performs the subtract resource operation for OBJ_fireball. */
    public void subtractResource(Entity user) {
        user.MP -= useCost;
    }

    /** Returns the particle color for OBJ_fireball. */
    public Color getParticleColor() {
        Color color = new Color(255, 72, 0);
        return color;
    }

    /** Returns the particle size for OBJ_fireball. */
    public int getParticleSize() {
        int size = 8;
        return size;
    }

    /** Returns the particle speed for OBJ_fireball. */
    public int getParticleSpeed() {
        int speed = 1;
        return speed;
    }

    /** Returns the particle max hp for OBJ_fireball. */
    public int getParticleMaxHP() {
        int maxHP = 20;
        return maxHP;
    }
}
