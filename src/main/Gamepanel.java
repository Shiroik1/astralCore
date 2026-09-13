package main;

import entity.Entity;
import entity.Player;
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

    //GAME SETTINGS
    final int originalTileSize = 24; //16x16 tile
    final int scale = 2;

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
    public Player player = new Player(this, keyH);
    public Entity obj[] = new Entity[50];
    public Entity npc[] = new Entity[10];
    public Entity monster[] = new Entity[20];
    public InteractiveTile interactable[] = new InteractiveTile[50];
    public ArrayList<Entity> entityList = new ArrayList<>();
    public ArrayList<Entity> projectileList = new ArrayList<>();
    public ArrayList<Entity> particleList = new ArrayList<>();

    //GAME STATE
    public int gameState;
    public final int titleState = 0;
    public final int playState = 1;
    public final int pauseState = 2;
    public final int dialogueState = 3;
    public final int characterState = 4;
    public final int optionState = 5;
    public final int gameOverState = 6;

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
        gameState = titleState;

        tempScreen = new BufferedImage(screenWidth, screenHeight, BufferedImage.TYPE_INT_ARGB);
        g2 = (Graphics2D) tempScreen.getGraphics();

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

            if(timer >= 1000000000){
                fpsCounter = drawCount;
                drawCount =  0;
                timer = 0;
            }
        }
    }

    public void update(){

        if(hitStopCounter > 0){
            hitStopCounter--;
            return; // skip every entity's update this tick — drawToTempscreen()/drawToScreen() still run right after, so the frame stays visible and still, not black
        }

        if(gameState == playState){
            //PLAYER
            player.update();

            //NPC
            for(int i = 0; i < npc.length; i++){
                if(npc[i] != null){
                    npc[i].update();
                }
            }

            //MONSTER
            for(int i = 0; i< monster.length; i++){
                if(monster[i] != null){
                    if(monster[i].alive && !monster[i].dying){
                        monster[i].update();
                    }
                    if(!monster[i].alive){
                        monster[i].checkDrop();
                        monster[i] = null;
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
            for(int i = 0; i < interactable.length; i++){
                if(interactable[i] != null){
                    interactable[i].update();
                }
            }
        }
        else if(gameState == titleState || gameState == optionState || gameState == characterState || gameState == gameOverState){
            ui.update(mouseH.getScaledX(),mouseH.getScaledY(),mouseH.leftClicked);
        }
    }

    public void retry(){
        player.setDefaultPosition();
        player.resetHPandMP();
        assetSetter.setNPC();
        assetSetter.setMonster();
    }

    public void restart(){
        player.setDefaultValues();
        player.setDefaultPosition();
        player.resetHPandMP();
        player.setItems();
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
            entityList.add(player);

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

    public void playMusic(int i){
        music.setFile(i);
        music.play();
        music.loop();
    }

    public void stopMusic(){
        music.stop();
    }

    public void playSE(int i){
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
}
