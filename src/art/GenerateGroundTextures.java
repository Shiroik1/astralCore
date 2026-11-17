package art;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.function.IntFunction;
import javax.imageio.ImageIO;

/** Offline export of the tile artwork. The game only reads the resulting PNGs. */
public final class GenerateGroundTextures {
    private static final int U = GroundTiles.ART_TILE_SIZE;
    private static final int CORNER_RADIUS = 7;

    private GenerateGroundTextures() {}

    public static void main(String[] args) throws IOException {
        if(args.length>0 && args[0].equals("--cliffs")){GenerateCliffTextures.main(new String[0]);return;}
        if(args.length>0 && args[0].equals("--cobble")){
            Path directory=Path.of("res/ground");Files.createDirectories(directory);
            mossPaths(directory);return;
        }
        if(args.length>0 && args[0].equals("--environment")){
            Path directory=Path.of("res/ground");Files.createDirectories(directory);
            environment(directory);return;
        }
        Path directory = Path.of(args.length == 0 ? "res/ground" : args[0]);
        Files.createDirectories(directory);
        write(directory, "grass", GroundTiles.LEVELS, 1, i -> flat(Palette.GROUND[i]));
        grassVariants(directory);
        write(directory, "cobble", GroundTiles.COBBLE_VARIANTS, 1, i -> cobbleTile(5000L + i));
        mossPaths(directory);
        for (int level = 0; level < GroundTiles.LEVELS; level++) {
            Color grass = Palette.GROUND[level];
            write(directory, "grass-edge-" + level, 16, 16,
                    mask -> edge(mask, GroundTiles.GRASS_INSET, grass, false, 0));
        }
        for (int variant = 0; variant < GroundTiles.PATH_VARIANTS; variant++) {
            int pattern = variant;
            write(directory, "path-" + variant, 16, 16, mask -> edge(mask, 7, Palette.PATH, true, pattern));
        }
        LakeTiles lakes = new LakeTiles(U);
        for (int variant = 0; variant < LakeTiles.VARIANTS; variant++) {
            int pattern = variant;
            write(directory, "water-" + variant, 16, 16, mask -> lakes.tile(mask, pattern));
        }
        write(directory, "lilies", 2, 1, i -> lakes.lilyPad(i == 1));
        write(directory, "ledge", 4, GroundTiles.LEVELS, i -> ledge(i / 4, i % 4, false));
        write(directory, "steps", 1, GroundTiles.LEVELS, i -> ledge(i, 3, true));
    }

    private static void environment(Path directory)throws IOException{
        grassVariants(directory);
        mossPaths(directory);
        LakeTiles lakes=new LakeTiles(U);
        for(int variant=0;variant<LakeTiles.VARIANTS;variant++){
            int pattern=variant;
            write(directory,"water-"+variant,16,16,mask->lakes.tile(mask,pattern));
        }
    }

    private static void grassVariants(Path directory)throws IOException{
        write(directory,"grass-variants",GroundTiles.GRASS_VARIANTS,GroundTiles.LEVELS,
                i->grassTile(i/GroundTiles.GRASS_VARIANTS,i%GroundTiles.GRASS_VARIANTS,false));
        write(directory,"grass-detail",GroundTiles.GRASS_VARIANTS,GroundTiles.LEVELS,
                i->grassTile(i/GroundTiles.GRASS_VARIANTS,i%GroundTiles.GRASS_VARIANTS,true));
    }

    private static BufferedImage grassTile(int level,int variant,boolean overlay){
        ArtKit.Surface surface=new ArtKit.Surface(U,U);Graphics2D g=surface.g;
        if(!overlay){g.setColor(Palette.GROUND[level]);g.fillRect(0,0,U,U);}
        Random random=new Random(9371L+variant*31L);
        Color ink=ArtKit.mix(Palette.GROUND[level],Palette.FOLIAGE_DARK,.28f);
        for(int i=0;i<variant*2;i++){
            // Keep blades inside the tile's central tone region, including transition tiles.
            double x=12+random.nextDouble()*24,y=15+random.nextDouble()*21;
            AffineTransform transform=AffineTransform.getTranslateInstance(x,y);
            transform.scale(.45+random.nextDouble()*.16,.48+random.nextDouble()*.16);
            g.setColor(ink);g.fill(transform.createTransformedShape(tuft(i+variant)));
        }
        return surface.finish();
    }

    private static void mossPaths(Path directory)throws IOException{
        for(int variant=0;variant<GroundTiles.COBBLE_VARIANTS;variant++){
            int pattern=variant;
            BufferedImage interior=cobbleTile(5000L+variant);
            write(directory,"cobble-connect-"+pattern,16,16,mask->connectedCobble(interior,mask));
        }
        for(int variant=0;variant<GroundTiles.MOSS_VARIANTS;variant++){
            int pattern=variant;
            write(directory,"moss-path-"+variant,16,16,mask->mossPath(mask,pattern));
        }
        write(directory,"cobble-fringe",16,16,CobbleTransitions::fringe);
    }

    private static BufferedImage connectedCobble(BufferedImage interior,int mask){
        ArtKit.Surface surface=new ArtKit.Surface(U,U);
        surface.g.clip(CobbleTransitions.footprint(mask));
        surface.g.drawImage(interior,0,0,null);
        return CobbleTransitions.finish(surface,mask);
    }

    private static BufferedImage mossPath(int mask,int variant){
        ArtKit.Surface surface=new ArtKit.Surface(U,U);Graphics2D g=surface.g;
        Area shape=CobbleTransitions.footprint(mask);
        g.clip(shape);
        Random random=new Random(8700L+variant);
        for(int row=0;row<3;row++) for(int col=-1;col<3;col++){
            double x=col*24+(row%2)*12,y=row*16;
            Shape stone=new RoundRectangle2D.Double(x+1.5,y+1,21,13,6,6);
            Color base=ArtKit.mix(Palette.STONES[random.nextInt(Palette.STONES.length)],Palette.PATH,.36f);
            g.setColor(base);g.fill(stone);
            // A narrow top plane adds vector depth while keeping the grass-connected silhouette.
            Area top=new Area(new RoundRectangle2D.Double(x+2,y+1,20,4,4,4));
            top.intersect(new Area(stone));
            g.setColor(ArtKit.mix(base,Palette.PLASTER,.24f));g.fill(top);
        }
        for(int i=0;i<1+variant;i++){
            double x=random.nextDouble()*48,y=random.nextDouble()*48;
            g.setColor(ArtKit.mix(Palette.FOLIAGE,Palette.GROUND[1],.45f));
            g.fill(new Ellipse2D.Double(x-2,y-1,3+random.nextInt(4),2+random.nextInt(3)));
        }
        return CobbleTransitions.finish(surface,mask);
    }

    private static void write(Path directory, String name, int columns, int rows,
                              IntFunction<BufferedImage> tile) throws IOException {
        BufferedImage atlas = new BufferedImage(columns * U, rows * U, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = atlas.createGraphics();
        for (int i = 0; i < columns * rows; i++) {
            graphics.drawImage(tile.apply(i), i % columns * U, i / columns * U, null);
        }
        graphics.dispose();
        ImageIO.write(atlas, "png", directory.resolve(name + ".png").toFile());
    }

    private static BufferedImage flat(Color color) {
        ArtKit.Surface surface = new ArtKit.Surface(U, U);
        surface.g.setColor(color);
        surface.g.fillRect(0, 0, U, U);
        return surface.finish();
    }

    private static BufferedImage edge(int mask, int inset, Color color, boolean path, int variant) {
        Area shape = GroundTiles.blobShape(mask, inset, CORNER_RADIUS, 0, -1);
        int[] directions = {GroundTiles.UP, GroundTiles.RIGHT, GroundTiles.DOWN, GroundTiles.LEFT};
        for (int side = 0; side < directions.length; side++) {
            if ((mask & directions[side]) != 0) continue;
            // Keep grass tufts away from shared tile seams.
            for (int clump = 0; clump < 2; clump++) {
                int x = 16 + clump * 16 + (variant + side + clump) % 3 - 1;
                AffineTransform placement = AffineTransform.getRotateInstance(side * Math.PI / 2, 24, 24);
                placement.translate(x, inset);
                if (path) placement.scale(1, -1);
                Area blades = new Area(placement.createTransformedShape(tuft(clump + variant)));
                if (path) shape.subtract(blades);
                else shape.add(blades);
            }
        }
        ArtKit.Surface surface = new ArtKit.Surface(U, U);
        surface.g.setColor(color);
        surface.g.fill(shape);
        return surface.finish();
    }

    private static Shape tuft(int variant) {
        Path2D blades = new Path2D.Double();
        blades.moveTo(-5, 1);
        blades.quadTo(-5, -1, -6, -3);
        blades.quadTo(-3, -3, -2, -1);
        blades.lineTo(variant % 2 == 0 ? -1 : 1, -6);
        blades.quadTo(2, -4, 2, -1);
        blades.lineTo(6, -3);
        blades.quadTo(4, -1, 4, 1);
        blades.closePath();
        return blades;
    }

    private static BufferedImage ledge(int level, int connections, boolean steps) {
        ArtKit.Surface surface = new ArtKit.Surface(U, U);
        Graphics2D graphics = surface.g;
        int left = (connections & GroundTiles.LEDGE_LEFT) == 0 ? GroundTiles.GRASS_INSET : 0;
        int right = (connections & GroundTiles.LEDGE_RIGHT) == 0 ? U - GroundTiles.GRASS_INSET : U;
        int wallTop = U / 2;
        // Extend joined ends past the tile so only the exposed ends are rounded.
        int arcLeft = left == 0 ? -CORNER_RADIUS : left;
        int arcRight = right == U ? U + CORNER_RADIUS : right;
        Area wall = new Area(new RoundRectangle2D.Double(arcLeft, wallTop - CORNER_RADIUS,
                arcRight - arcLeft, U - wallTop + CORNER_RADIUS, CORNER_RADIUS * 2, CORNER_RADIUS * 2));
        wall.intersect(new Area(new Rectangle2D.Double(left, wallTop, right - left, U - wallTop)));
        graphics.setColor(Palette.TIMBER);
        graphics.fill(wall);
        graphics.setColor(Palette.GROUND[level]);
        graphics.fillRect(left, 0, right - left, wallTop);
        for (int x = 12; x <= 36; x += 12) {
            AffineTransform placement = AffineTransform.getTranslateInstance(x, 24);
            placement.scale(1, -1);
            graphics.fill(placement.createTransformedShape(tuft(x / 12)));
        }
        if (steps) {
            graphics.setColor(Palette.PATH);
            graphics.fillRect(7, 0, 34, 24);
            for (int y = 24; y < 48; y += 6) {
                graphics.setColor(Palette.WOOD_LIGHT);
                graphics.fillRect(7, y, 34, 4);
                graphics.setColor(Palette.WOOD);
                graphics.fillRect(7, y + 4, 34, 2);
            }
        }
        return surface.finish();
    }

    //COBBLESTONE: flat stones on flat mortar. Row widths add up to exactly one tile, so it repeats seamlessly.
    private static BufferedImage cobbleTile(long seed){
        ArtKit.Surface s = new ArtKit.Surface(U, U);
        Graphics2D g = s.g;
        Random rnd = new Random(seed);

        g.setColor(Palette.MORTAR);
        g.fill(new Rectangle2D.Double(-1, -1, U + 2, U + 2));

        int rows = 3;
        double rowH = U / (double) rows;
        double gap = 1.6;
        for(int r = 0; r < rows; r++){
            int stones = 2 + rnd.nextInt(2);
            double[] w = new double[stones];
            double total = 0;
            for(int i = 0; i < stones; i++){
                w[i] = 0.7 + rnd.nextDouble() * 0.6;
                total += w[i];
            }
            double x = 0;
            for(int i = 0; i < stones; i++){
                double sw = w[i] / total * U;
                g.setColor(Palette.STONES[rnd.nextInt(Palette.STONES.length)]);
                double arc = Math.min(sw, rowH) * 0.5;
                g.fill(new RoundRectangle2D.Double(x + gap / 2, r * rowH + gap / 2, sw - gap, rowH - gap, arc, arc));
                x += sw;
            }
        }
        return s.finish();
    }
}
