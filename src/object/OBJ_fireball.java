package object;

import entity.Projectile;
import main.Gamepanel;

public class OBJ_fireball extends Projectile {
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
}
