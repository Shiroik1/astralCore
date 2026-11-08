package world;

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
            return;
        }

        try{
            Object parsed = MiniJson.parse(json);
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
                    Object t = o.get("text");
                    gp.worldObjects.add(new WorldObject(gp, type, col, row, t != null ? t.toString() : null));
                }
            }
        } catch(Exception e){
            e.printStackTrace();
        }
        System.out.println("[SCENERY] loaded " + gp.worldObjects.size() + " objects");
    }

    public static boolean save(Gamepanel gp){
        if(gp.quests.inDungeon())return false;
        List<WorldObject> sorted = new ArrayList<>(gp.worldObjects);
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