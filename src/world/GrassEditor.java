package world;

import entity.Player;
import main.Gamepanel;

import java.awt.*;
import java.awt.event.KeyEvent;

public class GrassEditor {
    private static final String[] NAMES = {"erase", "short", "tall", "flowers"};

    private final Gamepanel gp;
    public boolean active = false;
    private int type = GrassLayer.TALL;   // 0 erases
    private int radius = 1;               // brush radius, in tiles
    private int density = 3;              // 1 to 4

    public GrassEditor(Gamepanel gp){
        this.gp = gp;
    }

    public void start(int type, int radius, int density){
        this.type = type;
        this.radius = radius;
        this.density = density;
        this.active = true;
    }

    public void stop(){
        active = false;
    }

    // returns true when the key was a brush key (so it isn't also read as a skill key)
    public boolean handleKey(int code){
        switch(code){
            case KeyEvent.VK_1 -> type = GrassLayer.SHORT;
            case KeyEvent.VK_2 -> type = GrassLayer.TALL;
            case KeyEvent.VK_3 -> type = GrassLayer.FLOWERS;
            case KeyEvent.VK_4 -> type = GrassLayer.NONE;
            case KeyEvent.VK_OPEN_BRACKET -> radius = Math.max(0, radius - 1);
            case KeyEvent.VK_CLOSE_BRACKET -> radius = Math.min(4, radius + 1);
            case KeyEvent.VK_COMMA -> density = Math.max(1, density - 1);
            case KeyEvent.VK_PERIOD -> density = Math.min(4, density + 1);
            default -> { return false; }
        }
        return true;
    }

    private boolean inBrush(int dc, int dr){
        return dc * dc + dr * dr <= radius * radius + radius; // roughly round
    }

    public void update(){
        if(!active) return;
        Player me = gp.localPlayer();
        if(me == null || gp.inventoryOpen || gp.optionsOpen || gp.paused || gp.keyH.enteringChat) return;

        boolean paint = gp.mouseH.leftPressed;
        boolean erase = gp.mouseH.rightPressed;
        if(!paint && !erase) return;

        int mx = gp.mouseH.getScaledX() - me.screenX + me.worldX;
        int my = gp.mouseH.getScaledY() - me.screenY + me.worldY;
        int cc = Math.floorDiv(mx, gp.tileSize), cr = Math.floorDiv(my, gp.tileSize);
        for(int dc = -radius; dc <= radius; dc++){
            for(int dr = -radius; dr <= radius; dr++){
                if(inBrush(dc, dr)){
                    gp.grass.paint(cc + dc, cr + dr, paint ? type : GrassLayer.NONE, density);
                }
            }
        }
    }

    // the brush footprint, drawn in the world
    public void drawBrush(Graphics2D g2){
        if(!active) return;
        Player me = gp.localPlayer();
        if(me == null) return;

        int mx = gp.mouseH.getScaledX() - me.screenX + me.worldX;
        int my = gp.mouseH.getScaledY() - me.screenY + me.worldY;
        int cc = Math.floorDiv(mx, gp.tileSize), cr = Math.floorDiv(my, gp.tileSize);
        Color c = (type == GrassLayer.NONE) ? new Color(255, 90, 90) : Color.white;

        g2.setStroke(new BasicStroke(1f));
        for(int dc = -radius; dc <= radius; dc++){
            for(int dr = -radius; dr <= radius; dr++){
                if(!inBrush(dc, dr)) continue;
                int sx = (cc + dc) * gp.tileSize - me.worldX + me.screenX;
                int sy = (cr + dr) * gp.tileSize - me.worldY + me.screenY;
                g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 40));
                g2.fillRect(sx, sy, gp.tileSize, gp.tileSize);
                g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 140));
                g2.drawRect(sx, sy, gp.tileSize, gp.tileSize);
            }
        }
    }

    public void drawHud(Graphics2D g2, Font font){
        if(!active) return;
        g2.setFont(font.deriveFont(Font.BOLD, 14f));
        FontMetrics fm = g2.getFontMetrics();
        String line1 = "GRASS PAINT   " + NAMES[type] + "   size " + radius + "   density " + density;
        String line2 = "LMB paint   RMB erase   1 short  2 tall  3 flowers  4 erase   [ ] size   , . density   /paint off";
        int y = 30;
        for(String s : new String[]{line1, line2}){
            int x = gp.screenWidth - fm.stringWidth(s) - 16;
            g2.setColor(Color.black);
            g2.drawString(s, x + 1, y + 1);
            g2.setColor(Color.white);
            g2.drawString(s, x, y);
            y += 20;
        }
    }
}