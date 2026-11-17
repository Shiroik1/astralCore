package dialogue;

import main.Log;
import main.MiniJson;

import quest.QuestManager;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** An immutable, validated dialogue. Bad data fails at load time, never mid-conversation. */
public final class DialogueGraph {

    public record Choice(
            String text,
            String next,
            List<Condition> conditions,
            Effects effects,
            boolean isDefault) {
        /** Evaluates the available condition for Choice. */
        public boolean available(DialogueContext ctx) {
            for (Condition c : conditions) if (!c.test(ctx)) return false;
            return true;
        }
    }

    public record Node(
            String id,
            String speaker,
            String text,
            String next,
            List<Choice> choices,
            Effects effects) {
        /** Reports whether this component has choices for Node. */
        public boolean hasChoices() {
            return !choices.isEmpty();
        }

        /** Reports whether terminal for Node. */
        public boolean isTerminal() {
            return next == null && choices.isEmpty();
        }
    }

    private final String id;
    private final String startId;
    private final Map<String, Node> nodes;
    private final List<String> warnings;

    /** Creates a DialogueGraph instance and initializes the state required by its collaborators. */
    private DialogueGraph(
            String id, String startId, Map<String, Node> nodes, List<String> warnings) {
        this.id = id;
        this.startId = startId;
        this.nodes = nodes;
        this.warnings = warnings;
    }

    /** Returns the current id value. */
    public String id() {
        return id;
    }

    /** Resolves the start result used by DialogueGraph. */
    public Node start() {
        return nodes.get(startId);
    }

    /** Resolves the node result used by DialogueGraph. */
    public Node node(String nodeId) {
        return nodes.get(nodeId);
    }

    /** Resolves the nodes result used by DialogueGraph. */
    public Collection<Node> nodes() {
        return nodes.values();
    }

    /** Returns the current warnings value. */
    public List<String> warnings() {
        return warnings;
    }

    /**
     * Texts along the "next" chain from the start; stops at a choice or the end. Used to compare
     * with legacy lines.
     */
    public List<String> linearText() {
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Node n = start();
                n != null && seen.add(n.id());
                n = n.next() == null ? null : node(n.next())) {
            if (n.text() != null) out.add(n.text());
            if (n.hasChoices()) break;
        }
        return out;
    }

    // ---------------------------------------------------------------- loading

    /** Empty on a missing or invalid file; the reason is logged. */
    public static Optional<DialogueGraph> load(String resourcePath) {
        try (InputStream is = DialogueGraph.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                Log.warn("[DIALOGUE] Missing file: " + resourcePath);
                return Optional.empty();
            }
            Set<String> stages = new HashSet<>();
            for (QuestManager.Stage s : QuestManager.Stage.values()) stages.add(s.name());
            DialogueGraph graph =
                    parse(new String(is.readAllBytes(), StandardCharsets.UTF_8), stages);
            for (String w : graph.warnings) Log.warn("[DIALOGUE] " + resourcePath + ": " + w);
            return Optional.of(graph);
        } catch (Exception e) {
            Log.warn("[DIALOGUE] Invalid " + resourcePath + ":\n" + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * knownStages may be null to skip stage-name checking. Throws IllegalArgumentException listing
     * every problem.
     */
    public static DialogueGraph parse(String json, Set<String> knownStages) {
        Object root = MiniJson.parse(json);
        if (!(root instanceof Map<?, ?> map))
            throw new IllegalArgumentException("Dialogue root must be a JSON object");

        List<String> errors = new ArrayList<>();
        Map<String, Node> nodes = new LinkedHashMap<>();
        String start;

        if (map.get("nodes") instanceof List<?> raw) {
            for (int i = 0; i < raw.size(); i++) {
                if (!(raw.get(i) instanceof Map<?, ?> m)) {
                    errors.add("nodes[" + i + "] is not an object");
                    continue;
                }
                Node node = parseNode(m, i, knownStages, errors);
                if (node != null && nodes.putIfAbsent(node.id(), node) != null) {
                    errors.add("Duplicate node id: " + node.id());
                }
            }
            start = str(map.get("start"));
            if (start == null) errors.add("Missing \"start\"");
        } else if (map.get("lines") instanceof List<?> lines) {
            List<String> texts = new ArrayList<>();
            for (Object line : lines) {
                if (line instanceof Map<?, ?> l && l.get("text") != null)
                    texts.add(l.get("text").toString());
            }
            if (texts.isEmpty()) errors.add("\"lines\" has no text");
            for (int i = 0; i < texts.size(); i++) {
                String next = i + 1 < texts.size() ? "n" + (i + 1) : null;
                nodes.put(
                        "n" + i,
                        new Node("n" + i, null, texts.get(i), next, List.of(), Effects.NONE));
            }
            start = "n0";
        } else {
            throw new IllegalArgumentException("Dialogue needs \"nodes\" or \"lines\"");
        }

        if (start != null && !nodes.containsKey(start))
            errors.add("Start node \"" + start + "\" does not exist");
        for (Node n : nodes.values()) {
            if (n.next() != null && !nodes.containsKey(n.next())) {
                errors.add("Node \"" + n.id() + "\" points to unknown node \"" + n.next() + "\"");
            }
            for (Choice c : n.choices()) {
                if (!nodes.containsKey(c.next())) {
                    errors.add(
                            "Node \""
                                    + n.id()
                                    + "\" has a choice pointing to unknown node \""
                                    + c.next()
                                    + "\"");
                }
            }
        }
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("\n", errors));

        return new DialogueGraph(str(map.get("id")), start, nodes, findWarnings(start, nodes));
    }

    /** Parses the node for DialogueGraph. */
    private static Node parseNode(Map<?, ?> m, int index, Set<String> stages, List<String> errors) {
        String id = str(m.get("id"));
        if (id == null) {
            errors.add("nodes[" + index + "] has no id");
            return null;
        }
        if (!Condition.validName(id)) errors.add("Invalid node id \"" + id + "\"");
        String where = "Node \"" + id + "\"";
        String text = text(m.get("text"));
        String next = str(m.get("next"));
        boolean end = Boolean.TRUE.equals(m.get("end"));

        List<Choice> choices = new ArrayList<>();
        Object rawChoices = m.get("choices");
        if (rawChoices instanceof List<?> list) {
            if (list.isEmpty()) errors.add(where + " has an empty \"choices\" list");
            for (int j = 0; j < list.size(); j++) {
                if (!(list.get(j) instanceof Map<?, ?> c)) {
                    errors.add(where + " choice " + (j + 1) + " is not an object");
                    continue;
                }
                Choice choice = parseChoice(c, where + " choice " + (j + 1), stages, errors);
                if (choice != null) choices.add(choice);
            }
        } else if (rawChoices != null) {
            errors.add(where + ": \"choices\" must be a list");
        }

        if (next != null && rawChoices != null)
            errors.add(where + " has both \"next\" and \"choices\"");
        if (end && (next != null || rawChoices != null)) {
            errors.add(where + " is an end node and cannot have \"next\" or \"choices\"");
        }
        if (!end && text == null) errors.add(where + " has no text");
        if (choices.stream().filter(Choice::isDefault).count() > 1)
            errors.add(where + " has more than one default choice");

        return new Node(
                id,
                str(m.get("speaker")),
                text,
                next,
                List.copyOf(choices),
                parseEffects(m, where, stages, errors));
    }

    /** Parses the choice for DialogueGraph. */
    private static Choice parseChoice(
            Map<?, ?> c, String where, Set<String> stages, List<String> errors) {
        String text = text(c.get("text"));
        String next = str(c.get("next"));
        if (text == null) errors.add(where + " has no text");
        if (next == null) errors.add(where + " has no \"next\"");

        List<Condition> conditions = new ArrayList<>();
        for (String raw : stringList(c.get("if"), where, "if", errors)) {
            try {
                Condition cond = Condition.parse(raw);
                if (cond.stage() && stages != null && !stages.contains(cond.value())) {
                    errors.add(where + ": Unknown quest stage \"" + cond.value() + "\"");
                }
                conditions.add(cond);
            } catch (IllegalArgumentException e) {
                errors.add(where + ": " + e.getMessage());
            }
        }
        Effects effects = parseEffects(c, where, stages, errors);
        if (text == null || next == null) return null;
        return new Choice(
                text,
                next,
                List.copyOf(conditions),
                effects,
                Boolean.TRUE.equals(c.get("default")));
    }

    /** Parses the effects for DialogueGraph. */
    private static Effects parseEffects(
            Map<?, ?> m, String where, Set<String> stages, List<String> errors) {
        List<String> set = flagList(m.get("set"), where, "set", errors);
        List<String> clear = flagList(m.get("clear"), where, "clear", errors);
        String stage = str(m.get("stage"));
        if (stage != null && stages != null && !stages.contains(stage)) {
            errors.add(where + ": Unknown quest stage \"" + stage + "\"");
        }
        Effects effects =
                new Effects(
                        set,
                        clear,
                        stage,
                        amount(m.get("coins"), where, "coins", errors),
                        amount(m.get("exp"), where, "exp", errors),
                        str(m.get("item")));
        return effects.isEmpty() ? Effects.NONE : effects;
    }

    /** Resolves the flag list result used by DialogueGraph. */
    private static List<String> flagList(
            Object value, String where, String key, List<String> errors) {
        List<String> flags = stringList(value, where, key, errors);
        for (String flag : flags) {
            if (!Condition.validName(flag))
                errors.add(where + ": Invalid flag name \"" + flag + "\"");
        }
        return flags;
    }

    /** Resolves the string list result used by DialogueGraph. */
    private static List<String> stringList(
            Object value, String where, String key, List<String> errors) {
        if (value == null) return List.of();
        if (value instanceof String s) return str(s) == null ? List.of() : List.of(s.trim());
        if (!(value instanceof List<?> list)) {
            errors.add(where + ": \"" + key + "\" must be a list of text");
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            String s = item == null ? null : str(item);
            if (s == null) errors.add(where + ": \"" + key + "\" contains an empty entry");
            else out.add(s);
        }
        return List.copyOf(out);
    }

    /** Resolves the amount result used by DialogueGraph. */
    private static int amount(Object value, String where, String key, List<String> errors) {
        if (value == null) return 0;
        if (!(value instanceof Number n)) {
            errors.add(where + ": \"" + key + "\" must be a number");
            return 0;
        }
        if (n.intValue() < 0) errors.add(where + ": \"" + key + "\" cannot be negative");
        return Math.max(0, n.intValue());
    }

    /** Finds the warnings for DialogueGraph. */
    private static List<String> findWarnings(String start, Map<String, Node> nodes) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            String id = queue.poll();
            if (!seen.add(id)) continue;
            Node n = nodes.get(id);
            if (n.next() != null) queue.add(n.next());
            for (Choice c : n.choices()) queue.add(c.next());
        }
        for (String id : nodes.keySet()) {
            if (!seen.contains(id)) out.add("Node \"" + id + "\" can never be reached");
        }
        for (Node n : nodes.values()) {
            if (!n.choices().isEmpty()
                    && n.choices().stream().allMatch(c -> !c.conditions().isEmpty())) {
                out.add(
                        "Node \""
                                + n.id()
                                + "\": every choice has conditions, so a player could have none to"
                                + " pick");
            }
        }
        return List.copyOf(out);
    }

    /** Resolves the str result used by DialogueGraph. */
    private static String str(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    /** Resolves the text result used by DialogueGraph. */
    private static String text(Object o) {
        if (o == null || o.toString().isBlank()) return null;
        return o.toString();
    }
}
