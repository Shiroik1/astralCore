package main;

public final class Prof {
    /** Prevents instantiation of this utility class. */
    private Prof() {}

    private static final String[] NAMES = {
        "tiles", "collect", "grass", "sort", "entities", "light", "present"
    };
    private static final long[] acc = new long[NAMES.length];
    private static long mark;
    private static int frames;

    /** Performs the begin operation for Prof. */
    public static void begin() {
        mark = System.nanoTime();
    }

    /** Performs the lap operation for Prof. */
    public static void lap(int i) {
        long n = System.nanoTime();
        acc[i] += n - mark;
        mark = n;
    }

    /** Performs the frame done operation for Prof. */
    public static void frameDone() {
        if (++frames < 120) return;
        if (!Boolean.getBoolean("astral.dev") || !Boolean.getBoolean("astral.performance.log")) {
            java.util.Arrays.fill(acc, 0);
            frames = 0;
            return;
        }
        StringBuilder sb = new StringBuilder("[PROF] ms/frame:");
        for (int i = 0; i < acc.length; i++) {
            sb.append(' ')
                    .append(NAMES[i])
                    .append('=')
                    .append(String.format("%.2f", acc[i] / 120e6));
            acc[i] = 0;
        }
        if (Boolean.getBoolean("astral.dev") && Boolean.getBoolean("astral.performance.log"))
            System.out.println(sb);
        frames = 0;
    }
}
