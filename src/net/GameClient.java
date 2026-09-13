package net;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
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
                if(object instanceof WorldSnapshot snapshot){
                    pendingSnapshots.add(snapshot);
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
            if(!p.isLocal){
                p.worldX = ps.worldX;
                p.worldY = ps.worldY;
                p.direction = ps.direction;
                p.HP = ps.HP;
                p.maxHP = ps.maxHP;
                p.MP = ps.MP;
                p.maxMP = ps.maxMP;
                p.animState = ps.animState;
                p.attacking = ps.attacking;
            }
        }

        for(int i = 0; i < snapshot.monsters.length; i++){
            MonsterState ms = snapshot.monsters[i];
            if(ms == null || !ms.alive){
                gp.monster[i] = null;
                continue;
            }
            if(gp.monster[i] == null){
                gp.monster[i] = new MON_GreenSlime(gp); // simplification: assumes one monster type for now
            }
            gp.monster[i].worldX = ms.worldX;
            gp.monster[i].worldY = ms.worldY;
            gp.monster[i].direction = ms.direction;
            gp.monster[i].HP = ms.HP;
            gp.monster[i].maxHP = ms.maxHP;
            gp.monster[i].dying = ms.dying;
        }
    }
}