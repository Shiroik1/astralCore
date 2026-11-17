package world.generation;

import art.Terrain;

import world.GrassLayer;
import world.WorldObjectType;

import java.awt.Rectangle;
import java.util.ArrayList;

final class ObjectGenerator {
    private static final WorldObjectType[] FLOWERS = {
        WorldObjectType.FLOWERS_WHITE,
        WorldObjectType.FLOWERS_PINK,
        WorldObjectType.FLOWERS_PURPLE,
        WorldObjectType.FLOWERS_YELLOW,
        WorldObjectType.FLOWERS_BLUE,
        WorldObjectType.FLOWERS_MIXED
    };
    private static final WorldObjectType[] MUSHROOMS = {
        WorldObjectType.MUSHROOM_RED,
        WorldObjectType.MUSHROOM_BROWN,
        WorldObjectType.MUSHROOM_IVORY,
        WorldObjectType.MUSHROOMS_RED,
        WorldObjectType.MUSHROOMS_BROWN,
        WorldObjectType.MUSHROOMS_IVORY
    };
    private static final WorldObjectType[] STUMPS = {
        WorldObjectType.STUMP_OAK,
        WorldObjectType.STUMP_OAK_MOSS,
        WorldObjectType.STUMP_OAK_AGED,
        WorldObjectType.STUMP_PINE,
        WorldObjectType.STUMP_PINE_MOSS,
        WorldObjectType.STUMP_PINE_AGED
    };

    /** Performs the generate operation for ObjectGenerator. */
    void generate(GeneratedWorld world, NoiseGenerator noise, WorldPalette palette, int tileSize) {
        WorldConfig config = world.config;
        boolean[][] occupied = new boolean[config.width()][config.height()];
        var placements = new ArrayList<GeneratedWorld.Placement>();
        for (int r = 0; r < config.height(); r++)
            for (int c = 0; c < config.width(); c++) {
                if (world.reserved(c, r) || world.terrain(c, r) != Terrain.GRASS) continue;
                double vegetation =
                        noise.sample(
                                c * config.vegetationScale(), r * config.vegetationScale(), 30);
                var nest = world.slimeNest();
                if (nest != null) {
                    int dx =
                            Math.max(
                                    nest.col() - c,
                                    Math.max(0, c - (nest.col() + nest.size() - 1)));
                    int dy =
                            Math.max(
                                    nest.row() - r,
                                    Math.max(0, r - (nest.row() + nest.size() - 1)));
                    if (Math.max(dx, dy) <= 3)
                        vegetation =
                                Math.max(
                                        vegetation,
                                        .58 + noise.sample(c * .18, r * .18, 904) * .24);
                }
                double flowers = noise.sample(c * .23, r * .23, 40);
                WorldObjectType type = null;
                double pick = noise.random(c, r, 50);
                if (vegetation > .59 && pick < config.treeDensity() * (vegetation - .50) * 3) {
                    double species = noise.random(c, r, 51);
                    type =
                            species < .35
                                    ? WorldObjectType.OAK_1
                                    : species < .70
                                            ? WorldObjectType.OAK_2
                                            : species < .85
                                                    ? WorldObjectType.PINE_1
                                                    : WorldObjectType.PINE_2;
                } else if (vegetation > .55
                        && noise.random(c, r, 52) < config.vegetationDensity() * .055) {
                    type = flowers > .6 ? WorldObjectType.FLOWER_BUSH : WorldObjectType.BUSH;
                } else if (noise.sample(c * .11, r * .11, 60) > .53
                        && noise.random(c, r, 53) < config.rockDensity()) {
                    double size = noise.random(c, r, 54);
                    type =
                            size < .4
                                    ? WorldObjectType.ROCK_SMALL
                                    : size < .8
                                            ? WorldObjectType.ROCK_SMALL_MOSS
                                            : size < .9
                                                    ? WorldObjectType.ROCK_LARGE
                                                    : WorldObjectType.ROCK_LARGE_MOSS;
                } else if (vegetation > .53
                        && noise.random(c, r, 56) < config.detailDensity() * .022) {
                    type = STUMPS[(int) (noise.random(c, r, 156) * STUMPS.length)];
                } else if ((vegetation > .60 || noise.sample(c * .17, r * .17, 125) > .60)
                        && noise.random(c, r, 57) < config.detailDensity() * .065) {
                    type = MUSHROOMS[(int) (noise.random(c, r, 157) * MUSHROOMS.length)];
                } else if (flowers > .58 && noise.random(c, r, 58) < config.flowerDensity() * .25) {
                    int color = (int) (noise.value(c * .08, r * .08, 126) * FLOWERS.length);
                    type = FLOWERS[Math.min(FLOWERS.length - 1, color)];
                } else if (vegetation > .48
                        && noise.random(c, r, 59) < config.detailDensity() * .02) {
                    type = WorldObjectType.TWIG;
                }
                if (type != null && place(world, palette.footprint(type), occupied, c, r, tileSize))
                    placements.add(new GeneratedWorld.Placement(type, c, r));
            }
        // Meadow follows the same density field, with a separate small-scale flower-patch field.
        for (int r = 0; r < config.height(); r++)
            for (int c = 0; c < config.width(); c++) {
                if (occupied[c][r]
                        || !meadowFits(world, c, r)
                        || world.terrain(c, r) != Terrain.GRASS) continue;
                double field = noise.sample(c * .055, r * .055, 70);
                int fieldType = 0;
                if (config.fieldDensity() > 0) {
                    if (field < .31 + config.fieldDensity() * .12)
                        fieldType = GrassLayer.FIELD_GOLDEN;
                    else if (field > .69 - config.fieldDensity() * .13)
                        fieldType =
                                noise.random(c, r, 71) < .40
                                        ? GrassLayer.FIELD_DARK
                                        : GrassLayer.FIELD_GREEN;
                }
                if (fieldType != 0) {
                    world.meadow(c, r, fieldType, 2 + (noise.random(c, r, 72) < .4 ? 1 : 0));
                    continue;
                }
                // Ordinary grass is baked into the ground variants; only body-height fields use
                // draw layers.
            }
        world.objects(placements);
    }

    /** Evaluates the meadow fits condition for ObjectGenerator. */
    private boolean meadowFits(GeneratedWorld world, int c, int r) {
        // Existing bend frames extend beyond a tuft's anchor: keep a one-tile buffer at
        // edges/clearings.
        if (c < 1 || r < 1 || c >= world.config.width() - 1 || r >= world.config.height() - 1)
            return false;
        for (int dy = -2; dy <= 2; dy++)
            for (int dx = -1; dx <= 1; dx++) {
                int x = c + dx, y = r + dy;
                if (x < 0
                        || y < 0
                        || x >= world.config.width()
                        || y >= world.config.height()
                        || world.reserved(x, y)
                        || world.terrain(x, y) != Terrain.GRASS) return false;
            }
        return true;
    }

    /** Evaluates the place condition for ObjectGenerator. */
    private boolean place(
            GeneratedWorld world,
            WorldPalette.Footprint footprint,
            boolean[][] occupied,
            int c,
            int r,
            int ts) {
        int x = c * ts + ts / 2, y = (r + 1) * ts - 4; // Matches WorldObject's foot anchor.
        Rectangle image = new Rectangle(footprint.image());
        image.translate(x, y);
        Rectangle solid = new Rectangle(footprint.solid());
        solid.translate(x, y);
        Rectangle boundary =
                new Rectangle(0, 0, world.config.width() * ts, world.config.height() * ts);
        boolean blocks = solid.width > 0 && solid.height > 0;
        if (!boundary.contains(image) || blocks && !boundary.contains(solid)) return false;
        // Reserve actual sprite bounds around spawn/corridor so even tree crowns leave them
        // visible.
        int left = Math.floorDiv(image.x, ts), right = Math.floorDiv(image.x + image.width - 1, ts);
        int top = Math.floorDiv(image.y, ts),
                bottom = Math.floorDiv(image.y + image.height - 1, ts);
        for (int row = top; row <= bottom; row++)
            for (int col = left; col <= right; col++) if (world.reserved(col, row)) return false;
        // Half a tile of clearance around every blocking box. A tile occupancy grid is
        // conservative,
        // prevents all solid overlap, and leaves gaps without a quadratic scan of existing objects.
        if (blocks) solid.grow(ts / 2, ts / 2);
        else solid = new Rectangle(x - ts / 4, y - ts / 4, ts / 2, ts / 2);
        left = Math.max(0, Math.floorDiv(solid.x, ts));
        right = Math.min(world.config.width() - 1, Math.floorDiv(solid.x + solid.width - 1, ts));
        top = Math.max(0, Math.floorDiv(solid.y, ts));
        bottom = Math.min(world.config.height() - 1, Math.floorDiv(solid.y + solid.height - 1, ts));
        for (int row = top; row <= bottom; row++)
            for (int col = left; col <= right; col++)
                if (occupied[col][row]
                        || world.reserved(col, row)
                        || world.terrain(col, row) != Terrain.GRASS) return false;
        for (int row = top; row <= bottom; row++)
            for (int col = left; col <= right; col++) occupied[col][row] = true;
        return true;
    }
}
