package entity;

import java.awt.*;

public class StatusEffect {
    public String type;      // "attackBoost", "defenseBoost", "attackSpeedBoost", "bleed"
    public int duration;     // ticks remaining
    public int value;        // magnitude (flat stat bonus, or DOT damage per tick)
    public int tickInterval; // for DOT effects: ticks between damage applications
    public int tickCounter = 0;
    public Color tintColor;

    public StatusEffect(String type, int duration, int value, int tickInterval){
        this.type = type;
        this.duration = duration;
        this.value = value;
        this.tickInterval = tickInterval;
        this.tintColor = defaultTintFor(type);
    }

    private static Color defaultTintFor(String type){
        return switch(type){
            case "attackBoost" -> new Color(255, 60, 60, 100);
            case "bleed" -> new Color(60, 220, 60, 100);
            case "defenseBoost" -> new Color(80, 160, 255, 100);
            case "attackSpeedBoost" -> new Color(255, 240, 80, 100);
            default -> null;
        };
    }
}