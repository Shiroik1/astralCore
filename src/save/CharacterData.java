package save;

import net.*;

/** Versioned disk format. Kept separate from the character-creation UI for future customization. */
public final class CharacterData {
    public int version = 3;
    public progression.Progression.Data progression;
    public String id;
    public String craftingWorld, buildingWorld;
    public String slimeNestWorld;
    public String generationSettings;
    public long savedAt;
    public PlayerState character;
    public WorldSnapshot world;
    public ObjectState[] townQueuedLoot = new ObjectState[0], wellQueuedLoot = new ObjectState[0];
    public int returnX, returnY;
}
