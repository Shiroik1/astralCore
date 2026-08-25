package tile;

import main.Gamepanel;
import main.UtilityTool;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class TileManager {
    Gamepanel gp;
    public Tile[] tiles;
    public int mapTileNum[][];

    public TileManager(Gamepanel gp){
        this.gp = gp;

        tiles = new Tile[20];

        mapTileNum = new int[gp.maxWorldCol][gp.maxWorldRow];

        getTileImage();
        loadMap("/maps/world01.txt");
    }

    public void getTileImage(){

        setup(0,"002",false); //Grass
        setup(1,"019",true); //Water
        setup(2,"032",true); //Wall
        setup(3, "003",false); //Sand
        setup(4, "016",true); //Tree
        setup(5, "017", false); //Dirt

    }

    public void setup(int index, String imageName, boolean collision){
        UtilityTool utilityTool = new UtilityTool();

        try{
            tiles[index] = new Tile();
            tiles[index].image = ImageIO.read(getClass().getResourceAsStream("/tile/" + imageName + ".png"));
            tiles[index].image = utilityTool.scaleImage(tiles[index].image, gp.tileSize, gp.tileSize);
            tiles[index].collision = collision;
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    public void loadMap(String mapFilePath){
        try{
            InputStream is = getClass().getResourceAsStream(mapFilePath);
            BufferedReader br = new BufferedReader(new InputStreamReader(is));

            int col = 0;
            int row = 0;

            while(col < gp.maxWorldCol && row < gp.maxWorldRow){
                String line = br.readLine();

                while (col < gp.maxWorldCol){
                    String numbers[] = line.split(" ");
                    int num = Integer.parseInt(numbers[col]);

                    mapTileNum[col][row] = num;
                    col++;
                }

                if(col == gp.maxWorldCol){
                    col = 0;
                    row++;
                }
            }

            br.close();

        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    public void draw(Graphics2D g2){
        int worldCol = 0;
        int worldRow = 0;

        while(worldCol < gp.maxWorldCol && worldRow < gp.maxWorldRow){

            int tileNum = mapTileNum[worldCol][worldRow];

            int worldX = worldCol * gp.tileSize;
            int worldY = worldRow * gp.tileSize;
            int screenX = worldX - gp.player.worldX + gp.player.screenX;
            int screenY = worldY - gp.player.worldY + gp.player.screenY;

            if(worldX + gp.tileSize > gp.player.worldX - gp.player.screenX &&
            worldX - gp.tileSize < gp.player.worldX + gp.player.screenX &&
            worldY + gp.tileSize > gp.player.worldY - gp.player.screenY &&
            worldY - gp.tileSize < gp.player.worldY + gp.player.screenY){
                g2.drawImage(tiles[tileNum].image, screenX, screenY, null);
            }

            worldCol++;

            if(worldCol == gp.maxWorldCol){
                worldCol = 0;
                worldRow++;
            }
        }
    }
}
