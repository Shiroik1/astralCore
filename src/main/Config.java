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

    public void saveConfigSafe(){
        try{
            saveConfig();
        } catch(IOException e){
            e.printStackTrace();
        }
    }

    public void loadConfig() throws IOException {
        File file = new File("config.txt");
        if(!file.exists()){
            saveConfig(); // first launch: write the defaults and carry on
            return;
        }

        try(BufferedReader br = new BufferedReader(new FileReader(file))){
            String s = br.readLine();
            gp.fullScreenOn = "On".equals(s);

            gp.music.volumeScale = readVolume(br.readLine(), gp.music.volumeScale);
            gp.soundEffect.volumeScale = readVolume(br.readLine(), gp.soundEffect.volumeScale);
        } catch(IOException | RuntimeException e){
            e.printStackTrace(); // unreadable or corrupt file: keep the defaults
        }
    }

    private int readVolume(String line, int fallback){
        if(line == null) return fallback;
        try{
            int value = Integer.parseInt(line.trim());
            return Math.max(0, Math.min(5, value)); // Sound.checkVolume only handles 0 to 5
        } catch(NumberFormatException e){
            return fallback;
        }
    }
}
