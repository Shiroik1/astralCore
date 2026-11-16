package object;

import crafting.*;
import entity.Entity;
import main.Gamepanel;

/** Uses the existing equipment, bag, tooltip, stack and save representations. */
public final class OBJ_crafted extends Entity {
    public final ItemType itemType;
    public final RecipeRegistry.Item definition;
    public OBJ_crafted(Gamepanel gp,ItemType type,String id){
        super(gp);itemType=type;definition=RecipeRegistry.item(id);name=definition.name();
        this.type=switch(definition.kind()){case "axe"->type_axe;case "sword"->type_sword;case "staff"->type_staff;case "bow"->type_bow;default->type_none;};
        attackValue=definition.power();attackArea.setBounds(0,0,36,40);
        stackable=!definition.equipment();maxStackSize=stackable?99:1;
        down1=CraftingArt.icon(definition);description="("+name+")\n"+(definition.equipment()?"Equip in weapon slot.":definition.place().isEmpty()?"Crafting material.":"Place via crafting menu.");
    }
    @Override public boolean canStackWith(Entity other){return other instanceof OBJ_crafted c && c.itemType==itemType && stackable;}
}
