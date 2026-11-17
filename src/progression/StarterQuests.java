package progression;

import entity.Player;

import java.util.*;

/** Event-driven starter journal; no inventory polling or duplicate world bosses. */
public final class StarterQuests {
    public record Definition(
            String id,
            String title,
            String description,
            String[] events,
            int[] needed,
            int xp,
            int coins) {}

    public static final List<Definition> DEFINITIONS =
            List.of(
                    new Definition(
                            "rest",
                            "A Place to Rest",
                            "Collect logs and establish a workshop.",
                            new String[] {"gather:LOG", "build:workbench"},
                            new int[] {4, 1},
                            100,
                            20),
                    new Definition(
                            "night",
                            "The Restless Night",
                            "Help the old man by defeating the restless dead.",
                            new String[] {"kill:skeleton"},
                            new int[] {3},
                            180,
                            35),
                    new Definition(
                            "crown",
                            "The Crowned Disturbance",
                            "Discover the Slime Nest and defeat its crowned guardian.",
                            new String[] {"explore:nest", "boss:crowned"},
                            new int[] {1, 1},
                            350,
                            80));

    public static final class Entry {
        public boolean active, claimed;
        public int[] counts;

        /** Creates a Entry instance and initializes the state required by its collaborators. */
        public Entry() {}

        /** Creates a Entry instance and initializes the state required by its collaborators. */
        Entry(int n) {
            counts = new int[n];
        }
    }

    public final Map<String, Entry> entries = new LinkedHashMap<>();
    public String tracked = "rest";

    /** Creates a StarterQuests instance and initializes the state required by its collaborators. */
    public StarterQuests() {
        for (var d : DEFINITIONS) entries.put(d.id(), new Entry(d.events().length));
    }

    /** Resolves the state result used by StarterQuests. */
    public String state(Definition d) {
        Entry e = entries.get(d.id());
        if (e.claimed) return "COMPLETED";
        if (!e.active) return "AVAILABLE";
        return ready(d) ? "READY_TO_TURN_IN" : "ACTIVE";
    }

    /** Evaluates the ready condition for StarterQuests. */
    public boolean ready(Definition d) {
        Entry e = entries.get(d.id());
        if (!e.active || e.claimed) return false;
        for (int i = 0; i < e.counts.length; i++) if (e.counts[i] < d.needed()[i]) return false;
        return true;
    }

    /** Performs the event operation for StarterQuests. */
    public void event(String name, int amount) {
        if (amount <= 0) return;
        for (var d : DEFINITIONS) {
            Entry e = entries.get(d.id());
            if (!e.active || e.claimed) continue;
            for (int i = 0; i < d.events().length; i++)
                if (d.events()[i].equals(name))
                    e.counts[i] = (int) Math.min(d.needed()[i], (long) e.counts[i] + amount);
        }
    }

    /** Talking starts available quests or claims completed objectives exactly once. */
    public void talk(Player p) {
        if (p.gp.isNetworked) return;
        var plan = p.gp.generatedWorld == null ? null : p.gp.generatedWorld.slimeNest();
        if (plan != null && p.gp.slimeNest.wasCleared(plan.id()))
            p.progression.milestones.add("boss:crowned");
        for (var d : DEFINITIONS) {
            Entry e = entries.get(d.id());
            if (e.claimed) continue;
            if (!e.active) {
                e.active = true;
                if (d.id().equals("crown") && p.progression.milestones.contains("boss:crowned"))
                    e.counts[1] = 1;
                if (d.id().equals("crown") && p.progression.milestones.contains("explore:nest"))
                    e.counts[0] = 1;
                p.gp.ui.addMessage("Quest started: " + d.title());
            } else if (ready(d)) {
                e.claimed = true;
                p.coin += d.coins();
                p.grantExperience(d.xp());
                p.gp.ui.addMessage(
                        "Completed: "
                                + d.title()
                                + " · +"
                                + d.xp()
                                + " XP · +"
                                + d.coins()
                                + " coins");
            }
        }
    }
}
