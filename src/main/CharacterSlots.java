package main;

import save.SaveStore;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Five-slot picker shared by solo, host and join flows. */
public final class CharacterSlots {
    private final Gamepanel g;
    private List<SaveStore.Slot> slots=List.of();
    private int selected;
    public int creationSlot=-1;
    private boolean confirmingDelete;
    private String message;
    public CharacterSlots(Gamepanel g){this.g=g;}
    public void showMessage(String text){message=text;}
    public void open(){
        slots=g.saves.slots();selected=Math.max(0,Math.min(selected,4));
        creationSlot=-1;confirmingDelete=false;message=null;g.gameState=g.characterSelectState;
    }
    private Rectangle row(int i){return new Rectangle(g.screenWidth/2-372,135+i*76,744,66);}
    private Rectangle action(){return new Rectangle(g.screenWidth/2-372,568,230,44);}
    private Rectangle delete(){return new Rectangle(g.screenWidth/2-110,568,230,44);}
    private Rectangle back(){return new Rectangle(g.screenWidth/2+152,568,220,44);}
    public void update(int x,int y,boolean clicked){
        if(!clicked)return;
        for(int i=0;i<5;i++)if(row(i).contains(x,y)){selected=i;confirmingDelete=false;message=null;return;}
        if(action().contains(x,y))activate();
        else if(delete().contains(x,y))deleteSelected();
        else if(back().contains(x,y))backToTitle();
    }
    public void key(int key){
        if(key==KeyEvent.VK_W || key==KeyEvent.VK_UP){selected=Math.floorMod(selected-1,5);confirmingDelete=false;message=null;}
        if(key==KeyEvent.VK_S || key==KeyEvent.VK_DOWN){selected=(selected+1)%5;confirmingDelete=false;message=null;}
        if(key==KeyEvent.VK_ENTER){if(confirmingDelete)deleteSelected();else activate();}
        if(key==KeyEvent.VK_DELETE || key==KeyEvent.VK_BACK_SPACE)deleteSelected();
        if(key==KeyEvent.VK_ESCAPE){if(confirmingDelete){confirmingDelete=false;message=null;}else backToTitle();}
    }
    public void activate(){
        SaveStore.Slot slot=slots.get(selected);
        if(slot.error()!=null){message=slot.error();return;}
        if(g.saves.activeSlot()>=0){
            if(!g.saves.saveActive()){message=g.saves.error();return;}
            g.saves.deactivate();
        }
        if(slot.empty()){
            g.ui.clearCreationError();
            g.ui.customization.reset();
            creationSlot=selected;g.gameState=g.characterState;g.keyH.characterCreationStep=0;
            g.keyH.beginEnteringCharacterName();
        }else{
            try{g.saves.load(selected);g.ui.startSelectedCharacter();}
            catch(Exception e){message="Could not load: "+e.getMessage();}
        }
    }
    private void deleteSelected(){
        SaveStore.Slot slot=slots.get(selected);if(slot.empty())return;
        if(!confirmingDelete){confirmingDelete=true;message="Delete "+slot.name()+" permanently? Enter/Delete confirms. Esc cancels.";return;}
        try{g.saves.delete(selected);slots=g.saves.slots();confirmingDelete=false;message="Character deleted.";}
        catch(Exception e){message=e.getMessage();}
    }
    private void backToTitle(){g.gameState=g.titleState;g.ui.commandNum=0;}
    public void draw(Graphics2D g2,Font font){
        g2.setColor(new Color(0x151A24));g2.fillRect(0,0,g.screenWidth,g.screenHeight);
        WindowStyle.draw(g2,g.screenWidth/2-400,34,800,640);
        g2.setFont(font.deriveFont(Font.BOLD,28));g2.setColor(Color.WHITE);
        g2.drawString("YOUR CHARACTERS",g.screenWidth/2-372,85);
        g2.setFont(font.deriveFont(14f));g2.setColor(new Color(174,187,205));
        g2.drawString("Five slots · Each character has its own adventure",g.screenWidth/2-372,113);
        for(int i=0;i<slots.size();i++){
            SaveStore.Slot slot=slots.get(i);Rectangle r=row(i);
            g2.setColor(i==selected?new Color(0x384654):new Color(0x242C38));g2.fillRoundRect(r.x,r.y,r.width,r.height,10,10);
            if(i==selected){g2.setColor(new Color(0xD9B77D));g2.drawRoundRect(r.x,r.y,r.width,r.height,10,10);}
            g2.setColor(new Color(0xD9B77D));g2.setFont(font.deriveFont(Font.BOLD,21));g2.drawString(Integer.toString(i+1),r.x+17,r.y+40);
            g2.setColor(slot.error()!=null?new Color(245,161,151):Color.WHITE);g2.setFont(font.deriveFont(Font.BOLD,17));
            g2.drawString(slot.name(),r.x+53,r.y+26);
            g2.setFont(font.deriveFont(12f));g2.setColor(new Color(174,187,205));
            String detail=slot.empty()?"Create a new adventurer":slot.error()!=null?"Save unavailable; original files are preserved":
                    "Level "+slot.level()+" · "+(slot.dungeon()?"Well Depths":"Astravale");
            g2.drawString(detail,r.x+53,r.y+48);
            if(!slot.empty() && slot.error()==null){
                String date=DateTimeFormatter.ofPattern("MMM d, HH:mm").format(Instant.ofEpochMilli(slot.savedAt()).atZone(ZoneId.systemDefault()));
                g2.drawString(date,r.x+r.width-142,r.y+29);
            }
        }
        g2.setFont(font.deriveFont(12f));g2.setColor(message==null?new Color(174,187,205):new Color(240,198,145));
        String hint=message==null?"W/S or arrows: select   Enter: continue/create   Esc: back":message;
        // Keep errors within the panel even when a file-system message is lengthy.
        while(g2.getFontMetrics().stringWidth(hint)>744 && hint.length()>4)hint=hint.substring(0,hint.length()-4)+"...";
        g2.drawString(hint,g.screenWidth/2-372,542);
        drawButton(g2,font,action(),slots.isEmpty()||slots.get(selected).empty()?"Create character":"Continue");
        drawButton(g2,font,delete(),confirmingDelete?"Confirm delete":"Delete");
        drawButton(g2,font,back(),"Back");
        g2.setColor(new Color(174,187,205));g2.setFont(font.deriveFont(12f));
        g2.drawString("Autosaves every 30 seconds · F5 to save during play",g.screenWidth/2-372,647);
    }
    private void drawButton(Graphics2D g2,Font font,Rectangle r,String label){
        WindowStyle.draw(g2,r.x,r.y,r.width,r.height);g2.setColor(Color.WHITE);g2.setFont(font.deriveFont(Font.BOLD,14));
        g2.drawString(label,r.x+(r.width-g2.getFontMetrics().stringWidth(label))/2,r.y+28);
    }
}
