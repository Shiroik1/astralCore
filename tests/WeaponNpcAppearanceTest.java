import art.*;
import entity.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;

public class WeaponNpcAppearanceTest {
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        for(WeaponArt.Kind kind:WeaponArt.Kind.values()){
            var rest=WeaponArt.swing(kind,false,0);
            require(rest.front==0 && rest.back==0,"upright front/back weapon: "+kind);
        }
        for(boolean pixel:new boolean[]{false,true}){
            CharacterSprites.pixelMode=pixel;
            for(WeaponArt.Kind kind:new WeaponArt.Kind[]{WeaponArt.Kind.SWORD,WeaponArt.Kind.AXE,WeaponArt.Kind.STAFF}){
                CharacterArt.Look look=new CharacterArt.Look();look.shadow=false;look.weaponScale=pixel?1.6f:1;
                for(int frame=0;frame<8;frame++){
                    CharacterArt.Pose pose=new CharacterArt.Pose().attack(frame/7f);
                    look.weapon=kind;
                    BufferedImage armed=CharacterArt.render(CharacterArt.Dir.UP,pose,look,240);
                    // Skin and ivory impact effects stay bright here; steel, wood and violet
                    // weapon pixels would darken this opaque part of the back of the head.
                    int x=(int)((CharacterArt.OFFSET_X+27)*3),y=(int)((CharacterArt.OFFSET_Y+18)*3);
                    for(int color:armed.getRGB(x,y,30,30,null,0,30)){
                        require(((color>>16)&255)>=230,"upward weapon occluded by head: "+kind+" frame "+frame);
                    }
                }
            }
        }
        var game=new AppearanceEquipmentTest.QuietGame();
        for(boolean pixel:new boolean[]{false,true}){
            CharacterSprites.pixelMode=pixel;
            NPC_oldman npc=new NPC_oldman(game);game.npc[0]=npc;
            require(npc.type==Entity.type_npc && npc.npcId.equals("oldman"),"NPC identity retained");
            require(!npc.dialogueLines.isEmpty(),"dialogue retained");
            require(NPC_oldman.TEMPORARY_OUTFIT.hair()==null && NPC_oldman.TEMPORARY_OUTFIT.headpiece()==null,"NPC bald");
            require(NPC_oldman.TEMPORARY_OUTFIT.torso().equals(CharacterOutfit.ADVENTURER.torso()),"Hunter tunic");
            for(String dir:new String[]{"up","down","left","right"}){
                require(npc.sprites.get("idle_"+dir).frames.length==4,"shared idle animation");
                require(npc.sprites.get("walk_"+dir)==npc.sprites.get("run_"+dir),"shared walk/run rig");
            }
            require(npc.down1.getWidth()==CharacterSprites.canvasSize(),"same player canvas size");
            npc.worldX=game.localPlayer().worldX+game.tileSize*2;npc.worldY=game.localPlayer().worldY;
            int oldX=npc.worldX,oldY=npc.worldY;
            for(int i=0;i<25;i++)npc.update();
            require(!npc.moving && npc.animState.equals("idle") && npc.worldX==oldX && npc.worldY==oldY,"stationary NPC stays idle");
            var footing=Entity.class.getDeclaredMethod("spriteFootFraction");footing.setAccessible(true);
            require((float)footing.invoke(npc)==CharacterSprites.FOOT_FRAC,"NPC shares humanoid foot anchor");
            game.commands.execute(pixel?"/pixel off":"/pixel char");
            require(CharacterSprites.pixelMode!=pixel,"pixel command updates mode");
            CharacterArt.Look expectedLook=new CharacterArt.Look();expectedLook.outfit=NPC_oldman.TEMPORARY_OUTFIT;
            var expected=new HashMap<String,SpriteAnimation>();CharacterSprites.build(expected,expectedLook);
            require(AppearanceEquipmentTest.pixels(npc.down1)==AppearanceEquipmentTest.pixels(expected.get("idle_down").frames[0]),"pixel command refreshes NPC appearance");
        }
        System.out.println("PASS: upright held weapons, rear weapon occlusion, bald Hunter-tunic NPC, stationary idle, shared foot anchor and display-mode refresh.");
    }
}
