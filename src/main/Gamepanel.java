package main;

import entity.Entity;
import entity.Player;
import object.SuperObject;
import tile.TileManager;

import javax.swing.*;
import java.awt.*;

public class Gamepanel extends JPanel implements Runnable{

    //GAME SETTINGS
    final int originalTileSize = 16; //16x16 tile
    final int scale = 3;

    //SCREEN SETTINGS
    public final int tileSize =  originalTileSize * scale; //48x48 tile
    public final int maxScreenCol = 22;
    public final int maxScreenRow = 16;
    public final int screenWidth = tileSize * maxScreenCol; //1024 pixels
    public final int screenHeight = tileSize * maxScreenRow; //768 pixels

    //WORLD SETTINGS
    public final int maxWorldCol = 100;
    public final int maxWorldRow = 100;

    //SYSTEM
    TileManager tileM = new TileManager(this);
    public KeyHandler keyH = new KeyHandler(this);
    Sound soundEffect = new Sound();
    Sound music = new Sound();
    public CollisionChecker collisionChecker = new CollisionChecker(this);
    public AssetSetter assetSetter = new AssetSetter(this);
    public UI ui = new UI(this);
    Thread gameThread;

    //ENTITY AND OBJECTS
    public Player player = new Player(this, keyH);
    public SuperObject obj[] = new SuperObject[50];
    public Entity npc[] = new Entity[10];

    //GAME STATE
    public int gameState;
    public final int titleState = 0;
    public final int playState = 1;
    public final int pauseState = 2;
    public final int dialogueState = 3;

    //FPS
    int FPS = 60;
    int fpsCounter = 0;

    public Gamepanel(){
        this.setPreferredSize(new Dimension(screenWidth, screenHeight));
        this.setBackground(Color.black);
        this.setDoubleBuffered(true);
        this.addKeyListener(keyH);
        this.setFocusable(true);
    }

    public void setUpGame(){
        assetSetter.setObject();
        assetSetter.setNPC();
        gameState = titleState;
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
                repaint();

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
        if(gameState == playState){
            //PLAYER
            player.update();

            //NPC
            for(int i = 0; i < npc.length; i++){
                if(npc[i] != null){
                    npc[i].update();
                }
            }
        }
        if(gameState == pauseState){
            //Nothing for now
        }
    }

    public void paintComponent(Graphics g){
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g;

        //TITLE SCREEN
        if(gameState == titleState){
            ui.draw(g2);
        }
        //INGAME SCREEN
        else{
            //TILE Layer-0
            tileM.draw(g2);

            //OBJECTS Layer-1
            for(int i = 0; i < obj.length; i++){
                if(obj[i] != null){
                    obj[i].draw(g2, this);
                }
            }

            //NPCS Layer-2
            for(int i = 0; i < npc.length; i++){
                if(npc[i] != null){
                    npc[i].draw(g2);
                }
            }

            //PLAYER Layer-3
            player.draw(g2);

            //UI
            ui.draw(g2);
        }

        g2.dispose();
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
}
