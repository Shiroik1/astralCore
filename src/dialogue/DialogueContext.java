package dialogue;

/** What conditions are checked against. The game implements this over QuestManager. */
public interface DialogueContext {
    /** Evaluates the flag condition for DialogueContext. */
    boolean flag(String name);

    /** Resolves the stage result used by DialogueContext. */
    String stage();
}
