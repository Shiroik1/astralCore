package main;

import javax.sound.sampled.Clip;
import java.lang.reflect.Proxy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SoundSchedulingTest {
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        Sound sound=new Sound();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        AtomicInteger starts=new AtomicInteger();
        Clip fake=(Clip)Proxy.newProxyInstance(Clip.class.getClassLoader(),new Class<?>[]{Clip.class},(proxy,method,arguments)->{
            if(method.getName().equals("start")){starts.incrementAndGet();entered.countDown();release.await(3,TimeUnit.SECONDS);}
            if(method.getReturnType()==boolean.class)return false;
            if(method.getReturnType()==int.class)return 0;
            if(method.getReturnType()==long.class)return 0L;
            if(method.getReturnType()==float.class)return 0f;
            return null;
        });
        sound.clips[5]=fake;
        try{
            sound.volumeScale=0;sound.playEffect(5);require(starts.get()==0,"muted effects bypass native audio");
            sound.volumeScale=3;sound.playEffect(5);
            require(entered.await(2,TimeUnit.SECONDS),"audio runs on worker");
            CompletableFuture.runAsync(()->{for(int i=0;i<200;i++)sound.playEffect(5);}).get(1,TimeUnit.SECONDS);
            var worker=Sound.class.getDeclaredField("effects");worker.setAccessible(true);
            ThreadPoolExecutor queue=(ThreadPoolExecutor)worker.get(sound);
            require(queue.getQueue().size()<=64,"slow driver cannot create unbounded work");
            require(starts.get()==1,"game callers return while native playback is blocked");
            sound.volumeScale=0;
        }finally{release.countDown();sound.close();}
        System.out.println("PASS: muted audio bypass, asynchronous playback and bounded queue under a slow audio driver.");
    }
}
