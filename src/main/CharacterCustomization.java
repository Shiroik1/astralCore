package main;

import art.*;
import entity.CharacterClass;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

/** Mouse-first appearance step; all hairstyles are usable with every class/preset. */
public final class CharacterCustomization {
    private final Gamepanel g;
    public int style,color=1;
    private int page,view;
    private boolean initialized;
    private final Map<String,BufferedImage> images=new LinkedHashMap<>(64,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,BufferedImage> e){return size()>256;}
    };
    public CharacterCustomization(Gamepanel g){this.g=g;}
    public void reset(){initialized=false;view=0;images.clear();}
    public void open(){
        if(!initialized){
            style=g.keyH.selectedGenderIndex==1?20:0;
            color=switch(CharacterClass.at(g.keyH.selectedClassIndex)){case MAGE->11;case WARRIOR,BERSERKER->5;default->1;};
            initialized=true;
        }
        page=style/20;g.keyH.characterCreationStep=2;g.keyH.enteringCharacterName=false;
    }
    public Rectangle tile(int i){return new Rectangle(380+(i%5)*166,132+(i/5)*108,154,100);}
    public Rectangle swatch(int i){return new Rectangle(380+i*40,590,32,32);}
    public Rectangle tab(int i){return new Rectangle(380+i*224,83,214,36);}
    public Rectangle rotate(int i){return new Rectangle(39+i*105,455,96,34);}
    public Rectangle create(){return new Rectangle(g.screenWidth-260,650,220,44);}
    public Rectangle back(){return new Rectangle(36,650,160,44);}
    public void update(int x,int y,boolean clicked){
        if(!clicked)return;
        for(int i=0;i<2;i++)if(tab(i).contains(x,y)){page=i;return;}
        for(int i=0;i<20;i++)if(tile(i).contains(x,y)){style=page*20+i;return;}
        for(int i=0;i<Hairstyles.COLORS.length;i++)if(swatch(i).contains(x,y)){color=i;return;}
        for(int i=0;i<3;i++)if(rotate(i).contains(x,y)){view=i;return;}
        if(back().contains(x,y)){g.keyH.characterCreationStep=1;return;}
        if(create().contains(x,y))g.ui.confirmCharacterCreation(g.keyH.selectedClassIndex,g.keyH.selectedGenderIndex);
    }
    public void key(int key){
        if(key==KeyEvent.VK_A || key==KeyEvent.VK_LEFT){style=Math.floorMod(style-1,40);page=style/20;}
        if(key==KeyEvent.VK_D || key==KeyEvent.VK_RIGHT){style=(style+1)%40;page=style/20;}
        if(key==KeyEvent.VK_W || key==KeyEvent.VK_UP)color=Math.floorMod(color-1,Hairstyles.COLORS.length);
        if(key==KeyEvent.VK_S || key==KeyEvent.VK_DOWN)color=(color+1)%Hairstyles.COLORS.length;
        if(key==KeyEvent.VK_Q)view=Math.floorMod(view-1,3);
        if(key==KeyEvent.VK_E)view=(view+1)%3;
        if(key==KeyEvent.VK_TAB)page=1-page;
        if(key==KeyEvent.VK_ESCAPE)g.keyH.characterCreationStep=1;
        if(key==KeyEvent.VK_ENTER)g.ui.confirmCharacterCreation(g.keyH.selectedClassIndex,g.keyH.selectedGenderIndex);
    }
    private BufferedImage portrait(int id,int direction,int size,boolean thumbnail){
        String key=id+"/"+color+"/"+direction+"/"+size+"/"+thumbnail+"/"+g.keyH.selectedClassIndex+"/"+g.keyH.selectedGenderIndex;
        return images.computeIfAbsent(key,k->{
            CharacterArt.Look look=new CharacterArt.Look();look.shadow=false;
            CharacterOutfit outfit=CharacterClass.at(g.keyH.selectedClassIndex).outfit(g.keyH.selectedGenderIndex==1);
            // Let the player inspect hair unobstructed; equippable hats remain separate.
            outfit=new CharacterOutfit(outfit.hair(),outfit.torso(),outfit.pants(),outfit.boots(),outfit.gloves(),outfit.cloak(),outfit.details(),null);
            look.outfit=Hairstyles.outfit(outfit,id,color);
            if(thumbnail)return HairArt.thumbnail(look.outfit);
            if(CharacterClass.at(g.keyH.selectedClassIndex)==CharacterClass.RANGER){
                look.rangerBow=look.rangerOutfit=art.RangerGear.TRAIL;
            }
            if(CharacterClass.at(g.keyH.selectedClassIndex)==CharacterClass.HUNTER){
                look.weapon=art.WeaponArt.Kind.DAGGERS;look.assassinOutfit=art.AssassinGear.ALLEY;
            }
            if(CharacterClass.at(g.keyH.selectedClassIndex)==CharacterClass.MAGE){
                look.weapon=art.WeaponArt.Kind.STAFF;look.mageOutfit=art.MageGear.HEARTH;
            }
            if(CharacterClass.at(g.keyH.selectedClassIndex)==CharacterClass.PRIEST){
                look.weapon=art.WeaponArt.Kind.WAYLIGHT_STAFF;look.priestOutfit=art.PriestGear.WAY;
            }
            if(CharacterClass.at(g.keyH.selectedClassIndex)==CharacterClass.BERSERKER){
                look.weapon=art.WeaponArt.Kind.FIELDBREAKER;look.berserkerOutfit=art.BerserkerGear.ROAD;
            }
            if(CharacterClass.at(g.keyH.selectedClassIndex)==CharacterClass.PALADIN){
                look.weapon=WeaponArt.Kind.GATEKEEPER;look.paladinOutfit=look.paladinShield=PaladinGear.GATE;
            }
            return CharacterArt.render(direction==0?CharacterArt.Dir.DOWN:direction==1?CharacterArt.Dir.RIGHT:CharacterArt.Dir.UP,new CharacterArt.Pose(),look,size);
        });
    }
    private void button(Graphics2D d,Font font,Rectangle r,String label,boolean selected){
        WindowStyle.draw(d,r.x,r.y,r.width,r.height);
        d.setColor(selected?new Color(0xD9B77D):Color.WHITE);
        if(selected)d.drawRoundRect(r.x+3,r.y+3,r.width-6,r.height-6,8,8);
        d.setFont(font.deriveFont(13f));d.drawString(label,r.x+(r.width-d.getFontMetrics().stringWidth(label))/2,r.y+23);
    }
    public void draw(Graphics2D d,Font font){
        d.setColor(new Color(0x151A24));d.fillRect(0,0,g.screenWidth,g.screenHeight);
        d.setFont(font.deriveFont(Font.BOLD,27));d.setColor(Color.WHITE);d.drawString("Customize "+g.ui.pendingCharacterName,36,43);
        d.setFont(font.deriveFont(12f));d.setColor(new Color(0xB4BDCA));
        d.drawString("40 reference hairstyles · 20 muted colors · Click a style and color to preview",36,69);
        WindowStyle.draw(d,28,99,326,525);
        d.setColor(new Color(0xE4DECF));d.fillRoundRect(47,133,288,300,16,16);
        d.drawImage(portrait(style,view,280,false),51,140,null);
        String[] views={"Front","Side","Back"};for(int i=0;i<3;i++)button(d,font,rotate(i),views[i],view==i);
        d.setColor(Color.WHITE);d.setFont(font.deriveFont(Font.BOLD,16));
        String name=(style<20?"M":"F")+String.format("%02d",style%20+1)+" · "+Hairstyles.NAMES[style];
        d.drawString(name,46,523);d.setFont(font.deriveFont(13f));d.setColor(Hairstyles.COLORS[color]);
        d.fillOval(47,542,15,15);d.setColor(Color.WHITE);d.drawString(Hairstyles.COLOR_NAMES[color],71,555);
        d.setColor(new Color(0xB4BDCA));d.setFont(font.deriveFont(10f));
        d.drawString("Hair preview shown without headwear.",46,590);
        for(int i=0;i<2;i++)button(d,font,tab(i),i==0?"Male reference 01–20":"Female reference 01–20",page==i);
        d.setFont(font.deriveFont(10f));d.setColor(new Color(0xB4BDCA));d.drawString("Both collections work with every preset.",846,107);
        for(int i=0;i<20;i++){
            int id=page*20+i;Rectangle r=tile(i);
            d.setColor(id==style?new Color(0xCFC4B0):new Color(0xE7E0D3));d.fillRoundRect(r.x,r.y,r.width,r.height,9,9);
            if(id==style){d.setColor(new Color(0xD9B77D));d.setStroke(new BasicStroke(2));d.drawRoundRect(r.x-2,r.y-2,r.width+4,r.height+4,9,9);}
            d.drawImage(portrait(id,0,105,true),r.x+24,r.y-1,null);
            d.setFont(font.deriveFont(10f));d.setColor(new Color(0x51474B));
            String label=String.format("%02d. ",i+1)+Hairstyles.NAMES[id];
            d.drawString(label,r.x+(r.width-d.getFontMetrics().stringWidth(label))/2,r.y+91);
        }
        d.setFont(font.deriveFont(12f));d.setColor(Color.WHITE);d.drawString("HAIR COLOR",380,579);
        for(int i=0;i<Hairstyles.COLORS.length;i++){
            Rectangle r=swatch(i);d.setColor(Hairstyles.COLORS[i]);d.fillRoundRect(r.x,r.y,r.width,r.height,7,7);
            if(i==color){d.setColor(Color.WHITE);d.setStroke(new BasicStroke(2));d.drawRoundRect(r.x-3,r.y-3,r.width+6,r.height+6,9,9);}
        }
        button(d,font,back(),"Back to class",false);button(d,font,create(),"Create character",false);
        d.setFont(font.deriveFont(10f));d.setColor(new Color(0xB4BDCA));d.drawString("A/D: hair   W/S: color   Q/E: rotate   Enter: create",230,677);
    }
}
