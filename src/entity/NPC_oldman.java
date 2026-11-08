package entity;

import art.CharacterArt;
import art.CharacterOutfit;
import main.Gamepanel;

public class NPC_oldman extends Entity {
    // Temporary wardrobe data; a future character creator can replace this preset.
    public static final CharacterOutfit TEMPORARY_OUTFIT = new CharacterOutfit(
            null, CharacterOutfit.ADVENTURER.torso(), CharacterOutfit.ADVENTURER.pants(),
            CharacterOutfit.ADVENTURER.boots(), null, null);

    public NPC_oldman(Gamepanel gp){
        super(gp);
        direction = "down";
        speed = 0;
        npcId = "oldman";
        type = type_npc;
        getImage();
        loadDialogue("/dialogue/oldman.json");
    }

    public void getImage(){
        CharacterArt.Look look = new CharacterArt.Look();
        look.outfit = TEMPORARY_OUTFIT;
        CharacterSprites.build(sprites, look);
        // Entity uses walk_* while Player uses run_*; both use the same base rig.
        for(String dir : new String[]{"up", "down", "left", "right"}){
            sprites.put("walk_" + dir, sprites.get("run_" + dir));
        }
        up1 = sprites.get("idle_up").frames[0]; up2 = sprites.get("idle_up").frames[1];
        down1 = sprites.get("idle_down").frames[0]; down2 = sprites.get("idle_down").frames[1];
        left1 = sprites.get("idle_left").frames[0]; left2 = sprites.get("idle_left").frames[1];
        right1 = sprites.get("idle_right").frames[0]; right2 = sprites.get("idle_right").frames[1];
    }

    @Override
    protected float spriteFootFraction(){ return CharacterSprites.FOOT_FRAC; }

    @Override
    public void setAction(){ direction = "down"; }
}
