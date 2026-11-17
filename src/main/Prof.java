package main;

public final class Prof {
    private Prof(){}
    private static final String[] NAMES = {"tiles","collect","grass","sort","entities","light","present"};
    private static final long[] acc = new long[NAMES.length];
    private static long mark;
    private static int frames;

    public static void begin(){ mark = System.nanoTime(); }
    public static void lap(int i){ long n = System.nanoTime(); acc[i] += n - mark; mark = n; }
    public static void frameDone(){
        if(++frames < 120) return;
        if(!Boolean.getBoolean("astral.dev") || !Boolean.getBoolean("astral.performance.log")){java.util.Arrays.fill(acc,0);frames=0;return;}
        StringBuilder sb = new StringBuilder("[PROF] ms/frame:");
        for(int i = 0; i < acc.length; i++){
            sb.append(' ').append(NAMES[i]).append('=').append(String.format("%.2f", acc[i] / 120e6));
            acc[i] = 0;
        }
        if(Boolean.getBoolean("astral.dev") && Boolean.getBoolean("astral.performance.log"))System.out.println(sb);
        frames = 0;
    }
}