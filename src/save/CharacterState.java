package save;

import entity.*;

import main.Gamepanel;

import monster.MON_SlimeBoss;

import net.*;

import object.*;

import world.WorldObject;

import java.util.*;

/** Copies persistent values without retaining live entities or sharing inventory objects. */
public final class CharacterState {
    /** Prevents instantiation of this utility class. */
    private CharacterState() {}

    /** Resolves the capture result used by CharacterState. */
    public static PlayerState capture(Gamepanel g, Player p) {
        PlayerState s =
                Arrays.stream(SnapshotBuilder.buildPlayers(g))
                        .filter(state -> state.playerId == p.playerId)
                        .findFirst()
                        .orElseThrow();
        // A monster can land a fatal hit after the player's update, before the next death tick.
        s.HP = Math.max(0, Math.min(s.maxHP, s.HP));
        if (s.HP == 0) s.isDead = true;
        return s;
    }

    /** Rejects input values that violate this component’s data constraints. */
    public static void validate(PlayerState s) {
        if (s == null
                || s.playerName == null
                || s.playerName.isBlank()
                || s.playerName.length() > 16
                || !Set.of("warrior", "hunter", "mage", "ranger", "priest", "berserker", "paladin")
                        .contains(s.playerClass == null ? "" : s.playerClass)
                || !Set.of("male", "female").contains(s.gender == null ? "" : s.gender)
                || s.level < 1
                || s.level > 100000
                || s.maxHP < 1
                || s.maxHP > 1000000
                || s.HP < 0
                || s.HP > s.maxHP
                || s.maxMP < 0
                || s.maxMP > 1000000
                || s.MP < 0
                || s.MP > s.maxMP
                || s.coin < 0
                || s.exp < 0
                || s.nextLevelExp < 1
                || s.strength < 0
                || s.dexterity < 0
                || s.intelligence < 0
                || s.inventoryTypes == null
                || s.inventoryTypes.length != 20
                || s.inventoryStackCounts == null
                || s.inventoryStackCounts.length != 20
                || s.apparelTypes == null
                || s.apparelTypes.length != 7
                || s.weaponType == null
                || s.shieldType == null)
            throw new IllegalArgumentException("Invalid character data");
        if (!Set.of("up", "down", "left", "right").contains(s.direction == null ? "" : s.direction))
            throw new IllegalArgumentException("Invalid direction");
        for (int i = 0; i < 20; i++)
            if (s.inventoryTypes[i] != null
                    && (s.inventoryStackCounts[i] < 1 || s.inventoryStackCounts[i] > 1000000))
                throw new IllegalArgumentException("Invalid inventory stack");
        if (s.hotbarTypes != null && s.hotbarTypes.length != 10
                || s.selectedHotbar < 0
                || s.selectedHotbar >= 10) throw new IllegalArgumentException("Invalid hotbar");
        new crafting.CraftingProgression()
                .restore(s.craftingLevel, s.craftingXP, s.craftingBonuses);
        int effects = s.effectTypes == null ? 0 : s.effectTypes.length;
        if (effects > 100
                || effects > 0
                        && (s.effectDurations == null
                                || s.effectDurations.length != effects
                                || s.effectValues == null
                                || s.effectValues.length != effects))
            throw new IllegalArgumentException("Invalid status effects");
        for (int i = 0; i < effects; i++)
            if (s.effectTypes[i] == null || s.effectDurations[i] < 0)
                throw new IllegalArgumentException("Invalid status effect");
        if (!(s.hairStyle == -1 && s.hairColor == -1)
                && !(art.Hairstyles.valid(s.hairStyle) && art.Hairstyles.validColor(s.hairColor)))
            throw new IllegalArgumentException("Invalid hair appearance");
    }

    /** Applies the supplied state or operation to this component. */
    public static void apply(Gamepanel g, Player p, PlayerState s) {
        validate(s);
        p.resetForSceneTransition();
        p.playerName = s.playerName;
        p.setCharacterDesign(s.playerClass, s.gender);
        p.setDefaultValues();
        p.setItems();
        p.currentWeapon = ItemRegistry.create(s.weaponType, g);
        p.currentShield = ItemRegistry.create(s.shieldType, g);
        p.hairStyle = s.hairStyle;
        p.hairColor = s.hairColor;
        p.rightHandItem = p.currentWeapon;
        for (int i = 0; i < 7; i++) p.apparelSlots[i] = ItemRegistry.create(s.apparelTypes[i], g);
        for (int i = 0; i < 20; i++) {
            p.inventorySlots[i] = ItemRegistry.create(s.inventoryTypes[i], g);
            if (p.inventorySlots[i] != null)
                p.inventorySlots[i].stackCount = s.inventoryStackCounts[i];
        }
        p.hotbar.restore(p, s.hotbarTypes, s.selectedHotbar);
        p.craftingProgress.restore(s.craftingLevel, s.craftingXP, s.craftingBonuses);
        p.meleeMasteryXP = s.meleeMasteryXP;
        p.magicMasteryXP = s.magicMasteryXP;
        p.bowMasteryXP = s.bowMasteryXP;
        p.level = s.level;
        p.exp = s.exp;
        p.nextLevelExp = s.nextLevelExp;
        p.coin = s.coin;
        p.strength = s.strength;
        p.dexterity = s.dexterity;
        p.intelligence = s.intelligence;
        p.vitality = s.vitality;
        p.maxHP = s.maxHP;
        p.HP = s.HP;
        p.maxMP = s.maxMP;
        p.MP = s.MP;
        p.maxRage = s.maxRage;
        p.rage = s.rage;
        p.arcaneCharge = s.arcaneCharge;
        p.resolve = s.resolve;
        p.worldX = s.worldX;
        p.worldY = s.worldY;
        p.direction = s.direction;
        p.inWell = s.inWell;
        p.isDead = s.isDead;
        boolean legacyHunter = p.isAssassin() && !s.assassinKit;
        if (legacyHunter && p.level <= 25) {
            p.strength = 1 + (p.level - 1) / 2;
            p.nextLevelExp = p.level * (30 + 2 * (p.level - 1));
        }
        boolean legacyMage = p.isMage() && !s.mageKit;
        if (legacyMage && p.level <= 25) {
            p.intelligence = 2 + (p.level - 1) / 2;
            p.nextLevelExp = p.level * (30 + 2 * (p.level - 1));
        }
        p.syncWeaponProfile();
        p.attack = p.getAttack();
        p.defense = p.getDefense();
        p.refreshWeaponAnimation();
        for (int i = 0; i < p.skills.length; i++)
            if (!legacyHunter
                    && !legacyMage
                    && p.skills[i] != null
                    && s.skillCooldowns != null
                    && i < s.skillCooldowns.length)
                p.skills[i].cooldownRemaining = Math.max(0, s.skillCooldowns[i]);
        p.applySyncedEffects(s.effectTypes, s.effectDurations, s.effectValues, s.auraRgb);
        p.restoreSavedCombat(s);
        if (legacyMage) {
            p.statusEffects.removeIf(effect -> effect.type.equals(MageCombat.INFUSION));
            if (s.auraTicks > 360) p.auraActive = false;
        }
        p.currentInput = new InputState();
        p.lastProcessedInputTick = 0;
        p.shopChangedInputTick = 0;
    }

    /** Resolves the item result used by CharacterState. */
    public static ObjectState item(Entity e) {
        ObjectState s = new ObjectState();
        s.type = ItemRegistry.typeFor(e);
        s.stackCount = e.stackCount;
        s.worldX = e.worldX;
        s.worldY = e.worldY;
        s.groundLoot = e.groundLoot;
        s.lootAge = e.lootAge;
        s.lootVX = e.lootVX;
        s.lootVY = e.lootVY;
        s.lootLift = e.lootLift;
        return s;
    }

    /** Resolves the item result used by CharacterState. */
    public static Entity item(Gamepanel g, ObjectState s) {
        Entity e = ItemRegistry.create(s.type, g);
        if (e == null) throw new IllegalArgumentException("Unknown saved item");
        e.stackCount = s.stackCount;
        e.worldX = s.worldX;
        e.worldY = s.worldY;
        world.GroundLoot.restore(e, s);
        return e;
    }

    /** Restores the area for CharacterState. */
    public static void restoreArea(Gamepanel g, WorldSnapshot a) {
        g.quests.activateArea(a.inWellArea);
        Arrays.fill(g.monster, null);
        Arrays.fill(g.obj, null);
        g.projectileList.clear();
        for (int i = 0; i < a.monsters.length; i++) {
            MonsterState s = a.monsters[i];
            if (s == null) continue;
            Monster m = s.monsterType.create(g);
            m.worldX = s.worldX;
            m.worldY = s.worldY;
            m.direction = s.direction;
            m.HP = s.HP;
            m.maxHP = s.maxHP;
            m.alive = s.alive;
            m.dying = s.dying;
            Player local = g.localPlayer();
            if (local != null
                    && (s.assassinPairMask < 0
                            ? s.comboStacks > 0
                            : (s.assassinPairMask & (1 << local.playerId)) != 0)) {
                m.restoreAssassinPair(local);
            }
            if (a.inWellArea) m.questId = g.quests.definition().id();
            if (m instanceof MON_SlimeBoss boss) {
                boss.syncCharge(s.chargeX, s.chargeY);
                boss.bossState = s.bossState;
                boss.bossStateTimer = s.bossStateTimer;
                boss.lastAttackWasSlam = s.lastAttackWasSlam;
            }
            if (m instanceof monster.SlimeActor slime) slime.restoreSlime(s, true);
            if (m instanceof monster.MON_Skeleton skeleton) skeleton.restore(s);
            g.monster[i] = m;
        }
        for (int i = 0; i < a.objects.length; i++)
            if (a.objects[i] != null) g.obj[i] = item(g, a.objects[i]);
        for (int i = 0; i < a.npcs.length; i++)
            if (a.npcs[i] != null && g.npc[i] != null) {
                g.npc[i].worldX = a.npcs[i].worldX;
                g.npc[i].worldY = a.npcs[i].worldY;
                g.npc[i].direction = a.npcs[i].direction;
            }
        int i = 0;
        for (WorldObject o : g.worldObjects)
            if (o.hasState()) {
                o.state = i < a.sceneryState.length ? a.sceneryState[i] : 0;
                i++;
            }
    }
}
