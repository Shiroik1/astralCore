package dialogue;

import java.util.regex.Pattern;

/** One requirement: "flag", "!flag", "stage:SLAY" or "!stage:SLAY". */
public record Condition(boolean negated, boolean stage, String value) {
    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    public static boolean validName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    public static Condition parse(String text) {
        String t = text == null ? "" : text.trim();
        boolean negated = t.startsWith("!");
        if (negated) t = t.substring(1).trim();
        boolean stage = t.startsWith("stage:");
        if (stage) t = t.substring("stage:".length()).trim();
        if (!validName(t)) {
            throw new IllegalArgumentException("Invalid " + (stage ? "stage" : "flag") + " name \"" + t + "\"");
        }
        return new Condition(negated, stage, t);
    }

    public boolean test(DialogueContext ctx) {
        boolean holds = stage ? value.equals(ctx.stage()) : ctx.flag(value);
        return holds != negated;
    }

    @Override public String toString() {
        return (negated ? "!" : "") + (stage ? "stage:" : "") + value;
    }
}