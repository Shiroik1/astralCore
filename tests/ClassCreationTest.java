import art.*;
import entity.*;
import main.*;
import net.*;
import object.*;
import java.awt.event.KeyEvent;
import java.util.*;

/** Headless creation/design regression checks; uses the same classpath as AppearanceEquipmentTest. */
public class ClassCreationTest {
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        var game=new AppearanceEquipmentTest.QuietGame();Player p=game.localPlayer();
        require(CharacterClass.values().length==3,"three creation choices");
        require(CharacterClass.fromId("swordsman")==CharacterClass.WARRIOR,"legacy swordsman maps to Warrior");
        var navigation=KeyHandler.class.getDeclaredMethod("characterCreationStep",int.class);navigation.setAccessible(true);
        game.keyH.selectedClassIndex=0;
        navigation.invoke(game.keyH,KeyEvent.VK_A);require(game.keyH.selectedClassIndex==2,"A wraps backwards");
        navigation.invoke(game.keyH,KeyEvent.VK_D);require(game.keyH.selectedClassIndex==0,"D wraps forwards");
        navigation.invoke(game.keyH,KeyEvent.VK_D);require(game.keyH.selectedClassIndex==1,"Mage is selectable");
        navigation.invoke(game.keyH,KeyEvent.VK_W);require(game.keyH.selectedGenderIndex==1,"female selection");
        Set<Integer> portraits=new HashSet<>();
        for(CharacterClass choice:CharacterClass.values())for(int gender=0;gender<2;gender++){
            game.ui.pendingCharacterName="Test "+choice.label;
            game.ui.confirmCharacterCreation(choice.ordinal(),gender);
            require(p.playerClass.equals(choice.id),"confirmed class ID");
            require(p.gender.equals(gender==0?"male":"female"),"confirmed gender");
            require(p.currentOutfit().equals(choice.outfit(gender==1)),"preset matches actual equipment");
            portraits.add(AppearanceEquipmentTest.pixels(p.down1));
            for(int slot=3;slot<=Player.EQUIP_HEADPIECE;slot++){
                Entity item=p.equippedItem(slot);
                if(item==null)continue;
                require(ItemRegistry.typeFor(ItemRegistry.create(ItemRegistry.typeFor(item),game))==ItemRegistry.typeFor(item),"preset item registry roundtrip");
            }
            var state=SnapshotBuilder.build(game).players[0];
            require(state.gender.equals(p.gender) && state.playerClass.equals(p.playerClass),"class/gender snapshot");
        }
        require(portraits.size()==6,"six distinct designs at actual game resolution");
        p.setCharacterDesign("hunter","male");
        p.inventorySlots[1]=ItemRegistry.create(ItemType.MAGE_ROBE,game);
        p.tryEquipFromBag(1,Player.EQUIP_TORSO);
        require(p.currentOutfit().theme(CharacterOutfit.Slot.TORSO)==CharacterOutfit.Theme.CASTER,"mixed-class equipment retains its cut");
        require(p.currentOutfit().theme(CharacterOutfit.Slot.HAIR)==CharacterOutfit.Theme.LEATHER,"mixing robe preserves hair");
        var server=new GameServer(game);
        var join=GameServer.class.getDeclaredMethod("applyJoinRequestToPlayer",Player.class,JoinRequest.class);join.setAccessible(true);
        var request=new JoinRequest();request.playerClass="warrior";request.gender="female";request.playerName="Guest";
        join.invoke(server,p,request);
        require(p.currentOutfit().equals(CharacterClass.WARRIOR.outfit(true)),"same-class female join uses long hair");
        request.playerClass="mage";request.gender="male";join.invoke(server,p,request);
        require(p.currentOutfit().equals(CharacterClass.MAGE.outfit(false)),"male Mage join uses short hair");
        var remote=new Player(game,game.keyH);remote.playerId=1;remote.isLocal=false;game.players[1]=remote;
        var snapshot=SnapshotBuilder.build(game);var remoteState=snapshot.players[1];
        remoteState.playerClass="mage";remoteState.gender="female";
        p.setCharacterDesign("mage","female");
        remoteState.apparelTypes=SnapshotBuilder.build(game).players[0].apparelTypes;
        var apply=GameClient.class.getDeclaredMethod("applySnapshot",WorldSnapshot.class);apply.setAccessible(true);
        apply.invoke(new GameClient(game),snapshot);
        require(remote.gender.equals("female") && remote.playerClass.equals("mage"),"remote identity synchronizes");
        require(remote.currentOutfit().equals(CharacterClass.MAGE.outfit(true)),"remote outfit and hair synchronize");
        System.out.println("PASS: class navigation, six creation designs, legacy rename, mixed equipment, join presets and remote class/gender synchronization.");
    }
}
