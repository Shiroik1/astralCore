package world.generation;

import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Explicitly invoked asset/topology checks. No automatic build hook or restored test framework. */
public final class CliffTilesCheck {
    public static void main(String[] args)throws Exception{
        int[] dx={0,1,0,-1,1,1,-1,-1},dy={-1,0,1,0,-1,1,1,-1};
        var h=new HeightMap(33,33);h.set(15,15,3);
        for(int bit=0;bit<8;bit++){
            h.set(15+dx[bit],15+dy[bit],2);
            require(CliffTileResolver.mask(h,15,15,1)==1<<bit,"Wrong neighbour bit at cache edge");
            require(CliffTileResolver.mask(h,15,15,2)==1<<bit,"Higher neighbour missed");
            require(CliffTileResolver.mask(h,15,15,3)==0,"Lower neighbour joined high cap");
            h.set(15+dx[bit],15+dy[bit],0);
        }
        for(int tone=0;tone<4;tone++){
            var atlas=ImageIO.read(Path.of("res/ground/cliff-top-"+tone+".png").toFile());
            require(atlas.getWidth()==768 && atlas.getHeight()==768 && atlas.getColorModel().hasAlpha(),"Invalid native cap atlas");
            int[] px={24,47,24,0},py={0,24,47,24};
            for(int mask=0;mask<256;mask++){
                int x=mask%16*48,y=mask/16*48;
                require(alpha(atlas.getRGB(x+24,y+24))==255,"Cap center not opaque");
                for(int bit=0;bit<4;bit++)require((alpha(atlas.getRGB(x+px[bit],y+py[bit]))>0)==((mask&(1<<bit))!=0),"Broken straight/T/cross connection");
                require((alpha(atlas.getRGB(x,y))>0)==((mask&1)!=0 && (mask&8)!=0 && (mask&128)!=0),"Broken inner/outer NW corner");
            }
        }
        for(String name:new String[]{"cliff-middle","cliff-side-left","cliff-side-right","cliff-lip","cliff-foot"}){
            var image=ImageIO.read(Path.of("res/ground/"+name+".png").toFile());
            require(image.getHeight()==48 && image.getWidth()%48==0 && image.getColorModel().hasAlpha(),"Invalid face/lip/foot sprite dimensions");
        }
        var config=WorldConfig.developmentDefaults();
        var fixture=new GeneratedWorld(config,new NoiseGenerator(config.seed()),10,GenerationConfig.defaults(),NestConfig.defaults(),ElevationConfig.defaults(),CliffFormationConfig.defaults());
        int col=config.width()/2,row=config.height()/2;fixture.ground(col,row,art.Terrain.GRASS,1);
        art.GroundTiles.World tiles=new art.GroundTiles.World(){public art.Terrain terrain(int x,int y){return art.Terrain.GRASS;}public int level(int x,int y){return 1;}};
        var art=new art.GroundTiles(48);
        for(int height=1;height<=3;height++){
            fixture.heights().set(col,row,height);int lift=height*24;
            var image=new java.awt.image.BufferedImage(48,48+lift+12,java.awt.image.BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();
            CliffTileResolver.drawStack(g,art,tiles,fixture,col,row,48,0,lift);g.dispose();
            // These are middle-wall pixels at old intermediate grass-lip positions.
            for(int step=1;step<height;step++){var color=new java.awt.Color(image.getRGB(24,48+step*24));require(color.getRed()>color.getGreen(),"Repeated grassy lip in a taller face");}
        }
        fixture.heights().set(col,row,1);
        for(int y=row-1;y<=row+1;y++)for(int x=col;x<=col+2;x++)fixture.heights().set(x,y,1);
        var joined=world.CliffLayer.bakeImage(fixture,art,tiles,col,row,48);
        int lift=24+6,top=lift-24;
        require(alpha(joined.getRGB(24,top))==255 && alpha(joined.getRGB(24,top+47))==255,"Joined cap rows faded into transparent seams");
        var previous=world.CliffLayer.bakeImage(fixture,art,tiles,col,row-1,48);
        require(previous.getRGB(24,top+47)==joined.getRGB(24,top),"Color seam between connected cap rows");
        for(int y=row-1;y<=row+1;y++)for(int x=col;x<=col+2;x++)fixture.heights().set(x,y,0);
        fixture.heights().set(col,row,1);
        var corner=world.CliffLayer.bakeImage(fixture,art,tiles,col,row,48);
        require(alpha(corner.getRGB(13,lift+47))>0,"Rounded face stopped above its cliff foot");
        System.out.println("Cliff topology and native sprite checks passed.");
    }
    private static int alpha(int rgb){return rgb>>>24;}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
