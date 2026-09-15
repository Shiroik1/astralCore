package net;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import entity.Player;
import main.Gamepanel;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class GameServer {
    private Gamepanel gp;
    private Server server;
    private ConcurrentHashMap<Integer, Integer> connectionToPlayerId = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, Connection> pendingConnectionObjects = new ConcurrentHashMap<>();
    private ConcurrentLinkedQueue<Integer> pendingConnections = new ConcurrentLinkedQueue<>();
    private ConcurrentLinkedQueue<Integer> pendingDisconnections = new ConcurrentLinkedQueue<>();
    private ConcurrentLinkedQueue<Object[]> pendingInputs = new ConcurrentLinkedQueue<>();
    private ConcurrentLinkedQueue<GameEvent> pendingClientEvents = new ConcurrentLinkedQueue<>();

    public GameServer(Gamepanel gp){
        this.gp = gp;
    }

    public void start(int port) throws IOException {
        server = new Server();
        NetworkRegistration.register(server);

        server.addListener(new Listener(){
            @Override
            public void connected(Connection connection){
                System.out.println("[HOST] connected() fired, raw connection id=" + connection.getID());
                pendingConnectionObjects.put(connection.getID(), connection);
                pendingConnections.add(connection.getID());
            }

            @Override
            public void received(Connection connection, Object object){
                if(object instanceof InputState input){
                    Integer playerId = connectionToPlayerId.get(connection.getID());
                    if(playerId != null){
                        pendingInputs.add(new Object[]{playerId, input});
                    }
                }
                if(object instanceof GameEvent event){
                    pendingClientEvents.add(event);
                }
            }

            @Override
            public void disconnected(Connection connection){
                System.out.println("[HOST] disconnected() fired for connection id=" + connection.getID());
                Integer playerId = connectionToPlayerId.remove(connection.getID());
                if(playerId != null){
                    pendingDisconnections.add(playerId);
                }
            }
        });

        server.start();
        server.bind(port);
        System.out.println("[HOST] server.bind + server.start completed on port " + port);
    }

    // Called once per tick from Gamepanel.update(), on the game thread —
    // this is the ONLY place that touches gp.players[] for connects/disconnects,
    // so it can never race the simulation loop mid-tick.
    public void processConnectionEvents(){
        Integer connId;
        while((connId = pendingConnections.poll()) != null){
            Connection conn = pendingConnectionObjects.remove(connId);
            if(conn == null) continue;

            int assignedId = assignNextFreePlayerId();
            if(assignedId == -1){
                conn.close(); // session full
                continue;
            }
            connectionToPlayerId.put(connId, assignedId);

            Player p = new Player(gp, gp.keyH);
            p.playerId = assignedId;
            p.isLocal = false;
            gp.players[assignedId] = p;
            gp.ui.addMessage("Player " + assignedId + " connected.", java.awt.Color.green);

            JoinAccepted accepted = new JoinAccepted();
            accepted.assignedPlayerId = assignedId;
            conn.sendTCP(accepted);
        }

        Integer playerId;
        while((playerId = pendingDisconnections.poll()) != null){
            gp.players[playerId] = null;
        }
    }

    private int assignNextFreePlayerId(){
        for(int i = 0; i < gp.players.length; i++){
            if(i == gp.localPlayerIndex) continue;
            if(gp.players[i] == null) return i;
        }
        return -1;
    }

    public void applyPendingInputs(){
        java.util.Map<Integer, InputState> merged = new java.util.HashMap<>();

        Object[] item;
        while((item = pendingInputs.poll()) != null){
            int playerId = (int) item[0];
            InputState input = (InputState) item[1];

            InputState existing = merged.get(playerId);
            if(existing == null){
                merged.put(playerId, input);
            } else {
                existing.mergeOneShotFlags(input); // preserves any click/press seen anywhere in this batch
            }
        }

        for(var entry : merged.entrySet()){
            Player target = gp.players[entry.getKey()];
            if(target != null){
                target.applyInput(entry.getValue());
            }
        }
    }

    public void broadcastSnapshot(WorldSnapshot snapshot){
        server.sendToAllTCP(snapshot);
    }

    public void broadcastEvent(GameEvent event){
        server.sendToAllTCP(event);
    }

    public void relayPendingClientEvents(){
        GameEvent event;
        while((event = pendingClientEvents.poll()) != null){
            gp.applyGameEventLocally(event.playerId, event.type, event.message, event.personalText);
            broadcastEvent(event); // pass it along to every other connected client
        }
    }
}