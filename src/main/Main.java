package main;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class Main {
    public static JFrame window;
    public static void main(String[] args){
        SwingUtilities.invokeLater(()->{
            try{
                com.esotericsoftware.minlog.Log.set(com.esotericsoftware.minlog.Log.LEVEL_WARN);
                window=new JFrame("AstralCore");window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                window.setResizable(false);
                Gamepanel gamePanel=new Gamepanel();window.add(gamePanel);
                gamePanel.config.loadConfig();
                window.pack();window.setLocationRelativeTo(null);
                gamePanel.setUpGame();
                gamePanel.display.apply(gamePanel.fullScreenOn,gamePanel.display.selected());
                window.addWindowListener(new WindowAdapter(){
                    @Override public void windowClosing(WindowEvent e){
                        gamePanel.display.restoreDesktop();gamePanel.soundEffect.close();gamePanel.music.close();
                    }
                });
                gamePanel.startGameThread();
            }catch(Exception e){e.printStackTrace();}
        });
    }
}
