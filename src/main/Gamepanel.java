package main;

import entity.Entity;
import entity.Player;
import net.InputState;
import tile.TileManager;
import tiles_interactive.InteractiveTile;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import static main.Main.window;

public class Gamepanel extends JPanel implements Runnable{

    //NETWORK
    public long currentTick = 0;
    public boolean isNetworked = false;
    public boolean isHost = false;
    public net.GameServer gameServer;
    public net.GameClient gameClient;
    public static final int NETWORK_PORT = 5001;
    public static final int SNAPSHOT_INTERNAL_TICKS = 2;

    //GAME SETTINGS
    final int originalTileSize = 24; //16x16 tile
    final int scale = 2;
    public boolean inventoryOpen = false;

    //SCREEN SETTINGS
    public final int tileSize =  originalTileSize * scale; //48x48 tile
    public final int maxScreenCol = 26;
    public final int maxScreenRow = 15;
    public final int screenWidth = tileSize * maxScreenCol; //1280 pixels
    public final int screenHeight = tileSize * maxScreenRow; //720 pixels

    //FULL SCREEN
    int screenWidth2 = screenWidth;
    int screenHeight2 = screenHeight;
    BufferedImage tempScreen;
    Graphics2D g2;
    public boolean fullScreenOn = false;

    //WORLD SETTINGS
    public int maxWorldCol;
    public int maxWorldRow;

    //SYSTEM
    TileManager tileM = new TileManager(this);
    public KeyHandler keyH = new KeyHandler(this);
    Sound soundEffect = new Sound();
    Sound music = new Sound();
    public CollisionChecker collisionChecker = new CollisionChecker(this);
    public AssetSetter assetSetter = new AssetSetter(this);
    public UI ui = new UI(this);
    public EventHandler eventHandler = new EventHandler(this);
    Config config = new Config(this);
    Thread gameThread;
    public int hitStopCounter = 0;
    public int screenShakeCounter = 0;
    public int screenShakeIntensity = 0;
    private final java.util.Random shakeRandom = new java.util.Random();

    //MOUSE SETTING
    public MouseHandler mouseH = new MouseHandler(this);

    //ENTITY AND OBJECTS
    public Player[] players = new Player[4];
    public int localPlayerIndex = 0;
    {
        players[localPlayerIndex] = new Player(this, keyH); // instance initializer — preserves the same construction timing the old field had, relative to keyH above it
    }

    public Player localPlayer(){
        return players[localPlayerIndex];
    }
    public Entity obj[] = new Entity[50];
    public Entity npc[] = new Entity[10];
    public Entity monster[] = new Entity[20];
    public InteractiveTile interactable[] = new InteractiveTile[50];
    public ArrayList<Entity> entityList = new ArrayList<>();
    public ArrayList<Entity> projectileList = new ArrayList<>();
    public ArrayList<Entity> particleList = new ArrayList<>();
    public java.util.List<entity.FloatingText> floatingTextList = new java.util.ArrayList<>();

    //GAME STATE
    public int gameState;
    public final int titleState = 0;
    public final int playState = 1;
    public final int pauseState = 2;
    public final int dialogueState = 3;
    public final int characterState = 4;
    public final int optionState = 5;
    public final int gameOverState = 6;

    public int slimesKilled = 0;
    public boolean bossSpawned = false;

    //FPS
    int FPS = 60;
    int fpsCounter = 0;

    public Gamepanel() throws IOException {
        this.setPreferredSize(new Dimension(screenWidth, screenHeight));
        this.setBackground(Color.black);
        this.setDoubleBuffered(true);
        this.addKeyListener(keyH);
        this.addMouseListener(mouseH);
        this.addMouseMotionListener(mouseH);
        this.setFocusable(true);
        this.addMouseListener(mouseH);
        this.addMouseMotionListener(mouseH);
        this.addMouseWheelListener(mouseH); // new
    }

    public void setUpGame(){
        assetSetter.setObject();
        assetSetter.setNPC();
        assetSetter.setMonster();
        assetSetter.setInteractiveTile();
        soundEffect.preloadFrom(1);
        gameState = titleState;

        tempScreen = new BufferedImage(screenWidth, screenHeight, BufferedImage.TYPE_INT_ARGB);
        g2 = (Graphics2D) tempScreen.getGraphics();

        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if(fullScreenOn){
            setFullScreen();
        }
    }

    public void setFullScreen(){
        //GET LOCAL SCREEN DEVICE
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice gd = ge.getDefaultScreenDevice();
        gd.setFullScreenWindow(window);

        //GET FULLSCREEN WIDTH AND HEIGHT
        screenWidth2 = window.getWidth();
        screenHeight2 = window.getHeight();
    }

    public void startGameThread(){
        gameThread = new Thread(this);
        gameThread.start();
    }

    @Override
    public void run() {

        double drawInterval = 1000000000/FPS;
        double delta = 0;
        long lastTime = System.nanoTime();
        long currentTime;

        //FPS Setting
        int drawCount = 0;
        long timer = 0;

        while(gameThread != null){
            currentTime = System.nanoTime();
            delta += (currentTime - lastTime) / drawInterval;
            timer += (currentTime - lastTime);
            lastTime = currentTime;

            if(delta >= 1) {
                //Update information such as player position
                update();
                //Draw update data on the screen
                try {
                    drawToTempscreen();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                drawToScreen();

                delta--;
                drawCount++;
            }
            else {
                long remaining = (long)((1 - delta) * drawInterval); // ns until the next tick is due
                if(remaining > 2_000_000){
                    try{
                        Thread.sleep(1);
                    } catch(InterruptedException e){
                        break;
                    }
                } else {
                    Thread.onSpinWait(); // spin the last 2 ms so frame timing stays accurate
                }
            }

            if(timer >= 1000000000){
                fpsCounter = drawCount;
                drawCount =  0;
                timer = 0;
            }
        }
    }

    public void update(){

        currentTick++;

        if(hitStopCounter > 0){
            hitStopCounter--;
        }

        ui.update(mouseH.getScaledX(),mouseH.getScaledY(),mouseH.leftClicked);

        if(gameState == playState){

            // 1. Reconcile FIRST, using only inputs already buffered from previous ticks
            if(isNetworked && !isHost){
                if(gameClient.pollHostLost()){
                    shutdownNetwork();
                    resetAfterNetworkSession();
                    stopMusic();
                    gameState = titleState;
                    ui.showTitleMessage("Disconnected from host.");
                    return;
                }
                gameClient.processPendingJoinAccepted();
                gameClient.applyPendingSnapshots();
                gameClient.applyPendingEvents();
            }

            // 2. THEN capture and apply this tick's fresh input
            Player local = localPlayer();
            InputState localInput = null;
            if(local != null){
                int hoveredIndex = -1;
                if(local.hoveredTarget != null){
                    for(int i = 0; i < monster.length; i++){
                        if(monster[i] == local.hoveredTarget){
                            hoveredIndex = i;
                            break;
                        }
                    }
                }
                localInput = InputState.captureFrom(keyH, mouseH, currentTick, hoveredIndex);
                if(inventoryOpen){
                    localInput.suppressGameplay();   // the host never sees movement, clicks or skills while the bag is open
                    mouseH.leftClicked = false;      // inventory clicks are never attacks
                    java.util.Arrays.fill(keyH.skillKeyPressed, false);
                }
                local.applyInput(localInput);
            }

            if(isNetworked){
                if(isHost){
                    gameServer.processConnectionEvents();
                    gameServer.applyPendingInputs();
                    gameServer.relayPendingClientEvents();
                    gameServer.applyPendingInventoryActions();
                } else {
                    if(localInput != null){
                        gameClient.sendInput(localInput);
                    }
                }
            }

            //PLAYER
            for(Player p : players){
                if(p != null){
                    p.update();
                }
            }

            updateHighlights();

            boolean simulateWorld = !isNetworked || isHost;

            //NPC
            for(int i = 0; i < npc.length; i++){
                if(npc[i] != null){
                    if(simulateWorld){
                        npc[i].update();
                    } else {
                        npc[i].updateAnimation();
                    }
                }
            }

            //MONSTER
            for(int i = 0; i< monster.length; i++){
                if(monster[i] != null){
                    if(simulateWorld){
                        if(monster[i].alive){
                            monster[i].update();
                        }
                        if(!monster[i].alive){
                            if(monster[i] instanceof monster.MON_GreenSlime){
                                slimesKilled++;
                                if(slimesKilled >= 5 && !bossSpawned){
                                    spawnSlimeBoss();
                                }
                            }
                            monster[i].checkDrop();
                            monster[i] = null;
                        }
                    } else {
                        monster[i].updateAnimation();
                    }
                }
            }

            //PROJECTILE
            for(int i = 0; i< projectileList.size(); i++){
                if(projectileList.get(i) != null){
                    if(projectileList.get(i).alive){
                        projectileList.get(i).update();
                    }
                    if(!projectileList.get(i).alive){
                        projectileList.remove(i);
                    }
                }
            }

            //PARTICLES
            for(int i = 0; i< particleList.size(); i++){
                if(particleList.get(i) != null){
                    if(particleList.get(i).alive){
                        particleList.get(i).update();
                    }
                    if(!particleList.get(i).alive){
                        particleList.remove(i);
                    }
                }
            }

            //INTERACTABLE TILES
            if(simulateWorld){
                for(int i = 0; i < interactable.length; i++){
                    if(interactable[i] != null){
                        interactable[i].update();
                    }
                }
            }

            //FLOATING TEXT
            for(int i = floatingTextList.size() - 1; i >= 0; i--){
                if(!floatingTextList.get(i).update()){
                    floatingTextList.remove(i);
                }
            }

            if(isNetworked && isHost && SNAPSHOT_INTERNAL_TICKS == 0){
                broadcastWorldSnapshot();
            }
        }
        else if(gameState == titleState || gameState == optionState || gameState == characterState || gameState == gameOverState){
            ui.update(mouseH.getScaledX(),mouseH.getScaledY(),mouseH.leftClicked);
        }
    }

    public void retry(){
        for(Player p : players){
            if(p != null){
                p.setDefaultPosition();
                p.resetHPandMP();
            }
        }
        assetSetter.setNPC();
        assetSetter.setMonster();
    }

    public void restart(){
        for(Player p : players){
            if(p != null){
                p.setDefaultPosition();
                p.resetHPandMP();
            }
        }
        assetSetter.setNPC();
        assetSetter.setMonster();
        assetSetter.setInteractiveTile();

    }

    public void drawToTempscreen() throws IOException {

        g2.setColor(Color.black);
        g2.fillRect(0, 0, screenWidth, screenHeight);

        int shakeX = 0;
        int shakeY = 0;
        if(screenShakeCounter > 0){
            shakeX = shakeRandom.nextInt(screenShakeIntensity * 2 + 1) - screenShakeIntensity;
            shakeY = shakeRandom.nextInt(screenShakeIntensity * 2 + 1) - screenShakeIntensity;
            screenShakeCounter--;
            if(screenShakeCounter <= 0){
                screenShakeIntensity = 0;
            }
        }

        //TITLE SCREEN
        if(gameState == titleState){
            ui.draw(g2);
        }
        //INGAME SCREEN
        else{

            //SCREENSHAKE
            g2.translate(shakeX, shakeY);

            //TILE
            tileM.draw(g2);

            //GROUND-LEVEL PARTICLE EFFECTS - always drawn beneath every entity
            for(int i = 0; i < particleList.size(); i++){
                if(particleList.get(i) != null && particleList.get(i).groundEffect){
                    particleList.get(i).draw(g2);
                }
            }

            for(int i = 0; i < interactable.length; i++){
                if(interactable[i] != null){
                    interactable[i].draw(g2);
                }
            }

            //ADD ENTITIES TO THE LIST
            for(Player p : players){
                if(p != null){
                    entityList.add(p);
                }
            }

            for(int i = 0; i < npc.length; i++){
                if(npc[i] != null){
                    entityList.add(npc[i]);
                }
            }

            for(int i = 0; i < obj.length; i++){
                if(obj[i] != null){
                    entityList.add(obj[i]);
                }
            }

            for(int i = 0; i < monster.length; i++){
                if(monster[i] != null){
                    entityList.add(monster[i]);
                }
            }

            for(int i = 0; i < particleList.size(); i++){
                if(particleList.get(i) != null && !particleList.get(i).groundEffect){
                    entityList.add(particleList.get(i));
                }
            }

            for(int i = 0; i < projectileList.size(); i++){
                if(projectileList.get(i) != null){
                    entityList.add(projectileList.get(i));
                }
            }



            //SORT
            Collections.sort(entityList, new Comparator<Entity>() {
                @Override
                public int compare(Entity e1, Entity e2) {
                    int result = Integer.compare(e1.worldY, e2.worldY);
                    return result;
                }
            });

            //DRAW ENTITIES
            for(int i = 0; i < entityList.size(); i++){
                entityList.get(i).draw(g2);
            }

            //EMPTY ENTITY LIST
            entityList.clear();

            for(entity.FloatingText ft : floatingTextList){
                ft.draw(g2);
            }

            g2.translate(-shakeX, -shakeY);

            //UI
            ui.draw(g2);
        }
    }

    public void drawToScreen(){
        Graphics g = getGraphics();
        g.drawImage(tempScreen, 0,0,screenWidth2,screenHeight2,null);
        g.dispose();
        Toolkit.getDefaultToolkit().sync();
    }

    public synchronized void playMusic(int i){
        music.setFile(i);
        music.play();
        music.loop();
    }

    public synchronized void stopMusic(){
        music.stop();
    }

    public synchronized void playSE(int i){
        soundEffect.setFile(i);
        soundEffect.play();
    }

    public void startHitStop(int duration){
        hitStopCounter = Math.max(hitStopCounter, duration); // a bigger hit shouldn't get cut short by a smaller one already in progress
    }

    public void startScreenShake(int duration, int intensity){
        screenShakeCounter = Math.max(screenShakeCounter, duration);
        screenShakeIntensity = Math.max(screenShakeIntensity, intensity);
    }

    public void hostGame(int port) throws java.io.IOException {
        System.out.println("[HOST] hostGame() called, port=" + port);
        if(isNetworked){
            System.out.println("[HOST] already networked, aborting");
            return;
        }
        isNetworked = true;
        isHost = true;
        gameServer = new net.GameServer(this);
        gameServer.start(port);
        System.out.println("[HOST] gameServer.start() returned successfully");
    }

    public void joinGame(String hostAddress, int port) throws java.io.IOException {
        System.out.println("[CLIENT] joinGame() called, target=" + hostAddress + ":" + port);
        isNetworked = true;
        isHost = false;
        gameClient = new net.GameClient(this);
        gameClient.connect(hostAddress, port);
        System.out.println("[CLIENT] gameClient.connect() returned successfully");
    }

    public void shutdownNetwork(){
        if(gameServer != null){
            gameServer.stop();
            gameServer = null;
        }
        if(gameClient != null){
            gameClient.disconnect();
            gameClient = null;
        }
        isNetworked = false;
        isHost = false;
    }

    public void resetAfterNetworkSession(){
        Player local = localPlayer();

        for(int i = 0; i < players.length; i++){
            players[i] = null;
        }
        localPlayerIndex = 0;
        local.playerId = 0;
        local.isLocal = true;
        local.isDead = false;
        local.inDialogue = false;
        inventoryOpen = false;
        players[0] = local;

        java.util.Arrays.fill(monster, null);
        java.util.Arrays.fill(obj, null);
        java.util.Arrays.fill(npc, null);
        projectileList.clear();
        particleList.clear();
        floatingTextList.clear();

        local.setDefaultPosition();
        local.resetHPandMP();
        assetSetter.setNPC();
        assetSetter.setMonster();
    }

    private void broadcastWorldSnapshot(){
        java.util.List<net.PlayerState> playerStates = new java.util.ArrayList<>();
        for(Player p : players){
            if(p == null) continue;
            net.PlayerState ps = new net.PlayerState();
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
            playerStates.add(ps);
            String[] invTypeIds = new String[p.inventorySlots.length];
            int[] invStackCounts = new int[p.inventorySlots.length];
            for(int i = 0; i < p.inventorySlots.length; i++){
                Entity slot = p.inventorySlots[i];
                if(slot != null){
                    invTypeIds[i] = object.ItemRegistry.idFor(slot);
                    invStackCounts[i] = slot.stackCount;
                }
            }
            ps.inventoryTypeIds = invTypeIds;
            ps.inventoryStackCounts = invStackCounts;
            ps.weaponTypeId = (p.currentWeapon != null) ? object.ItemRegistry.idFor(p.currentWeapon) : null;
            ps.shieldTypeId = (p.currentShield != null) ? object.ItemRegistry.idFor(p.currentShield) : null;
        }

        net.MonsterState[] monsterStates = new net.MonsterState[monster.length];
        for(int i = 0; i < monster.length; i++){
            if(monster[i] == null) continue;
            net.MonsterState ms = new net.MonsterState();
            ms.worldX = monster[i].worldX;
            ms.worldY = monster[i].worldY;
            ms.direction = monster[i].direction;
            ms.HP = monster[i].HP;
            ms.maxHP = monster[i].maxHP;
            ms.dying = monster[i].dying;
            ms.alive = monster[i].alive;
            if(monster[i] instanceof monster.MON_SlimeBoss boss){
                ms.isBoss = true;
                ms.bossState = boss.bossState;
                ms.bossStateTimer = boss.bossStateTimer;
            }
            monsterStates[i] = ms;
        }

        net.ObjectState[] objectStates = new net.ObjectState[obj.length];
        for(int i = 0; i < obj.length; i++){
            if(obj[i] == null) continue;
            net.ObjectState os = new net.ObjectState();
            os.typeId = object.ItemRegistry.idFor(obj[i]);
            os.worldX = obj[i].worldX;
            os.worldY = obj[i].worldY;
            os.stackCount = obj[i].stackCount;
            objectStates[i] = os;
        }

        net.NpcState[] npcStates = new net.NpcState[npc.length];
        for(int i = 0; i < npc.length; i++){
            if(npc[i] == null) continue;
            net.NpcState ns = new net.NpcState();
            ns.worldX = npc[i].worldX;
            ns.worldY = npc[i].worldY;
            ns.direction = npc[i].direction;
            npcStates[i] = ns;
        }

        net.WorldSnapshot snapshot = new net.WorldSnapshot();
        snapshot.tick = currentTick;
        snapshot.players = playerStates.toArray(new net.PlayerState[0]);
        snapshot.monsters = monsterStates;
        snapshot.objects = objectStates;
        snapshot.npcs = npcStates;

        gameServer.broadcastSnapshot(snapshot);
    }

    public void broadcastPlayerEvent(int playerId, String type, String ambientMessage, String personalMessage){
        if(isNetworked && !isHost){
            // Clients don't apply locally here and don't resolve authority —
            // send to the host, which applies it once and relays to everyone (including echoing back to us)
            net.GameEvent event = new net.GameEvent();
            event.playerId = playerId;
            event.type = type;
            event.message = ambientMessage;
            event.personalText = personalMessage;
            if(gameClient != null){
                gameClient.sendEvent(event);
            }
            return;
        }

        applyGameEventLocally(playerId, type, ambientMessage, personalMessage);
        if(isNetworked && isHost && gameServer != null){
            net.GameEvent event = new net.GameEvent();
            event.playerId = playerId;
            event.type = type;
            event.message = ambientMessage;
            event.personalText = personalMessage;
            gameServer.broadcastEvent(event);
        }
    }

    public void applyGameEventLocally(int playerId, String type, String ambientMessage, String personalMessage){
        if(type.equals("respawn_request")){
            if(!isNetworked || isHost){
                Player target = players[playerId];
                if(target != null){
                    target.performRespawn();
                }
            }
        }

        if(type.equals("levelup")){
            Player leveledPlayer = players[playerId];
            if(leveledPlayer != null){
                leveledPlayer.spawnLevelUpBurst();
            }
        }

        if(ambientMessage != null){
            ui.addMessage(ambientMessage, colorForEventType(type));
        }
        if(playerId == localPlayerIndex && personalMessage != null && !type.equals("levelup")){
            Player p = localPlayer();
            if(p != null){
                p.showPersonalNotification(personalMessage);
            }
        }
    }

    private Color colorForEventType(String type){
        return switch(type){
            case "levelup" -> new Color(255, 215, 0);
            case "kill" -> Color.yellow;
            case "damage" -> Color.orange;
            case "pickup" -> new Color(120, 220, 120);
            case "death" -> Color.red;
            case "heal" -> new Color(120, 200, 255);
            default -> Color.white;
        };
    }

    public void requestInventoryAction(net.InventoryAction action){
        if(!isNetworked || isHost){
            applyInventoryActionLocally(action);
        } else if(gameClient != null){
            gameClient.sendInventoryAction(action);
        }
    }

    public void applyInventoryActionLocally(net.InventoryAction action){
        Player target = players[action.playerId];
        if(target == null) return;

        switch(action.actionType){
            case "equip" -> target.tryEquipFromBag(action.sourceIndex, action.slotType);
            case "swap" -> target.swapOrMergeBagSlots(action.sourceIndex, action.targetIndex);
            case "unequip_to_bag" -> target.unequipToBagIndex(action.slotType, action.targetIndex);
            case "unequip_to_world" -> target.unequipToWorld(action.slotType);
            case "drop" -> target.dropBagItemToWorld(action.sourceIndex);
            case "use" -> target.useInventoryItem(action.sourceIndex);
        }
    }

    public void spawnFloatingText(int worldX, int worldY, String text, Color color){
        floatingTextList.add(new entity.FloatingText(this, worldX, worldY, text, color));
    }

    public void updateHighlights(){
        Player local = localPlayer();
        if(local == null) return;

        int mouseWorldX = mouseH.getScaledX() - local.screenX + local.worldX;
        int mouseWorldY = mouseH.getScaledY() - local.screenY + local.worldY;

        applyHoverHighlight(obj, mouseWorldX, mouseWorldY);
        applyHoverHighlight(npc, mouseWorldX, mouseWorldY);
        applyHoverHighlight(monster, mouseWorldX, mouseWorldY);
        applyHoverHighlight(interactable, mouseWorldX, mouseWorldY);

        for(Entity n : npc){
            if(n != null && collisionChecker.isInInteractRange(local, n)){
                n.highlighted = true;
            }
        }
    }

    private void applyHoverHighlight(Entity[] arr, int mouseWorldX, int mouseWorldY){
        for(Entity e : arr){
            if(e == null) continue;
            e.highlighted = false;
            Rectangle bounds = new Rectangle(e.worldX + e.solidArea.x, e.worldY + e.solidArea.y,
                    e.solidArea.width, e.solidArea.height);
            if(bounds.contains(mouseWorldX, mouseWorldY)){
                e.highlighted = true;
            }
        }
    }

    private void spawnSlimeBoss(){
        bossSpawned = true;
        for(int i = 0; i < monster.length; i++){
            if(monster[i] == null){
                monster.MON_SlimeBoss boss = new monster.MON_SlimeBoss(this);
                boss.worldX = tileSize * 24; // placeholder spawn point — move as needed
                boss.worldY = tileSize * 30;
                monster[i] = boss;
                ui.addMessage("The Slime King has awoken!", new Color(200, 60, 220));
                break;
            }
        }
    }

}
