package interaction;

import entity.*;

import main.Gamepanel;

import world.WorldObject;

import java.awt.*;

/**
 * One actionable adjacent-cell target, shared by outlines, gathering and authoritative
 * interactions.
 */
public final class InteractionTargetResolver {
    public record Target(
            Object token,
            String id,
            double x,
            double y,
            double score,
            Rectangle footprint,
            Runnable action) {}

    private final Gamepanel gp;
    public final InteractionValidator validator;
    private Target current, best;
    private Player resolving;
    private int resolvedX, resolvedY;
    private String resolvedDirection;

    /**
     * Creates a InteractionTargetResolver instance and initializes the state required by its
     * collaborators.
     */
    public InteractionTargetResolver(Gamepanel gp) {
        this.gp = gp;
        validator = new InteractionValidator(gp);
    }

    /** Resolves the player result used by InteractionTargetResolver. */
    public Player player() {
        return resolving == null ? gp.localPlayer() : resolving;
    }

    /** Evaluates the blocked condition for InteractionTargetResolver. */
    public boolean blocked() {
        Player p = gp.localPlayer();
        return unavailable(p)
                || gp.inventoryOpen
                || gp.ui.game.blocksWorld()
                || gp.optionsOpen
                || gp.paused
                || gp.keyH.enteringChat
                || gp.crafting.open
                || gp.crafting.placingObject()
                || gp.grassEditor.active;
    }

    /** Evaluates the unavailable condition for InteractionTargetResolver. */
    private boolean unavailable(Player p) {
        return p == null
                || gp.gameState != gp.playState
                || p.isDead
                || p.inDialogue
                || p.merchantOpen
                || p.inWell != gp.quests.inDungeon()
                || gp.quests.isBossIntro();
    }

    /** Evaluates the facing condition for InteractionTargetResolver. */
    public boolean facing(double x, double y) {
        Player p = player();
        return p != null && validator.frontBounds(p).contains(x, y);
    }

    /** Evaluates the accepts condition for InteractionTargetResolver. */
    public boolean accepts(Player p, Rectangle footprint) {
        return accepts(p, footprint, null);
    }

    /** Evaluates the accepts condition for InteractionTargetResolver. */
    public boolean accepts(Player p, Rectangle footprint, Object owner) {
        return !unavailable(p) && validator.valid(p, footprint, owner);
    }

    /** Performs the offer operation for InteractionTargetResolver. */
    public void offer(Object token, String id, Rectangle footprint, int priority, Runnable action) {
        Player p = player();
        if (!accepts(p, footprint, token)) return;
        Target candidate =
                new Target(
                        token,
                        id,
                        footprint.getCenterX(),
                        footprint.getCenterY(),
                        -priority,
                        new Rectangle(footprint),
                        action);
        if (best == null
                || candidate.score() < best.score()
                || candidate.score() == best.score()
                        && (current != null && current.token() == token
                                || !(current != null && current.token() == best.token())
                                        && id.compareTo(best.id()) < 0)) best = candidate;
    }

    /**
     * Chooses one valid adjacent-tile interaction target using the same priority rules as actions
     * and outlines.
     */
    public Target resolve(Player p) {
        long performanceStart = gp.performance.start();
        try {
            if (unavailable(p) || p == gp.localPlayer() && blocked()) return null;
            Player saved = resolving;
            Target savedBest = best;
            resolving = p;
            best = null;
            try {
                gp.memorials.collectTargets(this);
                gp.crafting.collectTargets(this);
                gp.buildings.collectTargets(this);
                for (WorldObject o : gp.worldObjects.query(validator.frontBounds(p)))
                    if (!o.resourceDepleted && o.isInteractive())
                        offer(
                                o,
                                "scenery:" + o.kind + ":" + o.col + ":" + o.row,
                                o.interactionFootprint(),
                                2,
                                () -> o.interact(p));
                for (int i = 0; i < gp.obj.length; i++) {
                    Entity item = gp.obj[i];
                    if (item == null || item.pickupBlocked || item.groundLoot) continue;
                    final int slot = i;
                    offer(
                            item,
                            "pickup:" + i,
                            InteractionValidator.bounds(item),
                            4,
                            () -> p.pickUpObject(slot));
                }
                for (int i = 0; i < gp.npc.length; i++) {
                    Entity npc = gp.npc[i];
                    if (npc == null) continue;
                    final int slot = i;
                    offer(
                            npc,
                            "npc:" + i,
                            InteractionValidator.bounds(npc),
                            3,
                            () -> p.interactNPCDirectional(slot));
                }
                return best;
            } finally {
                best = savedBest;
                resolving = saved;
            }
        } finally {
            gp.performance.end(main.PerformanceMetrics.Section.INTERACTION, performanceStart);
        }
    }

    /** Performs the refresh operation for InteractionTargetResolver. */
    public void refresh() {
        Player p = gp.localPlayer();
        current = blocked() ? null : resolve(p);
        if (p != null) {
            resolvedX = p.worldX;
            resolvedY = p.worldY;
            resolvedDirection = p.direction;
        }
    }

    /** Evaluates the fresh condition for InteractionTargetResolver. */
    private boolean fresh() {
        Player p = gp.localPlayer();
        return p != null
                && p.worldX == resolvedX
                && p.worldY == resolvedY
                && p.direction.equals(resolvedDirection);
    }

    /** Evaluates the selected condition for InteractionTargetResolver. */
    public boolean selected(Object token) {
        return !blocked()
                && current != null
                && current.token() == token
                && fresh()
                && gp.crafting.usableTarget(current.token());
    }

    /** Resolves the token result used by InteractionTargetResolver. */
    public Object token() {
        return target() == null ? null : current.token();
    }

    /** Resolves the target result used by InteractionTargetResolver. */
    public Target target() {
        return blocked()
                        || current == null
                        || !fresh()
                        || !gp.crafting.usableTarget(current.token())
                ? null
                : current;
    }

    /** Reports whether target for InteractionTargetResolver. */
    public boolean isTarget(Player p, Object token) {
        Target t = resolve(p);
        return t != null && t.token() == token;
    }

    /** Evaluates the interact condition for InteractionTargetResolver. */
    public boolean interact() {
        return interact(gp.localPlayer());
    }

    /** Evaluates the interact condition for InteractionTargetResolver. */
    public boolean interact(Player p) {
        Target target = resolve(p);
        boolean local = p == gp.localPlayer();
        if (target == null) {
            if (local) clear();
            return false;
        }
        if (local)
            current = target; // Preserve the exact tie-break target through nested authoritative
        // validation.
        try {
            target.action().run();
            return true;
        } finally {
            if (local) clear();
        }
    }

    /** Performs the clear operation for InteractionTargetResolver. */
    public void clear() {
        current = best = null;
    }
}
