package world;

import art.BuildingCatalog;
import art.ObjectArt;
import entity.Entity;
import entity.Particle;
import entity.Player;
import main.Gamepanel;
import net.GameEventType;
import object.OBJ_potion_red;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class WorldObject extends Entity {

    public final WorldObjectType kind;
    public final int col, row;
    public String text;              // sign / well text; null uses the type's default
    public int state = 0;            // 0 = default, 1 = used (chest opened); synced to clients
    public final boolean blocks;
    public final Rectangle blockRect;   // world-space blocking box
    public final BuildingCatalog.Building building;
    public final Map<String, Integer> paletteOverrides;
    private final List<Rectangle> extraFootprint;
    private final List<BuildingLayer> buildingLayers;
    private final int[] emitterTimers;
    private final Rectangle reachRect;  // world-space interaction box
    private final ObjectArt.Sprite sprite, altSprite;
    private float alpha = 1f;

    public WorldObject(Gamepanel gp, WorldObjectType kind, int col, int row, String text){
        this(gp, kind, col, row, text, kind == WorldObjectType.INN ? "inn" : null, Map.of());
    }

    public WorldObject(Gamepanel gp, WorldObjectType kind, int col, int row, String text,
                       String buildingId, Map<String, Integer> paletteOverrides){
        super(gp);
        this.kind = kind;
        this.col = col;
        this.row = row;
        this.text = text;
        this.name = kind.name();
        boolean usesBuilding = kind == WorldObjectType.INN || kind == WorldObjectType.BUILDING;
        if(usesBuilding != (buildingId != null)) throw new IllegalArgumentException("Building id must match the scenery type");
        if(!usesBuilding && !paletteOverrides.isEmpty()) throw new IllegalArgumentException("Only buildings have palette overrides");
        this.building = usesBuilding ? BuildingCatalog.get(buildingId, paletteOverrides, gp.tileSize) : null;
        this.paletteOverrides = Map.copyOf(paletteOverrides);
        this.sprite = usesBuilding ? building.sprite() : kind.sprite(gp);
        this.altSprite = usesBuilding ? sprite : kind.altSprite(gp);
        buildingLayers = usesBuilding ? building.depthStrips().stream().map(BuildingLayer::new).toList() : List.of();
        emitterTimers = usesBuilding && !building.emitters().isEmpty() ? new int[building.emitters().size()] : null;

        // worldX / worldY is the object's FOOT point: the bottom-centre of its tile
        worldX = col * gp.tileSize + gp.tileSize / 2;
        worldY = (row + 1) * gp.tileSize - 4;

        // solidArea is relative to the foot point, which is exactly how the sprite's collision box is defined
        solidArea = new Rectangle(sprite.collision);
        solidAreaDefaultX = solidArea.x;
        solidAreaDefaultY = solidArea.y;
        extraFootprint = usesBuilding ? building.extraFootprint().stream().map(this::toWorld).toList() : List.of();
        blocks = solidArea.width > 0 || !extraFootprint.isEmpty();
        collision = blocks;
        blockRect = new Rectangle(worldX + solidArea.x, worldY + solidArea.y, solidArea.width, solidArea.height);
        int ts = gp.tileSize;
        if(usesBuilding && building.door() != null){
            reachRect = toWorld(building.door());
        } else if(kind == WorldObjectType.INN_EXIT){
            reachRect = new Rectangle(worldX - ts / 2, worldY - ts / 4, ts, ts * 3 / 4);
        } else {
            reachRect = new Rectangle(blockRect);
            reachRect.grow(6, kind == WorldObjectType.HEALING_POND ? ts / 2 : 6);
        }
    }

    private Rectangle toWorld(Rectangle local){
        return new Rectangle(worldX + local.x, worldY + local.y, local.width, local.height);
    }

    public List<BuildingCatalog.Light> lights(){
        return building == null ? kind.lights() : building.lights();
    }

    public boolean isInteractive(){
        return kind.behavior != WorldObjectType.Behavior.NONE;
    }

    public Rectangle reachRect(){
        return reachRect;
    }

    public boolean blocksEntry(Rectangle now, Rectangle moved){
        if(moved.intersects(blockRect) && !now.intersects(blockRect)) return true;
        for(int i = 0; i < extraFootprint.size(); i++){
            Rectangle box = extraFootprint.get(i);
            if(moved.intersects(box) && !now.intersects(box)) return true;
        }
        return false;
    }

    public boolean blocksArea(int x, int y, int width, int height){
        if(!blocks) return false;
        if(blockRect.intersects(x, y, width, height)) return true;
        for(int i = 0; i < extraFootprint.size(); i++){
            if(extraFootprint.get(i).intersects(x, y, width, height)) return true;
        }
        return false;
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

    public void addDrawLayers(List<Entity> drawList){
        if(buildingLayers.isEmpty()) drawList.add(this);
        else drawList.addAll(buildingLayers);
    }

    private final class BuildingLayer extends Entity {
        private final BuildingCatalog.DepthStrip strip;

        private BuildingLayer(BuildingCatalog.DepthStrip strip){
            super(WorldObject.this.gp);
            this.strip = strip;
        }

        @Override public int sortY(){
            return WorldObject.this.worldY + strip.depth();
        }

        @Override public void draw(Graphics2D g){
            Player camera = gp.localPlayer();
            int left = WorldObject.this.worldX - sprite.footX - camera.worldX + camera.screenX + strip.left();
            int top = WorldObject.this.worldY - sprite.footY - camera.worldY + camera.screenY;
            Graphics2D clipped = (Graphics2D) g.create();
            try{
                clipped.clipRect(left, top, strip.right() - strip.left(), sprite.image.getHeight());
                WorldObject.this.draw(clipped);
            } finally {
                clipped.dispose();
            }
        }
    }

    private boolean behind(Player player){
        if(building == null) return player.sortY() < worldY;
        int center = player.worldX + player.solidArea.x + player.solidArea.width / 2;
        int spriteX = center - (worldX - sprite.footX);
        for(int i = 0; i < buildingLayers.size(); i++){
            BuildingLayer layer = buildingLayers.get(i);
            if(spriteX >= layer.strip.left() && spriteX < layer.strip.right()) return player.sortY() < layer.sortY();
        }
        return false;
    }

    // visual only: trees and roofs turn translucent while the local player stands behind them
    @Override
    public void update(){
        Player p = gp.localPlayer();
        if(emitterTimers != null && p != null) updateEmitters(p);
        if(p == null || (building == null
                ? Math.abs(worldX - p.worldX) > 400 || Math.abs(worldY - p.worldY) > 500 : !onScreen())){
            alpha = 1f;
            return;
        }

        float target = 1f;
        if(kind.fadesWhenBehind && behind(p)){
            BufferedImage img = current().image;
            Rectangle imageBounds = new Rectangle(worldX - sprite.footX, worldY - sprite.footY, img.getWidth(), img.getHeight());
            Rectangle body = new Rectangle(p.worldX + 8, p.worldY - 12, 32, 64); // head to feet
            if(imageBounds.intersects(body)){
                target = 0.45f;
            }
        }
        alpha += (target - alpha) * 0.2f;
        if(Math.abs(target - alpha) < 0.01f){
            alpha = target;
        }
    }

    private void updateEmitters(Player camera){
        int margin = gp.tileSize * 2;
        for(int i = 0; i < emitterTimers.length; i++){
            BuildingCatalog.Emitter emitter = building.emitters().get(i);
            float x = worldX + emitter.x(), y = worldY + emitter.y();
            float sx = x - camera.worldX + camera.screenX, sy = y - camera.worldY + camera.screenY;
            if(sx < -margin || sy < -margin || sx > gp.screenWidth + margin || sy > gp.screenHeight + margin) continue;
            if(emitterTimers[i] > 0 && --emitterTimers[i] > 0) continue;
            var random = ThreadLocalRandom.current();
            emitterTimers[i] = random.nextInt(emitter.minInterval(), emitter.maxInterval() + 1);
            int life = random.nextInt(emitter.minLife(), emitter.maxLife() + 1);
            float size = emitter.minSize() + random.nextFloat() * (emitter.maxSize() - emitter.minSize());
            Particle.emitAmbient(gp, emitter.style(), emitter.color(), size, life,
                    emitter.vx() * random.nextFloat(0.6f, 1.4f), emitter.vy() * random.nextFloat(0.85f, 1.15f),
                    x + random.nextFloat(-3, 3), y + random.nextFloat(-2, 2), sortY() + 1);
        }
    }

    @Override
    public void draw(Graphics2D g2){
        Player cam = gp.localPlayer();
        if(cam == null) return;

        BufferedImage img = current().image;
        int sx = worldX - sprite.footX - cam.worldX + cam.screenX;
        int sy = worldY - sprite.footY - cam.worldY + cam.screenY;

        boolean buildingDoor = isInteractive() && (building != null || kind == WorldObjectType.INN_EXIT);
        if(highlighted && !buildingDoor){
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
        if(building != null && building.emissive() != null){
            float night = gp.lighting.nightStrength();
            if(night > 0.01f){
                changeAlpha(g2, alpha * night);
                g2.drawImage(building.emissive(), sx + building.emissiveX(), sy + building.emissiveY(), null);
            }
        }
        changeAlpha(g2, 1f);

        if(highlighted && isInteractive()){
            int doorOffset = building == null ? gp.tileSize : building.cueOffset();
            int bx = buildingDoor ? sx + sprite.footX + doorOffset : sx + img.getWidth() / 2;
            int by = buildingDoor ? sy + sprite.footY - 10 : sy - 4;
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
            case ENTER_INN -> InnInterior.enter(gp, p);
            case EXIT_INN -> InnInterior.leave(gp, p);
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
