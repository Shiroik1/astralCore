package world;

import java.util.*;

/** Plain diagnostic values; no rendering resources or extra entity ownership. */
public final class SpawnDebugInfo {
    public enum Reason {UNLOADED_CHUNK,INACTIVE,BOSS_ENCOUNTER,RESPAWN_GRACE,KILL_GRACE,IDLE_TRAVEL,CHANCE_ROLL,POPULATION_CAP,ENTITY_CAPACITY,WORLD_BOUNDARY,INSIDE_CAMERA,CLIFF_COLLISION,WATER,PROTECTED_AREA,BOSS_ARENA,BUILDING,SOLID_OBJECT,OCCUPIED,UNREACHABLE_LOCATION,NO_VALID_TERRAIN}
    public final Map<Reason,Integer> rejected=new EnumMap<>(Reason.class);
    public long checks,totalSpawned;
    public int attempts,valid,spawned,nearby,lastCol=-1,lastRow=-1;
    public double lastDistance;
    public String region="UNKNOWN",result="Waiting for first check";
    public boolean boss;
    public void begin(){checks++;attempts=valid=spawned=0;rejected.clear();}
    public void reject(Reason reason){rejected.merge(reason,1,Integer::sum);}
    public void skip(Reason reason){result="SKIPPED: "+reason;}
    public void finish(){
        totalSpawned+=spawned;
        if(spawned>0){result="SUCCESS: "+spawned;return;}
        Reason reason=rejected.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(Reason.NO_VALID_TERRAIN);
        result="FAILED: "+reason;
    }
}
