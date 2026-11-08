package main;

import monster.MON_HellSlime;
import object.ItemRegistry;
import object.ItemType;
import world.SceneryLoader;
import java.util.List;

/** Executable package check, also usable on Windows build runners without opening a game window. */
public final class InstallationCheck {
    private InstallationCheck(){}
    public static void run()throws Exception{
        for(String resource:List.of("/maps/terrain.txt","/maps/levels.txt","/maps/scenery.json","/maps/grass.txt","/font/JetBrainsMono-Regular.ttf","/sound/coin.wav","/dialogue/oldman.json"))
            if(InstallationCheck.class.getResource(resource)==null)throw new IllegalStateException("Missing packaged resource: "+resource);
        Gamepanel gp=new Gamepanel(){@Override public void playSE(int index){}};
        try{
            gp.config.loadConfig();SceneryLoader.load(gp);gp.grass.load();gp.preloadGameplayAssets();gp.assetSetter.setNPC();
            if(gp.worldObjects.isEmpty() || gp.npc[1]==null)throw new IllegalStateException("Town assets are missing");
            for(ItemType type:ItemType.values())if(ItemRegistry.create(type,gp)==null)throw new IllegalStateException("Missing item "+type);
            if(new MON_HellSlime(gp).HP<=0)throw new IllegalStateException("Boss assets are missing");
            gp.quests.onOldManFinished(gp.localPlayer());gp.quests.enterWell(gp.localPlayer());
            long slimes=java.util.Arrays.stream(gp.monster).filter(java.util.Objects::nonNull).count();
            if(slimes!=10)throw new IllegalStateException("Well dungeon did not initialize");
            System.out.println("INSTALLATION_OK AstralCore "+AppInfo.VERSION+" | "+System.getProperty("os.name")+" "+System.getProperty("os.arch")+" | Java "+System.getProperty("java.version"));
        }finally{gp.soundEffect.close();gp.music.close();}
    }
}
