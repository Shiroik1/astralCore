package world;

import art.ArtKit;
import entity.Player;
import main.Gamepanel;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

public final class InnInterior {
    public static final int ENTRANCE_COL = 17, ENTRANCE_ROW = 19;
    // An isolated part of the overworld coordinate space. Saves and multiplayer already carry this position.
    private static final int COL = 128, ROW = 128, COLS = 10, ROWS = 7;
    private static final int EXIT_COL = COL + 5, EXIT_ROW = ROW + ROWS - 1;
    private static final int TILE = 48, WIDTH = COLS * TILE, HEIGHT = ROWS * TILE;
    private static final Rectangle FLOOR = new Rectangle(25, 67, 430, 258);
    private static final Rectangle COUNTER = new Rectangle(40, 90, 124, 29);
    private static final Rectangle BENCH = new Rectangle(203, 76, 108, 25);
    private static final Rectangle BED = new Rectangle(373, 75, 59, 97);
    private static final Rectangle TABLE_LEFT = new Rectangle(104, 173, 76, 48);
    private static final Rectangle TABLE_RIGHT = new Rectangle(320, 202, 76, 48);
    private static final Rectangle[] FURNITURE = {COUNTER, BENCH, BED, TABLE_LEFT, TABLE_RIGHT};

    private InnInterior(){}

    public static boolean isTile(int col, int row){
        return col >= COL && col < COL + COLS && row >= ROW && row < ROW + ROWS;
    }

    public static boolean containsPosition(Gamepanel gp, int x, int y){
        return isTile(Math.floorDiv(x, gp.tileSize), Math.floorDiv(y, gp.tileSize));
    }

    public static boolean contains(Gamepanel gp, Player p){
        return p != null && !p.inWell && containsPosition(gp, p.worldX, p.worldY);
    }

    public static boolean blocksMovement(Gamepanel gp, int left, int top, int right, int bottom){
        int x = gp.tileSize * COL, y = gp.tileSize * ROW;
        Rectangle body = new Rectangle(left - x, top - y, right - left + 1, bottom - top + 1);
        if(!FLOOR.contains(body)) return true;
        for(Rectangle item : FURNITURE) if(item.intersects(body)) return true;
        return false;
    }

    static WorldObject createExit(Gamepanel gp){
        return new WorldObject(gp, WorldObjectType.INN_EXIT, EXIT_COL, EXIT_ROW, null);
    }

    static void enter(Gamepanel gp, Player p){
        if(p.isDead || p.inWell || !p.canResolveWorldActions() || contains(gp, p)) return;
        int x = EXIT_COL * gp.tileSize + gp.tileSize / 2;
        int y = EXIT_ROW * gp.tileSize - 5;
        moveTo(p, x, y, "up");
    }

    static void leave(Gamepanel gp, Player p){
        if(p.isDead || !p.canResolveWorldActions() || !contains(gp, p)) return;
        WorldObject door = null;
        for(WorldObject o : gp.worldObjects){
            if(o.kind != WorldObjectType.INN) continue;
            door = o;
            if(o.col == ENTRANCE_COL && o.row == ENTRANCE_ROW) break;
        }
        int x = door == null ? ENTRANCE_COL * gp.tileSize + gp.tileSize / 2 : door.worldX;
        int y = door == null ? (ENTRANCE_ROW + 1) * gp.tileSize - 4 : door.worldY;
        moveTo(p, x, y + gp.tileSize / 2, "down");
    }

    private static void moveTo(Player p, int centerX, int centerY, String direction){
        p.resetForSceneTransition();
        p.worldX = centerX - p.solidArea.x - p.solidArea.width / 2;
        p.worldY = centerY - p.solidArea.y - p.solidArea.height / 2;
        p.direction = direction;
    }

    public static void draw(Graphics2D g, Gamepanel gp, Player camera){
        g.setColor(new Color(0x28242E));
        g.fillRect(-gp.tileSize, -gp.tileSize, gp.screenWidth + gp.tileSize * 2, gp.screenHeight + gp.tileSize * 2);
        int x = COL * gp.tileSize - camera.worldX + camera.screenX;
        int y = ROW * gp.tileSize - camera.worldY + camera.screenY;
        g.drawImage(RoomArt.IMAGE, x, y, null);
    }

    private static class RoomArt {
        private static final BufferedImage IMAGE = paint();

        private static BufferedImage paint(){
            ArtKit.Surface surface = new ArtKit.Surface(WIDTH, HEIGHT);
            Graphics2D g = surface.g;
            fill(g, 0x1F1B26, 0, 12, WIDTH, HEIGHT - 12, 14);
            fill(g, 0x654937, 5, 0, WIDTH - 10, HEIGHT - 4, 12);
            fill(g, 0xA97550, 13, 6, WIDTH - 26, HEIGHT - 14, 8);
            fill(g, 0x77523E, 19, 51, WIDTH - 38, HEIGHT - 61, 3);
            fill(g, 0xC6956C, FLOOR.x, 62, FLOOR.width, 263, 2);

            // Staggered floorboards and deep wood trim at the base of the walls.
            Shape clip = g.getClip();
            g.clipRect(FLOOR.x, 62, FLOOR.width, 263);
            int[] planks = {0xBB8861, 0xC29068, 0xCA986E, 0xC5946B, 0xBE8C64};
            for(int row = 0; row < 16; row++){
                int y = 62 + row * 17;
                for(int col = -1; col < 7; col++){
                    int x = 25 + col * 77 + (row % 3) * 25;
                    fill(g, planks[Math.floorMod(row * 3 + col, planks.length)], x + 1, y + 1, 75, 15, 3);
                }
            }
            g.setClip(clip);
            fill(g, 0x7D533F, 19, 54, 442, 12, 3);
            fill(g, 0xA67553, 21, 54, 438, 4, 2);

            // Plastered back wall, window, and hearth.
            fill(g, 0xDAB692, 21, 10, 438, 44, 4);
            for(int x : new int[]{25, 151, 327, 448}){
                fill(g, 0x966744, x, 9, 9, 47, 2);
                fill(g, 0xB58457, x, 10, 3, 43, 1);
            }
            fill(g, 0x916348, 73, 15, 55, 36, 5);
            fill(g, 0x7894A0, 79, 20, 43, 25, 3);
            fill(g, 0xA6B4AE, 79, 20, 43, 9, 3);
            fill(g, 0xAF7D52, 98, 18, 5, 32, 2);
            fill(g, 0xAF7D52, 77, 31, 47, 4, 2);
            fill(g, 0xB9875B, 69, 46, 63, 6, 3);

            fill(g, 0x9A8071, 222, 7, 70, 53, 6);
            fill(g, 0xB49B86, 227, 10, 59, 7, 2);
            fill(g, 0xAE9582, 230, 20, 54, 5, 2);
            fill(g, 0x725748, 231, 27, 51, 31, 17);
            fill(g, 0x42303A, 237, 30, 39, 25, 15);
            fill(g, 0xED9656, 243, 41, 27, 13, 13);
            fill(g, 0xFFD38A, 250, 37, 14, 16, 12);
            fill(g, 0xBCA08C, 214, 55, 86, 7, 3);

            // Door runner and stone threshold.
            fill(g, 0x52665C, 219, 137, 90, 196, 6);
            fill(g, 0x6D8574, 224, 142, 80, 188, 4);
            for(int y = 155; y < 309; y += 28){
                diamond(g, 0xA6A782, 264, y, 9, 10);
                diamond(g, 0x6D8574, 264, y, 5, 5);
            }
            fill(g, 0xBAAB96, 226, 322, 76, 14, 3);
            fill(g, 0xD6C7AE, 228, 322, 72, 4, 2);
            for(int x : new int[]{217, 301}) fill(g, 0x7D583F, x, 310, 9, 24, 3);

            furniture(g, COUNTER, 0x84573F, 0xAC784F, 5);
            for(int x = 57; x < 146; x += 22){
                fill(g, 0x926143, x, 100, 4, 13, 2);
            }
            fill(g, 0xE0BD87, 58, 95, 20, 12, 3);
            fill(g, 0x788C77, 126, 96, 11, 10, 3);
            furniture(g, BENCH, 0x72513D, 0xB2835B, 6);
            fill(g, 0x986B5E, 211, 78, 92, 12, 5);

            furniture(g, BED, 0x76513D, 0xA56F4D, 6);
            fill(g, 0xDFCAA8, 378, 81, 49, 82, 6);
            fill(g, 0xF0DDC0, 383, 87, 39, 20, 6);
            fill(g, 0x71877A, 378, 111, 49, 52, 5);
            fill(g, 0xA8AC8B, 378, 115, 49, 6, 2);
            table(g, TABLE_LEFT);
            table(g, TABLE_RIGHT);

            g.clipRect(21, 10, 438, 312);
            g.setPaint(new RadialGradientPaint(257, 62, 87, new float[]{0f, 0.45f, 1f},
                    new Color[]{new Color(255, 202, 124, 84), new Color(255, 188, 104, 36),
                            new Color(255, 188, 104, 0)}));
            g.fillOval(170, -25, 174, 174);
            return surface.finish();
        }

        private static void furniture(Graphics2D g, Rectangle r, int wood, int top, int round){
            fill(g, 0xA77352, r.x + 3, r.y + 5, r.width + 2, r.height, round);
            fill(g, wood, r.x, r.y, r.width, r.height, round);
            fill(g, top, r.x + 3, r.y + 2, r.width - 6, r.height - 9, round);
        }

        private static void table(Graphics2D g, Rectangle r){
            furniture(g, r, 0x805740, 0xB78256, 17);
            fill(g, 0x88977C, r.x + 23, r.y + 6, 30, 29, 4);
            fill(g, 0xDDD0B2, r.x + 31, r.y + 12, 15, 15, 12);
            fill(g, 0xE5B575, r.x + 36, r.y + 15, 6, 6, 4);
        }

        private static void fill(Graphics2D g, int rgb, int x, int y, int w, int h, int arc){
            g.setColor(new Color(rgb));
            g.fillRoundRect(x, y, w, h, arc, arc);
        }

        private static void diamond(Graphics2D g, int rgb, int x, int y, int w, int h){
            Path2D path = new Path2D.Double();
            path.moveTo(x, y - h);
            path.lineTo(x + w, y);
            path.lineTo(x, y + h);
            path.lineTo(x - w, y);
            path.closePath();
            g.setColor(new Color(rgb));
            g.fill(path);
        }
    }
}
