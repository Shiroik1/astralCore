package net;

import entity.Entity;
import entity.Player;
import entity.StatusEffect;
import main.Gamepanel;
import monster.MON_SlimeBoss;
import object.ItemRegistry;
import object.ItemType;
import monster.MonsterType;

import java.util.ArrayList;
import java.util.List;
import world.WorldObject;

public class SnapshotBuilder {

    public static WorldSnapshot build(Gamepanel gp){
        WorldSnapshot snapshot = new WorldSnapshot();
        snapshot.tick = gp.currentTick;
        snapshot.quest = gp.quests.snapshot();
        snapshot.players = buildPlayers(gp);
        fillArea(snapshot,gp);
        if(gp.quests.hasOtherScene()){
            boolean current=gp.quests.inDungeon();gp.quests.activateArea(!current);
            snapshot.otherArea=new WorldSnapshot();fillArea(snapshot.otherArea,gp);gp.quests.activateArea(current);
        }
        return snapshot;
    }

    private static void fillArea(WorldSnapshot snapshot,Gamepanel gp){
        snapshot.inWellArea=gp.quests.inDungeon();
        snapshot.slimeProjectiles=buildSlimeProjectiles(gp);
        snapshot.monsters = buildMonsters(gp);
        snapshot.objects = buildObjects(gp);
        snapshot.npcs = buildNpcs(gp);
        snapshot.sceneryState = buildScenery(gp);
    }

    private static SlimeProjectileState[] buildSlimeProjectiles(Gamepanel gp){
        List<SlimeProjectileState> states=new ArrayList<>();
        for(Entity entity:gp.projectileList)if(entity instanceof object.OBJ_slimebolt bolt && bolt.alive){
            SlimeProjectileState state=new SlimeProjectileState();state.worldX=bolt.worldX;state.worldY=bolt.worldY;
            state.velocityX=bolt.velocityX();state.velocityY=bolt.velocityY();
            state.direction=bolt.direction;state.hell=bolt.hell;state.frame=bolt.spriteNum;states.add(state);
        }return states.toArray(SlimeProjectileState[]::new);
    }

    public static PlayerState[] buildPlayers(Gamepanel gp){
        List<PlayerState> states = new ArrayList<>();
        for(Player p : gp.players){
            if(p == null) continue;

            PlayerState ps = new PlayerState();
            ps.playerId = p.playerId;
            ps.merchantOpen=p.merchantOpen;ps.inWell=p.inWell;ps.resolve=p.resolve;
            ps.playerName = p.playerName;
            ps.playerClass = p.playerClass;
            ps.gender = p.gender;
            ps.hairStyle=p.hairStyle;ps.hairColor=p.hairColor;
            ps.worldX = p.worldX;
            ps.worldY = p.worldY;
            ps.direction = p.direction;
            ps.HP = p.HP;
            ps.maxHP = p.maxHP;
            ps.MP = p.MP;
            ps.maxMP = p.maxMP;
            ps.animState = p.animState;
            ps.attacking = p.attacking;
            ps.ackTick = p.lastProcessedInputTick;
            ps.isDead = p.isDead;
            ps.level = p.level;
            ps.exp = p.exp;
            ps.nextLevelExp = p.nextLevelExp;
            ps.strength = p.strength;
            ps.dexterity = p.dexterity;
            ps.intelligence = p.intelligence;
            ps.coin = p.coin;
            ps.rage = p.rage;
            ps.maxRage = p.maxRage;
            ps.arcaneCharge = p.arcaneCharge;

            ps.inventoryTypes = new ItemType[p.inventorySlots.length];
            ps.inventoryStackCounts = new int[p.inventorySlots.length];
            for(int i = 0; i < p.inventorySlots.length; i++){
                Entity slot = p.inventorySlots[i];
                if(slot != null){
                    ps.inventoryTypes[i] = ItemRegistry.typeFor(slot);
                    ps.inventoryStackCounts[i] = slot.stackCount;
                }
            }
            ps.weaponType = ItemRegistry.typeFor(p.currentWeapon);
            ps.shieldType = ItemRegistry.typeFor(p.currentShield);
            ps.apparelTypes = new ItemType[p.apparelSlots.length];
            for(int i=0;i<ps.apparelTypes.length;i++)ps.apparelTypes[i]=ItemRegistry.typeFor(p.apparelSlots[i]);

            ps.skillCooldowns = new int[p.skills.length];
            for(int i = 0; i < p.skills.length; i++){
                if(p.skills[i] != null){
                    ps.skillCooldowns[i] = p.skills[i].cooldownRemaining;
                }
            }

            int effectCount = p.statusEffects.size();
            ps.effectTypes = new String[effectCount];
            ps.effectDurations = new int[effectCount];
            ps.effectValues = new int[effectCount];
            for(int i = 0; i < effectCount; i++){
                StatusEffect e = p.statusEffects.get(i);
                ps.effectTypes[i] = e.type;
                ps.effectDurations[i] = e.duration;
                ps.effectValues[i] = e.value;
            }
            ps.auraRgb = (p.auraActive && p.auraColor != null) ? (p.auraColor.getRGB() & 0xFFFFFF) : -1;
            p.captureSavedCombat(ps);

            states.add(ps);
        }
        return states.toArray(new PlayerState[0]);
    }

    private static MonsterState[] buildMonsters(Gamepanel gp){
        MonsterState[] states = new MonsterState[gp.monster.length];
        for(int i = 0; i < gp.monster.length; i++){
            Entity m = gp.monster[i];
            if(m == null) continue;

            MonsterState ms = new MonsterState();
            ms.worldX = m.worldX;
            ms.worldY = m.worldY;
            ms.direction = m.direction;
            ms.HP = m.HP;
            ms.maxHP = m.maxHP;
            ms.dying = m.dying;
            ms.alive = m.alive;
            ms.monsterType = MonsterType.of(m);
            if(m instanceof MON_SlimeBoss boss){
                ms.chargeX=boss.chargeX();ms.chargeY=boss.chargeY();
                ms.isBoss = true;
                ms.bossState = boss.bossState;
                ms.bossStateTimer = boss.bossStateTimer;
                ms.lastAttackWasSlam = boss.lastAttackWasSlam;
            }
            states[i] = ms;
        }
        return states;
    }

    private static ObjectState[] buildObjects(Gamepanel gp){
        ObjectState[] states = new ObjectState[gp.obj.length];
        for(int i = 0; i < gp.obj.length; i++){
            Entity o = gp.obj[i];
            if(o == null) continue;

            ObjectState os = new ObjectState();
            os.type = ItemRegistry.typeFor(o);
            os.worldX = o.worldX;
            os.worldY = o.worldY;
            os.stackCount = o.stackCount;
            states[i] = os;
        }
        return states;
    }

    private static NpcState[] buildNpcs(Gamepanel gp){
        NpcState[] states = new NpcState[gp.npc.length];
        for(int i = 0; i < gp.npc.length; i++){
            Entity n = gp.npc[i];
            if(n == null) continue;

            NpcState ns = new NpcState();
            ns.worldX = n.worldX;
            ns.worldY = n.worldY;
            ns.direction = n.direction;
            states[i] = ns;
        }
        return states;
    }

    private static byte[] buildScenery(Gamepanel gp){
        int n = 0;
        for(WorldObject o : gp.worldObjects){
            if(o.hasState()) n++;
        }
        byte[] states = new byte[n];
        int i = 0;
        for(WorldObject o : gp.worldObjects){
            if(o.hasState()) states[i++] = (byte) o.state;
        }
        return states;
    }
}
