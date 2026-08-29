package main;

import javax.swing.*;
import java.io.IOException;

public class Main {

    public static JFrame window;

    public static void main(String[] args) throws IOException {
        window = new JFrame();
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setResizable(false);
        window.setTitle("AstralCore");
        //window.setUndecorated(true);

        Gamepanel gamePanel = new Gamepanel();
        window.add(gamePanel);

        gamePanel.config.loadConfig();
        if(gamePanel.fullScreenOn){
            window.setUndecorated(true);
        }

        window.pack();

        window.setLocationRelativeTo(null);
        window.setVisible(true);

        gamePanel.setUpGame();
        gamePanel.startGameThread();
    }
}
