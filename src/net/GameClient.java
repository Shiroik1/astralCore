package net;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import entity.Entity;
import entity.Player;
import main.Gamepanel;
import monster.MON_GreenSlime;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;

public class GameClient {
    private Gamepanel gp;
    private Client client;
    private ConcurrentLinkedQueue<WorldSnapshot> pendingSnapshots = new ConcurrentLinkedQueue<>();
    private ConcurrentLinkedQueue<JoinAccepted> pendingJoinAccepted = new ConcurrentLinkedQueue<>();
    private ConcurrentLinkedQueue<GameEvent> pendingEvents = new ConcurrentLinkedQueue<>();

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
            }

            @Override
            public void received(Connection connection, Object object){
                System.out.println("[CLIENT] received object: " + object.getClass().getSimpleName());
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
        client.sendTCP(new JoinRequest());
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

            if(p.isLocal){
                p.reconcile(ps.worldX, ps.worldY, ps.direction, ps.HP, ps.maxHP, ps.MP, ps.maxMP, ps.isDead, ps.ackTick);
                for(int i = 0; i < ps.inventoryTypeIds.length; i++){
                    String typeId = ps.inventoryTypeIds[i];
                    if(typeId == null){
                        p.inventorySlots[i] = null;
                        continue;
                    }
                    Entity existing = p.inventorySlots[i];
                    if(existing == null || !object.ItemRegistry.idFor(existing).equals(typeId)){
                        p.inventorySlots[i] = object.ItemRegistry.create(typeId, gp);
                    }
                    if(p.inventorySlots[i] != null){
                        p.inventorySlots[i].stackCount = ps.inventoryStackCounts[i];
                    }
                }

                if(ps.weaponTypeId != null && (p.currentWeapon == null || !object.ItemRegistry.idFor(p.currentWeapon).equals(ps.weaponTypeId))){
                    p.currentWeapon = object.ItemRegistry.create(ps.weaponTypeId, gp);
                    p.attack = p.getAttack();
                    p.getPlayerAttackImage();
                }
                if(ps.shieldTypeId != null && (p.currentShield == null || !object.ItemRegistry.idFor(p.currentShield).equals(ps.shieldTypeId))){
                    p.currentShield = object.ItemRegistry.create(ps.shieldTypeId, gp);
                    p.defense = p.getDefense();
                }
            } else {
                p.worldX = ps.worldX;
                p.worldY = ps.worldY;
                p.direction = ps.direction;
                p.HP = ps.HP;
                p.maxHP = ps.maxHP;
                p.MP = ps.MP;
                p.maxMP = ps.maxMP;
                p.animState = ps.animState;
                p.attacking = ps.attacking;
                p.isDead = ps.isDead;
            }
        }

        for(int i = 0; i < snapshot.monsters.length; i++){
            MonsterState ms = snapshot.monsters[i];
            if(ms == null || !ms.alive){
                gp.monster[i] = null;
                continue;
            }
            if(gp.monster[i] == null){
                gp.monster[i] = new MON_GreenSlime(gp);
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
            }

            m.worldX = ms.worldX;
            m.worldY = ms.worldY;
            m.direction = ms.direction;
            m.HP = ms.HP;
            m.maxHP = ms.maxHP;
            m.dying = ms.dying;
        }

        for(int i = 0; i < snapshot.objects.length; i++){
            net.ObjectState os = snapshot.objects[i];
            if(os == null){
                gp.obj[i] = null;
                continue;
            }
            if(gp.obj[i] == null || !object.ItemRegistry.idFor(gp.obj[i]).equals(os.typeId)){
                gp.obj[i] = object.ItemRegistry.create(os.typeId, gp);
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
}