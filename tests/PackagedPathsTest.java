import main.*;
import world.*;
import java.nio.file.*;
import java.util.Comparator;

/** Run against the packaged JAR from a folder that has no source files or external maps. */
public class PackagedPathsTest {
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        Path user=Files.createTempDirectory("astral-user-data-");
        System.setProperty("astral.packaged","true");System.setProperty("astral.dataDir",user.toString());
        var game=new Gamepanel(){@Override public void playSE(int index){}};
        try{
            require(AppInfo.VERSION.equals("0.1.0-alpha"),"release version is embedded");
            require(PackagedPathsTest.class.getResource("/maps/terrain.txt")!=null,"terrain embedded in game JAR");
            require(AppPaths.configFile().equals(user.resolve("config.txt")),"settings use user data");
            var config=new Config(game);config.loadConfig();require(Files.isRegularFile(user.resolve("config.txt")),"first launch writes settings outside install folder");
            SceneryLoader.load(game);game.grass.load();require(game.worldObjects.size()>100,"bundled town scenery loads without project files");
            require(SceneryLoader.save(game) && Files.isRegularFile(user.resolve("maps/scenery.json")),"map edits use user data");
            require(game.grass.save() && Files.isRegularFile(user.resolve("maps/grass.txt")),"grass edits use user data");
            int count=game.worldObjects.size();SceneryLoader.load(game);require(game.worldObjects.size()==count,"user map override loads correctly");
            require(!Files.exists(Path.of("maps")) && !Files.exists(Path.of("config.txt")),"no writes beside installed application");
            System.out.println("PASS: embedded release version/maps, first-launch settings, writable user maps and read-only install compatibility.");
        }finally{
            try(var paths=Files.walk(user)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}
        }
    }
}
