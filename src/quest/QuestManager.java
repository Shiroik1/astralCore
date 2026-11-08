package quest;

import entity.*;
import main.Gamepanel;
import monster.*;
import net.GameEventType;
import net.QuestState;
import object.*;
import tiles_interactive.InteractiveTile;
import world.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

/** Shared-party tutorial progress; only the host changes objectives or moves the party. */
public final class QuestManager {
    public enum Stage { NOT_STARTED, SLAY, BOSS_INTRO, KING, RETURN, COMPLETE, HELL_INTRO, HELL }
    public static final String INTRO_TAG="well_king_intro";
    public static final List<String> KING_INTRO=List.of(
            "The last slime dissolves.\nFor a moment, the well falls silent...",
            "A heavy ripple rolls through the dark water.\nSomething enormous is rising from the depths!",
            "Slime King: 'You dare disturb my kingdom?!'\nDefeat the Slime King!");
    private static final List<String> HELL_INTRO=List.of(
            "The essence dissolves into the altar.\nThe water turns a deep, burning red...",
            "Hell Slime rises from below!\nDefeat it to reclaim an essence and earn Hell weapons.");
    private final Gamepanel gp;
    private final QuestDefinition definition=QuestDefinition.WELL;
    private Stage stage=Stage.NOT_STARTED;
    private int kills,areaVersion,introPlayers,seenIntroVersion=-1,encounterVersion;
    private boolean inWell;
    private WellDungeon dungeon;
    private Scene townScene,wellScene;
    private final ItemType[] pendingRewards=new ItemType[4];
    private final java.util.ArrayList<Entity> pendingLoot=new java.util.ArrayList<>();
    private final Point[] returnPositions=new Point[4];
    private record Scene(List<WorldObject> scenery,Monster[] monsters,Entity[] npcs,Entity[] items,
                         InteractiveTile[] tiles,Lighting.Mood mood,boolean lightingEnabled,List<Entity> queuedLoot,List<Entity> projectiles,List<Entity> particles,List<FloatingText> texts) {}

    public QuestManager(Gamepanel gp){this.gp=gp;}
    public Stage stage(){return stage;}
    public int kills(){return kills;}
    public int areaVersion(){return areaVersion;}
    public boolean inDungeon(){return inWell;}
    public boolean isBossIntro(){return (stage==Stage.BOSS_INTRO || stage==Stage.HELL_INTRO) && inWell;}
    public QuestDefinition definition(){return definition;}
    private boolean authority(){return !gp.isNetworked || gp.isHost;}
    public void prepare(){if(dungeon==null)dungeon=new WellDungeon(gp);}
    public boolean isDungeonSolid(int col,int row){prepare();return dungeon.isSolid(col,row);}
    public void drawDungeon(Graphics2D g){prepare();dungeon.draw(g);}

    public List<String> oldManDialogue(List<String> introduction){
        return switch(stage){
            case NOT_STARTED -> introduction;
            case SLAY -> List.of("Your entrance exam is waiting below the village well.",
                    "Defeat ten slimes down there. You've cleared "+kills+" of ten.\nWalk to the well and press E to descend.");
            case BOSS_INTRO,KING -> List.of("A Slime King?! Under MY well?!\nDeal with it before it decides to come upstairs!");
            case RETURN -> List.of("You cleared the well... and defeated a Slime King?\nPerhaps I underestimated your entrance exam.",
                    "Well done, adventurer! Here are "+definition.rewardCoins()+" coins\nand "+definition.rewardExperience()+" experience, plus a powerful weapon for your class.");
            case COMPLETE,HELL_INTRO,HELL -> List.of("Slime Essence awakens the altar beneath the well.\nOffer one there to challenge the red Hell Slime.",
                    "It drops another essence and even stronger weapons.\nBe ready for its charge and three waves of fire-red droplets!");
        };
    }
    public void onOldManFinished(Player player){
        if(!authority() || inWell)return;
        if(stage==Stage.NOT_STARTED){
            stage=Stage.SLAY;
            gp.ui.addMessage("Quest accepted: "+definition.title(),new Color(244,213,137));
        }else if(stage==Stage.RETURN){
            stage=Stage.COMPLETE;
            for(Player member:gp.players)if(member!=null){
                member.coin+=definition.rewardCoins();member.grantExperience(definition.rewardExperience());
                ItemType weapon=OBJ_rewardweapon.forClass(member.playerClass,false);
                if(!member.addToInventory(ItemRegistry.create(weapon,gp)))pendingRewards[member.playerId]=weapon;
                gp.broadcastPlayerEvent(member.playerId,GameEventType.INTERACT,"Quest complete: "+definition.title(),
                        "Well cleared!\nReward: "+definition.rewardCoins()+" coins and "+definition.rewardExperience()+" experience.\n"+
                                ItemRegistry.create(weapon,gp).name+(pendingRewards[member.playerId]!=null?" awaits a free bag slot.":" added to your bag.")+"\nOffer Slime Essence at the well altar for a new challenge.");
            }
        }
    }
    public void enterWell(Player player){
        if(!authority() || player.isDead || player.inWell)return;
        if(stage==Stage.NOT_STARTED){
            gp.broadcastPlayerEvent(player.playerId,GameEventType.INTERACT,null,"Speak to the old man first.\nHe has a job for you beneath this well.");return;
        }
        returnPositions[player.playerId]=new Point(player.worldX,player.worldY);
        areaVersion++;activateArea(true);placeAtEntrance(player);
        gp.ui.addMessage("Entered the Well Depths.",new Color(183,212,222));
    }
    public void leaveWell(Player player){
        if(!authority() || player.isDead || !player.inWell || isBossIntro())return;
        player.resetForSceneTransition();player.inWell=false;areaVersion++;activateArea(false);
        Point spot=returnPositions[player.playerId];
        player.worldX=spot!=null?spot.x:25*gp.tileSize;player.worldY=spot!=null?spot.y:28*gp.tileSize;player.direction="down";
        gp.ui.addMessage("Returned to Astravale.",new Color(183,212,222));
    }
    public void placeAtEntrance(Player p){
        p.resetForSceneTransition();p.inWell=true;
        p.worldX=(WellDungeon.ENTRANCE_COL-1+p.playerId)*gp.tileSize;
        p.worldY=WellDungeon.ENTRANCE_ROW*gp.tileSize;p.direction="up";
        p.invincible=true;p.invincibleCounter=0;
    }
    private Scene capture(){
        return new Scene(List.copyOf(gp.worldObjects),gp.monster.clone(),gp.npc.clone(),gp.obj.clone(),
                gp.interactable.clone(),gp.lighting.mood(),gp.lighting.enabled,List.copyOf(pendingLoot),List.copyOf(gp.projectileList),List.copyOf(gp.particleList),List.copyOf(gp.floatingTextList));
    }
    private void restore(Scene scene){
        gp.worldObjects.clear();gp.worldObjects.addAll(scene.scenery);
        System.arraycopy(scene.monsters,0,gp.monster,0,gp.monster.length);
        System.arraycopy(scene.npcs,0,gp.npc,0,gp.npc.length);System.arraycopy(scene.items,0,gp.obj,0,gp.obj.length);
        System.arraycopy(scene.tiles,0,gp.interactable,0,gp.interactable.length);
        pendingLoot.clear();pendingLoot.addAll(scene.queuedLoot);
        gp.projectileList.clear();gp.projectileList.addAll(scene.projectiles);
        gp.particleList.clear();gp.particleList.addAll(scene.particles);
        gp.floatingTextList.clear();gp.floatingTextList.addAll(scene.texts);
        gp.lighting.setMood(scene.mood,true);gp.lighting.enabled=scene.lightingEnabled;
    }
    public void activateArea(boolean target){
        if(inWell==target)return;
        prepare();
        if(target){
            townScene=capture();inWell=true;
            if(wellScene!=null)restore(wellScene);
            else{
                pendingLoot.clear();gp.projectileList.clear();gp.particleList.clear();gp.floatingTextList.clear();
                Arrays.fill(gp.monster,null);Arrays.fill(gp.npc,null);Arrays.fill(gp.obj,null);Arrays.fill(gp.interactable,null);
                gp.worldObjects.clear();gp.worldObjects.addAll(dungeon.objects());
                gp.lighting.enabled=true;gp.lighting.setMood(Lighting.Mood.NIGHT,true);
                if(authority() && stage==Stage.SLAY){
                    for(int i=0;i<WellDungeon.SLIME_SPAWNS.length;i++){
                        int[] at=WellDungeon.SLIME_SPAWNS[i];MON_GreenSlime slime=new MON_GreenSlime(gp);
                        slime.worldX=at[0]*gp.tileSize;slime.worldY=at[1]*gp.tileSize;slime.questId=definition.id();gp.monster[i]=slime;
                    }
                }
            }
        }else{
            wellScene=capture();inWell=false;
            if(townScene!=null)restore(townScene);
        }
        gp.grassEditor.stop();
    }
    /** True means the dungeon handled the death; never apply the outdoor every-five-kills rule. */
    public boolean onMonsterDefeated(Monster monster){
        if(!inWell)return false;
        if(!authority() || !definition.id().equals(monster.questId))return true;
        if(stage==Stage.SLAY && monster instanceof MON_GreenSlime)kills=Math.min(definition.requiredKills(),kills+1);
        if(stage==Stage.HELL && monster instanceof MON_HellSlime){
            stage=Stage.COMPLETE;gp.projectileList.clear();
            gp.ui.addMessage("Hell Slime defeated! Claim its essence and Hell weapons.",new Color(255,176,110));
        }else if(stage==Stage.KING && monster instanceof MON_SlimeBoss){
            stage=Stage.RETURN;
            for(Player p:gp.players)if(p!=null)gp.broadcastPlayerEvent(p.playerId,GameEventType.INTERACT,
                    "The Slime King has been defeated!","The well is safe! Collect the Slime Essence it dropped.\nUse the ladder to return and report to the old man.");
        }
        return true;
    }
    public void update(){
        if(!authority())return;
        resetBossIfWiped();
        for(Player member:gp.players)if(member!=null && pendingRewards[member.playerId]!=null
                && Arrays.stream(member.inventorySlots).anyMatch(item->item==null)){
            ItemType reward=pendingRewards[member.playerId];
            if(member.addToInventory(ItemRegistry.create(reward,gp))){pendingRewards[member.playerId]=null;
                gp.broadcastPlayerEvent(member.playerId,GameEventType.PICKUP,null,ItemRegistry.create(reward,gp).name+" added to your bag.");}
        }
        flushLoot();
        if(inWell && stage==Stage.SLAY && kills==definition.requiredKills()){
            for(int i=0;i<gp.monster.length;i++)if(gp.monster[i]==null){
                MON_SlimeBoss king=new MON_SlimeBoss(gp);king.questId=definition.id();
                king.worldX=25*gp.tileSize;king.worldY=14*gp.tileSize;gp.monster[i]=king;
                beginIntro(false);
                break;
            }
        }
        if(isBossIntro()){
            for(int id=0;id<gp.players.length;id++)if(gp.players[id]==null || gp.players[id].isDead || !gp.players[id].inWell)introPlayers&=~(1<<id);
            if(introPlayers==0)stage=stage==Stage.HELL_INTRO?Stage.HELL:Stage.KING;
        }
    }
    public void showBossIntro(Player p){
        if(p==null || (p.isLocal && seenIntroVersion==encounterVersion) || (p.inDialogue && INTRO_TAG.equals(p.dialogueTag)))return;
        p.startScriptedDialogue(INTRO_TAG,stage==Stage.HELL_INTRO?HELL_INTRO:KING_INTRO);
        if(p.isLocal)seenIntroVersion=encounterVersion;
    }
    private void beginIntro(boolean hell){
        stage=hell?Stage.HELL_INTRO:Stage.BOSS_INTRO;introPlayers=0;encounterVersion++;
        gp.projectileList.clear();gp.startScreenShake(18,5);
        for(Player p:gp.players)if(p!=null && p.inWell && !p.isDead){introPlayers|=1<<p.playerId;showBossIntro(p);}
    }
    /** Altar interaction is host-owned; only a successful, unique summon consumes one essence. */
    public void summonHell(Player player){
        if(!authority() || !inWell || player.isDead || isBossIntro())return;
        if(stage!=Stage.COMPLETE){
            gp.broadcastPlayerEvent(player.playerId,GameEventType.INTERACT,null,
                    stage==Stage.RETURN?"Report to the old man before awakening the altar.":"The altar cannot be used during an encounter.");return;
        }
        int slot=-1;for(int i=0;i<gp.monster.length;i++)if(gp.monster[i]==null){slot=i;break;}
        if(slot<0 || Arrays.stream(gp.monster).anyMatch(m->m!=null && m.isBoss()))return;
        int essence=-1;
        for(int i=0;i<player.inventorySlots.length;i++)if(ItemRegistry.typeFor(player.inventorySlots[i])==ItemType.SLIME_ESSENCE){essence=i;break;}
        if(essence<0){gp.broadcastPlayerEvent(player.playerId,GameEventType.INTERACT,null,"Offer one Slime Essence to summon Hell Slime.\nThe Slime King and Hell Slime both drop it.");return;}
        MON_HellSlime hell=new MON_HellSlime(gp);hell.worldX=25*gp.tileSize;hell.worldY=14*gp.tileSize;hell.questId=definition.id();
        if(--player.inventorySlots[essence].stackCount<=0)player.inventorySlots[essence]=null;
        gp.monster[slot]=hell;beginIntro(true);
    }
    /** Preserve important loot even if every ground-item slot is occupied. */
    public void dropLoot(Entity item,int x,int y){
        item.worldX=x;item.worldY=y;pendingLoot.add(item);flushLoot();
    }
    private void flushLoot(){
        for(int i=0;i<gp.obj.length && !pendingLoot.isEmpty();i++)if(gp.obj[i]==null)gp.obj[i]=pendingLoot.removeFirst();
    }

    public void acknowledgeIntro(Player p){if(authority())introPlayers&=~(1<<p.playerId);}
    public void onPlayerJoined(Player p){
        if(!authority() || !inWell)return;
        placeAtEntrance(p);
        if(isBossIntro()){introPlayers|=1<<p.playerId;showBossIntro(p);}
    }
    public QuestState snapshot(){
        QuestState state=new QuestState();state.stage=stage.ordinal();state.kills=kills;state.inWell=inWell;
        state.areaVersion=areaVersion;state.encounterVersion=encounterVersion;state.introPlayers=introPlayers;
        state.pendingRewards=pendingRewards.clone();return state;
    }
    public void applySnapshot(QuestState state){
        if(state==null || authority())return;
        stage=Stage.values()[Math.max(0,Math.min(Stage.values().length-1,state.stage))];kills=state.kills;introPlayers=state.introPlayers;encounterVersion=state.encounterVersion;
        if(state.pendingRewards!=null)System.arraycopy(state.pendingRewards,0,pendingRewards,0,Math.min(4,state.pendingRewards.length));
        areaVersion=state.areaVersion;
        activateArea(state.inWell);
    }
    public void ensureLocalIntro(){
        Player p=gp.localPlayer();
        if(isBossIntro() && p!=null && (introPlayers&(1<<p.playerId))!=0)showBossIntro(p);
    }
    public String objective(){
        Player local=gp.localPlayer();
        if(local!=null && pendingRewards[local.playerId]!=null)return "Free a bag slot for your reward weapon.";
        return switch(stage){
            case NOT_STARTED -> "Speak to the old man.";
            case SLAY -> inWell?"Slay well slimes: "+kills+" / "+definition.requiredKills():"Enter the village well [E].";
            case BOSS_INTRO -> "Something is rising from the depths...";
            case KING -> "Defeat the Slime King.";
            case RETURN -> "Report back to the old man.";
            case COMPLETE -> inWell?"Offer essence at the altar [E].":"Bring Slime Essence to the well.";
            case HELL_INTRO -> "The essence awakens Hell Slime...";
            case HELL -> "Defeat Hell Slime.";
        };
    }
    public boolean hasOtherScene(){return inWell || wellScene!=null;}
    public List<Entity> queuedLootForSave(){return List.copyOf(pendingLoot);}
    public void restoreQueuedLoot(List<Entity> items){pendingLoot.clear();pendingLoot.addAll(items);}
    public Point returnPositionForSave(int playerId){
        Point p=returnPositions[playerId];return p==null?new Point(25*gp.tileSize,28*gp.tileSize):new Point(p);
    }
    /** Disk restore is authoritative; network snapshots continue to use applySnapshot(). */
    public void restoreSavedProgress(QuestState saved,Point returnPosition){
        stage=Stage.values()[saved.stage];kills=saved.kills;
        areaVersion=saved.areaVersion;encounterVersion=saved.encounterVersion;seenIntroVersion=-1;
        introPlayers=(stage==Stage.BOSS_INTRO || stage==Stage.HELL_INTRO)?1:0;
        Arrays.fill(pendingRewards,null);
        if(saved.pendingRewards!=null && saved.pendingRewards.length>0)pendingRewards[0]=saved.pendingRewards[0];
        returnPositions[0]=new Point(returnPosition);
    }
    public boolean hasDungeonPlayers(){return Arrays.stream(gp.players).anyMatch(p->p!=null && p.inWell);}
    public void updatePlayer(Player p){
        boolean previous=inWell;activateArea(p.inWell);
        try{p.update();}finally{activateArea(previous);}
    }
    public void respawnAtPlaza(Player p){
        activateArea(p.inWell);resetBossIfWiped();p.resetForSceneTransition();p.inWell=false;introPlayers&=~(1<<p.playerId);
        p.setDefaultPosition();p.resetHPandMP();p.isDead=false;p.rage=0;p.arcaneCharge=0;p.resolve=0;
        p.statusEffects.clear();p.auraActive=false;areaVersion++;activateArea(false);
    }
    private void resetBossIfWiped(){
        if(!inWell || Arrays.stream(gp.players).anyMatch(p->p!=null && p.inWell && !p.isDead && p.HP>0))return;
        for(Monster boss:gp.monster)if(boss instanceof MON_SlimeBoss king && king.alive && !king.dying && king.HP>0){
            king.resetEncounter();gp.projectileList.clear();gp.particleList.clear();
            if(isBossIntro()){stage=stage==Stage.HELL_INTRO?Stage.HELL:Stage.KING;introPlayers=0;}
        }
    }

    public void reset(){
        if(inWell)activateArea(false);
        stage=Stage.NOT_STARTED;kills=0;introPlayers=0;seenIntroVersion=-1;encounterVersion=0;townScene=null;wellScene=null;
        for(Player p:gp.players)if(p!=null)p.inWell=false;
        Arrays.fill(pendingRewards,null);pendingLoot.clear();
        Arrays.fill(returnPositions,null);areaVersion++;
    }
}
