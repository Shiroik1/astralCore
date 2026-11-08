package main;

import java.nio.file.Path;
import java.io.*;

public class Config {
    Gamepanel gp;
    private final Path path;

    public Config(Gamepanel gp){
        this(gp,Path.of("config.txt"));
    }

    public Config(Gamepanel gp,Path path){this.gp=gp;this.path=path;}

    public void saveConfig() throws IOException {

        BufferedWriter bw = new BufferedWriter(new FileWriter(path.toFile()));

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

        bw.write(Integer.toString(gp.display.selected().width()));bw.newLine();
        bw.write(Integer.toString(gp.display.selected().height()));bw.newLine();
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
        File file = path.toFile();
        if(!file.exists()){
            saveConfig(); // first launch: write the defaults and carry on
            return;
        }

        try(BufferedReader br = new BufferedReader(new FileReader(file))){
            String s = br.readLine();
            gp.fullScreenOn = "On".equals(s);

            gp.music.volumeScale = readVolume(br.readLine(), gp.music.volumeScale);
            gp.soundEffect.volumeScale = readVolume(br.readLine(), gp.soundEffect.volumeScale);
            String width=br.readLine(),height=br.readLine();
            try{gp.display.selectSaved(Integer.parseInt(width),Integer.parseInt(height));}
            catch(NumberFormatException e){gp.display.selectSaved(0,0);}
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
