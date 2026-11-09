package main;

import net.GameEventType;
import monster.MonsterType;
import entity.Entity;
import entity.Monster;
import entity.Player;
import entity.SkillParticles;
import net.InputState;
import tile.TileManager;
import tiles_interactive.InteractiveTile;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.locks.LockSupport;
import world.SceneryLoader;
import world.WorldObject;
import world.AmbientFX;
import world.GrassEditor;
import world.GrassLayer;
import world.Lighting;

public class Gamepanel extends JPanel implements Runnable{

    //NETWORK
    public long currentTick = 0;
    public boolean isNetworked = false;
    public boolean isHost = false;
    public net.GameServer gameServer;
    public net.GameClient gameClient;
    public static final int NETWORK_PORT = 5001;
    public static final int SNAPSHOT_INTERVAL_TICKS = 2;

    //GAME SETTINGS
    final int originalTileSize = 24; //16x16 tile
    final int scale = 2;
    public boolean inventoryOpen = false;

    //SCREEN SETTINGS
    public final int tileSize =  originalTileSize * scale; //48x48 tile
    public final int maxScreenCol = 26;
    public final int maxScreenRow = 15;
    public final int screenWidth = tileSize * maxScreenCol; //1248 logical pixels
    public final int screenHeight = tileSize * maxScreenRow; //720 pixels

    //FULL SCREEN
    int screenWidth2 = screenWidth;
    int screenHeight2 = screenHeight;
    BufferedImage tempScreen;
    private final FrameBuffer frames = new FrameBuffer();
    private final java.util.concurrent.atomic.AtomicInteger paintedFrames = new java.util.concurrent.atomic.AtomicInteger();
    private long lastPaintedSequence;
    Graphics2D g2;
    public volatile boolean fullScreenOn = false;
    public final DisplaySettings display = new DisplaySettings(this);

    //WORLD SETTINGS
    public int maxWorldCol;
    public int maxWorldRow;

    //SYSTEM
    public TileManager tileM = new TileManager(this);
    public KeyHandler keyH = new KeyHandler(this);
    Sound soundEffect = new Sound();
    Sound music = new Sound();
    public CollisionChecker collisionChecker = new CollisionChecker(this);
    public AssetSetter assetSetter = new AssetSetter(this);
    public UI ui = new UI(this);
    public EventHandler eventHandler = new EventHandler(this);
    public AmbientFX ambient = new AmbientFX(this);
    public Lighting lighting = new Lighting(this);
    public GrassLayer grass = new GrassLayer(this);
    public GrassEditor grassEditor = new GrassEditor(this);
    Config config = new Config(this);
    private volatile Thread gameThread;
    public int hitStopCounter = 0;
    public int screenShakeCounter = 0;
    public int screenShakeIntensity = 0;
    private final java.util.Random shakeRandom = new java.util.Random();
    private int shakeOffsetX = 0;
    private int shakeOffsetY = 0;
    public final java.util.concurrent.ConcurrentLinkedQueue<RawInput> inputQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();
    public CommandProcessor commands = new CommandProcessor(this);
    public final quest.QuestManager quests = new quest.QuestManager(this);

    public final Merchant merchant=new Merchant(this);
    public final save.SaveStore saves=new save.SaveStore(this);
    private volatile boolean closeRequested;

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
    public Monster monster[] = new Monster[20];
    public InteractiveTile interactable[] = new InteractiveTile[50];
    public ArrayList<Entity> entityList = new ArrayList<>();
    public java.util.List<WorldObject> worldObjects = new ArrayList<>();
    public ArrayList<Entity> projectileList = new ArrayList<>();
    public ArrayList<Entity> particleList = new ArrayList<>();
    public java.util.List<entity.FloatingText> floatingTextList = new java.util.ArrayList<>();

    //GAME STATE
    public int gameState;
    public volatile boolean optionsOpen = false;
    public volatile boolean paused = false;

    public boolean isWorldFrozen(){
        return !isNetworked && (optionsOpen || paused);
    }

    public final int titleState = 0;
    public final int playState = 1;
    public final int characterState = 4;
    public final int characterSelectState = 5;


    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final long TICK_INTERVAL = NANOS_PER_SECOND / 60;
    private static final long MAX_TICK_BACKLOG = 3 * TICK_INTERVAL;
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
        SceneryLoader.load(this);
        grass.load();
        preloadGameplayAssets();
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

    }

    public void preloadGameplayAssets(){
        MonsterType.preload(this);
        quests.prepare();
        merchant.prepare();
        new object.OBJ_slimebolt(this,true);
        for(object.ItemType type:object.ItemType.values())
            if(type.name().startsWith("ROYAL_") || type.name().startsWith("HELL_") || type==object.ItemType.SLIME_ESSENCE)
                object.ItemRegistry.create(type,this);
        new object.OBJ_slimebolt(this); // first boss spray already has its jelly frames
        for(object.ItemType type:new object.ItemType[]{object.ItemType.BRONZE_COIN,object.ItemType.HEART,
                object.ItemType.CRYSTAL,object.ItemType.POTION_RED}){
            object.ItemRegistry.create(type,this); // decode common event/drop artwork before play
        }
    }

    public void setFullScreen(){ display.apply(true,display.selected()); }

    public synchronized void startGameThread(){
        if(gameThread != null) return;
        gameThread = new Thread(this, "AstralCore game loop");
        gameThread.start();
    }

    @Override
    public void run() {
        Thread running = Thread.currentThread();
        long sampledAt = System.nanoTime();
        long fpsSample = sampledAt;
        long nextFrame = sampledAt + TICK_INTERVAL;
        long pending = 0;

        try {
            while(gameThread == running && !running.isInterrupted()){
                long now = System.nanoTime();
                // Drop excess time after a stall so the world cannot replay a long burst of updates.
                pending = Math.min(pending + (now - sampledAt), MAX_TICK_BACKLOG);
                sampledAt = now;
                while(pending >= TICK_INTERVAL && gameThread == running && !running.isInterrupted()){
                    update();
                    pending -= TICK_INTERVAL;
                }
                if(gameThread != running || running.isInterrupted()) break;

                now = System.nanoTime();
                if(now - nextFrame >= 0){
                    long frameInterval = NANOS_PER_SECOND / display.renderFps();
                    nextFrame += frameInterval;
                    if(now - nextFrame >= 0) nextFrame = now + frameInterval;
                    drawToTempscreen();
                    presentGameFrame();
                }

                now = System.nanoTime();
                if(now - fpsSample >= NANOS_PER_SECOND){
                    fpsCounter = (int)Math.round(paintedFrames.getAndSet(0) * (double)NANOS_PER_SECOND / (now - fpsSample));
                    fpsSample = now;
                }
                long untilTick = TICK_INTERVAL - pending - (now - sampledAt);
                long untilFrame = nextFrame - now;
                long wait = Math.min(untilTick, untilFrame);
                if(wait > 0) LockSupport.parkNanos(wait);
            }
        } catch(IOException e){
            throw new RuntimeException(e);
        } finally {
            synchronized(this){
                if(gameThread == running) gameThread = null;
            }
        }
    }

    public void update(){

        if(closeRequested){
            closeRequested=false;
            if(saves.saveActive()){
                shutdownNetwork();gameThread=null;
                javax.swing.SwingUtilities.invokeLater(()->{
                    display.restoreDesktop();soundEffect.close();music.close();
                    if(Main.window!=null)Main.window.dispose();System.exit(0);
                });
            }else ui.showTitleMessage(saves.error());
            return;
        }

        currentTick++;

        processInput();

        if(hitStopCounter > 0){
            hitStopCounter--;
        }

        ui.update(mouseH.getScaledX(),mouseH.getScaledY(),mouseH.leftClicked);

        if(gameState == playState && isWorldFrozen()){
            Player local=localPlayer();
            if(local!=null && local.isRanger())local.cancelBowDraw();
        }

        if(gameState == playState && !isWorldFrozen()){

            // 1. Reconcile FIRST, using only inputs already buffered from previous ticks
            if(isNetworked && !isHost){
                if(gameClient.pollHostLost()){
                    gameClient.processPendingJoinAccepted();gameClient.applyPendingSnapshots();gameClient.applyPendingEvents();
                    boolean saved=saves.saveActive();
                    if(saved)saves.deactivate();else saves.detachAfterDisconnect();
                    shutdownNetwork();
                    resetAfterNetworkSession();
                    stopMusic();
                    gameState = titleState;
                    ui.showTitleMessage(saved?"Disconnected from host. Character saved.":saves.error());
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
                int hoveredIndex = findHoveredMonsterIndex(local);
                local.hoveredTarget = (hoveredIndex >= 0) ? monster[hoveredIndex] : null;
                localInput = InputState.captureFrom(keyH, mouseH, currentTick, hoveredIndex);
                localInput.aimWorldX = mouseH.getScaledX() - local.screenX + local.worldX;
                localInput.aimWorldY = mouseH.getScaledY() - local.screenY + local.worldY;
                java.util.Arrays.fill(keyH.skillKeyPressed, false); // skill presses are one-shot: consumed at capture
                if(local.merchantOpen || inventoryOpen || optionsOpen || paused || keyH.enteringChat){
                    localInput.suppressGameplay();
                    mouseH.leftClicked = false;
                }
                if(grassEditor.active){
                    localInput.leftHeld = false;
                    localInput.gameplaySuppressed = true;
                    localInput.leftClicked = false;   // painting must not swing the weapon
                    mouseH.leftClicked = false;
                    localInput.targetedMonsterIndex = -1;
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
                    quests.updatePlayer(p);
                }
            }

            quests.activateArea(quests.hasDungeonPlayers());
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
                    if(quests.isBossIntro()){
                        monster[i].updateAnimation();
                    }else if(simulateWorld){
                        if(monster[i].alive){
                            monster[i].update();
                        }
                        if(!monster[i].alive){
                            removeDefeatedMonster(i);
                        }
                    } else {
                        monster[i].updateAnimation();
                    }
                }
            }

            if(simulateWorld)quests.update();

            //PROJECTILE
            for(int i = 0; i< projectileList.size(); i++){
                if(projectileList.get(i) != null){
                    if(projectileList.get(i).alive){
                        projectileList.get(i).update();
                    }
                    if(!projectileList.get(i).alive){
                        projectileList.remove(i--);
                    }
                }
            }

            //PARTICLES
            for(Entity particle : particleList){
                if(particle != null && particle.alive) particle.update();
            }
            particleList.removeIf(particle -> particle == null || !particle.alive);

            //INTERACTABLE TILES
            if(simulateWorld){
                for(int i = 0; i < interactable.length; i++){
                    if(interactable[i] != null){
                        interactable[i].update();
                    }
                }
            }

            //WORLD OBJECTS (visual only: trees and roofs fade when the player is behind them)
            for(WorldObject o : worldObjects){
                o.update();
            }

            //AMBIENT, LIGHTING AND GRASS
            ambient.update();
            lighting.update();
            grass.update();
            grassEditor.update();

            //FLOATING TEXT
            for(int i = floatingTextList.size() - 1; i >= 0; i--){
                if(!floatingTextList.get(i).update()){
                    floatingTextList.remove(i);
                }
            }

            if(isNetworked && isHost && currentTick % SNAPSHOT_INTERVAL_TICKS == 0){
                gameServer.broadcastSnapshot(net.SnapshotBuilder.build(this));
            }
        }
        updateScreenShake();
        saves.tick();
        // Keep the local scene active between updates, including when a frame is skipped.
        quests.activateArea(localPlayer().inWell);
    }

    public void requestClose(){closeRequested=true;}

    public boolean returnToTitle(){
        if(!saves.saveActive())return false;
        saves.deactivate();stopMusic();shutdownNetwork();resetAfterNetworkSession();gameState=titleState;return true;
    }

    // PIXEL BUFFER: world is drawn into a small image, then scaled up with nearest-neighbour
    public boolean pixelBuffer = false;
    private BufferedImage pixelBuf;
    private Graphics2D pixelG;
    private Graphics2D screenG;

    private void beginPixelBuffer(){
        int s = entity.CharacterSprites.PIXEL_SCALE;
        if(pixelBuf == null){
            pixelBuf = new BufferedImage(screenWidth / s, screenHeight / s, BufferedImage.TYPE_INT_ARGB);
            pixelG = pixelBuf.createGraphics();
            pixelG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            pixelG.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            pixelG.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        }
        pixelG.setTransform(new java.awt.geom.AffineTransform());
        pixelG.setComposite(AlphaComposite.Src);
        pixelG.setColor(Color.black);
        pixelG.fillRect(0, 0, pixelBuf.getWidth(), pixelBuf.getHeight());
        pixelG.setComposite(AlphaComposite.SrcOver);
        pixelG.scale(1.0 / s, 1.0 / s);   // game code keeps drawing in normal screen pixels
        screenG = g2;
        g2 = pixelG;
    }

    private void endPixelBuffer(){
        g2 = screenG;
        Object oldInterpolation=g2.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.drawImage(pixelBuf, 0, 0, screenWidth, screenHeight, null);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterpolation==null
                ?RenderingHints.VALUE_INTERPOLATION_BILINEAR:oldInterpolation);
    }

    public void setPixelBuffer(boolean on){
        pixelBuffer = on;
        lighting.setDivisor(on ? entity.CharacterSprites.PIXEL_SCALE : 2);
    }

    public void drawToTempscreen() throws IOException {
        g2.setColor(Color.black);
        g2.fillRect(0, 0, screenWidth, screenHeight);

        if(gameState == playState){

            if(pixelBuffer) beginPixelBuffer();

            //SCREENSHAKE
            g2.translate(shakeOffsetX, shakeOffsetY);

            //TILE
            tileM.draw(g2);

            for(WorldObject o : worldObjects){
                if(o.onScreen()){
                    entityList.add(o);
                }
            }

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

            if(!quests.inDungeon())grass.drawBase(g2);
            entity.PriestCombat.drawSanctuaries(this,g2);

            //ADD ENTITIES TO THE LIST
            for(Player p : players){
                if(p != null && p.inWell==quests.inDungeon()){
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

            entityList.sort(Comparator.comparingInt(Entity::sortY));

            //DRAW ENTITIES
            for(int i = 0; i < entityList.size(); i++){
                entityList.get(i).draw(g2);
                if(!quests.inDungeon())grass.drawFront(g2, entityList.get(i));
            }

            for(Player p:players){
                if(p!=null && p.inWell==quests.inDungeon() && p.isBerserker())p.berserker.drawImpact(g2);
            }

            //EMPTY ENTITY LIST
            entityList.clear();

            for(entity.FloatingText ft : floatingTextList){
                ft.draw(g2);
            }

            if(!quests.inDungeon())ambient.drawLeaves(g2);
            grassEditor.drawBrush(g2);

            g2.translate(-shakeOffsetX, -shakeOffsetY);

            if(pixelBuffer){
                lighting.draw(g2, shakeOffsetX, shakeOffsetY);   // lit inside the small buffer
                endPixelBuffer();
            } else {
                lighting.draw(g2, shakeOffsetX, shakeOffsetY);
            }

        }
        // Direct captures still include the UI. The live presenter draws it into its own layer.
        if(Thread.currentThread()!=gameThread){
            Graphics2D overlay=(Graphics2D)g2.create();
            try{ui.draw(overlay);}finally{overlay.dispose();}
        }
    }

    private void presentGameFrame() throws IOException{
        if(tempScreen==null)return;
        DisplaySettings.Resolution resolution=display.frameResolution();
        if(frames.publish(tempScreen,resolution.width(),resolution.height(),display.interfaceResolution(),ui::draw))repaint();
    }

    public void drawToScreen(){
        if(tempScreen==null)return;
        DisplaySettings.Resolution resolution=display.frameResolution();
        if(frames.publish(tempScreen,resolution.width(),resolution.height()))repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics){
        try(FrameBuffer.Frame frame=frames.acquire()){
            if(frame==null){super.paintComponent(graphics);return;}
            // An opaque completed frame plus its letterbox margins paints every pixel.
            // Avoid clearing the entire high-DPI window again before this blit.
            display.paintFrame((Graphics2D)graphics,frame.image(),frame.overlay(),getWidth(),getHeight(),frame.sequence());
            if(frame.sequence()!=lastPaintedSequence){
                lastPaintedSequence=frame.sequence();paintedFrames.incrementAndGet();
            }
        }
    }

    public synchronized void playMusic(int i){
        music.setFile(i);
        music.play();
        music.loop();
    }

    public synchronized void stopMusic(){
        music.stop();
    }

    public void playSE(int i){ soundEffect.playEffect(i); }

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
        if(!saves.saveActive())throw new java.io.IOException(saves.error());
        net.PlayerState carried=save.CharacterState.capture(this,localPlayer());
        resetAfterNetworkSession();save.CharacterState.apply(this,localPlayer(),carried);
        localPlayer().setDefaultPosition();localPlayer().inWell=false;
        saves.beginGuestSession();
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

        for(WorldObject o : worldObjects){
            o.state = 0;
        }

        for(int i = 0; i < players.length; i++){
            players[i] = null;
        }
        localPlayerIndex = 0;
        local.playerId = 0;
        local.isLocal = true;
        local.isDead = false;
        local.resetForSceneTransition();
        inventoryOpen = false;
        optionsOpen = false;
        paused = false;
        players[0] = local;

        quests.reset();
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

    public void broadcastPlayerEvent(int playerId, GameEventType type, String ambientMessage, String personalMessage){
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

    public void applyGameEventLocally(int playerId, GameEventType type, String ambientMessage, String personalMessage){
        if(type == null) return;
        if(type==GameEventType.SKILL_EFFECT){
            if(playerId>=0 && playerId<players.length && players[playerId]!=null){
                Player caster=players[playerId];boolean previous=quests.inDungeon();quests.activateArea(caster.inWell);
                try {
                    SkillParticles.spawn(caster, personalMessage);
                } finally {
                    quests.activateArea(previous);
                }
            }
            return;
        }
        if(type==GameEventType.SHOP){
            if(playerId==localPlayerIndex && localPlayer()!=null)localPlayer().shopMessage=personalMessage;
            return;
        }

        if(type == GameEventType.QUEST_INTRO){
            Player participant=players[playerId];
            if(participant!=null && (participant.isLocal || !isNetworked || isHost))quests.showBossIntro(participant);
        }

        if(type == GameEventType.RESPAWN_REQUEST){
            if(!isNetworked || isHost){
                Player target = players[playerId];
                if(target != null && target.isDead){
                    target.performRespawn();
                }
            }
        }

        if(type == GameEventType.LEVELUP){
            Player leveledPlayer = players[playerId];
            if(leveledPlayer != null){
                leveledPlayer.spawnLevelUpBurst();
            }
        }

        if(ambientMessage != null){
            ui.addMessage(ambientMessage, colorForEventType(type));
        }
        if(playerId == localPlayerIndex && personalMessage != null && type != GameEventType.LEVELUP){
            Player p = localPlayer();
            if(p != null){
                if(p.inDialogue && quest.QuestManager.INTRO_TAG.equals(p.dialogueTag))
                    ui.addMessage(personalMessage.replace('\n',' '));
                else p.showPersonalNotification(personalMessage);
            }
        }
    }

    private Color colorForEventType(GameEventType type){
        return switch(type){
            case LEVELUP -> new Color(255, 215, 0);
            case KILL -> Color.yellow;
            case DAMAGE -> Color.orange;
            case PICKUP -> new Color(120, 220, 120);
            case DEATH -> Color.red;
            case HEAL -> new Color(120, 200, 255);
            case SKILL -> new Color(200, 170, 255);
            case CHAT -> new Color(235,235,235);
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
        if(action.actionType == null || action.playerId<0 || action.playerId>=players.length) return;
        Player target = players[action.playerId];
        if(target == null) return;

        boolean previousArea=quests.inDungeon();quests.activateArea(target.inWell);
        try{
        switch(action.actionType){
            case EQUIP -> target.tryEquipFromBag(action.sourceIndex, action.slotType);
            case SWAP -> target.swapOrMergeBagSlots(action.sourceIndex, action.targetIndex);
            case UNEQUIP_TO_BAG -> target.unequipToBagIndex(action.slotType, action.targetIndex);
            case UNEQUIP_TO_WORLD -> target.unequipToWorld(action.slotType);
            case DROP -> target.dropBagItemToWorld(action.sourceIndex);
            case USE -> target.useInventoryItem(action.sourceIndex);
            case UNDRESS -> target.undress();
            case BUY,SELL,SHOP_CLOSE -> merchant.transact(target,action);
        }
        }finally{quests.activateArea(previousArea);}
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

        WorldObject near = collisionChecker.findInteractable(local);
        for(WorldObject o : worldObjects){
            o.highlighted = (o == near);
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

    private void removeDefeatedMonster(int index){
        Monster defeated=monster[index];
        if(defeated==null || defeated.alive)return;
        defeated.checkDrop();monster[index]=null;
        quests.onMonsterDefeated(defeated);
    }

    private int findHoveredMonsterIndex(Player local){
        int mouseWorldX = mouseH.getScaledX() - local.screenX + local.worldX;
        int mouseWorldY = mouseH.getScaledY() - local.screenY + local.worldY;
        int pickRadius = tileSize * 3 / 2; // forgiving: the sprite is bigger than the hitbox

        int best = -1;
        long bestDist = Long.MAX_VALUE;
        for(int i = 0; i < monster.length; i++){
            Entity m = monster[i];
            if(m == null || !m.alive || m.dying) continue;

            int centerX = m.worldX + m.solidArea.x + m.solidArea.width / 2;
            int centerY = m.worldY + m.solidArea.y + m.solidArea.height / 2;
            long dx = mouseWorldX - centerX;
            long dy = mouseWorldY - centerY;
            long dist = dx * dx + dy * dy;
            if(dist <= (long) pickRadius * pickRadius && dist < bestDist){
                bestDist = dist;
                best = i;
            }
        }
        return best;
    }

    // The ONLY place input handlers run. The EDT just records; this drains on the game thread.
    private void processInput(){
        mouseH.leftClicked = false;   // one-shot flags live exactly one tick
        mouseH.rightClicked = false;

        RawInput in;
        while((in = inputQueue.poll()) != null){
            switch(in.kind){
                case KEY_PRESSED -> keyH.handleKeyPressed(in.code);
                case KEY_RELEASED -> keyH.handleKeyReleased(in.code);
                case KEY_TYPED -> keyH.handleKeyTyped(in.ch);
                case MOUSE_PRESSED -> mouseH.handleMousePressed(in.code);
                case MOUSE_RELEASED -> mouseH.handleMouseReleased(in.code);
                case MOUSE_WHEEL -> mouseH.handleWheel(in.code);
            }
        }
    }

    private void updateScreenShake(){
        if(screenShakeCounter > 0){
            shakeOffsetX = shakeRandom.nextInt(screenShakeIntensity * 2 + 1) - screenShakeIntensity;
            shakeOffsetY = shakeRandom.nextInt(screenShakeIntensity * 2 + 1) - screenShakeIntensity;
            screenShakeCounter--;
            if(screenShakeCounter <= 0){
                screenShakeIntensity = 0;
            }
        } else {
            shakeOffsetX = 0;
            shakeOffsetY = 0;
        }
    }

    public void submitChat(String text){
        Player local = localPlayer();
        if(local == null) return;

        if(text.startsWith("/")){
            commands.execute(text);
            return;
        }

        if(isNetworked && !isHost){
            broadcastPlayerEvent(local.playerId, GameEventType.CHAT, text, null); // the host formats and relays it
        } else {
            String name = (local.playerName != null && !local.playerName.isEmpty()) ? local.playerName : "Player " + (local.playerId + 1);
            broadcastPlayerEvent(local.playerId, GameEventType.CHAT, name + ": " + text, null);
        }
    }

}
