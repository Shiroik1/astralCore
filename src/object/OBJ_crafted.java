package object;

import crafting.*;

import entity.Entity;

import main.Gamepanel;

/** Uses the existing equipment, bag, tooltip, stack and save representations. */
public final class OBJ_crafted extends Entity {
    public final ItemType itemType;
    public final RecipeRegistry.Item definition;

    /** Creates a OBJ_crafted instance and initializes the state required by its collaborators. */
    public OBJ_crafted(Gamepanel gp, ItemType type, String id) {
        super(gp);
        itemType = type;
        definition = RecipeRegistry.item(id);
        name = definition.name();
        this.type =
                switch (definition.kind()) {
                    case "axe" -> type_axe;
                    case "sword" -> type_sword;
                    case "staff" -> type_staff;
                    case "bow" -> type_bow;
                    case "food" -> type_consumable;
                    default -> type_none;
                };
        attackValue = definition.power();
        attackArea.setBounds(0, 0, 36, 40);
        stackable = !definition.equipment();
        maxStackSize = stackable ? 99 : 1;
        down1 = CraftingArt.inventoryIcon(definition, gp.tileSize);
        description =
                "("
                        + name
                        + ")\n"
                        + (definition.equipment()
                                ? "Equip in weapon slot."
                                : definition.place().isEmpty()
                                        ? "Crafting material."
                                        : "Place via crafting menu.");
        if (definition.kind().equals("food"))
            description =
                    "("
                            + name
                            + ")\n"
                            + "Use from bag or hotbar.\n"
                            + "One active meal and one drink.\n"
                            + "Meals: recovery +20 energy capacity.\n"
                            + "Drink: MP recovery over time.";
        if (itemType == ItemType.FISHING_ROD)
            description =
                    "(Fishing Rod)\n"
                            + "Keep in bag. Face adjacent pond water.\n"
                            + "Press V to cast; V again on Bite.";
    }

    /** Performs the use operation for OBJ_crafted. */
    @Override
    public void use(Entity user) {
        if (user instanceof entity.Player player && progression.LifeSkills.food(itemType))
            player.life.eat(itemType);
    }

    /** Reports whether this component can stack with for OBJ_crafted. */
    @Override
    public boolean canStackWith(Entity other) {
        return other instanceof OBJ_crafted c && c.itemType == itemType && stackable;
    }
}
