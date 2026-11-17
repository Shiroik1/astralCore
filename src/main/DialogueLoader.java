package main;

import dialogue.DialogueGraph;

import java.util.ArrayList;
import java.util.List;

public class DialogueLoader {
    /** Linear text of a dialogue file in either format. Choices are not offered until step 3. */
    public static List<String> loadLines(String resourcePath) {
        return DialogueGraph.load(resourcePath)
                .map(DialogueGraph::linearText)
                .orElseGet(ArrayList::new);
    }
}
