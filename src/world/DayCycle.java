package world;

/** Time of day: a clock plus a smooth blend between the Lighting moods. */
public final class DayCycle {
    public static final float DAY_MINUTES =
            16f; // real minutes for one full day (25% faster than 20)
    public static final float START_HOUR = 9f;
    public static final int TICKS_PER_DAY = Math.round(DAY_MINUTES * 60 * 60);

    /** r, g, b, a, glow, vignette, leaves, flies, clouds */
    public static final int VALUES = 9;

    // Hour of day -> mood at that hour. Must start at 0 and end at 24 with the same mood.
    private static final float[] HOURS = {0, 5, 6.5f, 8.5f, 16, 18, 19.5f, 21, 24};
    private static final Lighting.Mood[] MOODS = {
        Lighting.Mood.NIGHT,
        Lighting.Mood.NIGHT,
        Lighting.Mood.GOLDEN,
        Lighting.Mood.DAY,
        Lighting.Mood.DAY,
        Lighting.Mood.GOLDEN,
        Lighting.Mood.DUSK,
        Lighting.Mood.NIGHT,
        Lighting.Mood.NIGHT
    };

    private double time = START_HOUR / 24.0; // 0..1 of a day

    private int day = 1, warnedMoonNight;

    /** Returns the current day value. */
    public int day() {
        return day;
    }

    /** Evaluates the night condition for DayCycle. */
    public boolean night() {
        return hour() >= 19.5f || hour() < 6.5f;
    }

    /** Resolves the night day result used by DayCycle. */
    public int nightDay() {
        return hour() < 6.5f ? Math.max(1, day - 1) : day;
    }

    /** Evaluates the full moon condition for DayCycle. */
    public boolean fullMoon(int period) {
        return night() && nightDay() % period == 0;
    }

    /** Returns the current warned moon night value. */
    public int warnedMoonNight() {
        return warnedMoonNight;
    }

    /** Evaluates the claim moon warning condition for DayCycle. */
    public boolean claimMoonWarning(int period) {
        if (!fullMoon(period) || warnedMoonNight == nightDay()) return false;
        warnedMoonNight = nightDay();
        return true;
    }

    /** Restores this component from its saved state. */
    public void restore(int worldDay, float timeOfDay, int warningDay) {
        day = Math.max(1, worldDay);
        warnedMoonNight = Math.max(0, warningDay);
        setTime(timeOfDay);
    }

    /** Advances this component for one simulation tick. */
    public void tick() {
        time += 1.0 / TICKS_PER_DAY;
        if (time >= 1.0) {
            time -= 1.0;
            if (day < Integer.MAX_VALUE) day++;
        }
    }

    /** Resolves the time result used by DayCycle. */
    public float time() {
        return (float) time;
    }

    /** Updates the time for DayCycle. */
    public void setTime(float t) {
        time = t - Math.floor(t);
    }

    /** Resolves the hour result used by DayCycle. */
    public float hour() {
        return (float) (time * 24);
    }

    /** Updates the hour for DayCycle. */
    public void setHour(float hour) {
        setTime(hour / 24f);
    }

    /** Resolves the clock result used by DayCycle. */
    public String clock() {
        int minutes = (int) Math.floor(time * 1440 + 1e-4) % 1440;
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }

    /** A client follows the host's clock, ignoring drift under about 2 seconds. */
    public void syncFromHost(float hostTime) {
        double d = hostTime - time;
        d -= Math.rint(d); // shortest way round the clock
        if (Math.abs(d) > 0.002) setTime(hostTime);
    }

    /** Performs the sample operation for DayCycle. */
    public void sample(float[] out) {
        float hour = (float) (time * 24);
        int i = 0;
        while (i < HOURS.length - 2 && hour >= HOURS[i + 1]) i++;
        float f = (hour - HOURS[i]) / (HOURS[i + 1] - HOURS[i]);
        f = f * f * (3 - 2 * f);
        float[] a = new float[VALUES], b = new float[VALUES];
        sampleMood(MOODS[i], a);
        sampleMood(MOODS[i + 1], b);
        for (int k = 0; k < VALUES; k++) out[k] = a[k] + (b[k] - a[k]) * f;
    }

    /** Performs the sample mood operation for DayCycle. */
    public static void sampleMood(Lighting.Mood m, float[] out) {
        out[0] = m.r;
        out[1] = m.g;
        out[2] = m.b;
        out[3] = m.a;
        out[4] = m.glow;
        out[5] = m.vignette;
        out[6] = m.leaves;
        out[7] = m.flies;
        out[8] = m.clouds;
    }
}
