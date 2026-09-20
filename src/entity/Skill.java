package entity;

import java.awt.image.BufferedImage;

public class Skill {
    public String name;
    public int resourceCost;
    public int cooldownDuration; // ticks
    public int cooldownRemaining = 0;
    public BufferedImage icon;
    public Runnable onUse;

    public Skill(String name, int resourceCost, int cooldownDuration, Runnable onUse, BufferedImage icon){
        this.name = name;
        this.resourceCost = resourceCost;
        this.cooldownDuration = cooldownDuration;
        this.icon = icon;
        this.onUse = onUse;
    }

    public boolean isReady(){
        return cooldownRemaining <= 0;
    }

    public void update(){
        if(cooldownRemaining > 0){
            cooldownRemaining--;
        }
    }

    public void trigger(){
        cooldownRemaining = cooldownDuration;
        onUse.run();
    }
}