import art.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Standalone design exporter; does not modify maps or the running game's terrain. */
public final class GenerateLakeAssets {
    private static final int T=48;
    private static final String[] LAKE={
        "..................",
        "......wwwww.......",
        "....wwwwwwwww.....",
        "...wwwwwwwwwww....",
        "..wwwwwwwwwwww....",
        "..wwwwwwwwwwwww...",
        "..wwwwww..wwwww...",
        "...wwwww..wwwww...",
        "....wwwwwwwwww....",
        ".....wwwwwww......",
        ".......www........",
        ".................."
    };
    private static boolean water(int x,int y) {
        return y>=0 && y<LAKE.length && x>=0 && x<LAKE[y].length() && LAKE[y].charAt(x)=='w';
    }
    private static int mask(int x,int y) {
        int[] dx={0,1,0,-1,1,1,-1,-1}, dy={-1,0,1,0,-1,1,1,-1};
        int m=0;for(int i=0;i<8;i++)if(water(x+dx[i],y+dy[i]))m|=1<<i;
        return LakeTiles.canonicalMask(m);
    }
    private static void write(BufferedImage image,Path file)throws IOException {
        Files.createDirectories(file.getParent());ImageIO.write(image,"png",file.toFile());
    }
    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }
    public static void main(String[] args)throws Exception {
        Path out=Path.of(args.length==0?"assets/reference/lakes":args[0]);
        LakeTiles tiles=new LakeTiles(T);
        SortedSet<Integer> masks=new TreeSet<>();
        for(int i=0;i<256;i++)masks.add(LakeTiles.canonicalMask(i));
        if(masks.size()!=47)throw new AssertionError("Expected 47 connecting tile shapes");
        StringBuilder json=new StringBuilder("{\n  \"tileSize\":48,\n  \"atlasColumns\":8,\n  \"atlasRows\":6,\n  \"variants\":[\"calm\",\"ripple\",\"reflections\"],\n  \"maskBits\":{\"up\":1,\"right\":2,\"down\":4,\"left\":8,\"upRight\":16,\"downRight\":32,\"downLeft\":64,\"upLeft\":128},\n  \"tiles\":[\n");
        BufferedImage swatches=new BufferedImage(856,938,BufferedImage.TYPE_INT_RGB);
        Graphics2D sg=graphics(swatches);sg.setColor(new Color(0xF2EDE2));sg.fillRect(0,0,856,938);
        sg.setColor(new Color(0x51474B));sg.setFont(new Font("SansSerif",Font.BOLD,23));sg.drawString("ASTRALCORE / LAKE TILE STUDY",24,35);
        sg.setFont(new Font("SansSerif",Font.PLAIN,14));sg.drawString("48 px tiles / rounded banks / subtle top-left highlights",24,62);
        int index=0;
        BufferedImage[] atlases=new BufferedImage[3];
        Graphics2D[] ag=new Graphics2D[3];
        for(int v=0;v<3;v++){atlases[v]=new BufferedImage(384,288,BufferedImage.TYPE_INT_ARGB);ag[v]=atlases[v].createGraphics();}
        for(int m:masks) {
            int col=index%8,row=index/8;
            for(int v=0;v<3;v++) {
                BufferedImage image=tiles.tile(m,v);
                write(image,out.resolve(String.format("tiles_48/mask_%03d_v%d.png",m,v)));
                ag[v].drawImage(image,col*T,row*T,null);
            }
            int x=24+col*103,y=90+row*118;
            sg.setColor(Palette.GROUND[0]);sg.fillRoundRect(x,y,96,96,7,7);
            sg.drawImage(tiles.tile(m,0),x,y,96,96,null);
            sg.setColor(new Color(0x6D6B61));sg.setFont(new Font("SansSerif",Font.PLAIN,11));
            sg.drawString(String.format("mask %03d",m),x+13,y+110);
            if(index>0)json.append(",\n");
            json.append(String.format("    {\"mask\":%d,\"column\":%d,\"row\":%d}",m,col,row));
            index++;
        }
        json.append("\n  ]\n}\n");Files.writeString(out.resolve("manifest.json"),json);
        for(int v=0;v<3;v++){ag[v].dispose();write(atlases[v],out.resolve("lake_atlas_v"+v+".png"));}
        write(tiles.lilyPad(false),out.resolve("decor/lily_pad.png"));
        write(tiles.lilyPad(true),out.resolve("decor/lily_flower.png"));
        sg.setFont(new Font("SansSerif",Font.PLAIN,14));sg.setColor(new Color(0x51474B));
        sg.drawString("Calm water     /     Ripple     /     Reflections     /     Optional lily pads",24,837);
        for(int i=0;i<5;i++) {
            int x=24+i*145;sg.setColor(Palette.GROUND[0]);sg.fillRect(x,850,72,72);
            sg.drawImage(tiles.tile(255,Math.min(i,2)),x,850,72,72,null);
            if(i>2)sg.drawImage(tiles.lilyPad(i==4),x,850,72,72,null);
        }
        sg.dispose();write(swatches,out.resolve("tile_study.png"));

        GroundTiles ground=new GroundTiles(T);
        GroundTiles.World world=new GroundTiles.World(){
            public Terrain terrain(int c,int r){return Terrain.GRASS;}
            public int level(int c,int r){return ground.groundLevel(c,r);}
        };
        BufferedImage scene=new BufferedImage(18*T,12*T,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=graphics(scene);
        for(int r=0;r<12;r++)for(int c=0;c<18;c++){
            ground.drawCell(g,world,c,r,c*T,r*T);
            if(water(c,r)){
                double choice=ArtKit.hash01(32,c,r);
                int v=choice<.64?0:choice<.86?1:2;
                g.drawImage(tiles.tile(mask(c,r),v),c*T,r*T,null);
            }
        }
        g.drawImage(tiles.lilyPad(false),4*T,4*T,null);
        g.drawImage(tiles.lilyPad(true),5*T,4*T+8,null);
        g.drawImage(tiles.lilyPad(false),11*T,7*T,null);
        CharacterArt.Look look=new CharacterArt.Look();look.outfit=CharacterOutfit.ADVENTURER;
        g.drawImage(CharacterArt.render(CharacterArt.Dir.LEFT,new CharacterArt.Pose(),look,90),16*T-21,8*T-38,null);
        g.dispose();write(scene,out.resolve("lake_scene.png"));

        // Validate deterministic neighbour joins and the interior's absence of edge seams.
        BufferedImage full=tiles.tile(255,0);
        for(int i=0;i<T;i++){
            if(full.getRGB(0,i)!=full.getRGB(T-1,i)||full.getRGB(i,0)!=full.getRGB(i,T-1))
                throw new AssertionError("Interior water must tile seamlessly");
        }
        for(int m:masks)if(tiles.tile(m,0)!=tiles.tile(m,0))throw new AssertionError("Tile cache");
        System.out.println("Exported 47 connecting shapes x 3 variants, 2 lily decorations, atlases, scene and manifest. Interior seam check passed.");
    }
}
