package dialogue;

/** What conditions are checked against. The game implements this over QuestManager. */
public interface DialogueContext {
    boolean flag(String name);
    String stage();
}