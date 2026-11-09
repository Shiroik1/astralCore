import art.CharacterOutfit;
import entity.Entity;
import entity.Player;
import main.Gamepanel;
import net.*;
import object.*;
import java.awt.image.BufferedImage;
import java.util.*;

/** Run with: javac -cp '/tmp/astral-compile:lib/*' -d /tmp/astral-compile tests/AppearanceEquipmentTest.java
 * java -Djava.awt.headless=true -cp '/tmp/astral-compile:res:lib/*' AppearanceEquipmentTest
 */
public class AppearanceEquipmentTest {
    static class QuietGame extends Gamepanel {
        QuietGame() throws Exception { super(); }
        @Override public void playSE(int index) {}
    }
    static void require(boolean ok, String message) { if(!ok) throw new AssertionError(message); }
    static int pixels(BufferedImage image) {
        return Arrays.hashCode(image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth()));
    }
    public static void main(String[] args) throws Exception {
        QuietGame game=new QuietGame();Player p=game.localPlayer();
        p.setCharacterDesign("hunter","male");
        require(p.currentOutfit().equals(entity.CharacterClass.HUNTER.outfit(false)),"starter outfit");
        int dressed=pixels(p.down1);
        for(int slot=Player.EQUIP_HAIR;slot<=Player.EQUIP_CLOAK;slot++) {
            Entity item=p.equippedItem(slot);ItemType id=ItemRegistry.typeFor(item);
            require(ItemRegistry.typeFor(ItemRegistry.create(id,game))==id,"apparel item identity");
            int before=pixels(p.down1);
            p.unequipToBagIndex(slot,1);
            require(p.equippedItem(slot)==null && p.inventorySlots[1]==item,"remove layer to bag");
            require(pixels(p.down1)!=before,"layer changes portrait: "+slot);
            int wrong=slot==Player.EQUIP_HAIR?Player.EQUIP_TORSO:Player.EQUIP_HAIR;
            p.tryEquipFromBag(1,wrong);
            require(p.inventorySlots[1]==item,"wrong slot preserves item");
            p.tryEquipFromBag(1,slot);
            require(p.equippedItem(slot)==item && p.inventorySlots[1]==null,"re-equip layer");
            require(pixels(p.down1)==before,"cached visual restored");
        }
        Entity[] savedBag=p.inventorySlots.clone();
        for(int i=0;i<p.inventorySlots.length;i++)p.inventorySlots[i]=ItemRegistry.create(ItemType.POTION_RED,game);
        p.undress();require(p.currentOutfit().equals(entity.CharacterClass.HUNTER.outfit(false)),"full bag preserves outfit");
        require(pixels(p.down1)==dressed,"full bag preserves sprites");
        System.arraycopy(savedBag,0,p.inventorySlots,0,savedBag.length);
        Set<Entity> gear=Collections.newSetFromMap(new IdentityHashMap<>());
        for(int s=1;s<=8;s++)gear.add(p.equippedItem(s));
        InventoryAction action=new InventoryAction();action.playerId=0;action.actionType=InventoryActionType.UNDRESS;
        game.requestInventoryAction(action);
        require(p.currentOutfit().equals(CharacterOutfit.BARE),"undress reaches bare base");
        require(p.currentWeapon.isPlaceholder && p.currentShield.isPlaceholder,"empty weapon and shield");
        for(Entity item:gear)require(Arrays.stream(p.inventorySlots).filter(i->i==item).count()==1,"gear retained exactly once");
        var bareSprites=new HashMap<String,entity.SpriteAnimation>();
        entity.CharacterSprites.build(bareSprites,new art.CharacterArt.Look());
        require(pixels(p.down1)==pixels(bareSprites.get("idle_down").frames[0]),"stripping restores the exact bare sprite");
        PlayerState bare=SnapshotBuilder.build(game).players[0];
        require(Arrays.stream(bare.apparelTypes).allMatch(Objects::isNull),"bare outfit snapshot");
        for(int i=0;i<p.inventorySlots.length;i++)if(p.inventorySlots[i] instanceof OBJ_apparel clothing)p.tryEquipFromBag(i,3+clothing.slot.ordinal());
        require(p.currentOutfit().equals(entity.CharacterClass.HUNTER.outfit(false)),"redress from bag");
        Entity originalHair=p.equippedItem(Player.EQUIP_HAIR);
        Entity alternateHair=ItemRegistry.create(ItemType.HAIR,game);p.inventorySlots[1]=alternateHair;
        p.tryEquipFromBag(1,Player.EQUIP_HAIR);
        require(p.equippedItem(Player.EQUIP_HAIR)==alternateHair && p.inventorySlots[1]==originalHair,"same-slot swap preserves both items");
        p.tryEquipFromBag(1,Player.EQUIP_HAIR);p.inventorySlots[1]=null;
        PlayerState dressedState=SnapshotBuilder.build(game).players[0];
        require(Arrays.stream(dressedState.apparelTypes,0,6).allMatch(Objects::nonNull) && dressedState.apparelTypes[6]==null,"equipped outfit snapshot");
        // Separate players must not share appearance state through the sprite cache.
        Player other=new Player(game,game.keyH);other.undress();
        require(p.currentOutfit().equals(entity.CharacterClass.HUNTER.outfit(false)),"player outfits isolated");
        require(!other.currentOutfit().equals(p.currentOutfit()),"bare and dressed cache keys differ");
        other.playerId=1;other.isLocal=false;game.players[1]=other;
        WorldSnapshot snapshot=SnapshotBuilder.build(game);
        PlayerState remote=snapshot.players[1];remote.apparelTypes=dressedState.apparelTypes.clone();remote.weaponType=ItemType.SWORD_NORMAL;
        GameClient client=new GameClient(game);
        var apply=GameClient.class.getDeclaredMethod("applySnapshot",WorldSnapshot.class);apply.setAccessible(true);
        apply.invoke(client,snapshot);
        require(other.currentOutfit().equals(entity.CharacterClass.HUNTER.outfit(false)),"remote outfit updates");
        remote.apparelTypes=new ItemType[p.apparelSlots.length];remote.weaponType=ItemType.FISTS;
        apply.invoke(client,snapshot);
        require(other.currentOutfit().equals(CharacterOutfit.BARE),"remote outfit removes layers");
        require(other.currentWeapon.isPlaceholder,"remote weapon removes correctly");
        com.esotericsoftware.kryo.Kryo kryo=new com.esotericsoftware.kryo.Kryo();
        kryo.register(PlayerState.class);kryo.register(ItemType.class);kryo.register(ItemType[].class);
        kryo.register(int[].class);kryo.register(String[].class);
        var output=new com.esotericsoftware.kryo.io.Output(4096,-1);kryo.writeObject(output,dressedState);
        PlayerState decoded=kryo.readObject(new com.esotericsoftware.kryo.io.Input(output.toBytes()),PlayerState.class);
        require(Arrays.equals(decoded.apparelTypes,dressedState.apparelTypes),"outfit survives network serialization");
        System.out.println("PASS: six visual slots, item identity, invalid slots, unequip/re-equip, safe undress, redress, snapshots, remote appearance updates, network serialization and player isolation.");
    }
}
