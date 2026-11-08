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
    private ConcurrentHashMap<Integer, JoinRequest> pendingJoinRequests = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, Integer> connectionToPlayerId = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, Connection> pendingConnectionObjects = new ConcurrentHashMap<>();
    private ConcurrentLinkedQueue<Integer> pendingConnections = new ConcurrentLinkedQueue<>();
    private ConcurrentLinkedQueue<Integer> pendingDisconnections = new ConcurrentLinkedQueue<>();
    private ConcurrentLinkedQueue<Object[]> pendingInputs = new ConcurrentLinkedQueue<>();
    private ConcurrentLinkedQueue<GameEvent> pendingClientEvents = new ConcurrentLinkedQueue<>();
    private ConcurrentLinkedQueue<InventoryAction> pendingInventoryActions = new ConcurrentLinkedQueue<>();

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
                    Integer senderId = connectionToPlayerId.get(connection.getID());
                    boolean allowed = event.type == GameEventType.CHAT || event.type == GameEventType.RESPAWN_REQUEST;
                    if(senderId != null && allowed){
                        event.playerId = senderId; // never trust the id the client claims
                        pendingClientEvents.add(event);
                    }
                }
                if(object instanceof InventoryAction action){
                    Integer senderId = connectionToPlayerId.get(connection.getID());
                    if(senderId != null){
                        action.playerId = senderId;
                        pendingInventoryActions.add(action);
                    }
                }
                if(object instanceof JoinRequest request){
                    Integer existingId = connectionToPlayerId.get(connection.getID());
                    if(existingId != null){
                        Player p = gp.players[existingId];
                        if(p != null){
                            applyJoinRequestToPlayer(p, request);
                        }
                    } else {
                        pendingJoinRequests.put(connection.getID(), request);
                    }
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
                conn.close();
                continue;
            }
            connectionToPlayerId.put(connId, assignedId);

            Player p = new Player(gp, gp.keyH);
            p.playerId = assignedId;
            p.isLocal = false;
            gp.players[assignedId] = p;

            JoinRequest pendingRequest = pendingJoinRequests.remove(connId);
            if(pendingRequest != null){
                applyJoinRequestToPlayer(p, pendingRequest);
            }
            if(p.playerName == null || p.playerName.isEmpty()){
                p.playerName = "Player " + (assignedId + 1);
            }
            gp.ui.addMessage("Player " + assignedId + " connected.", java.awt.Color.green);

            JoinAccepted accepted = new JoinAccepted();
            accepted.assignedPlayerId = assignedId;
            conn.sendTCP(accepted);
            gp.quests.onPlayerJoined(p);
        }

        Integer playerId;
        while((playerId = pendingDisconnections.poll()) != null){
            gp.players[playerId] = null;
        }
    }

    private void applyJoinRequestToPlayer(Player p, JoinRequest request){
        if(request.playerName != null && !request.playerName.isEmpty()){
            p.playerName = request.playerName;
        }
        String classId=request.playerClass!=null?request.playerClass:p.playerClass;
        String genderId=request.gender!=null?request.gender:p.gender;
        p.setCharacterDesign(classId,genderId);
        p.setDefaultValues();
        p.setItems();
        p.refreshWeaponAnimation();
        gp.quests.onPlayerJoined(p);
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
            if(event.type == GameEventType.CHAT){
                Player sender = gp.players[event.playerId];
                String name = (sender != null && sender.playerName != null && !sender.playerName.isEmpty())
                        ? sender.playerName : "Player " + (event.playerId + 1);
                String text = (event.message == null) ? "" : event.message.trim();
                if(text.isEmpty()) continue;
                if(text.length() > 120) text = text.substring(0, 120);
                event.message = name + ": " + text;
                event.personalText = null;
            }
            gp.applyGameEventLocally(event.playerId, event.type, event.message, event.personalText);
            broadcastEvent(event); // pass it along to every connected client
        }
    }

    public void applyPendingInventoryActions(){
        InventoryAction action;
        while((action = pendingInventoryActions.poll()) != null){
            gp.applyInventoryActionLocally(action);
        }
    }

    public void stop(){
        if(server != null){
            server.stop();
        }
    }
}