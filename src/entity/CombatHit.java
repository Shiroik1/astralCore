package entity;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** One authored hit event. Multi-hit abilities create a separate event for each configured pulse. */
public final class CombatHit {
    private static final AtomicLong IDS=new AtomicLong();
    public final long id=IDS.incrementAndGet();
    public final String kind;
    public int masteryType=-1; // Authored projectiles retain their source mastery across weapon swaps.
    public CombatHit mastery(int weaponType){masteryType=weaponType;return this;}
    private final Set<Monster> targets=Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean impactFeedback;
    /** A cleave can damage several enemies without repeatedly pausing the same swing. */
    public boolean claimImpactFeedback(){if(impactFeedback)return false;impactFeedback=true;return true;}
    public CombatHit(String kind){this.kind=kind;}
    public boolean contains(Monster target){return targets.contains(target);}
    public void record(Monster target){targets.add(target);}
    public void log(Player attacker,Monster target,int damage,String result){
        if(!Boolean.getBoolean("astral.dev") || !Boolean.getBoolean("astral.combat.hitLog"))return;
        System.out.println("[HIT] event="+id+" kind="+kind+" attacker="+attacker.playerId+" target="+target.combatId+" damage="+damage+" tick="+attacker.gp.currentTick+" result="+result);
    }
}
