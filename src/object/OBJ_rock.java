package object;

import entity.Projectile;
import main.Gamepanel;

public class OBJ_rock extends Projectile {
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
}
