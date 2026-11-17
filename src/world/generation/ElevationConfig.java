package world.generation;

/** Layout settings are pinned in saves; frequencies use global tile coordinates. */
public record ElevationConfig(int maxHeight,double frequency,double raisedThreshold,double boundaryIrregularity,double boundaryRadius){
    public ElevationConfig {
        if(maxHeight<1 || maxHeight>8 || !Double.isFinite(frequency) || frequency<.005 || frequency>.15
                || !Double.isFinite(raisedThreshold) || raisedThreshold<.35 || raisedThreshold>.85
                || !Double.isFinite(boundaryIrregularity) || boundaryIrregularity<.05 || boundaryIrregularity>.23
                || !Double.isFinite(boundaryRadius) || boundaryRadius<.72 || boundaryRadius>.94)
            throw new IllegalArgumentException("Invalid elevation/boundary settings");
    }
    public static ElevationConfig defaults(){return new ElevationConfig(3,.028,.53,.14,.86);}
    public static ElevationConfig developmentDefaults(){return new ElevationConfig(Integer.getInteger("astral.elevation.maxHeight",3),n("frequency",.028),n("raisedThreshold",.53),n("boundaryIrregularity",.14),n("boundaryRadius",.86));}
    private static double n(String key,double value){return Double.parseDouble(System.getProperty("astral.elevation."+key,Double.toString(value)));}
}
