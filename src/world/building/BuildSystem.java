package world.building;

import art.BuildGeometry;
import art.BuildTextures;

import building.BuildElements;
import building.BuildingBlueprint;
import building.BuildingLayout;
import building.BuildingRenderer;
import building.Decoration;
import building.PropCatalog;

import entity.Entity;
import entity.Player;

import main.*;

import world.InnInterior;
import world.WorldObject;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/** Two independent tile layers. All mutations run on the game thread. */
public final class BuildSystem {
    public enum Kind {
        FLOOR,
        WALL,
        DOOR,
        STAIRS,
        PROP
    }

    /** Evaluates the ground condition for BuildSystem. */
    private static boolean ground(Kind kind) {
        return kind == Kind.FLOOR || kind == Kind.STAIRS;
    }

    private record Cell(int col, int row) {}

    private record Placement(Kind kind, boolean vertical, boolean open, String element) {}

    private final Gamepanel gp;
    private final Set<Cell> floors = new HashSet<>();
    private final Map<Cell, String> floorElements = new HashMap<>();
    private final Set<Cell> groundCells = new HashSet<>();
    private final Set<Cell> clearedCells = new HashSet<>();
    private final Map<Long, BuildingRenderer> surfaces = new LinkedHashMap<>(8, .75f, true);
    private Map<Cell, BuildingLayout.Tile> renderState = new HashMap<>();
    private Map<Cell, String> renderFloors = new HashMap<>();
    private final List<Decoration> decorations = new ArrayList<>();
    private final Map<String, Furnishing> furniture = new HashMap<>();
    private final Map<String, Decoration> indexedDecorations = new HashMap<>();
    private final world.spatial.SpatialIndex<Decoration> propIndex;
    private boolean propHeld, propRightHeld;
    private final Map<Cell, Piece> structures = new HashMap<>();
    private BuildTextures textures;
    public boolean active;
    private Kind selected = Kind.FLOOR;
    private String selectedElement = "floor";
    private boolean preferredVertical;
    private int propRotation;
    private boolean dirty;
    private String feedback = "";
    private int feedbackTicks;

    private static final int[] DC = {0, 1, 0, -1}, DR = {-1, 0, 1, 0};

    /** Creates a BuildSystem instance and initializes the state required by its collaborators. */
    public BuildSystem(Gamepanel gp) {
        this.gp = gp;
        propIndex = new world.spatial.SpatialIndex<>(gp.tileSize * 4);
    }

    /** Performs the prepare operation for BuildSystem. */
    public void prepare() {
        if (textures == null) textures = new BuildTextures(gp.tileSize);
    }

    /** Evaluates the overworld condition for BuildSystem. */
    private boolean overworld() {
        return !gp.isNetworked && gp.quests != null && !gp.quests.inDungeon();
    }

    /** Evaluates the available condition for BuildSystem. */
    public boolean available() {
        Player p = gp.localPlayer();
        return overworld() && p != null && !p.inWell && !InnInterior.contains(gp, p);
    }

    /** Evaluates the editable condition for BuildSystem. */
    private boolean editable() {
        return available() && !gp.isNetworked;
    }

    /** Performs the start operation for BuildSystem. */
    public void start(Kind kind) {
        if (!editable())
            throw new IllegalStateException(
                    "Building placement requires a single-player overworld.");
        gp.grassEditor.stop();
        select(kind);
        active = true;
    }

    /** Catalog ids allow future materials to be used by the game and standalone Builder. */
    public void start(String element) {
        if (PropCatalog.has(element)) {
            if (!editable())
                throw new IllegalStateException(
                        "Furnishing placement requires a single-player overworld.");
            prepare();
            textures.prop(element, false, 0);
            gp.grassEditor.stop();
            selected = Kind.PROP;
            selectedElement = element;
            active = true;
            propHeld = propRightHeld = false;
            return;
        }
        BuildElements.Element definition = BuildElements.get(element);
        start(Kind.valueOf(definition.shape().name()));
        selectedElement = definition.id();
    }

    /** Performs the select operation for BuildSystem. */
    private void select(Kind kind) {
        selected = kind;
        selectedElement = kind.name().toLowerCase(Locale.ROOT);
    }

    /** Performs the stop operation for BuildSystem. */
    public void stop() {
        active = false;
    }

    /** Returns the current false value. */
    public boolean handleKey(int code) {
        if (!active) return false;
        switch (code) {
            case KeyEvent.VK_1 -> select(Kind.FLOOR);
            case KeyEvent.VK_2 -> select(Kind.WALL);
            case KeyEvent.VK_3 -> select(Kind.DOOR);
            case KeyEvent.VK_R -> {
                if (selected == Kind.PROP && PropCatalog.get(selectedElement).rotatable())
                    propRotation = (propRotation + 1) % 4;
                else if (!ground(selected)
                        && !(selected == Kind.PROP && selectedElement.startsWith("table_")))
                    preferredVertical = !preferredVertical;
            }
            case KeyEvent.VK_ESCAPE -> stop();
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Advances this component for one simulation tick. */
    public void update() {
        if (feedbackTicks > 0) feedbackTicks--;
        if (!active) return;
        if (!editable()) {
            stop();
            return;
        }
        if (!canUseMouse()) {
            propHeld = gp.mouseH.leftPressed;
            propRightHeld = gp.mouseH.rightPressed;
            return;
        }
        if (selected == Kind.PROP) {
            updateProps();
            textures.prop(selectedElement, preferredVertical, propRotation);
            return;
        }
        propHeld = propRightHeld = false;
        Cell c = cursor();
        if (c == null) return;
        if (gp.mouseH.rightPressed) {
            erase(c);
            return;
        }
        if (!gp.mouseH.leftPressed) return;
        String invalid = placementError(c, selected);
        if (invalid != null) {
            feedback = invalid;
            feedbackTicks = 60;
            return;
        }
        Map<String, Integer> costs = new HashMap<>(), refunds = new HashMap<>();
        if (ground(selected)) {
            String old = floors.contains(c) ? floorElements.getOrDefault(c, "floor") : null;
            if (!selectedElement.equals(old)) {
                costs.merge(selectedElement, 1, Integer::sum);
                if (old != null) refunds.merge(old, 1, Integer::sum);
            }
        } else
            for (var entry : placementPlan(c, selected).entrySet()) {
                Piece old = structures.get(entry.getKey());
                String next = entry.getValue().element;
                if (old == null || !old.element.equals(next)) {
                    costs.merge(next, 1, Integer::sum);
                    if (old != null) refunds.merge(old.element, 1, Integer::sum);
                }
            }
        if (!gp.crafting.buildingTransaction(costs, refunds)) return;
        boolean modified = false;
        if (ground(selected)) {
            if (!floors.contains(c)
                    || !selectedElement.equals(floorElements.getOrDefault(c, "floor")))
                gp.placementEffects.tile(c.col, c.row, true);
            modified = floors.add(c);
            modified |= !selectedElement.equals(floorElements.put(c, selectedElement));
            if (modified || !clearedCells.contains(c)) {
                clearVegetation(c);
                modified = true;
            }
        } else {
            for (var entry : placementPlan(c, selected).entrySet()) {
                Cell at = entry.getKey();
                Placement next = entry.getValue();
                Piece old = structures.get(at);
                if (old == null
                        || old.vertical != next.vertical
                        || !old.element.equals(next.element)) {
                    if (old == null || !old.element.equals(next.element))
                        gp.placementEffects.tile(at.col, at.row, false);
                    structures.put(
                            at, new Piece(at, next.kind, next.open, next.vertical, next.element));
                    modified = true;
                }
                if (!clearedCells.contains(at)) {
                    clearVegetation(at);
                    modified = true;
                }
            }
        }
        if (modified) changed();
    }

    /** Resolves the placement plan result used by BuildSystem. */
    private Map<Cell, Placement> placementPlan(Cell c, Kind kind) {
        Piece old = structures.get(c);
        String element = kind == selected ? selectedElement : kind.name().toLowerCase(Locale.ROOT);
        Map<Cell, Placement> result = new LinkedHashMap<>();
        BuildingLayout.plan(
                        at -> {
                            Piece p = structures.get(new Cell(at.col(), at.row()));
                            return p == null
                                    ? null
                                    : new BuildingLayout.Tile(p.element, p.vertical, p.open);
                        },
                        shared(c),
                        new BuildingLayout.Tile(
                                element,
                                preferredVertical,
                                kind == Kind.DOOR && old != null && old.open))
                .forEach((at, p) ->
                        result.put(
                                new Cell(at.col(), at.row()),
                                new Placement(
                                        Kind.valueOf(p.shape().name()),
                                        p.vertical(),
                                        p.open(),
                                        p.element())));
        return result;
    }

    /** Resolves the shared result used by BuildSystem. */
    private static BuildingLayout.Cell shared(Cell c) {
        return new BuildingLayout.Cell(c.col, c.row);
    }

    /** Reports whether this component can use mouse for BuildSystem. */
    private boolean canUseMouse() {
        Player p = gp.localPlayer();
        return p != null
                && !p.isDead
                && !p.merchantOpen
                && !gp.inventoryOpen
                && !gp.ui.game.blocksWorld()
                && !gp.ui.game.pointerOverHotbar()
                && !gp.optionsOpen
                && !gp.paused
                && !gp.keyH.enteringChat
                && !p.inDialogue
                && !gp.quests.isBossIntro();
    }

    /** Resolves the cursor result used by BuildSystem. */
    private Cell cursor() {
        int x = gp.mouseH.getScaledX(), y = gp.mouseH.getScaledY();
        if (x < 0 || y < 0) return null;
        Player p = gp.localPlayer();
        return new Cell(
                Math.floorDiv(x - p.screenX + p.worldX, gp.tileSize),
                Math.floorDiv(y - p.screenY + p.worldY, gp.tileSize));
    }

    /** Evaluates the inside condition for BuildSystem. */
    private boolean inside(Cell c) {
        return c.col >= 0 && c.row >= 0 && c.col < gp.maxWorldCol && c.row < gp.maxWorldRow;
    }

    /** Resolves the placement error result used by BuildSystem. */
    private String placementError(Cell c, Kind kind) {
        String invalid = terrainError(c);
        if (invalid != null) return invalid;
        if (ground(kind)) return null;
        Piece old = structures.get(c);
        if (old != null && old.kind != kind) return "Remove the existing wall or door first";
        Map<Cell, Placement> plan = placementPlan(c, kind);
        for (Cell at : plan.keySet()) {
            invalid = terrainError(at);
            if (invalid != null) return at.equals(c) ? invalid : "Corner: " + invalid;
        }
        return safeStructureChanges(plan) ? null : "An actor is standing in the new wall footprint";
    }

    /** Resolves the terrain error result used by BuildSystem. */
    private String terrainError(Cell c) {
        if (!inside(c)) return "Outside the world";
        if (world.TerrainHeightResolver.active(gp) && !gp.generatedWorld.lowland(c.col, c.row))
            return "Build on connected lowland; plateaus need a future traversal route";
        if (!gp.worldConfig.procedural() && InnInterior.isTile(c.col, c.row))
            return "Use clear overworld ground";
        if (gp.tileM.isSolid(c.col, c.row)) return "Needs walkable ground";
        if (gp.tileM.terrain(c.col, c.row) == art.Terrain.STEPS) return "Cannot build on stairs";
        int ts = gp.tileSize, x = c.col * ts, y = c.row * ts;
        int level = gp.tileM.buildingElevation(c.col, c.row);
        for (int i = 0; i < 4; i++) {
            Cell n = new Cell(c.col + DC[i], c.row + DR[i]);
            if (inside(n) && gp.tileM.buildingElevation(n.col, n.row) != level)
                return "Keep buildings away from cliffs or stairs";
        }
        if (gp.crafting.blocks(new Rectangle(x, y, ts, ts))) return "Blocked by crafting station";
        for (WorldObject o : gp.worldObjects)
            if (!o.clearedByBuildingAt(c.col, c.row) && o.blocksArea(x, y, ts, ts))
                return "Blocked by scenery";
        for (Entity e : gp.interactable)
            if (e != null && !clearableTile(e) && e.collision && bounds(e).intersects(x, y, ts, ts))
                return "Blocked by scenery";
        return null;
    }

    /** Performs the erase operation for BuildSystem. */
    private void erase(Cell c) {
        // Erase the selected layer so removing a wall never destroys its floor.
        if (!ground(selected) && !safeStructureChange(c, null, false, false)) {
            feedback = "An actor is standing in a neighboring connection";
            feedbackTicks = 60;
            return;
        }
        String recovered =
                ground(selected)
                        ? (floors.contains(c) ? floorElements.getOrDefault(c, "floor") : null)
                        : (structures.containsKey(c) ? structures.get(c).element : null);
        if (recovered != null && !gp.crafting.buildingTransaction(Map.of(), Map.of(recovered, 1)))
            return;
        boolean removed = ground(selected) ? floors.remove(c) : structures.remove(c) != null;
        if (removed) {
            if (ground(selected)) floorElements.remove(c);
            changed();
        }
    }

    /** Performs the changed operation for BuildSystem. */
    private void changed() {
        dirty = true;
        gp.interactions.clear();
        refreshGround();
    }

    /** Refreshes the ground for BuildSystem. */
    private void refreshGround() {
        Map<Cell, BuildingLayout.Tile> next = new HashMap<>();
        for (Cell c : floors)
            next.put(
                    c,
                    new BuildingLayout.Tile(floorElements.getOrDefault(c, "floor"), false, false));
        structures.forEach(
                (c, p) -> next.put(c, new BuildingLayout.Tile(p.element, p.vertical, p.open)));
        Set<Cell> changed = new HashSet<>(renderState.keySet());
        changed.addAll(next.keySet());
        changed.removeIf(c -> Objects.equals(renderState.get(c), next.get(c)));
        Set<Cell> floorChanges = new HashSet<>(renderFloors.keySet());
        floorChanges.addAll(floorElements.keySet());
        floorChanges.removeIf(c -> Objects.equals(renderFloors.get(c), floorElements.get(c)));
        changed.addAll(floorChanges);
        renderFloors = new HashMap<>(floorElements);
        for (Cell cell : changed)
            for (int dr = -3; dr <= 3; dr++)
                for (int dc = -3; dc <= 3; dc++)
                    surfaces.remove(surfaceKey(cell.col + dc, cell.row + dr));
        renderState = next;
        groundCells.clear();
        groundCells.addAll(floors);
        for (Cell c : structures.keySet())
            if (!groundCells.contains(c)) {
                search:
                for (int dr = -1; dr <= 1; dr++)
                    for (int dc = -1; dc <= 1; dc++)
                        if (floors.contains(new Cell(c.col + dc, c.row + dr))) {
                            groundCells.add(c);
                            break search;
                        }
            }
        refreshDecorations();
        prepareVisibleCaches();
    }

    /** Refreshes the decorations for BuildSystem. */
    private void refreshDecorations() {
        Map<String, Decoration> next = new HashMap<>();
        for (Decoration d : decorations) next.put(d.id(), d);
        var iterator = indexedDecorations.entrySet().iterator();
        while (iterator.hasNext()) {
            var e = iterator.next();
            if (!Objects.equals(next.get(e.getKey()), e.getValue())) {
                propIndex.remove(e.getValue());
                furniture.remove(e.getKey());
                iterator.remove();
            }
        }
        for (Decoration d : decorations)
            if (!indexedDecorations.containsKey(d.id())) {
                var visual =
                        PropCatalog.get(d.element())
                                .visualBounds(d.x(), d.y(), d.flipped(), d.rotation());
                double scale = gp.tileSize / 48.0;
                Rectangle box =
                        new java.awt.geom.Rectangle2D.Double(
                                        visual.getX() * scale,
                                        visual.getY() * scale,
                                        visual.getWidth() * scale,
                                        visual.getHeight() * scale)
                                .getBounds();
                box.add(propBounds(d));
                textures.prop(d.element(), d.flipped(), d.rotation());
                propIndex.add(d, box);
                indexedDecorations.put(d.id(), d);
                if (!PropCatalog.get(d.element()).ground())
                    furniture.put(d.id(), new Furnishing(d));
            }
    }

    /** Resolves the surface key result used by BuildSystem. */
    private long surfaceKey(int col, int row) {
        return world.spatial.SpatialIndex.key(
                Math.floorDiv(col, world.chunk.ChunkConfig.BUILD_CACHE_SIZE),
                Math.floorDiv(row, world.chunk.ChunkConfig.BUILD_CACHE_SIZE));
    }

    /** Resolves the surface for result used by BuildSystem. */
    private BuildingRenderer surfaceFor(Cell c) {
        return surfaces.get(surfaceKey(c.col, c.row));
    }

    /** Geometry and shadow images are prepared at update boundaries, never in the render loop. */
    public void prepareVisibleCaches() {
        if (gp.localPlayer() == null
                || textures == null
                || (floors.isEmpty() && structures.isEmpty())) return;
        int size = world.chunk.ChunkConfig.BUILD_CACHE_SIZE, ts = gp.tileSize;
        Rectangle view = gp.chunks.camera(4);
        Set<Long> visible = new HashSet<>();
        for (int y = Math.floorDiv(view.y, ts * size);
                y <= Math.floorDiv(view.y + view.height, ts * size);
                y++)
            for (int x = Math.floorDiv(view.x, ts * size);
                    x <= Math.floorDiv(view.x + view.width, ts * size);
                    x++) {
                long key = world.spatial.SpatialIndex.key(x, y);
                visible.add(key);
                if (surfaces.containsKey(key)) continue;
                BuildingLayout layout = new BuildingLayout();
                // The three-cell halo includes foundation, neighbor connections, tall wall
                // overhangs and shadows.
                for (int row = y * size - 3; row < (y + 1) * size + 3; row++)
                    for (int col = x * size - 3; col < (x + 1) * size + 3; col++) {
                        Cell c = new Cell(col, row);
                        if (floors.contains(c))
                            layout.put(
                                    shared(c),
                                    new BuildingLayout.Tile(
                                            floorElements.getOrDefault(c, "floor"), false, false));
                        Piece piece = structures.get(c);
                        if (piece != null)
                            layout.put(
                                    shared(c),
                                    new BuildingLayout.Tile(
                                            piece.element, piece.vertical, piece.open));
                    }
                surfaces.put(key, new BuildingRenderer(layout, ts, textures));
            }
        int limit = Math.max(8, visible.size());
        while (surfaces.size() > limit) {
            Long oldest = null;
            for (long key : surfaces.keySet())
                if (!visible.contains(key)) {
                    oldest = key;
                    break;
                }
            if (oldest == null) break;
            surfaces.remove(oldest);
        }
    }

    /** Evaluates the clearable tile condition for BuildSystem. */
    private static boolean clearableTile(Entity e) {
        return e instanceof tiles_interactive.IT_drytree || e instanceof tiles_interactive.IT_trunk;
    }

    /** Evaluates the scenery cleared condition for BuildSystem. */
    public boolean sceneryCleared(world.WorldObject object) {
        for (Cell cell : clearedCells)
            if (object.clearedByBuildingAt(cell.col, cell.row)) return true;
        return false;
    }

    /** Clears the vegetation for BuildSystem. */
    private void clearVegetation(Cell c) {
        clearedCells.add(c);
        gp.grass.paint(c.col, c.row, world.GrassLayer.NONE, 1);
        gp.worldObjects.removeIf(o -> o.clearedByBuildingAt(c.col, c.row));
        int ts = gp.tileSize;
        for (int i = 0; i < gp.interactable.length; i++) {
            Entity e = gp.interactable[i];
            if (e != null
                    && clearableTile(e)
                    && Math.floorDiv(e.worldX, ts) == c.col
                    && Math.floorDiv(e.worldY, ts) == c.row) gp.interactable[i] = null;
        }
    }

    /** Evaluates the ground at condition for BuildSystem. */
    private boolean groundAt(Cell c) {
        return groundCells.contains(c);
    }

    /** Evaluates the covers ground condition for BuildSystem. */
    public boolean coversGround(int col, int row) {
        return overworld()
                && (!groundCells.isEmpty() || !structures.isEmpty())
                && (groundAt(new Cell(col, row)) || structures.containsKey(new Cell(col, row)));
    }

    /** Resolves the shared structure result used by BuildSystem. */
    private BuildingLayout.Tile sharedStructure(BuildingLayout.Cell cell) {
        Piece p = structures.get(new Cell(cell.col(), cell.row()));
        return p == null ? null : new BuildingLayout.Tile(p.element, p.vertical, p.open);
    }

    /** Resolves the planned structures result used by BuildSystem. */
    private java.util.function.Function<BuildingLayout.Cell, BuildingLayout.Tile> plannedStructures(
            Map<Cell, Placement> changes) {
        return cell -> {
            Placement p = plannedPiece(new Cell(cell.col(), cell.row()), changes);
            return p == null ? null : new BuildingLayout.Tile(p.element, p.vertical, p.open);
        };
    }

    /** Resolves the footprint result used by BuildSystem. */
    private List<Rectangle> footprint(Cell c, Kind kind, boolean vertical, boolean open) {
        Piece current = structures.get(c);
        String element = current != null ? current.element : kind.name().toLowerCase(Locale.ROOT);
        return footprint(c, new Placement(kind, vertical, open, element), this::sharedStructure);
    }

    /** Resolves the footprint result used by BuildSystem. */
    private List<Rectangle> footprint(
            Cell c,
            Placement p,
            java.util.function.Function<BuildingLayout.Cell, BuildingLayout.Tile> lookup) {
        List<Rectangle> result = new ArrayList<>();
        for (Rectangle r :
                BuildingLayout.footprint(
                        shared(c),
                        new BuildingLayout.Tile(p.element, p.vertical, p.open),
                        lookup)) {
            Rectangle scaled = BuildGeometry.scale(r, gp.tileSize);
            scaled.translate(c.col * gp.tileSize, c.row * gp.tileSize);
            result.add(scaled);
        }
        return result;
    }

    /** Resolves the shape result used by BuildSystem. */
    private Area shape(Cell c, Kind kind, boolean vertical, boolean open) {
        Area area = new Area();
        if (kind != null)
            for (Rectangle r : footprint(c, kind, vertical, open)) area.add(new Area(r));
        return area;
    }

    /** Resolves the shape result used by BuildSystem. */
    private Area shape(Cell c, Placement p, Map<Cell, Placement> changes) {
        Area area = new Area();
        for (Rectangle r : footprint(c, p, plannedStructures(changes))) area.add(new Area(r));
        return area;
    }

    /** Resolves the planned piece result used by BuildSystem. */
    private Placement plannedPiece(Cell cell, Map<Cell, Placement> changes) {
        if (changes.containsKey(cell)) return changes.get(cell);
        Piece p = structures.get(cell);
        return p == null ? null : new Placement(p.kind, p.vertical, p.open, p.element);
    }

    /** Evaluates the safe structure change condition for BuildSystem. */
    private boolean safeStructureChange(Cell cell, Kind kind, boolean vertical, boolean open) {
        return safeStructureChanges(
                Collections.singletonMap(
                        cell,
                        kind == null
                                ? null
                                : new Placement(
                                        kind,
                                        vertical,
                                        open,
                                        kind.name().toLowerCase(Locale.ROOT))));
    }

    /** Evaluates the safe structure changes condition for BuildSystem. */
    private boolean safeStructureChanges(Map<Cell, Placement> changes) {
        Set<Cell> affected = new HashSet<>(changes.keySet());
        for (Cell c : changes.keySet())
            for (int dr = -2; dr <= 2; dr++)
                for (int dc = -2; dc <= 2; dc++) affected.add(new Cell(c.col + dc, c.row + dr));
        for (Cell c : affected) {
            Piece old = structures.get(c);
            Placement next = plannedPiece(c, changes);
            if (next == null) continue;
            Area added = shape(c, next, changes);
            if (old != null) added.subtract(shape(c, old.kind, old.vertical, old.open));
            if (!added.isEmpty() && occupied(added)) return false;
        }
        return true;
    }

    /** Resolves the bounds result used by BuildSystem. */
    private static Rectangle bounds(Entity e) {
        return new Rectangle(
                e.worldX + e.solidArea.x,
                e.worldY + e.solidArea.y,
                e.solidArea.width,
                e.solidArea.height);
    }

    /** Evaluates the occupied condition for BuildSystem. */
    private boolean occupied(Shape area) {
        for (Player p : gp.players)
            if (p != null && !p.inWell && !p.isDead && area.intersects(bounds(p))) return true;
        for (Entity e : gp.monster)
            if (e != null && e.alive && !e.dying && area.intersects(bounds(e))) return true;
        for (Entity e : gp.npc) if (e != null && area.intersects(bounds(e))) return true;
        for (Decoration d : decorations) {
            var p = PropCatalog.get(d.element());
            if (p.collisionWidth() > 0 && area.intersects(propBounds(d))) return true;
        }
        return false;
    }

    /** Evaluates the neighbor condition for BuildSystem. */
    private boolean neighbor(Cell c, int i, boolean floor) {
        Cell n = new Cell(c.col + DC[i], c.row + DR[i]);
        return floor ? groundAt(n) : structures.containsKey(n);
    }

    /** Resolves the mask result used by BuildSystem. */
    private int mask(Cell c, boolean floor) {
        int m = 0;
        for (int i = 0; i < 4; i++) if (neighbor(c, i, floor)) m |= 1 << i;
        return m;
    }

    /** Evaluates the blocks area condition for BuildSystem. */
    public boolean blocksArea(int x, int y, int width, int height) {
        return blocksAreaExcept(x, y, width, height, null);
    }

    /** Evaluates the blocks area except condition for BuildSystem. */
    public boolean blocksAreaExcept(int x, int y, int width, int height, Object ignored) {
        if (!overworld()
                || width <= 0
                || height <= 0
                || structures.isEmpty() && decorations.isEmpty()) return false;
        int ts = gp.tileSize;
        for (int row = Math.floorDiv(y, ts) - 1;
                row <= Math.floorDiv(y + height - 1, ts) + 1;
                row++)
            for (int col = Math.floorDiv(x, ts) - 1;
                    col <= Math.floorDiv(x + width - 1, ts) + 1;
                    col++) {
                Cell c = new Cell(col, row);
                Piece p = structures.get(c);
                if (p != null && p != ignored)
                    for (Rectangle r : footprint(c, p.kind, p.vertical, p.open))
                        if (r.intersects(x, y, width, height)) return true;
            }
        for (Decoration d : propIndex.query(new Rectangle(x, y, width, height))) {
            var p = PropCatalog.get(d.element());
            if (p.collisionWidth() > 0 && propBounds(d).intersects(x, y, width, height))
                return true;
        }
        return false;
    }

    /** Returns true even for a blocked close, so E is consumed instead of attacking. */
    public boolean interact(Player player, boolean apply) {
        if (!overworld()
                || player.inWell
                || player.isDead
                || player.inDialogue
                || player.merchantOpen
                || gp.quests.isBossIntro()
                || InnInterior.contains(gp, player)) return false;
        var target = gp.interactions.resolve(player);
        return target != null
                && target.token() instanceof Piece piece
                && piece.kind == Kind.DOOR
                && toggleDoor(piece, apply);
    }

    /** Evaluates the toggle door condition for BuildSystem. */
    private boolean toggleDoor(Piece piece, boolean apply) {
        boolean clear = safeStructureChange(piece.cell, piece.kind, piece.vertical, false);
        if (apply && (!piece.open || clear)) {
            piece.open = !piece.open;
            changed();
        }
        return true;
    }

    /** Collects the targets for BuildSystem. */
    public void collectTargets(interaction.InteractionTargetResolver resolver) {
        if (!available()) return;
        Rectangle front = resolver.validator.frontBounds(resolver.player());
        Piece piece =
                structures.get(
                        new Cell(
                                Math.floorDiv(front.x, gp.tileSize),
                                Math.floorDiv(front.y, gp.tileSize)));
        if (piece != null && piece.kind == Kind.DOOR) {
            Rectangle footprint =
                    new Rectangle(
                            piece.cell.col * gp.tileSize,
                            piece.cell.row * gp.tileSize,
                            gp.tileSize,
                            gp.tileSize);
            resolver.offer(
                    piece,
                    "door:" + piece.cell.col + ":" + piece.cell.row,
                    footprint,
                    3,
                    () -> toggleDoor(piece, true));
        }
    }

    /** Evaluates the visible condition for BuildSystem. */
    private boolean visible(Cell c) {
        Player p = gp.localPlayer();
        int x = c.col * gp.tileSize - p.worldX + p.screenX,
                y = c.row * gp.tileSize - p.worldY + p.screenY;
        return x > -gp.tileSize
                && x < gp.screenWidth
                && y > -gp.tileSize
                && y < gp.screenHeight + BuildGeometry.IMAGE_HEIGHT * gp.tileSize / 48;
    }

    /** Draws the floors for BuildSystem. */
    public void drawFloors(Graphics2D g) {
        if (!overworld()) return;
        Rectangle view = gp.chunks.camera(3);
        int ts = gp.tileSize;
        for (int row = Math.floorDiv(view.y, ts);
                row <= Math.floorDiv(view.y + view.height, ts);
                row++)
            for (int col = Math.floorDiv(view.x, ts);
                    col <= Math.floorDiv(view.x + view.width, ts);
                    col++) {
                Cell c = new Cell(col, row);
                if (!groundCells.contains(c) || !visible(c)) continue;
                BuildingRenderer surface = surfaceFor(c);
                if (surface == null) continue;
                Player p = gp.localPlayer();
                Graphics2D tile = (Graphics2D) g.create();
                tile.translate(
                        c.col * gp.tileSize - p.worldX + p.screenX,
                        c.row * gp.tileSize - p.worldY + p.screenY);
                gp.placementEffects.floor(tile, c.col, c.row);
                surface.drawFloor(tile, shared(c));
                tile.dispose();
            }
        Player camera = gp.localPlayer();
        Graphics2D objects = (Graphics2D) g.create();
        objects.translate(camera.screenX - camera.worldX, camera.screenY - camera.worldY);
        for (Decoration decoration : propIndex.query(gp.chunks.camera(4)))
            if (PropCatalog.get(decoration.element()).ground()) {
                Graphics2D visual = (Graphics2D) objects.create();
                gp.placementEffects.object(
                        visual,
                        decoration.id(),
                        decoration.x() * gp.tileSize / 48,
                        decoration.y() * gp.tileSize / 48);
                BuildingRenderer.drawDecoration(visual, decoration, gp.tileSize, textures);
                visual.dispose();
            }
        objects.dispose();
    }

    /** Adds the draw layers for BuildSystem. */
    public void addDrawLayers(List<Entity> layers) {
        if (!overworld()) return;
        if (structures.size() < 256) {
            for (Piece p : structures.values()) if (visible(p.cell)) layers.add(p);
        } else {
            Rectangle view = gp.chunks.camera(4);
            int ts = gp.tileSize;
            for (int row = Math.floorDiv(view.y, ts);
                    row <= Math.floorDiv(view.y + view.height, ts);
                    row++)
                for (int col = Math.floorDiv(view.x, ts);
                        col <= Math.floorDiv(view.x + view.width, ts);
                        col++) {
                    Piece p = structures.get(new Cell(col, row));
                    if (p != null && visible(p.cell)) layers.add(p);
                }
        }
        for (Decoration d : propIndex.query(gp.chunks.camera(4))) {
            Furnishing visual = furniture.get(d.id());
            if (visual != null) layers.add(visual);
        }
    }

    /** Renders this component into the supplied Java2D graphics context. */
    private void draw(Graphics2D g, Cell c, BufferedImage image) {
        Player p = gp.localPlayer();
        int ts = gp.tileSize;
        g.drawImage(
                image,
                c.col * ts - p.worldX + p.screenX,
                c.row * ts - p.worldY + p.screenY + ts - image.getHeight(),
                null);
    }

    private final class Piece extends Entity {
        final Cell cell;
        final Kind kind;
        final boolean vertical;
        final String element;
        boolean open;

        /** Creates a Piece instance and initializes the state required by its collaborators. */
        Piece(Cell cell, Kind kind, boolean open, boolean vertical) {
            this(cell, kind, open, vertical, kind.name().toLowerCase(Locale.ROOT));
        }

        /** Creates a Piece instance and initializes the state required by its collaborators. */
        Piece(Cell cell, Kind kind, boolean open, boolean vertical, String element) {
            super(BuildSystem.this.gp);
            this.cell = cell;
            this.kind = kind;
            this.open = kind == Kind.DOOR && open;
            this.vertical = vertical;
            this.element = element;
            worldX = cell.col * gp.tileSize;
            worldY = cell.row * gp.tileSize;
        }

        /** Resolves the sort y result used by Piece. */
        @Override
        public int sortY() {
            return worldY + gp.tileSize;
        }

        /** Renders this component into the supplied Java2D graphics context. */
        @Override
        public void draw(Graphics2D original) {
            BuildingRenderer surface = surfaceFor(cell);
            if (surface == null) return;
            Player camera = gp.localPlayer();
            Graphics2D g = (Graphics2D) original.create();
            g.translate(
                    worldX - camera.worldX + camera.screenX,
                    worldY - camera.worldY + camera.screenY);
            gp.placementEffects.structure(g, cell.col, cell.row);
            if (gp.interactions.selected(this)) surface.drawStructureOutline(g, shared(cell));
            surface.drawStructure(g, shared(cell));
            g.dispose();
        }
    }

    /** Draws the preview for BuildSystem. */
    public void drawPreview(Graphics2D original) {
        if (!active || !available() || !canUseMouse()) return;
        if (selected == Kind.PROP) {
            drawPropPreview(original);
            return;
        }
        Cell c = cursor();
        if (c == null) return;
        Graphics2D g = (Graphics2D) original.create();
        boolean valid = placementError(c, selected) == null;
        g.setComposite(AlphaComposite.SrcOver.derive(.55f));
        if (ground(selected))
            draw(g, c, textures.get(selectedElement, mask(c, true), false, false));
        else {
            Map<Cell, Placement> plan = placementPlan(c, selected);
            for (var entry : plan.entrySet()) {
                Placement p = entry.getValue();
                Cell at = entry.getKey();
                Player camera = gp.localPlayer();
                Graphics2D tile = (Graphics2D) g.create();
                tile.translate(
                        at.col * gp.tileSize - camera.worldX + camera.screenX,
                        at.row * gp.tileSize - camera.worldY + camera.screenY);
                BuildingRenderer.drawStructure(
                        tile,
                        shared(at),
                        new BuildingLayout.Tile(p.element, p.vertical, p.open),
                        plannedStructures(plan),
                        gp.tileSize,
                        textures);
                tile.dispose();
            }
        }
        g.setComposite(AlphaComposite.SrcOver);
        Player p = gp.localPlayer();
        int ts = gp.tileSize;
        g.setColor(valid ? new Color(157, 220, 169) : new Color(235, 120, 120));
        g.setStroke(new BasicStroke(2));
        g.drawRect(
                c.col * ts - p.worldX + p.screenX,
                c.row * ts - p.worldY + p.screenY,
                ts - 1,
                ts - 1);
        if (!ground(selected))
            for (Cell at : placementPlan(c, selected).keySet())
                if (!at.equals(c))
                    g.drawRect(
                            at.col * ts - p.worldX + p.screenX,
                            at.row * ts - p.worldY + p.screenY,
                            ts - 1,
                            ts - 1);
        g.dispose();
    }

    /** Draws the hud for BuildSystem. */
    public void drawHud(Graphics2D original, Font font) {
        if (!active || !available()) return;
        Graphics2D g = (Graphics2D) original.create();
        g.setFont(font.deriveFont(Font.BOLD, 13f));
        String[] lines = {
            "BUILD  "
                    + selectedElement
                    + (selected == Kind.PROP
                            ? (PropCatalog.get(selectedElement).rotatable()
                                    ? "  " + (propRotation * 90) + "°"
                                    : preferredVertical ? "  mirrored" : "")
                            : ground(selected) ? "" : preferredVertical ? "  vertical" : "  auto")
                    + (dirty ? "  (unsaved)" : ""),
            "1 Floor   2 Wall   3 Door   LMB place   RMB remove layer",
            "R rotate / flip furnishing   E open/close   /savebuild saves"
        };
        g.setColor(new Color(38, 43, 49, 225));
        g.fillRoundRect(16, gp.screenHeight - 140, 520, feedbackTicks > 0 ? 112 : 88, 10, 10);
        g.setColor(new Color(242, 233, 214));
        int y = gp.screenHeight - 117;
        for (String line : lines) {
            g.drawString(line, 28, y);
            y += 23;
        }
        if (feedbackTicks > 0) {
            g.setColor(new Color(245, 170, 145));
            g.drawString(feedback, 28, y);
        }
        g.dispose();
    }

    /** Game-thread API for authored presets and future village generation. Null means success. */
    public String placeBlueprint(BuildingBlueprint blueprint, int anchorCol, int anchorRow) {
        if (!editable()) return "Blueprint placement requires a single-player overworld";
        if (blueprint.layout().empty()) return "Blueprint has no elements";
        final List<BuildingBlueprint.Placed> entries;
        final int left, top;
        try {
            entries = blueprint.placedAt(anchorCol, anchorRow);
            left = Math.subtractExact(anchorCol, blueprint.anchor().col());
            top = Math.subtractExact(anchorRow, blueprint.anchor().row());
        } catch (ArithmeticException e) {
            return "Blueprint outside map";
        }
        if (left < 0
                || top < 0
                || (long) left + blueprint.width() > gp.maxWorldCol
                || (long) top + blueprint.height() > gp.maxWorldRow) return "Blueprint outside map";
        gp.chunks.ensureTiles(
                left - 4, top - 4, left + blueprint.width() + 3, top + blueprint.height() + 3);
        int level = gp.tileM.buildingElevation(left, top);
        // Reserve the whole authoring rectangle, including empty courtyard/entrance cells.
        for (int row = top; row < top + blueprint.height(); row++)
            for (int col = left; col < left + blueprint.width(); col++) {
                Cell c = new Cell(col, row);
                String error = terrainError(c);
                if (error != null) return error;
                if (gp.tileM.buildingElevation(col, row) != level)
                    return "Blueprint needs level ground";
                if (floors.contains(c) || structures.containsKey(c))
                    return "Blueprint overlaps an existing building";
                if (occupied(
                        new Rectangle(
                                col * gp.tileSize, row * gp.tileSize, gp.tileSize, gp.tileSize)))
                    return "An actor is inside the blueprint area";
            }
        Map<Cell, Placement> plan = new LinkedHashMap<>();
        for (BuildingBlueprint.Placed entry : entries)
            if (!entry.tile().shape().ground()) {
                BuildingLayout.Tile tile = entry.tile();
                Cell c = new Cell(entry.cell().col(), entry.cell().row());
                plan.put(
                        c,
                        new Placement(
                                Kind.valueOf(tile.shape().name()),
                                tile.vertical(),
                                tile.open(),
                                tile.element()));
            }
        if (!safeStructureChanges(plan)) return "An actor is standing in a neighboring connection";
        List<Decoration> props = blueprint.decorationsAt(anchorCol, anchorRow);
        for (Decoration d : props) {
            String error = propPlacementError(d, plan);
            if (error != null) return error;
        }
        for (int i = 0; i < props.size(); i++)
            for (int j = 0; j < i; j++) {
                var a = PropCatalog.get(props.get(i).element());
                var b = PropCatalog.get(props.get(j).element());
                if (a.collisionWidth() > 0
                        && b.collisionWidth() > 0
                        && propBounds(props.get(i)).intersects(propBounds(props.get(j))))
                    return "Blueprint furnishings overlap";
            }
        prepare();
        for (Decoration d : props) textures.prop(d.element(), d.flipped(), d.rotation());
        // Decode every required atlas before any world mutation.
        for (BuildingBlueprint.Placed entry : entries) {
            BuildingLayout.Tile tile = entry.tile();
            textures.get(tile.element(), 0, tile.open(), tile.vertical());
            if (tile.shape() == BuildElements.Shape.DOOR)
                textures.get(tile.element(), 0, !tile.open(), tile.vertical());
        }
        Map<String, Integer> required = new HashMap<>();
        for (BuildingBlueprint.Placed entry : entries)
            required.merge(entry.tile().element(), 1, Integer::sum);
        for (Decoration d : props) required.merge(d.element(), 1, Integer::sum);
        if (!gp.crafting.buildingTransaction(required, Map.of()))
            return "Missing crafted blueprint components";
        for (BuildingBlueprint.Placed entry : entries) {
            BuildingLayout.Tile tile = entry.tile();
            Cell c = new Cell(entry.cell().col(), entry.cell().row());
            gp.placementEffects.tile(c.col, c.row, tile.shape().ground());
            if (tile.shape().ground()) {
                floors.add(c);
                floorElements.put(c, tile.element());
            } else
                structures.put(
                        c,
                        new Piece(
                                c,
                                Kind.valueOf(tile.shape().name()),
                                tile.open(),
                                tile.vertical(),
                                tile.element()));
        }
        decorations.addAll(props);
        for (Decoration d : props)
            gp.placementEffects.object(
                    d.id(),
                    d.x() * gp.tileSize / 48,
                    d.y() * gp.tileSize / 48,
                    PropCatalog.get(d.element()).oriented(d.rotation()).width() * gp.tileSize / 48);
        for (int row = top; row < top + blueprint.height(); row++)
            for (int col = left; col < left + blueprint.width(); col++)
                clearVegetation(new Cell(col, row));
        changed();
        return null;
    }

    /** Resolves the decorations result used by BuildSystem. */
    public List<Decoration> decorations() {
        return overworld() ? Collections.unmodifiableList(decorations) : List.of();
    }

    /** Resolves the prop cursor result used by BuildSystem. */
    private double[] propCursor() {
        if (gp.mouseH.getScaledX() < 0 || gp.mouseH.getScaledY() < 0) return null;
        Player p = gp.localPlayer();
        double scale = 48.0 / gp.tileSize;
        return new double[] {
            (gp.mouseH.getScaledX() - p.screenX + p.worldX) * scale,
            (gp.mouseH.getScaledY() - p.screenY + p.worldY) * scale
        };
    }

    /** Resolves the prop bounds result used by BuildSystem. */
    private java.awt.geom.Rectangle2D propBounds(Decoration d) {
        var b = PropCatalog.get(d.element()).bounds(d.x(), d.y(), d.flipped(), d.rotation());
        double scale = gp.tileSize / 48.0;
        return new java.awt.geom.Rectangle2D.Double(
                b.getX() * scale, b.getY() * scale, b.getWidth() * scale, b.getHeight() * scale);
    }

    /** Resolves the prop placement error result used by BuildSystem. */
    private String propPlacementError(Decoration d, Map<Cell, Placement> plan) {
        if (!BuildingBlueprint.decorationInside(d, gp.maxWorldCol, gp.maxWorldRow))
            return "Furnishing outside the world";
        var def = PropCatalog.get(d.element());
        var bounds = def.placementBounds(d.x(), d.y(), d.flipped(), d.rotation());
        for (int row = (int) Math.floor(bounds.getMinY() / 48);
                row <= (int) Math.floor((bounds.getMaxY() - .001) / 48);
                row++)
            for (int col = (int) Math.floor(bounds.getMinX() / 48);
                    col <= (int) Math.floor((bounds.getMaxX() - .001) / 48);
                    col++) {
                String error = terrainError(new Cell(col, row));
                if (error != null) return error;
            }
        if (def.mounted()) {
            boolean wall = false;
            int col = (int) Math.floor(d.x() / 48), row = (int) Math.floor(d.y() / 48);
            for (int r = row; r <= row + 2; r++)
                for (int c = col - 1; c <= col + 1; c++) {
                    Placement p = plannedPiece(new Cell(c, r), plan);
                    if (p != null
                            && p.kind == Kind.WALL
                            && d.x() >= c * 48
                            && d.x() <= (c + 1) * 48
                            && d.y() >= r * 48 + 48 - BuildGeometry.HEIGHT
                            && d.y() <= (r + 1) * 48) wall = true;
                }
            if (!wall) return "Place the lamp against a wall";
        }
        if (def.collisionWidth() > 0) {
            var box = propBounds(d);
            if (occupied(box)) return "Furnishing overlaps an actor or another furnishing";
            int minC = (int) Math.floor(box.getMinX() / gp.tileSize) - 1,
                    maxC = (int) Math.floor(box.getMaxX() / gp.tileSize) + 1;
            int minR = (int) Math.floor(box.getMinY() / gp.tileSize) - 1,
                    maxR = (int) Math.floor(box.getMaxY() / gp.tileSize) + 1;
            for (int r = minR; r <= maxR; r++)
                for (int c = minC; c <= maxC; c++) {
                    Cell cell = new Cell(c, r);
                    Placement p = plannedPiece(cell, plan);
                    if (p != null && shape(cell, p, plan).intersects(box))
                        return "Furnishing overlaps a wall or doorway";
                }
        }
        return null;
    }

    /** Updates the props for BuildSystem. */
    private void updateProps() {
        double[] point = propCursor();
        if (point == null) {
            propHeld = gp.mouseH.leftPressed;
            propRightHeld = gp.mouseH.rightPressed;
            return;
        }
        if (gp.mouseH.rightPressed && !propRightHeld) {
            for (int i = decorations.size() - 1; i >= 0; i--) {
                Decoration d = decorations.get(i);
                var p = PropCatalog.get(d.element());
                if (p.visualBounds(d.x(), d.y(), d.flipped(), d.rotation())
                        .contains(point[0], point[1])) {
                    if (gp.crafting.buildingTransaction(Map.of(), Map.of(d.element(), 1))) {
                        decorations.remove(i);
                        changed();
                    }
                    break;
                }
            }
        }
        if (gp.mouseH.leftPressed && !propHeld) {
            Decoration d =
                    Decoration.create(
                            selectedElement, point[0], point[1], preferredVertical, propRotation);
            String error = propPlacementError(d, Map.of());
            if (error != null) {
                feedback = error;
                feedbackTicks = 90;
            } else {
                textures.prop(d.element(), d.flipped(), d.rotation());
                if (!gp.crafting.buildingTransaction(Map.of(d.element(), 1), Map.of())) return;
                decorations.add(d);
                gp.placementEffects.object(
                        d.id(),
                        d.x() * gp.tileSize / 48,
                        d.y() * gp.tileSize / 48,
                        PropCatalog.get(d.element()).oriented(d.rotation()).width()
                                * gp.tileSize
                                / 48);
                clearDecorationVegetation(d);
                changed();
            }
        }
        propHeld = gp.mouseH.leftPressed;
        propRightHeld = gp.mouseH.rightPressed;
    }

    /** Clears the decoration vegetation for BuildSystem. */
    private void clearDecorationVegetation(Decoration d) {
        var b =
                PropCatalog.get(d.element())
                        .placementBounds(d.x(), d.y(), d.flipped(), d.rotation());
        for (int r = (int) Math.floor(b.getMinY() / 48);
                r <= (int) Math.floor((b.getMaxY() - .001) / 48);
                r++)
            for (int c = (int) Math.floor(b.getMinX() / 48);
                    c <= (int) Math.floor((b.getMaxX() - .001) / 48);
                    c++) if (inside(new Cell(c, r))) clearVegetation(new Cell(c, r));
    }

    /** Draws the prop preview for BuildSystem. */
    private void drawPropPreview(Graphics2D original) {
        double[] point = propCursor();
        if (point == null) return;
        Decoration d =
                Decoration.create(
                        selectedElement, point[0], point[1], preferredVertical, propRotation);
        Graphics2D g = (Graphics2D) original.create();
        Player camera = gp.localPlayer();
        g.translate(camera.screenX - camera.worldX, camera.screenY - camera.worldY);
        g.setComposite(AlphaComposite.SrcOver.derive(.55f));
        BuildingRenderer.drawDecoration(g, d, gp.tileSize, textures);
        g.setComposite(AlphaComposite.SrcOver);
        var def = PropCatalog.get(d.element());
        var b = def.placementBounds(d.x(), d.y(), d.flipped(), d.rotation());
        double scale = gp.tileSize / 48.0;
        g.setColor(
                propPlacementError(d, Map.of()) == null
                        ? new Color(157, 220, 169)
                        : new Color(235, 120, 120));
        g.draw(
                new java.awt.geom.Rectangle2D.Double(
                        b.getX() * scale,
                        b.getY() * scale,
                        b.getWidth() * scale,
                        b.getHeight() * scale));
        g.dispose();
    }

    private final class Furnishing extends Entity {
        final Decoration decoration;

        /**
         * Creates a Furnishing instance and initializes the state required by its collaborators.
         */
        Furnishing(Decoration d) {
            super(BuildSystem.this.gp);
            decoration = d;
            worldX = (int) (d.x() * gp.tileSize / 48);
            worldY = (int) (d.y() * gp.tileSize / 48);
        }

        /** Resolves the sort y result used by Furnishing. */
        @Override
        public int sortY() {
            return (int) BuildingRenderer.decorationDepth(decoration, gp.tileSize);
        }

        /** Renders this component into the supplied Java2D graphics context. */
        @Override
        public void draw(Graphics2D original) {
            Player camera = gp.localPlayer();
            if (Math.abs(worldX - camera.worldX) > gp.screenWidth + 200
                    || Math.abs(worldY - camera.worldY) > gp.screenHeight + 200) return;
            Graphics2D g = (Graphics2D) original.create();
            g.translate(camera.screenX - camera.worldX, camera.screenY - camera.worldY);
            gp.placementEffects.object(
                    g,
                    decoration.id(),
                    decoration.x() * gp.tileSize / 48,
                    decoration.y() * gp.tileSize / 48);
            BuildingRenderer.drawDecoration(g, decoration, gp.tileSize, textures);
            g.dispose();
        }
    }

    /** Resolves the generation version result used by BuildSystem. */
    private int generationVersion() {
        return gp.generatedWorld == null
                ? world.generation.WorldConfig.GENERATOR_VERSION
                : gp.generatedWorld.generatorVersion;
    }

    /** Resolves the world id result used by BuildSystem. */
    private String worldId() {
        return worldId(generationVersion());
    }

    /** Resolves the world id result used by BuildSystem. */
    private String worldId(int generatorVersion) {
        return gp.worldConfig.procedural()
                ? "procedural-"
                        + gp.worldConfig.seed()
                        + "-"
                        + Integer.toUnsignedString(
                                (gp.worldConfig.toString()
                                                + "/"
                                                + generatorVersion
                                                + (generatorVersion >= 6
                                                                && gp.generatedWorld != null
                                                        ? "/" + gp.generatedWorld.generation
                                                        : "")
                                                + (generatorVersion >= 8
                                                                && gp.generatedWorld != null
                                                        ? "/" + gp.generatedWorld.nestConfig
                                                        : "")
                                                + (generatorVersion >= 9
                                                                && gp.generatedWorld != null
                                                        ? "/" + gp.generatedWorld.elevationConfig
                                                        : "")
                                                + (generatorVersion >= 10
                                                                && gp.generatedWorld != null
                                                        ? "/" + gp.generatedWorld.cliffConfig
                                                        : ""))
                                        .hashCode(),
                                16)
                : "authored";
    }

    /** Saves the path for BuildSystem. */
    private Path savePath() {
        return AppPaths.mapFile("maps/buildings/" + worldId() + ".json").toPath();
    }

    /** Performs the load operation for BuildSystem. */
    public void load() {
        restore(null);
    }

    /** Restores this component from its saved state. */
    public void restore(String supplied) {
        gp.placementEffects.clear();
        gp.interactions.clear();
        stop();
        floors.clear();
        floorElements.clear();
        structures.clear();
        groundCells.clear();
        surfaces.clear();
        renderState.clear();
        renderFloors.clear();
        clearedCells.clear();
        decorations.clear();
        furniture.clear();
        indexedDecorations.clear();
        propIndex.clear();
        dirty = false;
        prepare();
        Path file = savePath();
        String json = supplied;
        if (supplied == null
                && gp.worldConfig.procedural()
                && generationVersion() == 5
                && !Files.isRegularFile(file)) {
            Path previous = AppPaths.mapFile("maps/buildings/" + worldId(4) + ".json").toPath();
            if (Files.isRegularFile(previous)) file = previous;
        }
        try {
            if (supplied == null && Files.isRegularFile(file))
                json = Files.readString(file, StandardCharsets.UTF_8);
            else if (supplied == null)
                try (InputStream stream =
                        BuildSystem.class.getResourceAsStream(
                                "/maps/buildings/" + worldId() + ".json")) {
                    if (stream != null)
                        json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                }
            if (json == null
                    && supplied == null
                    && gp.worldConfig.procedural()
                    && generationVersion() == 5)
                try (InputStream stream =
                        BuildSystem.class.getResourceAsStream(
                                "/maps/buildings/" + worldId(4) + ".json")) {
                    if (stream != null)
                        json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                }
            if (json == null) return;
            Object parsed = MiniJson.parse(json);
            if (!(parsed instanceof Map<?, ?> root)
                    || !(root.get("version") instanceof Number version)
                    || (version.intValue() < 1 || version.intValue() > 2)
                    || !(root.get("elements") instanceof List<?> elements))
                throw new IOException("Invalid building map or version");
            // Parse fully before applying to avoid partially loaded structures.
            Set<Cell> nextFloors = new HashSet<>();
            Map<Cell, String> nextFloorElements = new HashMap<>();
            Map<Cell, Piece> nextStructures = new HashMap<>();
            for (Object item : elements) {
                if (!(item instanceof Map<?, ?> entry))
                    throw new IOException("Invalid building entry");
                Kind kind = Kind.valueOf(String.valueOf(entry.get("kind")));
                String element =
                        entry.get("element") instanceof String id
                                ? id
                                : kind.name().toLowerCase(Locale.ROOT);
                if (!BuildElements.get(element).shape().name().equals(kind.name()))
                    throw new IOException("Element shape mismatch");
                Cell c =
                        new Cell(
                                ((Number) entry.get("col")).intValue(),
                                ((Number) entry.get("row")).intValue());
                if (!inside(c)) throw new IOException("Building outside map");
                if (ground(kind)) {
                    nextFloors.add(c);
                    nextFloorElements.put(c, element);
                } else
                    nextStructures.put(
                            c,
                            new Piece(
                                    c,
                                    kind,
                                    Boolean.TRUE.equals(entry.get("open")),
                                    Boolean.TRUE.equals(entry.get("vertical")),
                                    element));
            }
            Set<Cell> nextCleared = new HashSet<>();
            if (root.get("cleared") instanceof List<?> cleared)
                for (Object item : cleared) {
                    if (!(item instanceof Map<?, ?> cell))
                        throw new IOException("Invalid clearing entry");
                    Cell c =
                            new Cell(
                                    ((Number) cell.get("col")).intValue(),
                                    ((Number) cell.get("row")).intValue());
                    if (!inside(c)) throw new IOException("Clearing outside map");
                    nextCleared.add(c);
                }
            // Older layouts also clear their occupied cells when first loaded with this revision.
            nextCleared.addAll(nextFloors);
            nextCleared.addAll(nextStructures.keySet());
            for (String element : nextFloorElements.values())
                textures.get(element, 0, false, false);
            for (Piece piece : nextStructures.values()) {
                textures.get(piece.element, 0, piece.open, piece.vertical);
                if (piece.kind == Kind.DOOR)
                    textures.get(piece.element, 0, !piece.open, piece.vertical);
            }
            List<Decoration> nextProps = Decoration.parse(root.get("decorations"));
            for (Decoration d : nextProps) {
                if (!BuildingBlueprint.decorationInside(d, gp.maxWorldCol, gp.maxWorldRow))
                    throw new IOException("Furnishing outside map");
                textures.prop(d.element(), d.flipped(), d.rotation());
            }
            floors.addAll(nextFloors);
            floorElements.putAll(nextFloorElements);
            structures.putAll(nextStructures);
            decorations.addAll(nextProps);
            refreshGround();
            for (Cell c : nextCleared) clearVegetation(c);
            for (Decoration d : nextProps) clearDecorationVegetation(d);
        } catch (Exception e) {
            Log.warn("[BUILD] Could not load " + file + ": " + e.getMessage());
        }
    }

    /** Captures the current state in the representation used for persistence or replication. */
    public String snapshot() {
        List<Cell> cells = new ArrayList<>(floors);
        for (Cell c : structures.keySet()) if (!floors.contains(c)) cells.add(c);
        cells.sort(Comparator.comparingInt(Cell::row).thenComparingInt(Cell::col));
        List<String> entries = new ArrayList<>();
        for (Cell c : cells) {
            if (floors.contains(c))
                entries.add(
                        entry(
                                c,
                                Kind.valueOf(
                                        BuildElements.get(floorElements.getOrDefault(c, "floor"))
                                                .shape()
                                                .name()),
                                false,
                                false,
                                floorElements.getOrDefault(c, "floor")));
            Piece p = structures.get(c);
            if (p != null) entries.add(entry(c, p.kind, p.open, p.vertical, p.element));
        }
        List<Cell> cleared = new ArrayList<>(clearedCells);
        cleared.sort(Comparator.comparingInt(Cell::row).thenComparingInt(Cell::col));
        List<String> clearingEntries = new ArrayList<>();
        for (Cell c : cleared)
            clearingEntries.add("    {\"col\": " + c.col + ", \"row\": " + c.row + "}");
        return "{\n  \"version\": 2,\n  \"elements\": [\n"
                + String.join(",\n", entries)
                + "\n  ],\n  \"cleared\": [\n"
                + String.join(",\n", clearingEntries)
                + "\n  ],\n  \"decorations\": "
                + Decoration.json(decorations)
                + "\n}\n";
    }

    /** Evaluates the save condition for BuildSystem. */
    public boolean save() {
        if (!editable()) return false;
        Path file = savePath(), temp = null;
        try {
            AppPaths.createParent(file);
            temp = Files.createTempFile(file.toAbsolutePath().getParent(), "building-", ".tmp");
            Files.writeString(temp, snapshot(), StandardCharsets.UTF_8);
            try {
                Files.move(
                        temp,
                        file,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
            return true;
        } catch (IOException e) {
            Log.warn("[BUILD] Could not save: " + e.getMessage());
            return false;
        } finally {
            if (temp != null)
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
        }
    }

    /** Resolves the entry result used by BuildSystem. */
    private static String entry(Cell c, Kind kind, boolean open, boolean vertical, String element) {
        return "    {\"element\": \""
                + element
                + "\", \"kind\": \""
                + kind
                + "\", \"col\": "
                + c.col
                + ", \"row\": "
                + c.row
                + ", \"open\": "
                + open
                + ", \"vertical\": "
                + vertical
                + "}";
    }
}
