package building;

import static building.BuildingLayout.*;

import java.util.*;

/** Settlement planners supply an authored irregular boundary and road-aligned entrances. */
public final class PalisadePerimeter {
    private PalisadePerimeter() {}

    public record Entrance(Cell cell, boolean vertical, boolean open) {}

    /**
     * Builds a reusable blueprint layout; the game's normal placement validates terrain and actors.
     */
    public static BuildingLayout plan(Collection<Cell> boundary, List<Entrance> entrances) {
        if (entrances.isEmpty())
            throw new IllegalArgumentException("A perimeter requires an entrance");
        BuildingLayout layout = new BuildingLayout();
        for (Cell c : boundary) layout.put(c, new Tile("palisade", false, false));
        Set<Cell> doors = new HashSet<>();
        for (Entrance gate : entrances) {
            Cell end = gate.cell.offset(gate.vertical ? 0 : 1, gate.vertical ? 1 : 0);
            if (!doors.add(gate.cell) || !doors.add(end))
                throw new IllegalArgumentException("Overlapping entrances");
            layout.put(gate.cell, new Tile("palisade_gate", gate.vertical, gate.open));
            layout.put(end, new Tile("palisade_gate_tail", gate.vertical, gate.open));
        }
        return layout;
    }
}
