package entity;

import art.CharacterArt;
import art.CharacterOutfit;

import main.Gamepanel;

import java.awt.*;

public final class NPC_merchant extends Entity {
    /** Creates a NPC_merchant instance and initializes the state required by its collaborators. */
    public NPC_merchant(Gamepanel gp) {
        super(gp);
        type = type_npc;
        npcId = "merchant";
        name = "Mira the Merchant";
        speed = 0;
        direction = "down";
        getImage();
    }

    /** Returns the image for NPC_merchant. */
    public void getImage() {
        CharacterArt.Look look = new CharacterArt.Look();
        look.outfit =
                new CharacterOutfit(
                        new Color(0x704A38),
                        new Color(0xC99A46),
                        new Color(0x4B5553),
                        new Color(0x68483F),
                        null,
                        null);
        CharacterSprites.build(sprites, look);
        for (String dir : new String[] {"up", "down", "left", "right"})
            sprites.put("walk_" + dir, sprites.get("run_" + dir));
        down1 = sprites.get("idle_down").frames[0];
    }

    /** Returns the current character sprites.foot frac value. */
    @Override
    protected float spriteFootFraction() {
        return CharacterSprites.FOOT_FRAC;
    }

    /** Updates the action for NPC_merchant. */
    @Override
    public void setAction() {
        direction = "down";
    }

    /** Renders this component into the supplied Java2D graphics context. */
    @Override
    public void draw(Graphics2D g) {
        super.draw(g);
        Player camera = gp.localPlayer();
        if (camera == null) return;
        int x = worldX - camera.worldX + camera.screenX,
                y = worldY - camera.worldY + camera.screenY;
        if (x < 0 || x > gp.screenWidth || y < 0 || y > gp.screenHeight) return;
        g.setFont(g.getFont().deriveFont(Font.BOLD, 12f));
        String label = highlighted ? "Merchant [F]" : "Merchant";
        g.setColor(Color.BLACK);
        g.fillRoundRect(x - 20, y - 29, 90, 20, 7, 7);
        g.setColor(Color.WHITE);
        g.drawString(label, x - 15, y - 15);
    }
}
