package world;

import art.BuildingCatalog;
import main.Gamepanel;
import main.MiniJson;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SceneryLoader {
    // A file next to the game wins over the bundled copy, so /saveobjects takes effect on the next launch.
    public static final String EXTERNAL_PATH = "maps/scenery.json";
    public static final String RESOURCE_PATH = "/maps/scenery.json";

    public static void load(Gamepanel gp){
        gp.worldObjects.clear();

        String json = readExternal();
        if(json == null) json = readResource();
        if(json == null){
            System.out.println("[SCENERY] no scenery.json found (looked in " + EXTERNAL_PATH + " and " + RESOURCE_PATH + ")");
        }

        try{
            Object parsed = json == null ? null : MiniJson.parse(json);
            if(parsed instanceof Map<?, ?> root && root.get("objects") instanceof List<?> list){
                for(Object item : list){
                    if(!(item instanceof Map<?, ?> o)) continue;

                    WorldObjectType type = WorldObjectType.fromName(String.valueOf(o.get("type")));
                    if(type == null){
                        System.out.println("[SCENERY] unknown object type: " + o.get("type"));
                        continue;
                    }
                    int col = ((Number) o.get("col")).intValue();
                    int row = ((Number) o.get("row")).intValue();
                    if(type == WorldObjectType.INN_EXIT) continue;
                    if(type == WorldObjectType.HOUSE_CORAL && col == InnInterior.ENTRANCE_COL && row == InnInterior.ENTRANCE_ROW){
                        type = WorldObjectType.INN;
                    }
                    Object t = o.get("text");
                    String building = o.get("building") instanceof String id ? id
                            : type == WorldObjectType.INN ? "inn" : null;
                    Object palette = o.get("palette");
                    try{
                        gp.worldObjects.add(new WorldObject(gp, type, col, row, t != null ? t.toString() : null,
                                building, palette == null ? Map.of() : BuildingCatalog.parsePalette(palette)));
                    } catch(IllegalArgumentException | java.io.UncheckedIOException e){
                        System.out.println("[SCENERY] skipped " + type + " at " + col + "," + row + " (" + e.getMessage() + ")");
                    }
                }
            }
        } catch(Exception e){
            e.printStackTrace();
        }
        gp.worldObjects.add(InnInterior.createExit(gp));
        System.out.println("[SCENERY] loaded " + gp.worldObjects.size() + " objects");
    }

    public static boolean save(Gamepanel gp){
        if(gp.quests.inDungeon())return false;
        List<WorldObject> sorted = new ArrayList<>(gp.worldObjects);
        sorted.removeIf(o -> o.kind == WorldObjectType.INN_EXIT);
        sorted.sort(Comparator.comparingInt((WorldObject o) -> o.row).thenComparingInt(o -> o.col));

        StringBuilder sb = new StringBuilder("{\n  \"objects\": [\n");
        for(int i = 0; i < sorted.size(); i++){
            WorldObject o = sorted.get(i);
            sb.append("    {\"type\": \"").append(o.kind.name().toLowerCase(Locale.ROOT))
                    .append("\", \"col\": ").append(o.col)
                    .append(", \"row\": ").append(o.row);
            if(o.text != null && !o.text.isEmpty()){
                sb.append(", \"text\": \"").append(escape(o.text)).append("\"");
            }
            if(o.building != null){
                if(o.kind != WorldObjectType.INN || !o.building.id().equals("inn")){
                    sb.append(", \"building\": \"").append(escape(o.building.id())).append("\"");
                }
                if(!o.paletteOverrides.isEmpty()){
                    sb.append(", \"palette\": {");
                    boolean first = true;
                    for(var entry : new java.util.TreeMap<>(o.paletteOverrides).entrySet()){
                        if(!first) sb.append(", ");
                        first = false;
                        sb.append("\"").append(escape(entry.getKey())).append("\": \"")
                                .append(String.format(Locale.ROOT, "#%06X", entry.getValue())).append("\"");
                    }
                    sb.append("}");
                }
            }
            sb.append("}").append(i < sorted.size() - 1 ? "," : "").append("\n");
        }
        sb.append("  ]\n}\n");

        try{
            main.AppPaths.createParent(main.AppPaths.mapFile(EXTERNAL_PATH).toPath());
            Files.writeString(main.AppPaths.mapFile(EXTERNAL_PATH).toPath(), sb.toString(), StandardCharsets.UTF_8);
            return true;
        } catch(IOException e){
            e.printStackTrace();
            return false;
        }
    }

    private static String readExternal(){
        File f = main.AppPaths.mapFile(EXTERNAL_PATH);
        if(!f.isFile()) return null;
        try{
            return Files.readString(f.toPath(), StandardCharsets.UTF_8);
        } catch(IOException e){
            e.printStackTrace();
            return null;
        }
    }

    private static String readResource(){
        try(InputStream is = SceneryLoader.class.getResourceAsStream(RESOURCE_PATH)){
            if(is == null) return null;
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch(IOException e){
            e.printStackTrace();
            return null;
        }
    }

    private static String escape(String s){
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "").replace("\t", "\\t");
    }
}
