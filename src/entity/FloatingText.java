package entity;

import main.Gamepanel;

import java.awt.*;

public class FloatingText {
    private Gamepanel gp;
    public int worldX, worldY;
    private String text;
    private Color color;
    private int lifeTicks = 0;
    private final int maxLifeTicks = 45; // ~0.75s
    private final int riseSpeed = 1;

    /** Creates a FloatingText instance and initializes the state required by its collaborators. */
    public FloatingText(Gamepanel gp, int worldX, int worldY, String text, Color color) {
        this.gp = gp;
        this.worldX = worldX;
        this.worldY = worldY;
        this.text = text;
        this.color = color;
    }

    /** Advances this component for one simulation tick. */
    public boolean update() {
        worldY -= riseSpeed;
        lifeTicks++;
        return lifeTicks < maxLifeTicks;
    }

    /** Renders this component into the supplied Java2D graphics context. */
    public void draw(Graphics2D g2) {
        Player camera = gp.localPlayer();
        if (camera == null) return;

        int screenX = worldX - camera.worldX + camera.screenX;
        int screenY = worldY - camera.worldY + camera.screenY;

        float alpha = Math.max(0f, 1f - ((float) lifeTicks / maxLifeTicks));

        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 18f));
        g2.setColor(Color.black);
        g2.drawString(text, screenX + 1, screenY + 1);
        g2.setColor(
                new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (alpha * 255)));
        g2.drawString(text, screenX, screenY);
    }
}
