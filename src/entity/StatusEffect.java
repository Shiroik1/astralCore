package entity;

public class StatusEffect {
    public String type;      // "attackBoost", "defenseBoost", "attackSpeedBoost", "bleed"
    public int duration;     // ticks remaining
    public int value;        // magnitude (flat stat bonus, or DOT damage per tick)
    public int tickInterval; // for DOT effects: ticks between damage applications
    public int tickCounter = 0;

    public StatusEffect(String type, int duration, int value, int tickInterval){
        this.type = type;
        this.duration = duration;
        this.value = value;
        this.tickInterval = tickInterval;
    }
}