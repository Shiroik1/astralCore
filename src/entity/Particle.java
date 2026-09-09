package entity;

import main.Gamepanel;

import java.awt.*;

public class Particle extends Entity{

    Entity generator;
    Color color;
    int size;
    int xd;
    int yd;
    public boolean groundEffect = false;

    public Particle(Gamepanel gp, Entity generator, Color color, int size, int speed, int maxHP, int xd, int yd) {
        super(gp);
        this.generator = generator;
        this.color = color;
        this.size = size;
        this.speed = speed;
        this.maxHP = maxHP;
        this.xd = xd;
        this.yd = yd;

        HP = maxHP;
        int offset = (gp.tileSize/2) - (size/2);
        worldX = generator.worldX + offset;
        worldY = generator.worldY + offset;
    }

    public void update(){

        HP--;

        if(HP < maxHP/3){
            yd++;
        }

        worldX += xd*speed;
        worldY += yd*speed;


        if(HP <= 0){
            alive = false;
        }
    }

    public void draw(Graphics2D g2){
        int screenX = worldX - gp.player.worldX + gp.player.screenX;
        int screenY = worldY - gp.player.worldY + gp.player.screenY;

        g2.setColor(color);
        g2.fillRect(screenX, screenY, size, size);
    }

    public Particle(Gamepanel gp, Entity generator, Color color, int size, int speed, int maxHP, int xd, int yd, int spawnOffsetX, int spawnOffsetY) {
        this(gp, generator, color, size, speed, maxHP, xd, yd);
        worldX += spawnOffsetX;
        worldY += spawnOffsetY;
    }
}
