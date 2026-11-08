package net;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import entity.Entity;
import entity.Player;
import main.Gamepanel;
import monster.MON_GreenSlime;
import monster.MON_SlimeBoss;
import monster.MonsterType;
import world.WorldObject;

import java.awt.*;
import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;

public class GameClient {
    private Gamepanel gp;
    private Client client;
    private ConcurrentLinkedQueue<WorldSnapshot> pendingSnapshots = new ConcurrentLinkedQueue<>();
    private ConcurrentLinkedQueue<JoinAccepted> pendingJoinAccepted = new ConcurrentLinkedQueue<>();
    private ConcurrentLinkedQueue<GameEvent> pendingEvents = new ConcurrentLinkedQueue<>();
    private boolean warnedSceneryMismatch = false;

    private volatile boolean hostLost = false;

    public GameClient(Gamepanel gp){
        this.gp = gp;
    }

    public void connect(String hostAddress, int port) throws IOException {
        client = new Client();
        NetworkRegistration.register(client);
        client.start();
        System.out.println("[CLIENT] client.start() done, attempting connect to " + hostAddress + ":" + port);

        client.addListener(new Listener(){
            @Override
            public void connected(Connection connection){
                System.out.println("[CLIENT] connected() fired");
            }

            @Override
            public void disconnected(Connection connection){
                System.out.println("[CLIENT] disconnected() fired");
                hostLost = true;
            }

            @Override
            public void received(Connection connection, Object object){
                if(object instanceof JoinAccepted accepted){
                    pendingJoinAccepted.add(accepted);
                }
                if(object instanceof WorldSnapshot snapshot) {
                    pendingSnapshots.add(snapshot);
                }
                if(object instanceof GameEvent event){
                    pendingEvents.add(event);
                }
            }
        });

        client.connect(5000, hostAddress, port);
        System.out.println("[CLIENT] client.connect() call itself returned (no exception thrown)");

        JoinRequest request = new JoinRequest();
        request.playerName = gp.localPlayer().playerName;
        request.playerClass = gp.localPlayer().playerClass;
        request.gender = gp.localPlayer().gender;
        client.sendTCP(request);

        System.out.println("[CLIENT] JoinRequest sent");
    }

    public void sendInput(InputState input){
        if(client != null && client.isConnected()){
            client.sendTCP(input);
        }
    }

    // Called once per tick from Gamepanel.update(), on the game thread.
    public void processPendingJoinAccepted(){
        JoinAccepted accepted;
        while((accepted = pendingJoinAccepted.poll()) != null){
            int assigned = accepted.assignedPlayerId;
            Player existingLocal = gp.players[gp.localPlayerIndex];
            gp.players[gp.localPlayerIndex] = null;
            gp.localPlayerIndex = assigned;
            existingLocal.playerId = assigned;
            gp.players[assigned] = existingLocal;
            gp.ui.addMessage("Joined as player " + assigned + ".", java.awt.Color.green);
        }
    }

    public void applyPendingSnapshots(){
        WorldSnapshot latest = null;
        WorldSnapshot next;
        while((next = pendingSnapshots.poll()) != null){
            latest = next; // only the newest matters for now — interpolation buffering is Step 5
        }
        if(latest != null){
            gp.ui.addMessage("Snapshot received, tick " + latest.tick, java.awt.Color.cyan);
            applySnapshot(latest);
        }
    }

    private void applySnapshot(WorldSnapshot snapshot){
        for(PlayerState ps : snapshot.players){
            if(ps == null) continue;
            Player p = gp.players[ps.playerId];
            if(p == null){
                p = new Player(gp, gp.keyH);
                p.playerId = ps.playerId;
                p.isLocal = (ps.playerId == gp.localPlayerIndex);
                gp.players[ps.playerId] = p;
            }

            // Visual equipment must update for both the local player and remote players.
            boolean appearanceChanged=false;
            if(ps.weaponType!=null && object.ItemRegistry.typeFor(p.currentWeapon)!=ps.weaponType){
                p.currentWeapon=object.ItemRegistry.create(ps.weaponType,gp);p.attack=p.getAttack();appearanceChanged=true;
            }
            if(ps.shieldType!=null && object.ItemRegistry.typeFor(p.currentShield)!=ps.shieldType){
                p.currentShield=object.ItemRegistry.create(ps.shieldType,gp);p.defense=p.getDefense();
            }
            if(ps.apparelTypes!=null){
                for(int i=0;i<p.apparelSlots.length && i<ps.apparelTypes.length;i++){
                    if(object.ItemRegistry.typeFor(p.apparelSlots[i])!=ps.apparelTypes[i]){
                        p.apparelSlots[i]=object.ItemRegistry.create(ps.apparelTypes[i],gp);appearanceChanged=true;
                    }
                }
            }
            if(appearanceChanged)p.refreshWeaponAnimation();

            if(p.isLocal){
                p.reconcile(ps.worldX, ps.worldY, ps.direction, ps.HP, ps.maxHP, ps.MP, ps.maxMP, ps.isDead, ps.ackTick);
                for(int i = 0; i < ps.inventoryTypes.length; i++){
                    object.ItemType type = ps.inventoryTypes[i];
                    if(type == null){
                        p.inventorySlots[i] = null;
                        continue;
                    }
                    Entity existing = p.inventorySlots[i];
                    if(existing == null || object.ItemRegistry.typeFor(existing) != type){
                        p.inventorySlots[i] = object.ItemRegistry.create(type, gp);
                    }
                    if(p.inventorySlots[i] != null){
                        p.inventorySlots[i].stackCount = ps.inventoryStackCounts[i];
                    }
                }

                p.level = ps.level;
                p.exp = ps.exp;
                p.nextLevelExp = ps.nextLevelExp;
                p.strength = ps.strength;
                p.dexterity = ps.dexterity;
                p.intelligence = ps.intelligence;
                p.coin = ps.coin;
                p.rage = ps.rage;
                p.maxRage = ps.maxRage;
                p.arcaneCharge = ps.arcaneCharge;
                p.attack = p.getAttack();
                p.defense = p.getDefense();
                for(int i = 0; i < p.skills.length && ps.skillCooldowns != null && i < ps.skillCooldowns.length; i++){
                    if(p.skills[i] != null){
                        p.skills[i].cooldownRemaining = ps.skillCooldowns[i];
                    }
                }
                p.applySyncedEffects(ps.effectTypes, ps.effectDurations, ps.effectValues, ps.auraRgb);
            } else {
                int previousHP = p.HP;
                p.playerName = ps.playerName;
                p.worldX = ps.worldX;
                p.worldY = ps.worldY;
                p.direction = ps.direction;
                p.HP = ps.HP;
                p.maxHP = ps.maxHP;
                p.rage = ps.rage;
                p.maxRage = ps.maxRage;
                p.MP = ps.MP;
                p.maxMP = ps.maxMP;
                p.animState = ps.animState;
                p.attacking = ps.attacking;
                p.isDead = ps.isDead;
                p.applySyncedEffects(ps.effectTypes, ps.effectDurations, ps.effectValues, ps.auraRgb);

                if(ps.playerClass != null && !ps.playerClass.equals(p.playerClass)){
                    p.playerClass = ps.playerClass;
                    if(p.playerClass.equals("mage")){
                        p.getMagePlayerImage();
                    } else {
                        p.getPlayerImage();
                    }
                    p.refreshWeaponAnimation();
                }

                if(!p.isDead && ps.HP != previousHP){
                    int delta = ps.HP - previousHP;
                    Color color = delta > 0 ? new Color(120, 220, 120) : Color.red;
                    gp.spawnFloatingText(p.worldX, p.worldY, (delta > 0 ? "+" : "") + delta, color);
                }
            }
        }

        for(int i = 0; i < snapshot.monsters.length; i++){
            MonsterState ms = snapshot.monsters[i];
            if(ms == null || !ms.alive){
                gp.monster[i] = null;
                continue;
            }
            if(gp.monster[i] == null || (ms.monsterType != null && MonsterType.of(gp.monster[i]) != ms.monsterType)){
                gp.monster[i] = (ms.monsterType != null) ? ms.monsterType.create(gp) : new MON_GreenSlime(gp);
                gp.monster[i].HP = ms.HP; // avoid a false "just got hit" trigger on first creation
            }

            Entity m = gp.monster[i];
            if(ms.HP < m.HP){
                // The HP delta IS the hit signal — no separate "you got hit" event needed
                m.flashing = true;
                m.flashCounter = 0;
                m.hpBarOn = true;
                m.hpBarCounter = 0;
                m.spawnHitParticles();
                gp.spawnFloatingText(m.worldX + gp.tileSize/2, m.worldY, String.valueOf(m.HP - ms.HP), new Color(255, 220, 80));
            }

            m.worldX = ms.worldX;
            m.worldY = ms.worldY;
            m.direction = ms.direction;
            m.HP = ms.HP;
            m.maxHP = ms.maxHP;
            m.dying = ms.dying;

            if(ms.isBoss && m instanceof MON_SlimeBoss boss){
                boss.bossState = ms.bossState;
                boss.bossStateTimer = ms.bossStateTimer;
            }
        }

        for(int i = 0; i < snapshot.objects.length; i++){
            net.ObjectState os = snapshot.objects[i];
            if(os == null){
                gp.obj[i] = null;
                continue;
            }
            if(gp.obj[i] == null || object.ItemRegistry.typeFor(gp.obj[i]) != os.type){
                gp.obj[i] = object.ItemRegistry.create(os.type, gp);
            }
            if(gp.obj[i] != null){
                gp.obj[i].worldX = os.worldX;
                gp.obj[i].worldY = os.worldY;
                gp.obj[i].stackCount = os.stackCount;
            }
        }

        for(int i = 0; i < snapshot.npcs.length; i++){
            net.NpcState ns = snapshot.npcs[i];
            if(ns == null || gp.npc[i] == null) continue;
            gp.npc[i].worldX = ns.worldX;
            gp.npc[i].worldY = ns.worldY;
            gp.npc[i].direction = ns.direction;
        }

        if(snapshot.sceneryState != null){
            int expected = 0;
            for(WorldObject o : gp.worldObjects){
                if(o.hasState()) expected++;
            }
            if(expected == snapshot.sceneryState.length){
                int i = 0;
                for(WorldObject o : gp.worldObjects){
                    if(o.hasState()) o.state = snapshot.sceneryState[i++];
                }
            } else if(!warnedSceneryMismatch){
                warnedSceneryMismatch = true;
                System.out.println("[CLIENT] scenery mismatch: host has " + snapshot.sceneryState.length
                        + " stateful objects, this client has " + expected + ". Use the same maps/ files.");
            }
        }
    }

    public void applyPendingEvents(){
        GameEvent event;
        while((event = pendingEvents.poll()) != null){
            gp.applyGameEventLocally(event.playerId, event.type, event.message, event.personalText);
        }
    }

    public void sendEvent(GameEvent event){
        if(client != null && client.isConnected()){
            client.sendTCP(event);
        }
    }

    public void sendInventoryAction(net.InventoryAction action){
        if(client != null && client.isConnected()){
            client.sendTCP(action);
        }
    }

    public boolean pollHostLost(){
        return hostLost;
    }

    public void disconnect(){
        if(client != null){
            client.stop();
        }
    }
}