package main;

import java.awt.image.BufferedImage;
import java.io.*;

public class Config {
    Gamepanel gp;

    public Config(Gamepanel gp){
        this.gp = gp;
    }

    public void saveConfig() throws IOException {

        BufferedWriter bw = new BufferedWriter(new FileWriter("config.txt"));

        //Fullscreen
        if(gp.fullScreenOn){
            bw.write("On");
        }
        if(!gp.fullScreenOn){
            bw.write("Off");
        }

        bw.newLine();

        //Music Volume
        bw.write(String.valueOf(gp.music.volumeScale));
        bw.newLine();

        //SE Volume
        bw.write(String.valueOf(gp.soundEffect.volumeScale));
        bw.newLine();

        bw.close();
    }

    public void loadConfig() throws IOException {

        BufferedReader br = new BufferedReader(new FileReader("config.txt"));

        String s = br.readLine();

        //Fullscreen
        if(s.equals("On")){
            gp.fullScreenOn = true;
        }
        if(s.equals("Off")){
            gp.fullScreenOn = false;
        }

        //Music Volume
        s = br.readLine();
        gp.music.volumeScale = Integer.parseInt(s);

        //SE Volume
        s = br.readLine();
        gp.soundEffect.volumeScale = Integer.parseInt(s);

        br.close();
    }
}
