package monster;

/** Primitive snapshot fields work with both disk saves and the existing network serializer. */
public interface SlimeActor {
    static void validate(net.MonsterState state){
        if(state.slimeState==null)return; // Backward-compatible pre-hop saves.
        if(state.monsterType==MonsterType.GREEN_SLIME)MON_GreenSlime.State.valueOf(state.slimeState);
        else if(state.monsterType==MonsterType.CROWNED_SLIME)MON_CrownedSlime.State.valueOf(state.slimeState);
        if(state.slimeTimer<0 || state.slimeCooldown<0 || state.slimeArrivalGrace<0 || state.slimeAdds<0 || state.slimeAdds>SlimeCombatConfig.SUMMONS)
            throw new IllegalArgumentException("Invalid slime clock or summon count");
    }
    void captureSlime(net.MonsterState state);
    void restoreSlime(net.MonsterState state,boolean fromDisk);
}
