package world.generation;

import world.GrassLayer;
import world.WorldObjectType;

/** Version-seven balance: fuller groves and shores, flower beds in plains, sparse grouped stones. */
final class VegetationGenerator {
    private static final WorldObjectType[] TREES={WorldObjectType.OAK_1,WorldObjectType.OAK_2,WorldObjectType.PINE_1,WorldObjectType.PINE_2};
    private static final WorldObjectType[] FLOWERS={WorldObjectType.FLOWERS_WHITE,WorldObjectType.FLOWERS_PINK,WorldObjectType.FLOWERS_PURPLE,WorldObjectType.FLOWERS_YELLOW,WorldObjectType.FLOWERS_BLUE,WorldObjectType.FLOWERS_MIXED};
    private static final WorldObjectType[] MUSHROOMS={WorldObjectType.MUSHROOM_RED,WorldObjectType.MUSHROOM_BROWN,WorldObjectType.MUSHROOM_IVORY,WorldObjectType.MUSHROOMS_RED,WorldObjectType.MUSHROOMS_BROWN,WorldObjectType.MUSHROOMS_IVORY};
    void generate(GeneratedWorld w,NoiseGenerator n,PlacementRules rules){
        var m=w.regions();var cfg=w.generation;
        for(var prop:w.starterProps())if(!rules.tryPlace(prop.type(),prop.col(),prop.row(),true))
            throw new IllegalStateException("Reserved starter resource could not be placed");
        // Give canopy and understory first choice of space. Stones fill occasional gaps afterwards.
        for(int pass=0;pass<6;pass++)for(int y=1;y<m.height-1;y++)for(int x=1;x<m.width-1;x++){
            if(!rules.grass(x,y) || w.decorationReserved(x,y) || m.golden(x,y)>0)continue;
            double forest=m.forest(x,y),flower=m.flowers(x,y),shore=m.shore(x,y)*cfg.shoreDensity();
            double near=pass>0?rules.nearTrees(x,y):0;
            WorldObjectType type=null;
            switch(pass){
                case 0 -> {
                    double grove=.4+.6*RegionGenerator.smooth(.25,.65,n.sample(x*.13,y*.13,860));
                    double clearing=RegionGenerator.smooth(.16,.32,n.value(x*.095,y*.095,864));
                    double chance=(.004*(1-flower)+.46*forest*grove*clearing)*cfg.forestDensity()*w.config.treeDensity()/.18;
                    if(m.waterDistance(x,y)>cfg.shoreNear() && n.random(x,y,870)<chance)
                        type=TREES[Math.min(3,(int)(n.value(x*.09,y*.09,871)*TREES.length))];
                }
                case 1 -> {
                    double chance=(.003*(1-flower)+.13*forest*(.35+.65*near)+.025*shore)*cfg.forestDensity()*w.config.vegetationDensity()/.65;
                    if(m.waterDistance(x,y)>cfg.shoreNear() && n.random(x,y,875)<chance)type=WorldObjectType.BUSH;
                    else if(near>.3 && n.random(x,y,879)<.006*w.config.detailDensity()/.65)type=WorldObjectType.STUMP_OAK_MOSS;
                }
                case 2 -> {
                    double bed=RegionGenerator.smooth(.48,.72,n.sample(x*.18,y*.18,872));
                    double rockScale=w.config.rockDensity()/.008;
                    // A few rock groups in groves, very few loose stones in open plains.
                    double large=(.0005+.010*forest)*bed*rockScale;
                    double small=(.0035*(1-forest)+.016*forest)*(.1+.9*bed)*rockScale;
                    if(m.waterDistance(x,y)>cfg.shoreNear() && n.random(x,y,873)<large)
                        type=n.random(x,y,874)<.4?WorldObjectType.ROCK_LARGE_MOSS:WorldObjectType.ROCK_LARGE;
                    else if(n.random(x,y,885)<small)
                        type=n.random(x,y,886)<.4?WorldObjectType.ROCK_SMALL_MOSS:WorldObjectType.ROCK_SMALL;
                }
                case 3 -> {
                    // Keep beds present even in ordinary plains; the mask still gives them clear edges.
                    double bed=.25+.75*RegionGenerator.smooth(.34,.64,n.sample(x*.19,y*.19,880));
                    double chance=(.035*(1-forest)+.27*flower+.02*shore)*bed*cfg.flowerDensity()*w.config.flowerDensity()/.65;
                    if(n.random(x,y,881)<chance){
                        int color=Math.min(FLOWERS.length-1,(int)(n.value(x*.075,y*.075,882)*FLOWERS.length));type=FLOWERS[color];
                    }
                }
                case 4 -> {
                    // Damp shores support mushrooms directly, including open ponds without tree shade.
                    double patch=.35+.65*RegionGenerator.smooth(.32,.64,n.sample(x*.23,y*.23,876));
                    double chance=(.025*forest+.20*rules.shade(x,y)+.34*shore)*patch*w.config.detailDensity()/.65;
                    if(n.random(x,y,877)<chance)
                        type=MUSHROOMS[Math.min(MUSHROOMS.length-1,(int)(n.random(x,y,878)*MUSHROOMS.length))];
                }
                case 5 -> {
                    double litter=.35+.65*RegionGenerator.smooth(.32,.63,n.sample(x*.2,y*.2,887));
                    double chance=(.008*(1-flower)+.12*forest*(.25+.75*near))*litter*w.config.detailDensity()/.65;
                    if(n.random(x,y,883)<chance)type=WorldObjectType.TWIG;
                }
            }
            if(type!=null)rules.tryPlace(type,x,y);
        }
        rules.finish();
        m.accessible=rules.reachable();
        // Reject a whole inaccessible footprint rather than clipping it into disconnected fragments.
        var rejected=new java.util.HashSet<Integer>();
        for(int y=0;y<m.height;y++)for(int x=0;x<m.width;x++)if(m.golden(x,y)>0 && !m.accessible[x][y])rejected.add(m.golden(x,y));
        if(!rejected.isEmpty())for(int y=0;y<m.height;y++)for(int x=0;x<m.width;x++)if(rejected.contains(m.golden(x,y)))m.golden[x][y]=0;
        // The golden plan was protected from every prop's actual sprite bounds: no late clipping or holes.
        for(int y=0;y<m.height;y++)for(int x=0;x<m.width;x++){
            if(m.golden(x,y)>0){w.meadow(x,y,GrassLayer.FIELD_GOLDEN,2+(n.random(x,y,890)<.35?1:0));continue;}
            if(!m.accessible[x][y] || !rules.meadowFits(x,y,false) || w.config.fieldDensity()==0 || cfg.grassDensity()==0)continue;
            double threshold=.62+.10*m.flowers(x,y)-.20*m.forest(x,y)-.25*m.shore(x,y)*cfg.shoreDensity()-.04*(m.moisture(x,y)-.5);
            double local=n.sample(x*.135,y*.135,891);
            double density=Math.min(2,cfg.grassDensity()*w.config.fieldDensity()/.75);
            if(local>1-(1-threshold)*density){
                int type=n.value(x*.18,y*.18,892)<.4?GrassLayer.FIELD_DARK:GrassLayer.FIELD_GREEN;
                int clumps=m.shore(x,y)>.7?4:m.shore(x,y)>.3 || m.forest(x,y)>.4?3:2;
                w.meadow(x,y,type,clumps);
            }
        }
        int fiber=0;
        for(int id:w.starterFiber()){
            int x=id%m.width,y=id/m.width;
            if(rules.meadowFits(x,y,false,true) && rules.reachable()[x][y]){w.meadow(x,y,GrassLayer.FIELD_GREEN,2);fiber++;}
        }
        if(!rules.hasTreeNearSpawn() || !rules.hasRockNearSpawn() || fiber<1)
            throw new IllegalStateException("No safe starter resource area fits this world configuration");
    }
}
