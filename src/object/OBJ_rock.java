package object;

import entity.Projectile;
import main.Gamepanel;

import java.awt.*;

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

    public Color getParticleColor(){
        Color color = new Color(65,50,50);
        return  color;
    }

    public int getParticleSize(){
        int size = 8;
        return size;
    }

    public int getParticleSpeed(){
        int speed = 1;
        return speed;
    }

    public int getParticleMaxHP(){
        int maxHP = 20;
        return maxHP;
    }
}
