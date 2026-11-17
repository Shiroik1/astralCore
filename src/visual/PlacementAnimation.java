package visual;

/** Simulation-time, render-only spring-like pop. No gameplay position or size is retained here. */
public record PlacementAnimation(double started) {
    public static final double DURATION=.240;
    public static final double INITIAL_SCALE=.75,OVERSHOOT=1.12,SETTLE=.96,MAX_LIFT=2;
    public double scale(double now){
        double t=Math.max(0,now-started);
        if(t>=DURATION)return 1;
        double phase=DURATION/3;
        if(t<phase)return ease(INITIAL_SCALE,OVERSHOOT,t/phase);
        if(t<phase*2)return ease(OVERSHOOT,SETTLE,(t-phase)/phase);
        return ease(SETTLE,1,(t-phase*2)/phase);
    }
    public double lift(double now){return MAX_LIFT*Math.sin(Math.PI*Math.min(1,Math.max(0,(now-started)/DURATION)));}
    public boolean expired(double now){return now-started>=DURATION;}
    private static double ease(double a,double b,double t){return a+(b-a)*t*t*(3-2*t);}
}
