package net;

import com.esotericsoftware.kryonet.EndPoint;

public class NetworkRegistration {
    public static void register(EndPoint endPoint){
        var kryo = endPoint.getKryo();
        kryo.register(InputState.class);
        kryo.register(boolean[].class);
        kryo.register(PlayerState.class);
        kryo.register(PlayerState[].class);
        kryo.register(MonsterState.class);
        kryo.register(MonsterState[].class);
        kryo.register(WorldSnapshot.class);
        kryo.register(JoinRequest.class);
        kryo.register(JoinAccepted.class);
    }
}