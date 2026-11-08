package main;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import java.net.URL;

public class Sound implements AutoCloseable {
    Clip clip;
    FloatControl fc;
    URL soundURL[] = new URL[30];
    Clip[] clips = new Clip[30];
    FloatControl[] gainControls = new FloatControl[30];
    volatile int volumeScale = 3;
    private final java.util.concurrent.ThreadPoolExecutor effects = new java.util.concurrent.ThreadPoolExecutor(
            1,1,0L,java.util.concurrent.TimeUnit.MILLISECONDS,new java.util.concurrent.ArrayBlockingQueue<>(64),
            runnable -> {Thread thread=new Thread(runnable,"game-audio");thread.setDaemon(true);return thread;},
            new java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy());
    float volume;

    public Sound(){
        soundURL[0] = getClass().getResource("/sound/BlueBoyAdventure.wav");
        soundURL[1] = getClass().getResource("/sound/coin.wav");
        soundURL[2] = getClass().getResource("/sound/powerup.wav");
        soundURL[3] = getClass().getResource("/sound/unlock.wav");
        soundURL[4] = getClass().getResource("/sound/fanfare.wav");
        soundURL[5] = getClass().getResource("/sound/hitmonster.wav");
        soundURL[6] = getClass().getResource("/sound/receivedamage.wav");
        soundURL[7] = getClass().getResource("/sound/levelup.wav");
        soundURL[8] = getClass().getResource("/sound/cursor.wav");
        soundURL[9] = getClass().getResource("/sound/burning.wav");
        soundURL[10] = getClass().getResource("/sound/cuttree.wav");
        soundURL[11] = getClass().getResource("/sound/gameover.wav");
    }

    private void load(int i){
        if(clips[i] != null || soundURL[i] == null) return;
        try(AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(soundURL[i])){
            Clip newClip = AudioSystem.getClip();
            newClip.open(audioInputStream);
            clips[i] = newClip;
            if(newClip.isControlSupported(FloatControl.Type.MASTER_GAIN)){
                gainControls[i] = (FloatControl) newClip.getControl(FloatControl.Type.MASTER_GAIN);
            }
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    public synchronized void preloadFrom(int firstIndex){
        for(int i = firstIndex; i < soundURL.length; i++){
            load(i);
        }
        effects.prestartCoreThread();
    }

    /** Native audio operations never run on the simulation/rendering thread. */
    public void playEffect(int index){
        if(volumeScale==0 || index<0 || index>=soundURL.length || effects.isShutdown())return;
        effects.execute(()->{
            synchronized(this){
                if(volumeScale==0)return;
                setFile(index);play();
            }
        });
    }

    public synchronized void setFile(int i){
        load(i);
        clip = clips[i];
        fc = gainControls[i];
        checkVolume();
    }

    public synchronized void play(){
        if(clip == null) return;
        clip.stop();
        clip.setFramePosition(0);
        clip.start();
    }

    public synchronized void loop(){
        if(clip == null) return;
        clip.loop(Clip.LOOP_CONTINUOUSLY);
    }

    public synchronized void stop(){
        if(clip != null){
            clip.stop();
        }
    }

    public synchronized void checkVolume(){
        switch (volumeScale){
            case 0 -> volume = -80f;
            case 1 -> volume = -20f;
            case 2 -> volume = -12f;
            case 3 -> volume = -5f;
            case 4 -> volume = 1f;
            case 5 -> volume = 6f;
        }

        if(fc != null){
            fc.setValue(Math.max(fc.getMinimum(), Math.min(fc.getMaximum(), volume)));
        }
    }
    @Override
    public void close(){
        effects.shutdownNow();
        synchronized(this){for(Clip loaded:clips)if(loaded!=null)loaded.close();}
    }

}