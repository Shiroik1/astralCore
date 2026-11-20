package ui;

import crafting.*;

import entity.*;

import main.Gamepanel;

import net.*;

import object.*;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;

/**
 * Shared Java2D HUD and mutually exclusive windows. All actions reuse existing game transactions.
 */
public final class GameUI {
    public enum Window {
        NONE,
        INVENTORY,
        CHARACTER,
        JOURNAL
    }

    private final Gamepanel gp;
    private Window window = Window.NONE, paintWindow = Window.NONE;
    private double visibility, hpDisplay = Double.NaN, mpDisplay = Double.NaN;
    private final int[] pendingStats = new int[4];
    private int tab,
            filter,
            recipeIndex,
            scroll,
            quantity = 1,
            selectedBag = -1,
            dragBag = -1,
            dragEquip = -1,
            dragHotbar = -1;
    private boolean previousLeft, previousRight, releaseGate;
    public boolean shift;
    private int mx, my, pressAge;
    private Rectangle frame = new Rectangle(),
            close = new Rectangle(),
            craftButton = new Rectangle(),
            placeButton = new Rectangle(),
            plus = new Rectangle(),
            minus = new Rectangle();
    private final Rectangle[] bag = new Rectangle[20],
            equipment = new Rectangle[8],
            hotbar = new Rectangle[10],
            tabs = new Rectangle[3],
            filters = new Rectangle[3];
    private final Map<Integer, Rectangle> recipeRows = new LinkedHashMap<>();
    private int skillIndex, craftCooldown, talentPage;
    private int selectedAbility = -2;
    private final String[] equipmentNames = {
        "Weapon", "Offhand", "Torso", "Legs", "Boots", "Hands", "Cloak", "Headwear"
    };
    // Keep legacy save/network slot IDs stable while omitting the former hair slot.
    private static final int[] EQUIPMENT_SLOTS = {
        Player.EQUIP_WEAPON, Player.EQUIP_SHIELD, Player.EQUIP_TORSO, Player.EQUIP_PANTS,
        Player.EQUIP_BOOTS, Player.EQUIP_GLOVES, Player.EQUIP_CLOAK, Player.EQUIP_HEADPIECE
    };
    private final String[] skillNames = {
        "Sword", "Staff", "Bow", "Crafting", "Mining", "Gathering", "Cooking", "Fishing"
    };

    /** Creates a GameUI instance and initializes the state required by its collaborators. */
    public GameUI(Gamepanel gp) {
        this.gp = gp;
        for (int i = 0; i < 20; i++) bag[i] = new Rectangle();
        for (int i = 0; i < equipment.length; i++) equipment[i] = new Rectangle();
        for (int i = 0; i < 10; i++) hotbar[i] = new Rectangle();
        for (int i = 0; i < 3; i++) {
            tabs[i] = new Rectangle();
            filters[i] = new Rectangle();
        }
    }

    /** Returns the current window value. */
    public Window window() {
        return window;
    }

    /** Evaluates the pointer over hotbar condition for GameUI. */
    public boolean pointerOverHotbar() {
        for (Rectangle r : hotbar)
            if (r.contains(gp.mouseH.getScaledX(), gp.mouseH.getScaledY())) return true;
        return false;
    }

    /** Evaluates the blocks world condition for GameUI. */
    public boolean blocksWorld() {
        return gp.memorials.open() || window != Window.NONE || visibility > 0 || releaseGate;
    }

    /** Reports whether this component can control for GameUI. */
    public boolean canControl() {
        Player p = gp.localPlayer();
        return gp.gameState == gp.playState
                && p != null
                && !p.isDead
                && !p.inDialogue
                && !p.merchantOpen
                && !gp.optionsOpen
                && !gp.paused
                && !gp.keyH.enteringChat;
    }

    /** Performs the open operation for GameUI. */
    public void open(Window next) {
        if (!canControl()) return;
        Arrays.fill(pendingStats, 0);
        window = paintWindow = next;
        gp.inventoryOpen = next != Window.NONE;
        gp.crafting.open = next == Window.INVENTORY;
        gp.buildings.stop();
        gp.crafting.cancelPlacement();
        cancelDrag();
    }

    /** Closes this component and releases or resets the state it owns. */
    public void close() {
        window = Window.NONE;
        gp.inventoryOpen = false;
        gp.crafting.open = false;
        releaseGate = true;
        cancelDrag();
    }

    /** Restores this component to its initial runtime state. */
    public void reset() {
        window = paintWindow = Window.NONE;
        visibility = 0;
        gp.inventoryOpen = false;
        gp.crafting.open = false;
        releaseGate = false;
        shift = false;
        cancelDrag();
        hpDisplay = mpDisplay = Double.NaN;
    }

    /** Cancels the drag for GameUI. */
    private void cancelDrag() {
        dragBag = dragEquip = dragHotbar = -1;
    }

    /** Evaluates the key condition for GameUI. */
    public boolean key(int code) {
        if (gp.memorials.open()) {
            if (code == KeyEvent.VK_ESCAPE) gp.memorials.close();
            return true;
        }
        if (code == KeyEvent.VK_SHIFT) {
            shift = true;
            return true;
        }
        if (!canControl()) return false;
        if (code == KeyEvent.VK_E || code == KeyEvent.VK_I || code == KeyEvent.VK_K) {
            if (window == Window.INVENTORY) close();
            else open(Window.INVENTORY);
            return true;
        }
        if (code == KeyEvent.VK_J) {
            if (window == Window.JOURNAL) close();
            else open(Window.JOURNAL);
            return true;
        }
        if (code == KeyEvent.VK_V && window == Window.NONE) {
            gp.localPlayer().life.input();
            return true;
        }
        if (code == KeyEvent.VK_C) {
            if (window == Window.CHARACTER) close();
            else open(Window.CHARACTER);
            return true;
        }
        if (code == KeyEvent.VK_ESCAPE && blocksWorld()) {
            close();
            return true;
        }
        if (code >= KeyEvent.VK_0 && code <= KeyEvent.VK_9) {
            int slot = code == KeyEvent.VK_0 ? 9 : code - KeyEvent.VK_1;
            if (window == Window.CHARACTER && tab == 2 && skillIndex < 3 && slot < 5) {
                assignSkill(slot);
                return true;
            }
            if (!shift && slot < 5 && window == Window.NONE) {
                gp.keyH.skillKeyPressed[slot] = true;
                return true;
            }
            selectHotbar(slot, window == Window.NONE);
            return true;
        }
        if (window != Window.NONE) {
            if (window == Window.INVENTORY) {
                if (code == KeyEvent.VK_DOWN || code == KeyEvent.VK_UP)
                    selectRecipe(code == KeyEvent.VK_DOWN ? 1 : -1);
                if (code == KeyEvent.VK_ENTER) craft();
                if (code == KeyEvent.VK_P) place();
                if (code == KeyEvent.VK_EQUALS && gp.localPlayer().craftingProgress.level >= 9)
                    quantity = Math.min(99, quantity + 1);
                if (code == KeyEvent.VK_MINUS) quantity = Math.max(1, quantity - 1);
            }
            if (code == KeyEvent.VK_TAB && window == Window.CHARACTER) tab = (tab + 1) % 3;
            return true;
        }
        return visibility > 0;
    }

    /** Performs the wheel operation for GameUI. */
    public void wheel(int amount) {
        if (!canControl()) return;
        if (window == Window.INVENTORY) {
            scroll = Math.max(0, scroll + amount);
            return;
        }
        if (window == Window.CHARACTER) {
            if (tab == 2) {
                skillIndex = Math.floorMod(skillIndex + amount, skillNames.length);
                talentPage = 0;
            }
            return;
        }
        selectHotbar(Math.floorMod(gp.localPlayer().hotbar.selected + amount, 10), true);
    }

    /** Performs the action operation for GameUI. */
    private void action(InventoryActionType type, int source, int target, int slot, ItemType item) {
        InventoryAction action = new InventoryAction();
        action.playerId = gp.localPlayer().playerId;
        action.actionType = type;
        action.sourceIndex = source;
        action.targetIndex = target;
        action.slotType = slot;
        action.hotbarType = item;
        gp.requestInventoryAction(action);
    }

    /** Performs the select hotbar operation for GameUI. */
    public void selectHotbar(int slot, boolean activate) {
        if (slot < 0 || slot >= 10) return;
        action(InventoryActionType.HOTBAR_SELECT, slot, 0, activate ? 0 : 1, null);
    }

    /** Advances this component for one simulation tick. */
    public boolean update(int x, int y, boolean clicked) {
        mx = x;
        my = y;
        if (pressAge > 0) pressAge--;
        if (craftCooldown > 0) craftCooldown--;
        Player p = gp.localPlayer();
        if (p == null || gp.gameState != gp.playState) return false;
        if (gp.memorials.open()) return gp.memorials.updateUI(x, y, clicked);
        if (p.isDead || p.inDialogue || p.merchantOpen) {
            reset();
            return false;
        }
        if (gp.crafting.open && window != Window.INVENTORY) open(Window.INVENTORY);
        if (window == Window.NONE && gp.inventoryOpen) open(Window.INVENTORY);
        p.hotbar.normalize(p);
        if (Double.isNaN(hpDisplay)) hpDisplay = p.HP;
        if (Double.isNaN(mpDisplay)) mpDisplay = p.MP;
        hpDisplay += (p.HP - hpDisplay) * .2;
        mpDisplay += (p.MP - mpDisplay) * .2;
        double delta = 1.0 / 60 / UITheme.TRANSITION;
        visibility =
                Math.max(0, Math.min(1, visibility + (window == Window.NONE ? -delta : delta)));
        if (visibility == 0 && window == Window.NONE) paintWindow = Window.NONE;
        if (releaseGate && !gp.mouseH.leftPressed && !gp.mouseH.rightPressed) releaseGate = false;
        boolean left = gp.mouseH.leftPressed,
                right = gp.mouseH.rightPressed,
                consume = blocksWorld();
        if (!canControl()) {
            previousLeft = left;
            previousRight = right;
            return consume;
        }
        if (window != Window.NONE) {
            frame = UITheme.window(gp.screenWidth, gp.screenHeight);
            double ease = visibility * visibility * (3 - 2 * visibility), scale = .97 + .03 * ease;
            x = (int) Math.round(frame.getCenterX() + (x - frame.getCenterX()) / scale);
            y = (int) Math.round(frame.getCenterY() + (y - frame.getCenterY()) / scale);
            if (clicked) {
                pressAge = 6;
                click(x, y);
            }
            if (left && !previousLeft) beginDrag(x, y);
            if (!left && previousLeft) drop(x, y);
            if (gp.mouseH.rightClicked || right && !previousRight) {
                secondary(x, y);
                gp.mouseH.rightClicked = false;
            }
        } else if (visibility == 0) {
            for (int i = 0; i < 10; i++)
                if (hotbar[i].contains(x, y)) {
                    consume = true;
                    if (clicked) {
                        selectHotbar(i, true);
                        consume = true;
                        gp.mouseH.leftPressed = false;
                        left = false;
                    }
                    if (gp.mouseH.rightClicked || right && !previousRight) {
                        gp.mouseH.rightClicked = false;
                        action(InventoryActionType.HOTBAR_CLEAR, i, 0, 0, null);
                        consume = true;
                    }
                }
        }
        previousLeft = left;
        previousRight = right;
        if (consume) gp.mouseH.leftClicked = false;
        return consume;
    }

    /** Begins the drag for GameUI. */
    private void beginDrag(int x, int y) {
        Player p = gp.localPlayer();
        if (window == Window.INVENTORY || window == Window.CHARACTER && tab == 1)
            for (int i = 0; i < 20; i++)
                if (bag[i].contains(x, y) && p.inventorySlots[i] != null) {
                    dragBag = i;
                    selectedBag = i;
                    return;
                }
        if (window == Window.CHARACTER && tab == 1)
            for (int i = 0; i < equipment.length; i++)
                if (equipment[i].contains(x, y)) {
                    Entity item = p.equippedItem(EQUIPMENT_SLOTS[i]);
                    if (item != null && !item.isPlaceholder) {
                        dragEquip = EQUIPMENT_SLOTS[i];
                        return;
                    }
                }
        for (int i = 0; i < 10; i++)
            if (hotbar[i].contains(x, y) && p.hotbar.type(i) != null) {
                dragHotbar = i;
                return;
            }
    }

    /** Performs the drop operation for GameUI. */
    private void drop(int x, int y) {
        Player p = gp.localPlayer();
        if (dragBag < 0 && dragEquip < 0 && dragHotbar < 0) return;
        for (int i = 0; i < 10; i++)
            if (hotbar[i].contains(x, y)) {
                if (dragHotbar >= 0)
                    action(InventoryActionType.HOTBAR_SWAP, dragHotbar, i, 0, null);
                else {
                    Entity item =
                            dragBag >= 0 ? p.inventorySlots[dragBag] : p.equippedItem(dragEquip);
                    if (item != null)
                        action(
                                InventoryActionType.HOTBAR_ASSIGN,
                                0,
                                i,
                                0,
                                ItemRegistry.typeFor(item));
                }
                cancelDrag();
                return;
            }
        if (window == Window.INVENTORY || window == Window.CHARACTER && tab == 1)
            for (int i = 0; i < 20; i++)
                if (bag[i].contains(x, y)) {
                    if (dragBag >= 0 && dragBag != i)
                        action(InventoryActionType.SWAP, dragBag, i, 0, null);
                    if (dragEquip >= 0)
                        action(InventoryActionType.UNEQUIP_TO_BAG, 0, i, dragEquip, null);
                    cancelDrag();
                    return;
                }
        if (window == Window.CHARACTER && tab == 1 && dragBag >= 0)
            for (int i = 0; i < equipment.length; i++)
                if (equipment[i].contains(x, y)) {
                    action(InventoryActionType.EQUIP, dragBag, 0, EQUIPMENT_SLOTS[i], null);
                    cancelDrag();
                    return;
                }
        if (dragHotbar >= 0) action(InventoryActionType.HOTBAR_CLEAR, dragHotbar, 0, 0, null);
        cancelDrag();
    }

    /** Performs the secondary operation for GameUI. */
    private void secondary(int x, int y) {
        for (int i = 0; i < 10; i++)
            if (hotbar[i].contains(x, y)) {
                action(InventoryActionType.HOTBAR_CLEAR, i, 0, 0, null);
                return;
            }
        if (window == Window.CHARACTER && tab == 1)
            for (int i = 0; i < equipment.length; i++)
                if (equipment[i].contains(x, y)) {
                    for (int n = 0; n < 20; n++)
                        if (gp.localPlayer().inventorySlots[n] == null) {
                            action(
                                    InventoryActionType.UNEQUIP_TO_BAG,
                                    0,
                                    n,
                                    EQUIPMENT_SLOTS[i],
                                    null);
                            return;
                        }
                    gp.ui.addMessage("Make room in your inventory first.");
                    return;
                }
        if (window != Window.INVENTORY && !(window == Window.CHARACTER && tab == 1)) return;
        for (int i = 0; i < 20; i++)
            if (bag[i].contains(x, y)) {
                Entity item = gp.localPlayer().inventorySlots[i];
                if (item == null) return;
                if (item.type == Entity.type_consumable)
                    action(InventoryActionType.USE, i, 0, 0, null);
                else if (inventory.Hotbar.isWeapon(item))
                    action(InventoryActionType.EQUIP, i, 0, Player.EQUIP_WEAPON, null);
                else if (item instanceof OBJ_apparel clothing
                        && clothing.slot != art.CharacterOutfit.Slot.HAIR)
                    action(
                            InventoryActionType.EQUIP,
                            i,
                            0,
                            Player.EQUIP_HAIR + clothing.slot.ordinal(),
                            null);
                else if (item.type == Entity.type_shield)
                    action(InventoryActionType.EQUIP, i, 0, Player.EQUIP_SHIELD, null);
                return;
            }
    }

    /** Performs the click operation for GameUI. */
    private void click(int x, int y) {
        if (close.contains(x, y)) {
            close();
            return;
        }
        for (int i = 0; i < 10; i++)
            if (hotbar[i].contains(x, y)) {
                selectHotbar(i, false);
                return;
            }
        if (window == Window.INVENTORY || window == Window.CHARACTER && tab == 1)
            for (int i = 0; i < 20; i++)
                if (bag[i].contains(x, y)) {
                    selectedBag = i;
                    return;
                }
        if (window == Window.CHARACTER && tab == 0) {
            Player p = gp.localPlayer();
            int left = frame.x + 28, top = frame.y + 119;
            int[] base = {p.strength, p.dexterity, p.intelligence, p.progression.stamina};
            for (int i = 0; i < 4; i++) {
                if (new Rectangle(left + 250, top + 86 + i * 30, 24, 24).contains(x, y)
                        && pendingStats[i] > 0) {
                    pendingStats[i]--;
                    return;
                }
                if (new Rectangle(left + 282, top + 86 + i * 30, 24, 24).contains(x, y)
                        && base[i] + pendingStats[i] < 60
                        && Arrays.stream(pendingStats).sum() < p.progression.attributePoints) {
                    pendingStats[i]++;
                    return;
                }
            }
            if (new Rectangle(left, top + 330, 140, 28).contains(x, y)) {
                Arrays.fill(pendingStats, 0);
                return;
            }
            if (new Rectangle(left + 155, top + 330, 140, 28).contains(x, y)) {
                if (p.progression.allocate(p, pendingStats)) Arrays.fill(pendingStats, 0);
                return;
            }
        }
        if (window == Window.CHARACTER) {
            for (int i = 0; i < 3; i++)
                if (tabs[i].contains(x, y)) {
                    tab = i;
                    cancelDrag();
                    return;
                }
            if (tab == 2 && skillIndex < 3) {
                for (int i = 0; i < 5; i++)
                    if (new Rectangle(frame.x + 439 + i * 42, frame.y + 173, 34, 34)
                            .contains(x, y)) {
                        assignSkill(i);
                        return;
                    }
                for (int i = 0; i <= abilityCount(); i++)
                    if (new Rectangle(
                                    frame.x + 439 + (i % 2) * 193,
                                    frame.y + 255 + (i / 2) * 18,
                                    190,
                                    18)
                            .contains(x, y)) {
                        selectedAbility = i - 1;
                        return;
                    }

                if (new Rectangle(frame.x + 439, frame.y + 493, 70, 26).contains(x, y)) {
                    talentPage = Math.max(0, talentPage - 1);
                    return;
                }
                if (new Rectangle(frame.x + 750, frame.y + 493, 70, 26).contains(x, y)) {
                    talentPage = Math.min((talentRows().size() - 1) / 3, talentPage + 1);
                    return;
                }
                for (int row = 0; row < 3; row++) {
                    int i = talentPage * 3 + row;
                    if (i < talentRows().size()
                            && new Rectangle(frame.x + 439, frame.y + 386 + row * 34, 385, 30)
                                    .contains(x, y)) {
                        progression.Talents.buy(gp.localPlayer(), talentRows().get(i));
                        return;
                    }
                }
            }
            if (tab == 2)
                for (int i = 0; i < 8; i++)
                    if (new Rectangle(frame.x + 28, skillRowTop(i), 340, 32).contains(x, y)) {
                        skillIndex = i;
                        selectedAbility = -2;
                        talentPage = 0;
                        break;
                    }
            return;
        }
        if (window == Window.JOURNAL) return;
        for (int i = 0; i < 3; i++)
            if (filters[i].contains(x, y)) {
                filter = i;
                scroll = 0;
                List<Integer> rows = recipes();
                if (!rows.contains(recipeIndex) && !rows.isEmpty()) recipeIndex = rows.getFirst();
                return;
            }
        for (var row : recipeRows.entrySet())
            if (row.getValue().contains(x, y)) {
                recipeIndex = row.getKey();
                return;
            }
        if (craftButton.contains(x, y)) craft();
        else if (placeButton.contains(x, y)) place();
        else if (plus.contains(x, y) && gp.localPlayer().craftingProgress.level >= 9)
            quantity = Math.min(99, quantity + 1);
        else if (minus.contains(x, y)) quantity = Math.max(1, quantity - 1);
    }

    /** Resolves the recipe result used by GameUI. */
    private RecipeRegistry.Recipe recipe() {
        return RecipeRegistry.RECIPES.get(
                Math.max(0, Math.min(recipeIndex, RecipeRegistry.RECIPES.size() - 1)));
    }

    /** Resolves the batches result used by GameUI. */
    private int batches() {
        return gp.localPlayer().craftingProgress.level >= 9 ? quantity : 1;
    }

    /** Performs the craft operation for GameUI. */
    private void craft() {
        if (!recipes().contains(recipeIndex)) return;
        if (craftCooldown == 0 && gp.crafting.craft(recipe(), batches())) craftCooldown = 6;
    }

    /** Performs the place operation for GameUI. */
    private void place() {
        if (!recipes().contains(recipeIndex)) return;
        var definition = RecipeRegistry.item(recipe().output());
        if (definition.place().isEmpty()) return;
        ItemType item = definition.type();
        if (crafting.InventoryTransaction.count(gp.localPlayer(), item) < 1) return;
        close();
        gp.crafting.beginPlace(item);
        gp.mouseH.leftPressed = false;
    }

    /** Resolves the recipes result used by GameUI. */
    private List<Integer> recipes() {
        List<Integer> rows = new ArrayList<>();
        for (int i = 0; i < RecipeRegistry.RECIPES.size(); i++) {
            var r = RecipeRegistry.RECIPES.get(i);
            boolean unlocked = gp.localPlayer().craftingProgress.level >= r.level();
            if (filter == 0
                    || filter == 1 && gp.crafting.craftError(r, 1) == null
                    || filter == 2 && !unlocked) rows.add(i);
        }
        return rows;
    }

    /** Performs the select recipe operation for GameUI. */
    private void selectRecipe(int change) {
        List<Integer> rows = recipes();
        if (rows.isEmpty()) return;
        int at = rows.indexOf(recipeIndex);
        at = Math.floorMod(Math.max(0, at) + change, rows.size());
        recipeIndex = rows.get(at);
        scroll = Math.max(0, at - 5);
    }

    /** Renders this component into the supplied Java2D graphics context. */
    public void draw(Graphics2D original, Font font) {
        Player p = gp.localPlayer();
        if (p == null) return;
        UITheme.baseFont(font);
        Graphics2D g = (Graphics2D) original.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        drawHotbar(g);
        gp.memorials.drawHint(g);
        if (gp.memorials.open()) {
            gp.memorials.drawUI(g);
            g.dispose();
            return;
        }
        if (paintWindow == Window.NONE) drawAbilities(g);
        if (paintWindow != Window.NONE && visibility > 0) {
            frame = UITheme.window(gp.screenWidth, gp.screenHeight);
            double ease = visibility * visibility * (3 - 2 * visibility), scale = .97 + .03 * ease;
            Graphics2D panel = (Graphics2D) g.create();
            panel.setComposite(AlphaComposite.SrcOver.derive((float) ease));
            panel.translate(frame.getCenterX(), frame.getCenterY());
            panel.scale(scale, scale);
            panel.translate(-frame.getCenterX(), -frame.getCenterY());
            UITheme.panel(panel, frame);
            close.setBounds(frame.x + frame.width - 42, frame.y + 12, 26, 26);
            button(panel, close, "×", true, false);
            UITheme.text(
                    panel,
                    paintWindow == Window.INVENTORY ? "Inventory & Crafting" : "Character",
                    frame.x + 18,
                    frame.y + 31,
                    19,
                    UITheme.TEXT);
            if (paintWindow == Window.INVENTORY) inventoryWindow(panel);
            else if (paintWindow == Window.JOURNAL) journalWindow(panel);
            else characterWindow(panel);
            panel.dispose();
        }
        Entity hover = hovered();
        if (hover != null && canControl() && dragBag < 0 && dragEquip < 0 && dragHotbar < 0)
            tooltip(g, hover);
        Entity dragged =
                dragBag >= 0
                        ? p.inventorySlots[dragBag]
                        : dragEquip >= 0
                                ? p.equippedItem(dragEquip)
                                : dragHotbar >= 0 ? p.hotbar.item(p, dragHotbar) : null;
        if (dragged != null)
            UITheme.itemIcon(g, dragged.down1, new Rectangle(mx - 23, my - 23, 46, 46));
        g.dispose();
    }

    /** Performs the button operation for GameUI. */
    private void button(Graphics2D g, Rectangle r, String label, boolean enabled, boolean active) {
        UITheme.button(
                g,
                r,
                label,
                enabled,
                r.contains(mx, my),
                active || pressAge > 0 && gp.mouseH.leftPressed && r.contains(mx, my));
    }

    /** Draws the hotbar for GameUI. */
    private void drawHotbar(Graphics2D g) {
        Player p = gp.localPlayer();
        int stride = UITheme.SLOT + UITheme.GAP,
                start = (gp.screenWidth - (stride * 10 - UITheme.GAP)) / 2,
                y = gp.screenHeight - 62;
        for (int i = 0; i < 10; i++) {
            Rectangle r = hotbar[i];
            r.setBounds(start + i * stride, y, UITheme.SLOT, UITheme.SLOT);
            Entity item = p.hotbar.item(p, i);
            UITheme.slot(g, r, p.hotbar.selected == i, item == null ? null : item.down1);
            UITheme.text(
                    g,
                    i < 5 ? "S+" + (i + 1) : i == 9 ? "0" : Integer.toString(i + 1),
                    r.x + 4,
                    r.y + 12,
                    10,
                    UITheme.MUTED);
            int count = p.hotbar.count(p, i);
            if (item != null && item.stackable && count > 1)
                UITheme.text(
                        g,
                        Integer.toString(count),
                        r.x + r.width - 7 - Integer.toString(count).length() * 7,
                        r.y + r.height - 4,
                        11,
                        UITheme.TEXT);
        }
    }

    /** Draws the abilities for GameUI. */
    private void drawAbilities(Graphics2D g) {
        Player p = gp.localPlayer();
        int x = gp.screenWidth - 232, y = gp.screenHeight - 69;
        if (p.isMage())
            UITheme.slot(
                    g,
                    new Rectangle(x - 42, y, 34, 34),
                    false,
                    entity.MageCombat.icon("arcane_bolt"));
        UITheme.text(
                g,
                p.isMage() ? "LMB bolt · 1–5 skills · C to assign" : "1–5 skills · C to assign",
                x,
                y - 8,
                10,
                UITheme.MUTED);
        if (p.sword.equipped() && p.sword.chargePercent() >= 0)
            UITheme.bar(
                    g,
                    new Rectangle(x, y - 37, 180, 16),
                    p.sword.chargePercent(),
                    100,
                    UITheme.ACCENT,
                    "Bullthrust");
        if (p.isMage()) {
            if (!p.mage.castingName().isEmpty())
                UITheme.bar(
                        g,
                        new Rectangle(x, y - 37, 180, 16),
                        p.mage.castLength() - p.mage.castTicks(),
                        p.mage.castLength(),
                        UITheme.ACCENT,
                        p.mage.castingName());
            else if (p.mage.crochetReady())
                UITheme.text(g, "Crochet ready", x, y - 22, 11, UITheme.ACCENT);
        }
        for (int i = 0; i < p.skills.length; i++) {
            Skill skill = p.skills[i];
            Rectangle r = new Rectangle(x + i * 42, y, 34, 34);
            if (skill == null) {
                UITheme.slot(g, r, false, null);
                UITheme.text(g, Integer.toString(i + 1), r.x + 3, r.y + 11, 10, UITheme.MUTED);
                continue;
            }

            UITheme.slot(g, r, false, skill.icon);
            UITheme.text(g, Integer.toString(i + 1), r.x + 3, r.y + 11, 10, UITheme.TEXT);
            if (!skill.isReady()) {
                g.setColor(new Color(0, 0, 0, 160));
                g.fillRect(r.x + 1, r.y + 1, r.width - 1, r.height - 1);
                UITheme.text(
                        g,
                        Integer.toString((skill.cooldownRemaining + 59) / 60),
                        r.x + 10,
                        r.y + 24,
                        12,
                        UITheme.TEXT);
            }
        }
    }

    /** Returns the authored ability count for the selected combat mastery. */
    private int abilityCount() {
        return skillIndex == 0
                ? entity.SwordCombat.NAMES.length
                : skillIndex == 1 ? entity.MageCombat.SPELLS.length : 5;
    }

    /** Assigns through player authority, with a visible reason for a rejected operation. */
    private void assignSkill(int slot) {
        if (selectedAbility < -1) return;
        if (!gp.localPlayer().assignAction(skillIndex, selectedAbility, slot))
            gp.ui.addMessage("Unlock this skill first; assign while idle in solo play.");
    }

    /** Shows every ability, its correct icon, mastery lock, and the saved five-slot bar. */
    private void drawSkillCatalog(Graphics2D g, int x, int y) {
        Player p = gp.localPlayer();
        int[] bar = p.actionSlots(skillIndex);
        for (int i = 0; i < 5; i++) {
            int id = bar[i];
            var r = new Rectangle(x + i * 42, y + 54, 34, 34);
            UITheme.slot(g, r, false, id < 0 ? null : abilityIcon(id));
            UITheme.text(g, Integer.toString(i + 1), r.x + 3, r.y + 11, 10, UITheme.TEXT);
        }
        for (int i = 0; i <= abilityCount(); i++) {
            int id = i - 1;
            int left = x + (i % 2) * 193, top = y + 136 + (i / 2) * 18;
            boolean unlocked =
                    id < 0
                            || abilityLevel(id)
                                    <= progression.Progression.skillLevel(
                                            skillIndex == 0
                                                    ? p.meleeMasteryXP
                                                    : skillIndex == 1
                                                            ? p.magicMasteryXP
                                                            : p.bowMasteryXP);
            if (selectedAbility == id) {
                g.setColor(UITheme.SECONDARY);
                g.fillRect(left, top, 190, 18);
            }
            if (id >= 0) g.drawImage(abilityIcon(id), left, top + 1, 16, 16, null);
            String name =
                    id < 0
                            ? "Empty slot"
                            : skillIndex == 0
                                    ? entity.SwordCombat.NAMES[id]
                                    : skillIndex == 1
                                            ? entity.MageCombat.SPELLS[id]
                                            : p.ranger.catalogSkill(id).name;
            UITheme.text(
                    g,
                    name + (unlocked ? "" : " [Lv " + abilityLevel(id) + "]"),
                    left + 19,
                    top + 13,
                    10,
                    unlocked ? UITheme.TEXT : UITheme.DISABLED);
        }
    }

    /** Uses stable ability IDs rather than their current hotbar positions for icon selection. */
    private java.awt.image.BufferedImage abilityIcon(int id) {
        return skillIndex == 0
                ? entity.SwordCombat.icon(id)
                : skillIndex == 1
                        ? entity.MageCombat.spellIcon(id)
                        : gp.localPlayer().ranger.catalogSkill(id).icon;
    }

    /** Mirrors the authoritative mastery requirements for the catalog's lock labels. */
    private int abilityLevel(int id) {
        return skillIndex == 0
                ? entity.SwordCombat.UNLOCK[id]
                : skillIndex == 1 ? entity.MageCombat.UNLOCK[id] : new int[] {5, 7, 9, 12, 18}[id];
    }

    /** Performs the bag operation for GameUI. */
    private void bag(Graphics2D g, int x, int y) {
        Player p = gp.localPlayer();
        for (int i = 0; i < 20; i++) {
            Rectangle r = bag[i];
            r.setBounds(x + i % 5 * 54, y + i / 5 * 54, 46, 46);
            Entity item = p.inventorySlots[i];
            UITheme.slot(
                    g,
                    r,
                    selectedBag == i || r.contains(mx, my),
                    dragBag == i || item == null ? null : item.down1);
            if (item != null && item.stackable && item.stackCount > 1)
                UITheme.text(
                        g, Integer.toString(item.stackCount), r.x + 28, r.y + 42, 11, UITheme.TEXT);
        }
    }

    /** Performs the inventory window operation for GameUI. */
    private void inventoryWindow(Graphics2D g) {
        Player p = gp.localPlayer();
        int x = frame.x + 20, y = frame.y;
        UITheme.text(g, "Carried items", x, y + 68, 14, UITheme.TEXT);
        bag(g, x, y + 84);
        UITheme.text(g, "Drag items to move or assign shortcuts.", x, y + 318, 11, UITheme.MUTED);
        UITheme.text(g, "Right-click to equip or use.", x, y + 337, 11, UITheme.MUTED);
        Entity selected = selectedBag >= 0 ? p.inventorySlots[selectedBag] : null;
        if (selected != null) {
            UITheme.text(g, fit(selected.name, 35), x, y + 374, 15, UITheme.ACCENT);
            UITheme.text(g, "Quantity: " + selected.stackCount, x, y + 397, 12, UITheme.MUTED);
            UITheme.text(
                    g,
                    fit(selected.description.replace('\n', ' '), 36),
                    x,
                    y + 420,
                    11,
                    UITheme.MUTED);
        }
        int right = frame.x + 325;
        UITheme.text(
                g, "Crafting · Level " + p.craftingProgress.level, right, y + 68, 14, UITheme.TEXT);
        UITheme.bar(
                g,
                new Rectangle(right, y + 79, frame.width - 350, 12),
                p.craftingProgress.xp,
                p.craftingProgress.next(),
                UITheme.STAMINA,
                null);
        UITheme.text(
                g,
                p.craftingProgress.xp
                        + " / "
                        + (p.craftingProgress.next() == 0 ? "MAX" : p.craftingProgress.next())
                        + " XP",
                right,
                y + 107,
                11,
                UITheme.MUTED);
        String[] labels = {"All", "Available", "Locked"};
        for (int i = 0; i < 3; i++) {
            filters[i].setBounds(right + i * 86, y + 123, 80, 27);
            button(g, filters[i], labels[i], true, filter == i);
        }
        List<Integer> rows = recipes();
        if (!rows.contains(recipeIndex) && !rows.isEmpty()) recipeIndex = rows.getFirst();
        int visible = 10;
        scroll = Math.min(scroll, Math.max(0, rows.size() - visible));
        recipeRows.clear();
        for (int n = scroll; n < Math.min(rows.size(), scroll + visible); n++) {
            int index = rows.get(n), ry = y + 163 + (n - scroll) * 28;
            var r = RecipeRegistry.RECIPES.get(index);
            Rectangle rect = new Rectangle(right, ry, 254, 26);
            recipeRows.put(index, rect);
            boolean locked = p.craftingProgress.level < r.level();
            if (index == recipeIndex) {
                g.setColor(UITheme.SECONDARY.brighter());
                g.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 4, 4);
            }
            UITheme.itemIcon(
                    g,
                    CraftingArt.icon(RecipeRegistry.item(r.output())),
                    new Rectangle(right, ry, 25, 25));
            UITheme.text(
                    g,
                    fit(RecipeRegistry.item(r.output()).name(), 23),
                    right + 31,
                    ry + 17,
                    12,
                    locked ? UITheme.DISABLED : UITheme.TEXT);
        }
        if (rows.isEmpty()) {
            UITheme.text(g, "No matching recipes.", right, y + 185, 12, UITheme.MUTED);
            craftButton.setBounds(0, 0, 0, 0);
            placeButton.setBounds(0, 0, 0, 0);
            plus.setBounds(0, 0, 0, 0);
            minus.setBounds(0, 0, 0, 0);
            return;
        }
        int dx = right + 278;
        var r = recipe();
        var item = RecipeRegistry.item(r.output());
        UITheme.itemIcon(g, CraftingArt.icon(item), new Rectangle(dx, y + 121, 46, 46));
        UITheme.text(g, fit(item.name(), 30), dx, y + 190, 15, UITheme.ACCENT);
        UITheme.text(g, "Output: " + r.count() * batches(), dx, y + 215, 12, UITheme.TEXT);
        UITheme.text(g, "Requires Crafting Lv " + r.level(), dx, y + 239, 12, UITheme.MUTED);
        UITheme.text(g, "Station: " + r.station(), dx, y + 263, 12, UITheme.MUTED);
        int line = y + 295;
        for (var entry : gp.crafting.costs(r, batches()).entrySet()) {
            int owned = InventoryTransaction.count(p, entry.getKey()), need = entry.getValue();
            UITheme.text(
                    g,
                    RecipeRegistry.item(entry.getKey()).name() + "  " + owned + " / " + need,
                    dx,
                    line,
                    12,
                    owned >= need ? UITheme.STAMINA : UITheme.HP);
            line += 24;
        }
        minus.setBounds(dx, y + 404, 28, 26);
        plus.setBounds(dx + 101, y + 404, 28, 26);
        button(g, minus, "−", batches() > 1, false);
        button(g, plus, "+", p.craftingProgress.level >= 9, false);
        UITheme.text(g, "× " + batches(), dx + 45, y + 423, 12, UITheme.TEXT);
        craftButton.setBounds(dx, y + 445, 115, 32);
        placeButton.setBounds(dx + 125, y + 445, 115, 32);
        String error = gp.crafting.craftError(r, batches());
        button(g, craftButton, "Craft", error == null, false);
        button(
                g,
                placeButton,
                "Place",
                !item.place().isEmpty() && InventoryTransaction.count(p, item.type()) > 0,
                false);
        UITheme.text(
                g,
                fit(error == null ? "Ready · Enter to craft" : error, 42),
                dx,
                y + 502,
                11,
                error == null ? UITheme.STAMINA : UITheme.HP);
        UITheme.text(
                g, fit(gp.crafting.feedback, 130), x, y + frame.height - 18, 11, UITheme.MUTED);
    }

    /** Resolves the talent rows result used by GameUI. */
    private java.util.List<progression.Talents.Definition> talentRows() {
        return progression.Talents.PATHS.get(
                skillIndex == 0 ? "Sword" : skillIndex == 1 ? "Staff" : "Bow");
    }

    /** Resolves the skill row top result used by GameUI. */
    private int skillRowTop(int index) {
        return frame.y + 119 + (index < 3 ? 12 + index * 36 : 146 + (index - 3) * 36);
    }

    /** Performs the journal window operation for GameUI. */
    private void journalWindow(Graphics2D g) {
        Player p = gp.localPlayer();
        int x = frame.x + 28, y = frame.y + 95;
        UITheme.text(
                g,
                "Quest Journal · Speak to the Old Man to accept or turn in",
                x,
                y,
                17,
                UITheme.ACCENT);
        for (var d : progression.StarterQuests.DEFINITIONS) {
            var entry = p.progression.journal.entries.get(d.id());
            y += 43;
            UITheme.text(
                    g, d.title() + " · " + p.progression.journal.state(d), x, y, 15, UITheme.TEXT);
            UITheme.text(
                    g,
                    d.description() + " · Reward: " + d.xp() + " XP / " + d.coins() + " coins",
                    x,
                    y + 21,
                    12,
                    UITheme.MUTED);
            y += 25;
            for (int i = 0; i < d.events().length; i++) {
                y += 20;
                UITheme.text(
                        g,
                        d.events()[i] + "   " + entry.counts[i] + " / " + d.needed()[i],
                        x + 18,
                        y,
                        12,
                        UITheme.TEXT);
            }
        }
    }

    /** Performs the character window operation for GameUI. */
    private void characterWindow(Graphics2D g) {
        Player p = gp.localPlayer();
        String[] labels = {"Character", "Equipment", "Skills"};
        for (int i = 0; i < 3; i++) {
            tabs[i].setBounds(frame.x + 20 + i * 150, frame.y + 51, 140, 30);
            button(g, tabs[i], labels[i], true, tab == i);
        }
        int x = frame.x + 28, y = frame.y + 119;
        if (tab == 0) {
            UITheme.text(g, p.playerName, x, y, 23, UITheme.TEXT);
            UITheme.text(g, "Level " + p.level, x, y + 31, 15, UITheme.ACCENT);
            UITheme.bar(
                    g,
                    new Rectangle(x, y + 48, 360, 19),
                    p.exp,
                    p.nextLevelExp,
                    UITheme.STAMINA,
                    "XP " + p.exp + " / " + p.nextLevelExp);
            String[] names = {"STR", "DEX", "INT", "STA", "Attack", "Defense", "Coin"};
            int[] values = {
                p.strength + pendingStats[0],
                p.dexterity + pendingStats[1],
                p.intelligence + pendingStats[2],
                p.progression.stamina + pendingStats[3],
                p.getAttack(),
                p.getDefense(),
                p.coin
            };
            for (int i = 0; i < names.length; i++) {
                UITheme.text(g, names[i], x, y + 103 + i * 30, 14, UITheme.MUTED);
                UITheme.text(
                        g,
                        Integer.toString(values[i]),
                        x + 192,
                        y + 103 + i * 30,
                        14,
                        UITheme.TEXT);
            }
            for (int i = 0; i < 4; i++) {
                UITheme.button(
                        g,
                        new Rectangle(x + 250, y + 86 + i * 30, 24, 24),
                        "−",
                        pendingStats[i] > 0,
                        false,
                        false);
                UITheme.button(
                        g,
                        new Rectangle(x + 282, y + 86 + i * 30, 24, 24),
                        "+",
                        Arrays.stream(pendingStats).sum() < p.progression.attributePoints,
                        false,
                        false);
            }
            UITheme.text(
                    g,
                    "Available: "
                            + (p.progression.attributePoints - Arrays.stream(pendingStats).sum())
                            + " · Talent points: "
                            + p.progression.talentPoints,
                    x,
                    y + 315,
                    12,
                    UITheme.ACCENT);
            UITheme.button(
                    g, new Rectangle(x, y + 330, 140, 28), "Reset Pending", true, false, false);
            UITheme.button(
                    g,
                    new Rectangle(x + 155, y + 330, 140, 28),
                    "Confirm",
                    !gp.isNetworked,
                    false,
                    false);
            UITheme.text(
                    g,
                    String.format(
                            "Preview: Sword %.1f%% · Bow %.1f%% · Staff %.1f%%",
                            100 * (progression.Progression.multiplier(values[0]) - 1),
                            100 * (progression.Progression.multiplier(values[1]) - 1),
                            100 * (progression.Progression.multiplier(values[2]) - 1)),
                    x,
                    y + 385,
                    12,
                    UITheme.MUTED);
            UITheme.text(
                    g,
                    "Gathering energy: " + p.progression.energy + " / " + p.progression.maxEnergy(),
                    x,
                    y + 410,
                    12,
                    UITheme.STAMINA);
            UITheme.text(
                    g,
                    "Weapons determine your combat actions.",
                    x + 442,
                    y + 259,
                    13,
                    UITheme.MUTED);
            UITheme.icon(g, p.portraitFrame(), new Rectangle(x + 460, y, 190, 225));
        } else if (tab == 1) {
            UITheme.text(g, "Inventory", x, y, 14, UITheme.TEXT);
            bag(g, x, y + 17);
            UITheme.text(
                    g,
                    "Drag onto equipment; right-click a slot to unequip.",
                    x,
                    y + 263,
                    12,
                    UITheme.MUTED);
            for (int i = 0; i < equipment.length; i++) {
                Rectangle r = equipment[i];
                r.setBounds(frame.x + 470 + i % 3 * 165, frame.y + 135 + i / 3 * 117, 54, 54);
                Entity item = p.equippedItem(EQUIPMENT_SLOTS[i]);
                UITheme.text(g, equipmentNames[i], r.x, r.y - 10, 12, UITheme.MUTED);
                UITheme.slot(
                        g,
                        r,
                        r.contains(mx, my),
                        item == null || item.isPlaceholder || dragEquip == EQUIPMENT_SLOTS[i]
                                ? null
                                : item.down1);
                if (item != null && !item.isPlaceholder)
                    UITheme.text(g, fit(item.name, 20), r.x, r.y + 74, 10, UITheme.TEXT);
            }
        } else {
            int[] xp = {
                p.meleeMasteryXP,
                p.magicMasteryXP,
                p.bowMasteryXP,
                p.craftingProgress.xp,
                p.progression.lifeXP[0],
                p.progression.lifeXP[1],
                p.progression.lifeXP[2],
                p.progression.lifeXP[3]
            };
            UITheme.text(g, "Combat Skills", x, y, 12, UITheme.ACCENT);
            UITheme.text(g, "Life Skills", x, y + 134, 12, UITheme.ACCENT);
            for (int i = 0; i < 8; i++) {
                int sy = skillRowTop(i);
                Rectangle row = new Rectangle(x, sy, 340, 32);
                if (skillIndex == i) {
                    g.setColor(UITheme.SECONDARY.brighter());
                    g.fillRoundRect(row.x, row.y, row.width, row.height, 4, 4);
                }
                UITheme.text(g, skillNames[i], x + 8, sy + 21, 13, UITheme.TEXT);
                UITheme.text(
                        g,
                        i == 3
                                ? "Lv " + p.craftingProgress.level + " · " + xp[i] + " XP"
                                : "Lv "
                                        + progression.Progression.skillLevel(xp[i])
                                        + " · "
                                        + xp[i]
                                        + " XP",
                        x + 140,
                        sy + 21,
                        12,
                        UITheme.MUTED);
            }
            int dx = x + 411;
            UITheme.text(g, skillNames[skillIndex], dx, y, 20, UITheme.ACCENT);
            if (skillIndex == 3) {
                UITheme.bar(
                        g,
                        new Rectangle(dx, y + 30, 385, 20),
                        p.craftingProgress.xp,
                        p.craftingProgress.next(),
                        UITheme.STAMINA,
                        p.craftingProgress.next() == 0
                                ? "MAX LEVEL"
                                : p.craftingProgress.xp
                                        + " / "
                                        + p.craftingProgress.next()
                                        + " XP");
                UITheme.text(
                        g, "Gain XP by completing existing recipes.", dx, y + 83, 13, UITheme.TEXT);
                var next =
                        RecipeRegistry.RECIPES.stream()
                                .filter(r -> r.level() > p.craftingProgress.level)
                                .min(Comparator.comparingInt(RecipeRegistry.Recipe::level))
                                .orElse(null);
                UITheme.text(
                        g,
                        next == null
                                ? "All current recipes unlocked."
                                : "Next: "
                                        + RecipeRegistry.item(next.output()).name()
                                        + " at Lv "
                                        + next.level(),
                        dx,
                        y + 113,
                        12,
                        UITheme.MUTED);
            } else if (skillIndex < 3) {
                UITheme.text(
                        g,
                        "Select a skill below, then press 1–5 to assign.",
                        dx,
                        y + 42,
                        13,
                        UITheme.TEXT);
                UITheme.text(g, "", dx, y + 73, 13, UITheme.MUTED);
                UITheme.text(
                        g,
                        "Level "
                                + progression.Progression.skillLevel(xp[skillIndex])
                                + " / 25 · Talent points: "
                                + p.progression.talentPoints,
                        dx,
                        y + 104,
                        12,
                        UITheme.MUTED);
            } else {
                int profession = skillIndex - 3;
                int total = xp[skillIndex], lv = progression.LifeSkills.level(p, profession);
                UITheme.bar(
                        g,
                        new Rectangle(dx, y + 30, 385, 20),
                        progression.Progression.skillRemainder(total),
                        progression.Progression.masteryRequired(lv),
                        UITheme.STAMINA,
                        "Lv " + lv + " / 25");
                UITheme.text(
                        g,
                        skillIndex == 7
                                ? p.life.status()
                                : skillIndex == 6
                                        ? "Cook at a campfire; use meals/drinks from your bag."
                                        : "Use appropriate tools or forge recipes to gain XP.",
                        dx,
                        y + 72,
                        11,
                        UITheme.MUTED);
            }
            if (skillIndex >= 3) {
                int profession = skillIndex - 3;
                UITheme.text(
                        g,
                        "Profession perks · no Combat Talent cost",
                        dx,
                        y + 146,
                        13,
                        UITheme.ACCENT);
                for (int tier = 1; tier <= 5; tier++)
                    UITheme.text(
                            g,
                            "Lv "
                                    + tier * 5
                                    + " · "
                                    + progression.LifeSkills.perk(profession, tier),
                            dx,
                            y + 175 + tier * 30,
                            11,
                            progression.LifeSkills.perks(p, profession) >= tier
                                    ? UITheme.TEXT
                                    : UITheme.DISABLED);
                return;
            }
            drawSkillCatalog(g, dx, y);
            for (int row = 0; row < 3; row++) {
                int i = talentPage * 3 + row;
                if (i >= talentRows().size()) break;
                var talent = talentRows().get(i);
                UITheme.button(
                        g,
                        new Rectangle(dx, frame.y + 386 + row * 34, 385, 30),
                        talent.name()
                                + " "
                                + p.progression.talents.getOrDefault(talent.id(), 0)
                                + "/"
                                + talent.maxRank()
                                + " · Lv "
                                + talent.characterLevel()
                                + " · prior "
                                + progression.Talents.prior(talent),
                        progression.Talents.available(p, talent),
                        false,
                        false);
                if (new Rectangle(dx, frame.y + 386 + row * 34, 385, 30).contains(mx, my))
                    UITheme.text(g, talent.effect(), dx, frame.y + 487, 11, UITheme.ACCENT);
            }
            UITheme.text(
                    g,
                    "Requires character AND weapon level + lower-tier investment",
                    dx,
                    frame.y + 378,
                    10,
                    UITheme.MUTED);
            UITheme.button(
                    g,
                    new Rectangle(dx, frame.y + 493, 70, 26),
                    "Previous",
                    talentPage > 0,
                    false,
                    false);
            UITheme.text(
                    g,
                    "Talents " + (talentPage + 1) + " / " + ((talentRows().size() + 2) / 3),
                    dx + 140,
                    frame.y + 510,
                    12,
                    UITheme.MUTED);
            UITheme.button(
                    g,
                    new Rectangle(frame.x + 750, frame.y + 493, 70, 26),
                    "Next",
                    talentPage < (talentRows().size() - 1) / 3,
                    false,
                    false);
        }
    }

    /** Resolves the hovered result used by GameUI. */
    private Entity hovered() {
        Player p = gp.localPlayer();
        for (int i = 0; i < 10; i++) if (hotbar[i].contains(mx, my)) return p.hotbar.item(p, i);
        if (window == Window.INVENTORY || window == Window.CHARACTER && tab == 1) {
            for (int i = 0; i < 20; i++) if (bag[i].contains(mx, my)) return p.inventorySlots[i];
        }
        if (window == Window.CHARACTER && tab == 1)
            for (int i = 0; i < equipment.length; i++)
                if (equipment[i].contains(mx, my)) {
                    Entity item = p.equippedItem(EQUIPMENT_SLOTS[i]);
                    return item == null || item.isPlaceholder ? null : item;
                }
        return null;
    }

    /** Performs the tooltip operation for GameUI. */
    private void tooltip(Graphics2D g, Entity item) {
        List<String> lines = new ArrayList<>();
        lines.add(item.name);
        lines.add(category(item));
        for (String line : item.description.split("\\n"))
            if (!line.equals("(" + item.name + ")")) lines.add(fit(line, 54));
        lines.add("Quantity: " + item.stackCount);
        if (item.attackValue > 0) lines.add("Attack: " + item.attackValue);
        if (item.defenseValue > 0) lines.add("Defense: " + item.defenseValue);
        if (item instanceof OBJ_crafted c && c.definition.gather() > 0)
            lines.add("Gathering power: " + c.definition.gather());
        int w = 80;
        g.setFont(UITheme.font(12));
        for (String line : lines) w = Math.max(w, g.getFontMetrics().stringWidth(line) + 24);
        w = Math.min(w, gp.screenWidth - 24);
        int h = lines.size() * 20 + 18,
                x = Math.max(8, Math.min(mx + 16, gp.screenWidth - w - 8)),
                y = Math.max(8, Math.min(my + 18, gp.screenHeight - h - 8));
        UITheme.panel(g, new Rectangle(x, y, w, h));
        for (int i = 0; i < lines.size(); i++)
            UITheme.text(
                    g,
                    lines.get(i),
                    x + 12,
                    y + 23 + i * 20,
                    12,
                    i == 0 ? UITheme.ACCENT : UITheme.TEXT);
    }

    /** Resolves the category result used by GameUI. */
    private String category(Entity item) {
        if (item instanceof OBJ_crafted c) return "Category: " + c.definition.kind();
        if (item instanceof OBJ_apparel) return "Category: Apparel";
        if (inventory.Hotbar.isWeapon(item)) return "Category: Weapon";
        if (item.type == Entity.type_consumable) return "Category: Consumable";
        if (item.type == Entity.type_shield) return "Category: Offhand";
        return "Category: Item";
    }

    /** Resolves the fit result used by GameUI. */
    private String fit(String text, int count) {
        if (text == null) return "";
        return text.length() > count ? text.substring(0, Math.max(0, count - 1)) + "…" : text;
    }

    /** Performs the status operation for GameUI. */
    public void status(Graphics2D g) {
        Player p = gp.localPlayer();
        if (p == null) return;
        if (p.life.fishing())
            UITheme.text(g, p.life.status(), 24, gp.screenHeight - 110, 15, UITheme.ACCENT);
        if (Double.isNaN(hpDisplay)) hpDisplay = p.HP;
        if (Double.isNaN(mpDisplay)) mpDisplay = p.MP;
        UITheme.panel(
                g,
                new Rectangle(
                        16, 16, 232, !p.usesMana() && (p.maxRage > 0 || p.isPaladin()) ? 100 : 76));
        UITheme.bar(
                g,
                new Rectangle(26, 27, 212, 20),
                hpDisplay,
                p.maxHP,
                UITheme.HP,
                "HP   " + p.HP + " / " + p.maxHP);
        UITheme.bar(
                g,
                new Rectangle(26, 55, 212, 20),
                mpDisplay,
                p.maxMP,
                UITheme.MP,
                "MP   " + p.MP + " / " + p.maxMP);
        if (p.isMage())
            UITheme.text(
                    g,
                    "Runes  " + p.arcaneCharge + " / " + p.maxArcaneCharge,
                    26,
                    112,
                    11,
                    UITheme.ACCENT);
        else if (p.hasShield() && !p.isPaladin())
            UITheme.text(
                    g, "Resolve  " + p.resolve + " / " + p.maxResolve, 26, 134, 11, UITheme.ACCENT);
        if (!p.usesMana() && (p.maxRage > 0 || p.isPaladin()))
            UITheme.bar(
                    g,
                    new Rectangle(26, 83, 212, 16),
                    p.skillResource(),
                    p.isPaladin() ? p.maxResolve : p.maxRage,
                    UITheme.ACCENT,
                    p.resourceName() + "  " + p.skillResource());
    }
}
