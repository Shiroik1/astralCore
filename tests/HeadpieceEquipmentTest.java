import art.*;
import entity.*;
import object.*;
import net.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;

/** Headgear is independent equipment, and rear long hair must remain over the cape. */
public class HeadpieceEquipmentTest {
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static int firstOpaqueRow(BufferedImage image){
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)
            if((image.getRGB(x,y)>>>24)>200)return y;
        return image.getHeight();
    }
    public static void main(String[] args)throws Exception{
        var game=new AppearanceEquipmentTest.QuietGame();Player p=game.localPlayer();
        for(String id:new String[]{"hunter","warrior"}){
            p.setCharacterDesign(id,"female");require(p.equippedItem(Player.EQUIP_HEADPIECE)==null,"starter hair stays visible");
        }
        for(boolean pixel:new boolean[]{false,true}){
            CharacterSprites.pixelMode=pixel;p.setCharacterDesign("mage","female");p.setItems();
            require(p.equippedItem(Player.EQUIP_HEADPIECE)==null && ItemRegistry.typeFor(p.inventorySlots[1])==ItemType.MAGE_HAT,
                    "Mage carries the optional hat in the bag");
            p.tryEquipFromBag(1,Player.EQUIP_HEADPIECE);
            Entity hair=p.equippedItem(Player.EQUIP_HAIR),hat=p.equippedItem(Player.EQUIP_HEADPIECE);
            BufferedImage hatFrame=p.down1;
            int dressed=AppearanceEquipmentTest.pixels(hatFrame);
            require(ItemRegistry.typeFor(hat)==ItemType.MAGE_HAT,"separate mage hat item");
            p.unequipToBagIndex(Player.EQUIP_HEADPIECE,1);
            require(p.equippedItem(Player.EQUIP_HAIR)==hair,"removing hat preserves hair");
            require(p.currentOutfit().longHair() && p.currentOutfit().headpiece()==null,"long hair stays without hat");
            require(AppearanceEquipmentTest.pixels(p.down1)!=dressed,"hat visibly removable");
            int size=p.down1.getWidth();
            require(Arrays.equals(hatFrame.getRGB(0,40,size,size-40,null,0,size),
                    p.down1.getRGB(0,40,size,size-40,null,0,size)),"hat does not shrink or move the body");
            p.tryEquipFromBag(1,Player.EQUIP_HAIR);require(p.inventorySlots[1]==hat,"hat cannot replace hair");
            p.tryEquipFromBag(1,Player.EQUIP_HEADPIECE);
            require(AppearanceEquipmentTest.pixels(p.down1)==dressed,"hat re-equip restores visuals");
            p.unequipToBagIndex(Player.EQUIP_HAIR,1);
            require(p.currentOutfit().hair()==null && p.currentOutfit().headpiece()!=null,"hat independent of hair presence");
            p.tryEquipFromBag(1,Player.EQUIP_HAIR);
        }
        PlayerState state=SnapshotBuilder.build(game).players[0];
        require(state.apparelTypes.length==7 && state.apparelTypes[6]==ItemType.MAGE_HAT,"headpiece included in network snapshot");
        var remote=new Player(game,game.keyH);remote.playerId=1;remote.isLocal=false;game.players[1]=remote;
        var snapshot=SnapshotBuilder.build(game);var remoteState=snapshot.players[1];
        remoteState.apparelTypes=state.apparelTypes.clone();remoteState.playerClass="mage";remoteState.gender="female";
        var apply=GameClient.class.getDeclaredMethod("applySnapshot",WorldSnapshot.class);apply.setAccessible(true);
        var client=new GameClient(game);apply.invoke(client,snapshot);
        require(ItemRegistry.typeFor(remote.equippedItem(Player.EQUIP_HEADPIECE))==ItemType.MAGE_HAT,"remote hat equip");
        remoteState.apparelTypes[6]=null;apply.invoke(client,snapshot);
        require(remote.equippedItem(Player.EQUIP_HEADPIECE)==null && remote.equippedItem(Player.EQUIP_HAIR)!=null,"remote hat removal preserves hair");
        CharacterArt.Look look=new CharacterArt.Look();look.shadow=false;
        var o=CharacterClass.MAGE.outfit(true);
        var hat=(OBJ_apparel)ItemRegistry.create(ItemType.MAGE_HAT,game);
        look.outfit=new CharacterOutfit(o.hair(),o.torso(),o.pants(),o.boots(),o.gloves(),o.cloak(),o.details(),hat.color);
        BufferedImage withHat=CharacterArt.render(CharacterArt.Dir.UP,new CharacterArt.Pose(),look,240);
        look.outfit=o;
        BufferedImage noHat=CharacterArt.render(CharacterArt.Dir.UP,new CharacterArt.Pose(),look,240);
        require(firstOpaqueRow(withHat)<firstOpaqueRow(noHat)-20,"hat sits above actual crown");
        // At x=32, y=39, rear long hair must cover the cape and collar.
        int sample=noHat.getRGB((int)((32+CharacterArt.OFFSET_X)*3),(int)((39+CharacterArt.OFFSET_Y)*3));
        Color c=new Color(sample,true);
        require(c.getRed()>130 && c.getBlue()>150,"long rear hair covers cape");
        p.undress();require(p.currentOutfit().equals(CharacterOutfit.BARE),"undress removes independent hat too");
        require(Arrays.stream(p.inventorySlots).anyMatch(i->ItemRegistry.typeFor(i)==ItemType.MAGE_HAT),"undress preserves hat in bag");
        System.out.println("PASS: independent hat equip/remove, both display modes, native headroom, rear hair overlap, snapshots and safe undress.");
    }
}
