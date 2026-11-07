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
        kryo.register(GameEvent.class);
        kryo.register(ObjectState.class);
        kryo.register(ObjectState[].class);
        kryo.register(String[].class);
        kryo.register(int[].class);
        kryo.register(NpcState.class);
        kryo.register(NpcState[].class);
        kryo.register(InventoryAction.class);
        kryo.register(GameEventType.class);
        kryo.register(InventoryActionType.class);
        kryo.register(object.ItemType.class);
        kryo.register(object.ItemType[].class);
        kryo.register(monster.MonsterType.class);
        kryo.register(byte[].class);
    }
}