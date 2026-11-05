package tile;

import main.Gamepanel;
import main.UtilityTool;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import entity.Player;

public class TileManager {
    Gamepanel gp;
    public Tile[] tiles;
    public int mapTileNum[][];
    ArrayList<String> filenames = new ArrayList<>();
    ArrayList<String> collisionStatus = new ArrayList<>();

    public TileManager(Gamepanel gp) throws IOException {
        this.gp = gp;

        //READ TILE DATA
        InputStream is = getClass().getResourceAsStream("/maps/tile_data.txt");
        BufferedReader br = new BufferedReader(new InputStreamReader(is));

        String line;
        while ((line = br.readLine()) != null){
            filenames.add(line);
            collisionStatus.add(br.readLine());
        }
        br.close();

        tiles = new Tile[filenames.size()];

        getTileImage();

        is = getClass().getResourceAsStream("/maps/overworld.txt");
        br = new BufferedReader(new InputStreamReader(is));

        String line2 = br.readLine();
        String maxTile[] = line2.split(" ");

        gp.maxWorldCol = maxTile.length;
        gp.maxWorldRow = maxTile.length;

        mapTileNum = new int[gp.maxWorldCol][gp.maxWorldRow];

        br.close();

        loadMap("/maps/overworld.txt");
    }

    public void getTileImage(){

        for(int i = 0; i < filenames.size(); i++){
            String fileName;
            boolean collision;

            fileName = filenames.get(i);

            if(collisionStatus.get(i).equals("true")){
                collision = true;
            }
            else {
                collision = false;
            }

            setup(i, fileName, collision);
        }

    }

    public void setup(int index, String imageName, boolean collision){
        UtilityTool utilityTool = new UtilityTool();

        try{
            tiles[index] = new Tile();
            tiles[index].image = ImageIO.read(getClass().getResourceAsStream("/tile/" + imageName));
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
        Player camera = gp.localPlayer();
        int viewLeft = camera.worldX - camera.screenX;
        int viewTop = camera.worldY - camera.screenY;

        // one extra tile of margin on each side so screen shake never exposes a gap
        int firstCol = Math.max(0, Math.floorDiv(viewLeft, gp.tileSize) - 1);
        int lastCol = Math.min(gp.maxWorldCol - 1, Math.floorDiv(viewLeft + gp.screenWidth, gp.tileSize) + 1);
        int firstRow = Math.max(0, Math.floorDiv(viewTop, gp.tileSize) - 1);
        int lastRow = Math.min(gp.maxWorldRow - 1, Math.floorDiv(viewTop + gp.screenHeight, gp.tileSize) + 1);

        for(int row = firstRow; row <= lastRow; row++){
            for(int col = firstCol; col <= lastCol; col++){
                int tileNum = mapTileNum[col][row];
                int screenX = col * gp.tileSize - camera.worldX + camera.screenX;
                int screenY = row * gp.tileSize - camera.worldY + camera.screenY;
                g2.drawImage(tiles[tileNum].image, screenX, screenY, null);
            }
        }
    }
}
