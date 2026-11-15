package dialogue;

import java.util.List;

/** Changes applied when a node is entered or a choice is picked. Applying them comes in step 3. */
public record Effects(List<String> set, List<String> clear, String stage, int coins, int exp, String item) {
    public static final Effects NONE = new Effects(List.of(), List.of(), null, 0, 0, null);

    public boolean isEmpty() {
        return set.isEmpty() && clear.isEmpty() && stage == null && coins == 0 && exp == 0 && item == null;
    }
}