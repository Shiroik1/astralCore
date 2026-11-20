package save;

import com.esotericsoftware.jsonbeans.Json;
import com.esotericsoftware.jsonbeans.OutputType;

import main.*;

import net.*;

import java.awt.Point;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Five local slots. All capture/restore calls run on the game thread. */
public final class SaveStore {
    public static final int SLOTS = 5;
    private final Gamepanel g;
    private final Path directory;
    private int activeSlot = -1, saveTicks;
    private CharacterData active;
    private boolean guestSession;
    private boolean detached;
    private String error;
    private boolean recoveredBackup;

    public record Slot(
            int index,
            boolean empty,
            String name,
            String classId,
            int level,
            boolean dungeon,
            long savedAt,
            String error) {}

    /** Creates a SaveStore instance and initializes the state required by its collaborators. */
    public SaveStore(Gamepanel g) {
        this(g, AppPaths.dataDirectory().resolve("characters"));
    }

    /** Creates a SaveStore instance and initializes the state required by its collaborators. */
    public SaveStore(Gamepanel g, Path directory) {
        this.g = g;
        this.directory = directory;
    }

    /** Returns the current directory value. */
    public Path directory() {
        return directory;
    }

    /** Returns the current active slot value. */
    public int activeSlot() {
        return activeSlot;
    }

    /** Returns the current error value. */
    public String error() {
        return error;
    }

    /** Resolves the file result used by SaveStore. */
    private Path file(int slot) {
        if (slot < 0 || slot >= SLOTS)
            throw new IllegalArgumentException("Choose one of five slots");
        return directory.resolve("character-" + (slot + 1) + ".json");
    }

    /** Resolves the backup result used by SaveStore. */
    private Path backup(int slot) {
        return file(slot).resolveSibling(file(slot).getFileName() + ".bak");
    }

    /**
     * Configures the disk serializer to use stable field names and tolerate older optional save
     * fields.
     */
    private static Json json() {
        Json j = new Json(OutputType.json);
        j.setTypeName(null);
        j.setUsePrototypes(false);
        j.setIgnoreUnknownFields(true);
        j.setElementType(progression.Progression.Data.class, "talents", Integer.class);
        j.setElementType(
                progression.Progression.Data.class,
                "quests",
                progression.StarterQuests.Entry.class);
        return j;
    }

    /** Resolves the slots result used by SaveStore. */
    public List<Slot> slots() {
        List<Slot> result = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            if (!Files.exists(file(i)) && !Files.exists(backup(i))) {
                result.add(new Slot(i, true, "Empty slot", "", 0, false, 0, null));
                continue;
            }
            try {
                CharacterData d = read(i);
                result.add(
                        new Slot(
                                i,
                                false,
                                d.character.playerName,
                                d.character.playerClass,
                                d.character.level,
                                d.character.inWell,
                                d.savedAt,
                                null));
            } catch (Exception e) {
                result.add(new Slot(i, false, "Unavailable save", "", 0, false, 0, e.getMessage()));
            }
        }
        return result;
    }

    /** Resolves the read result used by SaveStore. */
    public CharacterData read(int slot) throws IOException {
        recoveredBackup = false;
        IOException first;
        try {
            return readFile(file(slot));
        } catch (IOException e) {
            first = e;
        }
        // Future versions must never silently fall back to an older backup.
        if (first.getMessage().startsWith("Newer save version")) throw first;
        try {
            CharacterData d = readFile(backup(slot));
            recoveredBackup = true;
            return d;
        } catch (IOException ignored) {
            throw first;
        }
    }

    /**
     * Escapes raw control characters inside JSON string values. The bundled jsonbeans writer
     * escapes quotes and backslashes but not newlines or tabs, so multi-line snapshot fields
     * (building layouts and slime-nest plans) would otherwise write files that cannot be read back.
     * Applying this to both write and read also repairs saves created before the fix.
     */
    private static String escapeStringControlCharacters(String text) {
        StringBuilder out = new StringBuilder(text.length() + 16);
        boolean inString = false, escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!inString) {
                if (c == '"') inString = true;
                out.append(c);
                continue;
            }
            if (escaped) {
                out.append(c);
                escaped = false;
                continue;
            }
            switch (c) {
                case '\\' -> {
                    out.append(c);
                    escaped = true;
                }
                case '"' -> {
                    out.append(c);
                    inString = false;
                }
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }

    /** Reads the file for SaveStore. */
    private CharacterData readFile(Path path) throws IOException {
        try {
            if (Files.size(path) > 8 * 1024 * 1024) throw new IOException("Save file is too large");
            CharacterData d =
                    json().fromJson(
                                    CharacterData.class,
                                    escapeStringControlCharacters(
                                            Files.readString(path, StandardCharsets.UTF_8)));
            if (d != null) {
                expandMonsterSlots(d.world);
                if (d.world != null) expandMonsterSlots(d.world.otherArea);
            }
            validate(d);
            return d;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Save could not be read: " + e.getMessage(), e);
        }
    }

    /** Performs the expand monster slots operation for SaveStore. */
    private void expandMonsterSlots(WorldSnapshot snapshot) {
        if (snapshot != null && snapshot.monsters != null && snapshot.monsters.length == 20)
            snapshot.monsters = Arrays.copyOf(snapshot.monsters, g.monster.length);
    }

    /** Rejects input values that violate this component’s data constraints. */
    private void validate(CharacterData d) throws IOException {
        if (d != null && d.version > 3)
            throw new IOException("Newer save version; update the game to load it");
        if (d == null
                || (d.version < 1 || d.version > 3)
                || d.id == null
                || d.world == null
                || d.world.quest == null) throw new IOException("Incomplete save");
        if (d.version >= 2 && d.generationSettings == null)
            throw new IOException("Missing saved world generation settings");
        CharacterState.validate(d.character);
        final world.generation.WorldConfig config;
        try {
            config = generationFor(d).config();
        } catch (RuntimeException e) {
            throw new IOException("Invalid saved world generation settings: " + e.getMessage(), e);
        }
        try {
            crafting.CraftingManager.validateSavedWorld(
                    d.craftingWorld,
                    config.procedural() ? config.width() : g.maxWorldCol,
                    config.procedural() ? config.height() : g.maxWorldRow,
                    g.tileSize,
                    config.seed());
        } catch (RuntimeException e) {
            throw new IOException("Invalid saved crafting world: " + e.getMessage(), e);
        }
        if (d.world.quest.stage < 0
                || d.world.quest.stage >= quest.QuestManager.Stage.values().length
                || d.world.quest.kills < 0
                || d.world.quest.kills > 10) throw new IOException("Invalid quest progress");
        if (d.version >= 3) {
            try {
                var savedProgress = new progression.Progression();
                savedProgress.restore(d.progression);
                savedProgress.validateBudget(
                        d.character.level,
                        d.character.strength,
                        d.character.dexterity,
                        d.character.intelligence);
            } catch (RuntimeException invalid) {
                throw new IOException("Invalid saved progression", invalid);
            }
        }
        validateArea(d.world);
        if (d.world.otherArea != null) {
            if (d.world.otherArea.otherArea != null
                    || d.world.otherArea.inWellArea == d.world.inWellArea)
                throw new IOException("Invalid saved areas");
            validateArea(d.world.otherArea);
        }
        if (d.character.inWell
                && !d.world.inWellArea
                && (d.world.otherArea == null || !d.world.otherArea.inWellArea))
            throw new IOException("Missing saved dungeon");
        if (d.townQueuedLoot == null || d.wellQueuedLoot == null)
            throw new IOException("Missing queued loot");
        for (ObjectState[] queued : new ObjectState[][] {d.townQueuedLoot, d.wellQueuedLoot})
            for (ObjectState item : queued)
                if (item == null || item.type == null || item.stackCount < 1)
                    throw new IOException("Invalid queued loot");
    }

    /** Validates the area for SaveStore. */
    private void validateArea(WorldSnapshot a) throws IOException {
        if (a.monsters == null
                || a.monsters.length != g.monster.length
                || a.objects == null
                || a.objects.length != g.obj.length
                || a.npcs == null
                || a.npcs.length != g.npc.length
                || a.sceneryState == null) throw new IOException("Invalid saved area");
        for (MonsterState m : a.monsters)
            if (m != null) {
                if (m.monsterType == null
                        || m.maxHP < 1
                        || m.HP < 0 && !m.dying && m.alive
                        || m.HP > m.maxHP) throw new IOException("Invalid saved monster");
                if (m.monsterType == monster.MonsterType.SKELETON) {
                    if (m.skeletonDay < 1
                            || m.skeletonBaseHealth < 1
                            || m.skeletonTimer < 0
                            || m.skeletonCooldown < 0
                            || m.skeletonDaylight < 0
                            || m.skeletonSwipe < 1
                            || m.skeletonLunge < 1
                            || m.skeletonRake < 1) throw new IOException("Invalid skeleton state");
                    try {
                        monster.MON_Skeleton.State.valueOf(m.skeletonState);
                    } catch (RuntimeException invalid) {
                        throw new IOException("Invalid skeleton state", invalid);
                    }
                }
                try {
                    monster.SlimeActor.validate(m);
                } catch (IllegalArgumentException invalid) {
                    throw new IOException("Invalid saved slime state", invalid);
                }
            }
        for (ObjectState o : a.objects)
            if (o != null && (o.type == null || o.stackCount < 1))
                throw new IOException("Invalid saved loot");
    }

    /** Resolves the generation for result used by SaveStore. */
    private world.generation.WorldSettings generationFor(CharacterData d) {
        if (d.generationSettings != null)
            return world.generation.WorldSettings.decode(d.generationSettings);
        // Pre-versioned saves: retain the historic layout algorithm, never apply new regional
        // rules.
        var config = g.newWorldDefaults();
        if (d.craftingWorld != null && !d.craftingWorld.isBlank())
            config = config.withSeed(crafting.CraftingManager.savedWorldSeed(d.craftingWorld));
        return world.generation.WorldSettings.of(
                config,
                d.slimeNestWorld == null ? 4 : 5,
                world.generation.GenerationConfig.defaults());
    }

    /** Performs the create operation for SaveStore. */
    public void create(int slot, String name, int classIndex, int genderIndex) throws IOException {
        create(slot, name, classIndex, genderIndex, -1, -1);
    }

    /** Performs the create operation for SaveStore. */
    public void create(
            int slot, String name, int classIndex, int genderIndex, int hairStyle, int hairColor)
            throws IOException {
        if (g.arena.training) throw new IOException("Practice characters cannot be saved.");
        if (!(hairStyle == -1 && hairColor == -1)
                && !(art.Hairstyles.valid(hairStyle) && art.Hairstyles.validColor(hairColor)))
            throw new IOException("Choose a hairstyle and hair color");
        if (Files.exists(file(slot)) || Files.exists(backup(slot)))
            throw new IOException("That character slot is occupied");
        if (name == null || name.isBlank()) name = "Adventurer";
        if (name.length() > 16) throw new IOException("Names can contain up to 16 characters");
        g.resetAfterNetworkSession();
        g.prepareNewWorld();
        g.players[0] = new entity.Player(g, g.keyH);
        var p = g.localPlayer();
        p.playerName = name.trim();
        p.setCharacterDesign("warrior", genderIndex == 1 ? "female" : "male");
        p.setDefaultValues();
        p.setItems();
        p.refreshWeaponAnimation();
        if (hairStyle >= 0) p.customizeHair(hairStyle, hairColor);
        CharacterData d = new CharacterData();
        d.id = UUID.randomUUID().toString();
        p.deathOwnerId = d.id;
        g.assetSetter.setMonster();
        g.crafting.reset();
        g.crafting.ensureWorld();
        capture(d);
        write(slot, d);
        activeSlot = slot;
        active = d;
        saveTicks = 0;
        guestSession = false;
        detached = false;
        error = null;
    }

    /**
     * Validates saved character and world data before restoring the session and its persistent
     * progression.
     */
    public void load(int slot) throws IOException {
        if (g.arena.training) throw new IOException("Leave the arena before loading a character.");
        CharacterData d =
                read(slot); // Fully decode and validate before touching the current session.
        boolean recovered = recoveredBackup;
        g.resetAfterNetworkSession();
        g.installGeneration(generationFor(d));
        g.players[0] = new entity.Player(g, g.keyH);
        var p = g.localPlayer();
        CharacterState.apply(g, p, d.character);
        if (d.character.deathOwnerId == null) p.deathOwnerId = d.id;
        g.memorials.restore(d.world.deathMemorials);
        if (d.progression == null) p.progression.migrate(p);
        else p.progression.restore(d.progression);
        p.progression.validateBudget(p);
        p.grantExperience(0);
        p.refreshActionBar();
        if (p.isMage()) p.mage.setup(p.skills);
        p.attack = p.getAttack();
        g.quests.restoreSavedProgress(d.world.quest, new Point(d.returnX, d.returnY));
        restoreArea(d, d.world);
        if (d.world.otherArea != null) restoreArea(d, d.world.otherArea);
        g.quests.activateArea(p.inWell);
        g.quests.ensureLocalIntro();
        boolean loadedArea = g.quests.inDungeon();
        try {
            g.quests.activateArea(false);
            if (d.buildingWorld != null) g.buildings.restore(d.buildingWorld);
            g.crafting.restoreWorld(d.craftingWorld);
            g.slimeNest.restore(d.slimeNestWorld);
        } finally {
            g.quests.activateArea(loadedArea);
        }
        g.chunks.update();
        g.lighting.cycle.restore(d.world.worldDay, d.world.dayTime, d.world.moonWarningDay);
        g.ensureProceduralSpawn(p);
        activeSlot = slot;
        active = d;
        saveTicks = 0;
        guestSession = false;
        detached = false;
        error = null;
        if (recovered)
            g.ui.addMessage(
                    "Recovered the previous character save from backup.",
                    new java.awt.Color(240, 198, 145));
    }

    /** Restores the area for SaveStore. */
    private void restoreArea(CharacterData d, WorldSnapshot a) {
        CharacterState.restoreArea(g, a);
        ObjectState[] queued = a.inWellArea ? d.wellQueuedLoot : d.townQueuedLoot;
        g.quests.restoreQueuedLoot(
                Arrays.stream(queued).map(s -> CharacterState.item(g, s)).toList());
    }

    /**
     * Captures character progression, inventory and world owners into the disk-save representation.
     */
    private void capture(CharacterData d) {
        PlayerState player = CharacterState.capture(g, g.localPlayer());
        if (d.generationSettings == null && d.world != null)
            d.generationSettings = generationFor(d).encode();
        d.version = 3;
        d.progression = g.localPlayer().progression.snapshot();
        if (guestSession && d.character != null) {
            // Guest equipment/levels carry home; the host's map position and quest do not.
            player.worldX = d.character.worldX;
            player.worldY = d.character.worldY;
            player.inWell = d.character.inWell;
            player.direction = d.character.direction;
        } else {
            d.generationSettings = g.generationSettingsJson();
            g.crafting.ensureWorld();
            d.craftingWorld = g.crafting.worldJson();
            d.buildingWorld = g.buildings.snapshot();
            d.slimeNestWorld = g.slimeNest.snapshot();
            d.world = SnapshotBuilder.build(g);
            d.world.deathMemorials = g.memorials.snapshot();
            d.world.players = null;
            if (d.world.otherArea != null) d.world.otherArea.players = null;
            var returnPosition = g.quests.returnPositionForSave(g.localPlayerIndex);
            d.returnX = returnPosition.x;
            d.returnY = returnPosition.y;
            if (d.world.quest.pendingRewards != null) {
                var own = d.world.quest.pendingRewards[g.localPlayerIndex];
                d.world.quest.pendingRewards = new object.ItemType[4];
                d.world.quest.pendingRewards[0] = own;
            }
            boolean area = g.quests.inDungeon();
            try {
                if (area) {
                    d.wellQueuedLoot = queued();
                    g.quests.activateArea(false);
                    d.townQueuedLoot = queued();
                } else {
                    d.townQueuedLoot = queued();
                    if (g.quests.hasOtherScene()) {
                        g.quests.activateArea(true);
                        d.wellQueuedLoot = queued();
                    } else d.wellQueuedLoot = new ObjectState[0];
                }
            } finally {
                g.quests.activateArea(area);
            }
        }
        player.playerId = 0;
        player.merchantOpen = false;
        player.attacking = false;
        player.animState = "idle";
        player.ackTick = 0;
        d.character = player;
        d.savedAt = System.currentTimeMillis();
    }

    /** Resolves the queued result used by SaveStore. */
    private ObjectState[] queued() {
        return g.quests.queuedLootForSave().stream()
                .map(CharacterState::item)
                .toArray(ObjectState[]::new);
    }

    /** Saves the active for SaveStore. */
    public boolean saveActive() {
        if (g.arena.training) return true;
        long performanceStart = g.performance.start();
        try {
            if (activeSlot < 0 || active == null) return true;
            try {
                if (!detached) capture(active);
                write(activeSlot, active);
                error = null;
                saveTicks = 0;
                return true;
            } catch (Exception e) {
                error = "Could not save character: " + e.getMessage();
                g.ui.showSaveError(error);
                return false;
            }
        } finally {
            g.performance.end(main.PerformanceMetrics.Section.SAVE, performanceStart);
        }
    }

    /** Writes the character snapshot through the existing safe-save and backup procedure. */
    private void write(int slot, CharacterData d) throws IOException {
        if (g.arena.training) throw new IOException("Practice characters cannot be saved.");
        validate(d);
        Files.createDirectories(directory);
        byte[] bytes =
                escapeStringControlCharacters(json().toJson(d, CharacterData.class))
                        .getBytes(StandardCharsets.UTF_8);
        Path temporary = Files.createTempFile(directory, "character-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            // Keep the last readable generation. Never replace it with a corrupt primary.
            if (Files.exists(file(slot))) {
                boolean readable = false;
                try {
                    readFile(file(slot));
                    readable = true;
                } catch (IOException bad) {
                    if (bad.getMessage().startsWith("Newer save version")) throw bad;
                }
                if (readable) replaceAtomically(file(slot), backup(slot), false);
            }
            replaceAtomically(temporary, file(slot), true);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Performs the replace atomically operation for SaveStore. */
    private static void replaceAtomically(Path from, Path to, boolean move) throws IOException {
        if (!move) {
            Path temp = Files.createTempFile(to.getParent(), "backup-", ".tmp");
            try {
                Files.copy(from, temp, StandardCopyOption.REPLACE_EXISTING);
                replaceAtomically(temp, to, true);
            } finally {
                Files.deleteIfExists(temp);
            }
            return;
        }
        try {
            Files.move(
                    from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Performs the delete operation for SaveStore. */
    public void delete(int slot) throws IOException {
        if (g.arena.training) throw new IOException("Practice mode cannot delete characters.");
        if (slot == activeSlot)
            throw new IOException("Return to the title before deleting this character");
        Files.deleteIfExists(backup(slot));
        Files.deleteIfExists(file(slot));
    }

    /** Begins the guest session for SaveStore. */
    public void beginGuestSession() {
        guestSession = true;
    }

    /** Performs the detach after disconnect operation for SaveStore. */
    public void detachAfterDisconnect() {
        detached = true;
    }

    /** Performs the deactivate operation for SaveStore. */
    public void deactivate() {
        activeSlot = -1;
        active = null;
        guestSession = false;
        detached = false;
        saveTicks = 0;
    }

    /** Advances this component for one simulation tick. */
    public void tick() {
        if (activeSlot >= 0 && g.gameState == g.playState && ++saveTicks >= 1800) {
            saveTicks = 0;
            saveActive();
        }
    }
}
