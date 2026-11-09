package world;

import art.ObjectArt;
import entity.Entity;
import entity.Player;
import main.Gamepanel;
import net.GameEventType;
import object.OBJ_potion_red;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

public class WorldObject extends Entity {

    public final WorldObjectType kind;
    public final int col, row;
    public String text;              // sign / well text; null uses the type's default
    public int state = 0;            // 0 = default, 1 = used (chest opened); synced to clients
    public final boolean blocks;
    public final Rectangle blockRect;   // world-space blocking box
    private final Rectangle reachRect;  // world-space interaction box
    private final ObjectArt.Sprite sprite, altSprite;
    private float alpha = 1f;

    public WorldObject(Gamepanel gp, WorldObjectType kind, int col, int row, String text){
        super(gp);
        this.kind = kind;
        this.col = col;
        this.row = row;
        this.text = text;
        this.name = kind.name();
        this.sprite = kind.sprite(gp);
        this.altSprite = kind.altSprite(gp);

        // worldX / worldY is the object's FOOT point: the bottom-centre of its tile
        worldX = col * gp.tileSize + gp.tileSize / 2;
        worldY = (row + 1) * gp.tileSize - 4;

        // solidArea is relative to the foot point, which is exactly how the sprite's collision box is defined
        solidArea = new Rectangle(sprite.collision);
        solidAreaDefaultX = solidArea.x;
        solidAreaDefaultY = solidArea.y;
        blocks = solidArea.width > 0;
        collision = blocks;
        blockRect = new Rectangle(worldX + solidArea.x, worldY + solidArea.y, solidArea.width, solidArea.height);
        reachRect = new Rectangle(blockRect);
        reachRect.grow(6, kind == WorldObjectType.HEALING_POND ? gp.tileSize / 2 : 6);
    }

    public boolean isInteractive(){
        return kind.behavior != WorldObjectType.Behavior.NONE;
    }

    public Rectangle reachRect(){
        return reachRect;
    }

    private ObjectArt.Sprite current(){
        return (state == 1) ? altSprite : sprite;
    }

    @Override
    public int sortY(){
        return worldY; // the foot point
    }

    public boolean onScreen(){
        Player cam = gp.localPlayer();
        if(cam == null) return false;
        BufferedImage img = current().image;
        int sx = worldX - sprite.footX - cam.worldX + cam.screenX;
        int sy = worldY - sprite.footY - cam.worldY + cam.screenY;
        return sx + img.getWidth() > -gp.tileSize && sx < gp.screenWidth + gp.tileSize
                && sy + img.getHeight() > -gp.tileSize && sy < gp.screenHeight + gp.tileSize;
    }

    // visual only: trees and roofs turn translucent while the local player stands behind them
    @Override
    public void update(){
        Player p = gp.localPlayer();
        if(p == null || Math.abs(worldX - p.worldX) > 400 || Math.abs(worldY - p.worldY) > 500){
            alpha = 1f;
            return;
        }

        float target = 1f;
        if(kind.fadesWhenBehind){
            int feet = p.worldY + p.solidArea.y + p.solidArea.height;
            if(feet < worldY){
                BufferedImage img = current().image;
                Rectangle imageBounds = new Rectangle(worldX - sprite.footX, worldY - sprite.footY, img.getWidth(), img.getHeight());
                Rectangle body = new Rectangle(p.worldX + 8, p.worldY - 12, 32, 64); // head to feet
                if(imageBounds.intersects(body)){
                    target = 0.45f;
                }
            }
        }
        alpha += (target - alpha) * 0.2f;
        if(Math.abs(target - alpha) < 0.01f){
            alpha = target;
        }
    }

    @Override
    public void draw(Graphics2D g2){
        Player cam = gp.localPlayer();
        if(cam == null) return;

        BufferedImage img = current().image;
        int sx = worldX - sprite.footX - cam.worldX + cam.screenX;
        int sy = worldY - sprite.footY - cam.worldY + cam.screenY;

        if(highlighted){
            drawOutline(g2, getFlashVersion(img), sx, sy);
        }
        changeAlpha(g2, alpha);
        if(kind.swaysInWind()){
            // The shear tapers to zero at the trunk base, keeping the tree rooted to its collision box.
            double sway = gp.ambient.treeSway(worldX, worldY);
            AffineTransform lean = new AffineTransform(1, 0, -sway, 1, sx + sway * sprite.footY, sy);
            Object interpolation = g2.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(img, lean, null);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation == null
                    ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : interpolation);
        } else {
            g2.drawImage(img, sx, sy, null);
        }
        changeAlpha(g2, 1f);

        if(highlighted && isInteractive()){
            int bx = sx + img.getWidth() / 2;
            int by = sy - 4;
            g2.setFont(g2.getFont().deriveFont(Font.BOLD, 14f));
            g2.setColor(new Color(0, 0, 0, 170));
            g2.fillRoundRect(bx - 11, by - 20, 22, 22, 8, 8);
            g2.setColor(Color.white);
            g2.drawString("E", bx - 5, by - 4);
        }

        if(gp.keyH.showDebug){
            drawHitbox(g2);
        }
    }

    // Host only. Remote players' interactions arrive here too, because the host simulates their input.
    public void interact(Player p){
        String shown = (text != null && !text.isEmpty()) ? text : kind.defaultText;

        switch(kind.behavior){
            case SUMMON_HELL -> gp.quests.summonHell(p);
            case ENTER_WELL -> gp.quests.enterWell(p);
            case EXIT_WELL -> gp.quests.leaveWell(p);
            case TEXT -> {
                if(shown != null){
                    gp.broadcastPlayerEvent(p.playerId, GameEventType.INTERACT, null, shown);
                }
            }
            case CHEST -> {
                if(state == 1){
                    gp.broadcastPlayerEvent(p.playerId, GameEventType.INTERACT, null, "The chest is empty.");
                    return;
                }
                if(!p.addToInventory(new OBJ_potion_red(gp))){
                    gp.broadcastPlayerEvent(p.playerId, GameEventType.INTERACT, null, "Your bag is full.");
                    return;
                }
                state = 1;
                p.coin += 5;
                gp.broadcastPlayerEvent(p.playerId, GameEventType.PICKUP,
                        "Player " + (p.playerId + 1) + " opened a chest!",
                        "You found a Red Potion\nand 5 coins!");
            }
            case HEAL -> {
                if(p.isDead || p.inWell || !p.canResolveWorldActions()) return;
                int healed = Math.max(0, p.maxHP - p.HP);
                p.HP = p.maxHP;
                gp.playSE(2);
                gp.broadcastPlayerEvent(p.playerId, GameEventType.HEAL,
                        healed > 0 ? "Player " + (p.playerId + 1) + " rested at the forest pond." : null,
                        healed > 0 ? "The pond's cool water soothes your wounds.\nYour HP is fully restored."
                                : "The forest pond is peaceful.\nYour HP is already full.");
                if(healed > 0 && p == gp.localPlayer()){
                    gp.spawnFloatingText(p.worldX + gp.tileSize / 2, p.worldY, "+" + healed, new Color(120, 220, 120));
                }
            }
            default -> { }
        }
    }

    public boolean hasState(){
        return kind.behavior == WorldObjectType.Behavior.CHEST;
    }
}
