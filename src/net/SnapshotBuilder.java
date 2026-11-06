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

public class SnapshotBuilder {

    public static WorldSnapshot build(Gamepanel gp){
        WorldSnapshot snapshot = new WorldSnapshot();
        snapshot.tick = gp.currentTick;
        snapshot.players = buildPlayers(gp);
        snapshot.monsters = buildMonsters(gp);
        snapshot.objects = buildObjects(gp);
        snapshot.npcs = buildNpcs(gp);
        return snapshot;
    }

    private static PlayerState[] buildPlayers(Gamepanel gp){
        List<PlayerState> states = new ArrayList<>();
        for(Player p : gp.players){
            if(p == null) continue;

            PlayerState ps = new PlayerState();
            ps.playerId = p.playerId;
            ps.playerName = p.playerName;
            ps.playerClass = p.playerClass;
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
                ms.isBoss = true;
                ms.bossState = boss.bossState;
                ms.bossStateTimer = boss.bossStateTimer;
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
}