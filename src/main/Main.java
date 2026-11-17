package main;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.*;

public class Main {
    public static JFrame window;

    /** Starts this application or command-line tool using the supplied arguments. */
    public static void main(String[] args) throws Exception {
        if (java.util.Arrays.asList(args).contains("--verify-install")) {
            System.setProperty("java.awt.headless", "true");
            InstallationCheck.run();
            return;
        }
        AppPaths.initializeLogging();
        SwingUtilities.invokeLater(() -> {
            try {
                com.esotericsoftware.minlog.Log.set(com.esotericsoftware.minlog.Log.LEVEL_WARN);
                window = new JFrame("AstralCore v" + AppInfo.VERSION);
                window.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
                window.setResizable(false);
                Gamepanel gamePanel = new Gamepanel();
                window.add(gamePanel);
                gamePanel.config.loadConfig();
                window.pack();
                window.setLocationRelativeTo(null);
                gamePanel.setUpGame();
                gamePanel.display.apply(gamePanel.fullScreenOn, gamePanel.display.selected());
                window.addWindowListener(
                        new WindowAdapter() {
                            /** Performs the window closing operation for . */
                            @Override
                            public void windowClosing(WindowEvent e) {
                                gamePanel.requestClose();
                            }
                        });
                gamePanel.startGameThread();
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(
                        null,
                        "AstralCore could not start.\n"
                                + e.getMessage()
                                + "\nLog: "
                                + AppPaths.logFile(),
                        "AstralCore",
                        JOptionPane.ERROR_MESSAGE);
                if (window != null) window.dispose();
                System.exit(1);
            }
        });
    }
}
