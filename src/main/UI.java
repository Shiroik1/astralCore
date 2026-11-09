package main;

import entity.Entity;
import net.GameEventType;
import net.InventoryActionType;
import object.OBJ_crystal;
import object.OBJ_heart;
import entity.Player;
import entity.Monster;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import entity.Skill;

public class UI {
    Gamepanel gp;
    Graphics2D g2;

    //FONTS
    Font solomonKey;
    Font jetbrainsMono;

    public boolean messageOn = false;
    public boolean gameFinished = false;
    public String pendingCharacterName = "";
    public final CharacterSlots characterSlots;
    public final CharacterCustomization customization;
    private String creationError;
    public int commandNum = 0;

    private Rectangle[] inventorySlotBounds = new Rectangle[20];

    private UIButton[] titleButtons = {new UIButton(), new UIButton(), new UIButton(), new UIButton()};
    private UIButton[] optionTopButtons = {new UIButton(), new UIButton(), new UIButton(), new UIButton(), new UIButton(), new UIButton()};
    private UIButton[] displayButtons = {new UIButton(),new UIButton(),new UIButton(),new UIButton()};
    private boolean displayDraftFullscreen;
    private DisplaySettings.Resolution displayDraftResolution;
    private UIButton[] controlButtons = {new UIButton()};
    private UIButton[] endgameConfirmButtons = {new UIButton(), new UIButton()};
    private UIButton lastHoveredButton = null;
    private UIButton respawnButton = new UIButton();
    private int pendingTitleAction = -1;

    public int subState = 0;

    double playTime;
    DecimalFormat decimalFormat = new DecimalFormat("#0.00");

    private static final int SLOT_BAG = 0;

    private boolean draggingItem = false;
    private Entity draggedItem = null;
    private int dragSourceType = SLOT_BAG;
    private int dragSourceIndex = -1;
    private Rectangle inventoryWindowBounds = new Rectangle();
    private Entity hoveredItem = null;
    private final Rectangle[] equipmentBounds = new Rectangle[9];
    private final Rectangle undressBounds = new Rectangle();
    private static final String[] EQUIPMENT_LABELS = {"Weapon", "Shield", "Hair", "Tunic", "Pants", "Boots", "Gloves", "Cloak", "Head Piece"};

    private final int slotIconSize = 48;      // visual size of one slot — currently matches gp.tileSize
    private final int slotSpacing = 3;        // gap between adjacent slot cells
    private final int slotStride = slotIconSize + slotSpacing;
    private final int bagColumns = 5;
    private final int equipSlotGap = 10;       // vertical gap between weapon/shield slots
    private final int equipBagGap = 30;        // horizontal gap between paperdoll column and bag grid
    private final int windowPadding = 20;      // inner margin on all sides of the window
    private final int screenRightMargin = 48;  // distance kept from the right edge of the screen

    //CHAT MESSAGE
    private static class ChatMessage {
        String text;
        Color color;
        int ageTicks = 0;
        ChatMessage(String text, Color color){ this.text = text; this.color = color; }
    }

    private ArrayList<ChatMessage> chatHistory = new ArrayList<>();
    private int chatScrollOffset = 0;       // 0 = live view; >0 = scrolled back into history
    private Rectangle combatLogBounds = new Rectangle();
    private boolean combatLogHovered = false;

    private final int chatLogWidth = 420;
    private final int chatLogHeight = 160;
    private final int chatLogMargin = 16;
    private final int chatVisibleLines = 7;
    private final int chatFadeStartTicks = 300;   // ~5s before an unread message starts fading
    private final int chatFadeDurationTicks = 60; // ~1s fade-out
    private final int chatMaxHistory = 200;       // scrollback cap
    private String joinStatusMessage = null;

    public UI(Gamepanel gp){
        this.gp = gp;
        characterSlots=new CharacterSlots(gp);
        customization=new CharacterCustomization(gp);

        try{
            InputStream is = getClass().getResourceAsStream("/font/SolomonsKey.ttf");
            solomonKey = Font.createFont(Font.TRUETYPE_FONT, is);
            InputStream is2 = getClass().getResourceAsStream("/font/JetBrainsMono-Regular.ttf");
            jetbrainsMono = Font.createFont(Font.TRUETYPE_FONT, is2);
        }
        catch (FontFormatException e){
            e.printStackTrace();
        }
        catch (Exception e){
            e.printStackTrace();
        }

        //CREATE HUD OBJECTS
        Entity heart = new OBJ_heart(gp);
        Entity crystal = new OBJ_crystal(gp);
    }

    public void addMessage(String text){
        addMessage(text, Color.white);
    }

    public void addMessage(String text, Color color){
        chatHistory.add(new ChatMessage(text, color));
        if(chatHistory.size() > chatMaxHistory){
            chatHistory.remove(0);
        }
        chatScrollOffset = 0; // new activity snaps the view back to live, like WoW
    }

    public void update(int mouseX, int mouseY, boolean clicked){
        if(gp.gameState==gp.characterState){
            int step=gp.keyH.characterCreationStep;
            if(gp.keyH.characterCreationStep==2)customization.update(mouseX,mouseY,clicked);
            else if(gp.keyH.characterCreationStep==0 && clicked){
                if(nameNext().contains(mouseX,mouseY))confirmCharacterName();
                else if(nameBack().contains(mouseX,mouseY)){gp.keyH.enteringCharacterName=false;characterSlots.open();}
            }
            if(step!=1)gp.mouseH.leftClicked=false;
            return;
        }
        if(gp.gameState==gp.characterSelectState){
            characterSlots.update(mouseX,mouseY,clicked);gp.mouseH.leftClicked=false;return;
        }
        if(gp.gameState == gp.playState){
            playTime += 1.0 / 60;
            updateCombatLog(mouseX, mouseY);
        }

        Player local = gp.localPlayer();
        if(local != null && local.isDead && gp.gameState == gp.playState && !gp.optionsOpen){
            int hovered = processButtonHover(new UIButton[]{respawnButton}, mouseX, mouseY);
            if(hovered != -1 && clicked){
                gp.broadcastPlayerEvent(local.playerId, GameEventType.RESPAWN_REQUEST,
                        "Player " + (local.playerId + 1) + " has respawned.", null);
            }
            gp.mouseH.leftClicked = false;
            return;
        }

        if(gp.gameState == gp.titleState){
            if(gp.keyH.enteringNetworkAddress){
                gp.mouseH.leftClicked = false;
                return;
            }
            int hovered = processButtonHover(titleButtons, mouseX, mouseY);
            if(hovered != -1){
                commandNum = hovered;
                if(clicked){
                    confirmTitleSelection(hovered);
                }
            }
            gp.mouseH.leftClicked = false;
        }
        else if(gp.gameState == gp.playState && gp.optionsOpen){
            int hovered = processButtonHover(currentOptionButtons(), mouseX, mouseY);
            if(hovered != -1){
                commandNum = hovered;
                if(clicked){
                    activateOptionItem(hovered);
                }
            }
            gp.mouseH.leftClicked = false; // the options overlay swallows every click
        }
        else if(local!=null && local.merchantOpen){
            gp.merchant.update(mouseX,mouseY,clicked);gp.mouseH.leftClicked=false;
        }
        else if(gp.inventoryOpen){
            updateInventoryDrag(mouseX, mouseY);
        }
    }

    private UIButton[] currentOptionButtons(){
        return switch (subState){
            case 0 -> optionTopButtons;
            case 1 -> displayButtons;
            case 2 -> controlButtons;
            case 3 -> endgameConfirmButtons;
            default -> new UIButton[0];
        };
    }

    void confirmTitleSelection(int index){
        joinStatusMessage = null;
        if(index == 0 || index == 1 || index == 2){
            pendingTitleAction = index;
            characterSlots.open();
        }
        if(index == 3){
            gp.requestClose();
        }
    }

    public void draw(Graphics2D g2) throws IOException {

        this.g2 = g2;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        g2.setFont(jetbrainsMono);
        g2.setColor(Color.white);

        //TITLE STATE
        if(gp.gameState == gp.titleState){
            drawTitleScreen();
        }

        //CHARACTER StATE
        if(gp.gameState == gp.characterState){
            drawCharacterCreationScreen();
        }
        if(gp.gameState==gp.characterSelectState)characterSlots.draw(g2,jetbrainsMono);

        //PLAY STATE
        if(gp.gameState == gp.playState){

            //Toggle debug menu with [ T ] key
            if(gp.keyH.showDebug){
                drawDebug(g2, playTime);
            }

            drawPlayerHP();
            drawBossHealth();
            drawQuestTracker();
            Player local = gp.localPlayer();
            if(local != null){
                drawStatusEffects(local);
                drawOtherPlayersHUD(local);
            }
            drawCombatLog();
            gp.grassEditor.drawHud(g2, jetbrainsMono);
            drawSkillHotbar();

            if(gp.inventoryOpen){
                drawCharacterScreen();
                drawInventory();
            }

            if(local!=null && local.merchantOpen)gp.merchant.draw(g2);

            if(local != null && local.isDead){
                drawDeathScreen();
            }
            if(local != null && local.inDialogue){
                drawPersonalDialogue(local.getRevealedDialogueText(), local.isDialogueFullyRevealed());
            }
        }

        if(gp.gameState == gp.playState){
            if(gp.paused){
                drawPauseScreen();
            }
            if(gp.optionsOpen){
                drawOptionScreen();
            }
        }

    }

    private void drawInventory() {
        int bagRows = (int) Math.ceil((double) gp.localPlayer().inventorySlots.length / bagColumns);
        int bagGridWidth = bagColumns * slotStride - slotSpacing;
        int bagGridHeight = bagRows * slotStride - slotSpacing;
        int equipmentRows = (equipmentBounds.length+1)/2;
        int equipColumnHeight = equipmentRows * (slotIconSize + 18) + 32;
        int equipmentWidth = 2 * slotIconSize + equipSlotGap;

        int frameWidth = windowPadding + equipmentWidth + equipBagGap + bagGridWidth + windowPadding;
        int frameHeight = windowPadding + Math.max(equipColumnHeight, bagGridHeight) + windowPadding;
        int frameX = gp.screenWidth - frameWidth - screenRightMargin;
        int frameY = gp.tileSize;

        drawSubWindow(frameX, frameY, frameWidth, frameHeight);
        inventoryWindowBounds.setBounds(frameX, frameY, frameWidth, frameHeight);

        //EQUIPMENT PAPERDOLL
        int equipX = frameX + windowPadding;
        Font savedFont=g2.getFont();
        for(int i=0;i<equipmentBounds.length;i++){
            int x=equipX+(i%2)*(slotIconSize+equipSlotGap);
            int y=frameY+windowPadding+(i/2)*(slotIconSize+18)+13;
            if(equipmentBounds[i]==null)equipmentBounds[i]=new Rectangle();
            equipmentBounds[i].setBounds(x,y,slotIconSize,slotIconSize);
            g2.setFont(savedFont.deriveFont(11f));g2.setColor(Color.white);
            var player=gp.localPlayer();
            String label=EQUIPMENT_LABELS[i];
            if(i==0 && player.isRanger())label="Bow";
            if(i==1 && player.isRanger())label="Off hand";
            if(i==1 && player.playerClass.equals("hunter"))label="Paired blades";
            g2.drawString(label,x,y-3);
            drawEquipSlot(equipmentBounds[i],draggingItem && dragSourceType==i+1 ? null : gp.localPlayer().equippedItem(i+1));
        }
        undressBounds.setBounds(equipX,frameY+windowPadding+equipmentRows*(slotIconSize+18)+6,equipmentWidth,24);
        g2.setColor(new Color(100,65,65));g2.fillRoundRect(undressBounds.x,undressBounds.y,undressBounds.width,24,8,8);
        g2.setColor(Color.white);g2.setFont(savedFont.deriveFont(12f));
        g2.drawString("Undress",undressBounds.x+24,undressBounds.y+17);g2.setFont(savedFont);

        //BAG GRID
        final int slotStartX = equipX + equipmentWidth + equipBagGap;
        final int slotStartY = frameY + windowPadding;
        int slotX = slotStartX;
        int slotY = slotStartY;

        for(int i = 0; i < gp.localPlayer().inventorySlots.length; i++){
            inventorySlotBounds[i] = new Rectangle(slotX, slotY, slotIconSize, slotIconSize);

            g2.setColor(new Color(255, 255, 255, 40));
            g2.fillRoundRect(slotX, slotY, slotIconSize, slotIconSize, 8, 8);

            Entity item = gp.localPlayer().inventorySlots[i];
            boolean isBeingDragged = draggingItem && dragSourceType == SLOT_BAG && dragSourceIndex == i;

            if(item != null && !isBeingDragged){
                g2.drawImage(item.down1, slotX, slotY, null);
                if(item.stackable && item.stackCount > 1){
                    drawStackCount(item.stackCount, slotX, slotY);
                }
            }

            slotX += slotStride;
            if((i + 1) % bagColumns == 0){
                slotX = slotStartX;
                slotY += slotStride;
            }
        }

        //FLOATING DRAGGED ITEM
        if(draggingItem && draggedItem != null){
            int mx = gp.mouseH.getScaledX();
            int my = gp.mouseH.getScaledY();
            g2.drawImage(draggedItem.down1, mx - slotIconSize/2, my - slotIconSize/2, null);
        }

        //TOOLTIP
        if(!draggingItem && hoveredItem != null){
            drawTooltip(hoveredItem, gp.mouseH.getScaledX(), gp.mouseH.getScaledY());
        }
    }

    private void drawEquipSlot(Rectangle bounds, Entity equipped){
        g2.setColor(new Color(255,255,255,40));
        g2.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);
        g2.setColor(Color.white);
        g2.setStroke(new BasicStroke(2));
        g2.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);

        if(equipped != null && equipped.down1 != null){
            g2.drawImage(equipped.down1, bounds.x, bounds.y, null);
        }
    }

    private void drawStackCount(int count, int slotX, int slotY){
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 14f));
        String text = String.valueOf(count);
        int textWidth = (int) g2.getFontMetrics().getStringBounds(text, g2).getWidth();
        int textX = slotX + gp.tileSize - textWidth - 4;
        int textY = slotY + gp.tileSize - 4;

        g2.setColor(Color.black);
        g2.drawString(text, textX + 1, textY + 1);
        g2.setColor(Color.white);
        g2.drawString(text, textX, textY);
    }

    private void drawTooltip(Entity item, int mouseX, int mouseY){
        g2.setFont(g2.getFont().deriveFont(15f));
        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add(item.name);
        for(String line : item.description.split("\n")){
            lines.add(line);
        }
        if(item.stackable){
            lines.add("Stack: " + item.stackCount + "/" + item.maxStackSize);
        }

        int padding = 10;
        int lineHeight = 20;
        int tooltipWidth = 0;
        for(String line : lines){
            int w = (int) g2.getFontMetrics().getStringBounds(line, g2).getWidth();
            tooltipWidth = Math.max(tooltipWidth, w);
        }
        tooltipWidth += padding * 2;
        int tooltipHeight = lines.size() * lineHeight + padding;

        drawSubWindow(mouseX + 16, mouseY + 16, tooltipWidth, tooltipHeight);

        int textX = mouseX + 16 + padding;
        int textY = mouseY + 16 + padding + 12;
        for(String line : lines){
            g2.drawString(line, textX, textY);
            textY += lineHeight;
        }
    }

    private void drawCombatLog(){
        int boxX = combatLogBounds.x;
        int boxY = combatLogBounds.y;
        boolean hovering = combatLogHovered;
        boolean chatActive = gp.keyH.enteringChat;

        //Background only shows while actively interacting
        boolean boxVisible = hovering || chatActive || chatScrollOffset > 0;
        if(boxVisible){
            g2.setColor(Color.BLACK);
            g2.fillRoundRect(boxX, boxY, chatLogWidth, chatLogHeight, 12, 12);
        }

        g2.setFont(jetbrainsMono.deriveFont(14f));
        int lineHeight = 20;
        int textX = boxX + 10;
        int textY = boxY + chatLogHeight - 10;

        int endIndex = chatHistory.size() - chatScrollOffset;
        int startIndex = Math.max(0, endIndex - chatVisibleLines);

        for(int i = endIndex - 1; i >= startIndex; i--){
            ChatMessage msg = chatHistory.get(i);   // msg only exists from here, inside the loop

            float alpha = 1f;
            if(chatScrollOffset == 0 && !hovering && !chatActive && msg.ageTicks > chatFadeStartTicks){
                float fadeProgress = (msg.ageTicks - chatFadeStartTicks) / (float) chatFadeDurationTicks;
                alpha = Math.max(0f, 1f - fadeProgress);
            }

            if(alpha > 0f){
                Color c = msg.color;
                g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int)(alpha * 255)));
                g2.drawString(msg.text, textX, textY);
            }

            textY -= lineHeight;
        }
    }

    private void drawCharacterScreen() {
        final int frameX = gp.tileSize * 2;
        final int frameY = gp.tileSize;
        final int frameWidth = gp.tileSize * 7;
        final int frameHeight = gp.tileSize * 11;
        drawSubWindow(frameX, frameY, frameWidth, frameHeight);

        g2.setColor(Color.white);
        g2.setFont(g2.getFont().deriveFont(15f));

        int textX = frameX + 20;
        int textY = frameY + gp.tileSize;
        final int lineHeight = 30;

        //TITLES
        g2.drawString("Class", textX, textY);
        textY += lineHeight;
        g2.drawString("Gender", textX, textY);
        textY += lineHeight;
        g2.drawString("Level", textX, textY);
        textY += lineHeight;
        g2.drawString("HP", textX, textY);
        textY += lineHeight;
        g2.drawString(gp.localPlayer().resourceName(), textX, textY);
        textY += lineHeight;
        g2.drawString("Strength", textX, textY);
        textY += lineHeight;
        g2.drawString("Dexterity", textX, textY);
        textY += lineHeight;
        g2.drawString("Attack", textX, textY);
        textY += lineHeight;
        g2.drawString("Defense", textX, textY);
        textY += lineHeight;
        g2.drawString("EXP", textX, textY);
        textY += lineHeight;
        g2.drawString("Next Level", textX, textY);
        textY += lineHeight;
        g2.drawString("Coin", textX, textY);
        textY += lineHeight + 30;
        g2.drawString("Weapon", textX, textY);
        textY += lineHeight + 20;
        g2.drawString("Shield", textX, textY);
        textY += lineHeight;

        //VALUES
        int tailX = (frameX + frameWidth) - 30;
        textY = frameY + gp.tileSize;
        String value;

        value = capitalize(gp.localPlayer().playerClass);
        textX = getXforRightAlignText(value, tailX);
        g2.drawString(value, textX, textY);
        textY += lineHeight;

        value = capitalize(gp.localPlayer().gender);
        textX = getXforRightAlignText(value, tailX);
        g2.drawString(value, textX, textY);
        textY += lineHeight;

        value = String.valueOf(gp.localPlayer().level);

        textX = getXforRightAlignText(value, tailX);
        g2.drawString(value, textX, textY);
        textY += lineHeight;

        value = String.valueOf(gp.localPlayer().HP + "/" + gp.localPlayer().maxHP);
        textX = getXforRightAlignText(value, tailX);
        g2.drawString(value, textX, textY);
        textY += lineHeight;

        if(gp.localPlayer().usesMana()){
            value = gp.localPlayer().MP + "/" + gp.localPlayer().maxMP;
        }else if(gp.localPlayer().isPaladin()){
            value=gp.localPlayer().resolve+"/"+gp.localPlayer().maxResolve;
        }else{
            value = gp.localPlayer().rage + "/" + gp.localPlayer().maxRage;
        }
        textX = getXforRightAlignText(value, tailX);
        g2.drawString(value, textX, textY);
        textY += lineHeight;

        value = String.valueOf(gp.localPlayer().strength);
        textX = getXforRightAlignText(value, tailX);
        g2.drawString(value, textX, textY);
        textY += lineHeight;

        value = String.valueOf(gp.localPlayer().dexterity);
        textX = getXforRightAlignText(value, tailX);
        g2.drawString(value, textX, textY);
        textY += lineHeight;

        value = String.valueOf(gp.localPlayer().attack);
        textX = getXforRightAlignText(value, tailX);
        g2.drawString(value, textX, textY);
        textY += lineHeight;

        value = String.valueOf(gp.localPlayer().defense);
        textX = getXforRightAlignText(value, tailX);
        g2.drawString(value, textX, textY);
        textY += lineHeight;

        value = String.valueOf(gp.localPlayer().exp);
        textX = getXforRightAlignText(value, tailX);
        g2.drawString(value, textX, textY);
        textY += lineHeight;

        value = String.valueOf(gp.localPlayer().nextLevelExp);
        textX = getXforRightAlignText(value, tailX);
        g2.drawString(value, textX, textY);
        textY += lineHeight;

        value = String.valueOf(gp.localPlayer().coin);
        textX = getXforRightAlignText(value, tailX);
        g2.drawString(value, textX, textY);
        textY += lineHeight;

        g2.drawImage(gp.localPlayer().currentWeapon.down1, tailX - gp.tileSize, textY, null);
        textY += gp.tileSize;

        g2.drawImage(gp.localPlayer().currentShield.down1, tailX - gp.tileSize, textY, null);
    }

    private Monster hudBoss;

    /** An encounter begins when its boss enters view and remains visible until that boss is gone. */
    private Monster findBossForHud(){
        if(hudBoss!=null && hudBoss.alive){
            for(Monster monster:gp.monster)if(monster==hudBoss)return hudBoss;
        }
        hudBoss=null;
        Player camera=gp.localPlayer();if(camera==null)return null;
        double nearest=Double.MAX_VALUE;
        for(Monster monster:gp.monster){
            if(monster==null || !monster.alive || monster.dying || !monster.isBoss())continue;
            int x=monster.worldX-camera.worldX+camera.screenX+monster.solidArea.x+monster.solidArea.width/2;
            int y=monster.worldY-camera.worldY+camera.screenY+monster.solidArea.y+monster.solidArea.height/2;
            if(!gp.quests.inDungeon() && (x<0 || x>gp.screenWidth || y<0 || y>gp.screenHeight))continue;
            double distance=Math.hypot(x-gp.screenWidth/2.0,y-gp.screenHeight/2.0);
            if(distance<nearest){hudBoss=monster;nearest=distance;}
        }
        return hudBoss;
    }

    private void drawQuestTracker(){
        int x=gp.screenWidth-340,y=110,w=316,h=108;
        g2.setColor(Color.BLACK);g2.fillRoundRect(x,y,w,h,14,14);
        g2.setColor(Color.WHITE);g2.setStroke(new BasicStroke(2));g2.drawRoundRect(x,y,w,h,14,14);
        g2.setFont(jetbrainsMono.deriveFont(Font.BOLD,14f));g2.setColor(Color.WHITE);
        g2.drawString(gp.quests.definition().title(),x+14,y+25);
        g2.setFont(jetbrainsMono.deriveFont(Font.PLAIN,12f));g2.setColor(Color.WHITE);
        String objective=gp.quests.objective();
        if(g2.getFontMetrics().stringWidth(objective)>w-28){
            int split=objective.lastIndexOf(' ',objective.length()/2+6);
            g2.drawString(objective.substring(0,split),x+14,y+47);
            g2.drawString(objective.substring(split+1),x+14,y+63);
        }else g2.drawString(objective,x+14,y+51);
        g2.setColor(new Color(169,192,198));
        g2.drawString(gp.quests.inDungeon()?"Well Depths  |  Ladder: return [E]":"Astravale",x+14,y+88);
    }

    private void drawBossHealth(){
        Monster boss=findBossForHud();if(boss==null)return;
        int width=Math.min(620,gp.screenWidth-520),x=(gp.screenWidth-width)/2,y=55;
        Font font=g2.getFont();Stroke stroke=g2.getStroke();Paint paint=g2.getPaint();
        String name=boss.name==null?"Boss":boss.name;
        g2.setFont(jetbrainsMono.deriveFont(Font.BOLD,19f));
        int textX=(gp.screenWidth-g2.getFontMetrics().stringWidth(name))/2;
        g2.setColor(new Color(0,0,0,160));
        g2.fillRoundRect(textX-14,y-37,g2.getFontMetrics().stringWidth(name)+28,31,8,8);
        g2.setColor(Color.black);g2.drawString(name,textX+1,y-9);
        g2.setColor(Color.white);g2.drawString(name,textX,y-10);
        drawStatBar(x,y,width,22,boss.HP,boss.maxHP,new Color(60,20,20),new Color(200,40,40),"HP");
        g2.setFont(font);g2.setStroke(stroke);g2.setPaint(paint);
    }

    private void drawPlayerHP() {
        Player local = gp.localPlayer();
        if(local == null) return;

        int barX = gp.tileSize / 2;
        int barWidth = 200;
        int barHeight = 22;
        int barSpacing = 8;

        int hpY = gp.tileSize / 2;
        drawStatBar(barX, hpY, barWidth, barHeight, local.HP, local.maxHP,
                new Color(60, 20, 20), new Color(200, 40, 40), "HP");

        int secondaryY = hpY + barHeight + barSpacing;
        if(local.usesMana()){
            drawStatBar(barX, secondaryY, barWidth, barHeight, local.MP, local.maxMP,
                    new Color(20, 30, 60), new Color(60, 130, 230), "MP");
            if(local.isMage())drawArcaneCharge(local, barX + barWidth + 16, secondaryY, barHeight);
            else{
                g2.setFont(jetbrainsMono.deriveFont(Font.BOLD,12f));g2.setColor(new Color(0xF7D58C));
                g2.drawString("Beacon · "+local.priest.beaconLabel(),barX+barWidth+14,secondaryY+15);
            }
        }else if(local.isPaladin()){
            drawPaladinResolve(local,barX,secondaryY);
        }else{
            drawStatBar(barX, secondaryY, barWidth, barHeight, local.rage, local.maxRage,
                    local.resourceBarBackground(), local.resourceBarColor(), local.resourceName().toUpperCase(java.util.Locale.ROOT));
        }
        if(local.playerClass.equals("warrior")){
            g2.setFont(jetbrainsMono.deriveFont(11f));g2.setColor(Color.WHITE);g2.drawString("RESOLVE",barX,91);
            if(new Rectangle(barX,78,200,18).contains(gp.mouseH.getScaledX(),gp.mouseH.getScaledY()))gp.merchant.tooltip(g2,java.util.List.of("Resolve","Shield-mitigated hits build Resolve.","At five, your next hit gains twice defense."),barX,105);
            for(int i=0;i<local.maxResolve;i++){g2.setColor(i<local.resolve?Color.WHITE:Color.BLACK);g2.fillRect(barX+66+i*17,80,11,11);g2.setColor(Color.WHITE);g2.drawRect(barX+66+i*17,80,11,11);}
        }
    }

    private void drawPaladinResolve(Player local,int x,int y){
        g2.setFont(jetbrainsMono.deriveFont(Font.BOLD,12f));g2.setColor(Color.WHITE);
        g2.drawString("RESOLVE",x,y+15);
        for(int i=0;i<local.maxResolve;i++){
            int cx=x+77+i*22;
            var pip=new Polygon(new int[]{cx-7,cx,cx+7,cx+5,cx,cx-5},new int[]{y+4,y+2,y+4,y+14,y+18,y+14},6);
            g2.setColor(i<local.resolve?new Color(0xF3C98A):new Color(0x344355));g2.fill(pip);
            g2.setStroke(new BasicStroke(1.5f));g2.setColor(new Color(0xF1DEB9));g2.draw(pip);
        }
        if(local.paladin.guarding()){
            g2.setColor(new Color(0xB4DBE9));g2.drawString("GUARDING",x+205,y+15);
        }
        if(new Rectangle(x,y,184,22).contains(gp.mouseH.getScaledX(),gp.mouseH.getScaledY()))
            gp.merchant.tooltip(g2,java.util.List.of("Resolve","Hold right click to Guard.","Each mitigated frontal hit grants one Resolve."),x,y+30);
    }

    private void drawOtherPlayersHUD(Player local){
        int startX = gp.tileSize / 2;
        int y = gp.tileSize / 2 + 22 + 8 + 22 + 10; // status effects row baseline
        y += 32 + 14; // below the status icon row, plus a gap

        for(Player p : gp.players){
            if(p == null || p == local) continue;

            int barWidth = 140;
            int hpHeight = 14;
            int mpHeight = 12;

            g2.setFont(jetbrainsMono.deriveFont(Font.BOLD, 13f));
            g2.setColor(Color.white);
            String label = "P" + (p.playerId + 1) + (p.isDead ? " (Down)" : "");
            g2.drawString(label, startX, y + hpHeight - 3);

            int barX = startX + 60;
            drawMiniStatBar(barX, y, barWidth, hpHeight, p.HP, p.maxHP, new Color(60, 20, 20), new Color(200, 40, 40));

            int secondY = y + hpHeight + 3;
            if(p.usesMana()){
                drawMiniStatBar(barX, secondY, barWidth, mpHeight, p.MP, p.maxMP, new Color(20, 30, 60), new Color(60, 130, 230));
            }else if(p.isPaladin()){
                drawMiniStatBar(barX,secondY,barWidth,mpHeight,p.resolve,p.maxResolve,new Color(0x344355),new Color(0xF3C98A));
            }else{
                drawMiniStatBar(barX, secondY, barWidth, mpHeight, p.rage, p.maxRage, p.resourceBarBackground(), p.resourceBarColor());
            }

            y += hpHeight + 3 + mpHeight + 14; // advance to the next player's row
        }
    }

    private void drawMiniStatBar(int x, int y, int width, int height, int current, int max, Color bgFill, Color barFill){
        if(max <= 0) max = 1;

        g2.setColor(new Color(0, 0, 0, 140));
        g2.fillRoundRect(x - 1, y - 1, width + 2, height + 2, 5, 5);

        g2.setColor(bgFill);
        g2.fillRoundRect(x, y, width, height, 4, 4);

        int filledWidth = (int)((double) width * Math.max(0, current) / max);
        g2.setColor(barFill);
        g2.fillRoundRect(x, y, filledWidth, height, 4, 4);

        g2.setColor(Color.white);
        g2.setStroke(new BasicStroke(1));
        g2.drawRoundRect(x, y, width, height, 4, 4);
    }

    private void drawStatBar(int x, int y, int width, int height, int current, int max, Color bgFill, Color barFill, String label){
        if(max <= 0) max = 1; // guard against a stray divide-by-zero if maxHP/maxMP are ever misconfigured
        current=Math.max(0,Math.min(max,current));

        g2.setColor(new Color(0, 0, 0, 160));
        g2.fillRoundRect(x - 2, y - 2, width + 4, height + 4, 8, 8);

        g2.setColor(bgFill);
        g2.fillRoundRect(x, y, width, height, 6, 6);

        int filledWidth = (int)((double) width * Math.max(0, current) / max);
        g2.setColor(barFill);
        g2.fillRoundRect(x, y, filledWidth, height, 6, 6);

        g2.setColor(Color.white);
        g2.setStroke(new BasicStroke(2));
        g2.drawRoundRect(x, y, width, height, 6, 6);

        g2.setFont(jetbrainsMono.deriveFont(Font.BOLD, 14f));
        String text = label + "  " + current + " / " + max;
        int textWidth = (int) g2.getFontMetrics().getStringBounds(text, g2).getWidth();
        int textX = x + (width - textWidth) / 2;
        int textY = y + height - 6;

        g2.setColor(Color.black);
        g2.drawString(text, textX + 1, textY + 1);
        g2.setColor(Color.white);
        g2.drawString(text, textX, textY);
    }

    private void drawTitleScreen() {
        g2.setFont(solomonKey.deriveFont(Font.BOLD, 90F));
        String text = "AstralCore";
        int x = getXforCenteredText(text);
        int y = gp.tileSize * 5;

        g2.setColor(Color.gray);
        g2.drawString(text, x+10, y+10);
        g2.setColor(Color.white);
        g2.drawString(text, x, y);

        x = gp.screenWidth/2 - (gp.tileSize*2)/2;
        y += gp.tileSize;
        g2.drawImage(gp.localPlayer().down1, x, y, gp.tileSize * 2, gp.tileSize * 2, null);

        g2.setFont(jetbrainsMono.deriveFont(Font.PLAIN, 30F));

        text = "PLAY";
        x = getXforCenteredText(text);
        y += gp.tileSize * 5;
        drawMenuButton(titleButtons[0], text, x, y, commandNum == 0);

        text = "HOST GAME";
        x = getXforCenteredText(text);
        y += gp.tileSize;
        drawMenuButton(titleButtons[1], text, x, y, commandNum == 1);

        text = "JOIN GAME";
        x = getXforCenteredText(text);
        y += gp.tileSize;
        drawMenuButton(titleButtons[2], text, x, y, commandNum == 2);

        text = "QUIT";
        x = getXforCenteredText(text);
        y += gp.tileSize;
        drawMenuButton(titleButtons[3], text, x, y, commandNum == 3);

        //NETWORK STATUS / INPUT — anchored to the bottom of the screen, independent of menu length
        int networkUIY = gp.screenHeight - 100;

        if(joinStatusMessage != null){
            g2.setFont(jetbrainsMono.deriveFont(16f));
            g2.setColor(Color.red);
            int mx = getXforCenteredText(joinStatusMessage);
            g2.drawString(joinStatusMessage, mx, networkUIY);
        }

        if(gp.keyH.enteringNetworkAddress){
            drawJoinAddressInput(networkUIY + 20);
        }
    }

    private void drawJoinAddressInput(int y){
        int boxWidth = 340;
        int boxHeight = 36;
        int boxX = gp.screenWidth/2 - boxWidth/2;

        drawSubWindow(boxX, y, boxWidth, boxHeight);

        g2.setFont(jetbrainsMono.deriveFont(16f));
        g2.setColor(Color.white);
        String label = "Host IP  (Enter to join, Esc to cancel)";
        g2.drawString(label, getXforCenteredText(label), y - 10);

        String display = gp.keyH.networkAddressInput.toString();
        if((System.currentTimeMillis() / 400) % 2 == 0){
            display += "_";
        }
        g2.drawString(display, boxX + 12, y + 24);
    }

    private void drawPersonalDialogue(String text, boolean showArrow){
        int x = gp.tileSize * 2;
        int y = gp.tileSize / 2;
        int width = gp.screenWidth - (gp.tileSize * 4);
        int height = gp.tileSize * 5;

        drawSubWindow(x, y, width, height);

        g2.setFont(jetbrainsMono.deriveFont(Font.PLAIN, 20F));
        int textX = x + gp.tileSize;
        int textY = y + gp.tileSize;

        for(String line : text.split("\n")){
            g2.drawString(line, textX, textY);
            textY += 40;
        }

        if(showArrow){
            drawContinueArrow(x + width - 50, y + height - 40);
        }
    }

    private void drawContinueArrow(int baseX, int baseY){
        double bounce = Math.abs(Math.sin(System.currentTimeMillis() / 200.0)) * 6;
        int ax = baseX;
        int ay = (int)(baseY + bounce);

        int[] xs = { ax, ax + 14, ax + 7 };
        int[] ys = { ay, ay, ay + 10 };

        g2.setColor(Color.white);
        g2.fillPolygon(xs, ys, 3);
    }

    public void drawSubWindow(int x, int y, int width, int height){
        Color color = Color.BLACK;
        g2.setColor(color);
        g2.fillRoundRect(x, y, width, height, 35, 35);

        color = new Color(255, 255, 255);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(5));
        g2.drawRoundRect(x+5, y+5, width-10, height-10, 25, 25);
    }

    public void drawPauseScreen(){
        String text = "PAUSE";

        int x = getXforCenteredText(text);
        int y = gp.screenHeight/2;

        g2.drawString(text, x, y);
    }

    private void drawOptionScreen(){
        g2.setColor(Color.white);
        g2.setFont(g2.getFont().deriveFont(15f));

        int frameWidth = subState==1?576:gp.tileSize*8;
        int frameX = (gp.screenWidth-frameWidth)/2;
        int frameY = gp.tileSize;
        int frameHeight = gp.tileSize * 10;
        drawSubWindow(frameX, frameY, frameWidth, frameHeight);

        switch (subState){
            case 0 -> option_top(frameX, frameY);
            case 1 -> option_display(frameX, frameY);
            case 2 -> option_control(frameX, frameY);
            case 3 -> option_endgameConfirm(frameX, frameY);
        }
    }

    private void option_top(int frameX, int frameY){
        int textX;
        int textY;
        String text = "OPTIONS";

        //TITLE
        textX = getXforCenteredText(text);
        textY = frameY + gp.tileSize;
        g2.drawString(text, textX, textY);

        textX = frameX + gp.tileSize;
        textY += gp.tileSize * 2;
        drawMenuButton(optionTopButtons[0], "Display", textX, textY, commandNum == 0);

        textY += gp.tileSize;
        drawMenuButton(optionTopButtons[1], "Music", textX, textY, commandNum == 1);

        textY += gp.tileSize;
        drawMenuButton(optionTopButtons[2], "SE", textX, textY, commandNum == 2);

        textY += gp.tileSize;
        drawMenuButton(optionTopButtons[3], "Control", textX, textY, commandNum == 3);

        textY += gp.tileSize * 2;
        drawMenuButton(optionTopButtons[4], "END GAME", textX, textY, commandNum == 4);

        textY += gp.tileSize;
        drawMenuButton(optionTopButtons[5], "BACK", textX, textY, commandNum == 5);

        textX = frameX + gp.tileSize*5;
        textY = frameY + gp.tileSize*2 + 24;
        g2.setFont(jetbrainsMono.deriveFont(13f));
        g2.drawString(gp.fullScreenOn?"Fullscreen":"Windowed",textX,textY+17);
        //MUSIC VOLUME
        textY += gp.tileSize;
        g2.drawRect(textX, textY, 120, 20);
        int volumeWidth = 24 * gp.music.volumeScale;
        g2.fillRect(textX, textY, volumeWidth, 20);

        //SOUND EFFECT VOLUME
        textY += gp.tileSize;
        g2.drawRect(textX, textY, 120, 20);
        volumeWidth = 24 * gp.soundEffect.volumeScale;
        g2.fillRect(textX, textY, volumeWidth, 20);
    }

    private void option_display(int frameX,int frameY){
        if(displayDraftResolution==null)displayDraftResolution=gp.display.selected();
        g2.setFont(jetbrainsMono.deriveFont(Font.BOLD,22f));g2.setColor(Color.white);
        String title="DISPLAY";g2.drawString(title,getXforCenteredText(title),frameY+52);
        int x=frameX+48,y=frameY+118;
        g2.setFont(jetbrainsMono.deriveFont(17f));
        drawMenuButton(displayButtons[0],"Mode: "+(displayDraftFullscreen?"Fullscreen":"Windowed"),x,y,commandNum==0);
        String nativeLabel=displayDraftResolution.equals(gp.display.nativeResolution())?" (Native)":"";
        drawMenuButton(displayButtons[1],"Resolution: < "+displayDraftResolution+nativeLabel+" >",x,y+64,commandNum==1);
        g2.setFont(jetbrainsMono.deriveFont(13f));g2.setColor(Color.lightGray);
        g2.drawString("A/D changes the selected setting",x,y+112);
        g2.drawString("Resolutions come from this display.",x,y+139);
        g2.setFont(jetbrainsMono.deriveFont(18f));
        drawMenuButton(displayButtons[2],"Apply",x,y+216,commandNum==2);
        drawMenuButton(displayButtons[3],"Back",x,y+280,commandNum==3);
    }

    public void adjustDisplaySelection(int delta){
        if(subState!=1)return;
        if(commandNum==0)displayDraftFullscreen=!displayDraftFullscreen;
        if(commandNum==1)displayDraftResolution=gp.display.cycle(displayDraftResolution,delta);
    }

    private void option_endgameConfirm(int frameX, int frameY){
        int textX = frameX + gp.tileSize;
        int textY = frameY + gp.tileSize*3;

        String message = "Save your character\nand return to the\ntitle screen?";
        for(String line: message.split("\n")){
            g2.drawString(line, textX, textY);
            textY += 40;
        }

        String text = "Yes";
        textX = getXforCenteredText(text);
        textY += gp.tileSize * 3;
        drawMenuButton(endgameConfirmButtons[0], text, textX, textY, commandNum == 0);

        text = "No";
        textX = getXforCenteredText(text);
        textY += gp.tileSize;
        drawMenuButton(endgameConfirmButtons[1], text, textX, textY, commandNum == 1);
    }

    public void option_control(int frameX, int frameY){
        int textX;
        int textY;

        //TITLE
        String text = "CONTROL";
        textX = getXforCenteredText(text);
        textY = frameY + gp.tileSize;
        g2.drawString(text, textX, textY);

        textX = frameX + gp.tileSize;
        textY += gp.tileSize;
        g2.drawString("Move",textX, textY);
        textY += gp.tileSize;
        g2.drawString("Chat/Pause",textX, textY);
        textY += gp.tileSize;
        g2.drawString("Attack",textX, textY);
        textY += gp.tileSize;
        g2.drawString("Shoot/Cast",textX, textY);
        textY += gp.tileSize;
        g2.drawString("Character",textX, textY);
        textY += gp.tileSize;
        g2.drawString("Option",textX, textY);

        textX = frameX + gp.tileSize*6;
        textY = frameY + gp.tileSize*2;
        g2.drawString("WASD", textX, textY);
        textY += gp.tileSize;
        g2.drawString("ENTER/P", textX, textY);
        textY += gp.tileSize;
        g2.drawString("E", textX, textY);
        textY += gp.tileSize;
        g2.drawString("1-5", textX, textY);
        textY += gp.tileSize;
        g2.drawString("C", textX, textY);
        textY += gp.tileSize;
        g2.drawString("ESC", textX, textY);

        //BACK
        textX = frameX + gp.tileSize;
        textY = frameY + gp.tileSize*9;
        drawMenuButton(controlButtons[0], "BACK", textX, textY,  commandNum == 0);
    }

    public int getXforCenteredText(String text){
        int length = (int) g2.getFontMetrics().getStringBounds(text, g2).getWidth();
        int x = gp.screenWidth/2 - length/2;
        return x;
    }

    public int getXforRightAlignText(String text, int tailX){
        int length = (int) g2.getFontMetrics().getStringBounds(text, g2).getWidth();
        int x = tailX - length;
        return x;
    }

    public void drawDebug(Graphics2D g2, double playTime){
        g2.setFont(new Font("Arial", Font.PLAIN, 18));
        g2.setColor(Color.white);

        int playerTileX = (gp.localPlayer().worldX + gp.localPlayer().solidArea.x) / gp.tileSize;
        int playerTileY = (gp.localPlayer().worldY + gp.localPlayer().solidArea.y) / gp.tileSize;

        int x = 24 * gp.tileSize;
        int y = 25;
        int lineHeight = 22;

        g2.drawString("FPS:" + gp.fpsCounter, x, y);
        y += lineHeight;
        g2.drawString("X:" + playerTileX + ",Y:" + playerTileY, x, y);
        y += lineHeight;
        g2.drawString("Time: " + decimalFormat.format(playTime), x, y);
    }



    private int processButtonHover(UIButton[] buttons, int mouseX, int mouseY){
        int hoveredIndex = -1;

        for(int i = 0; i< buttons.length; i++){
            boolean hovered = buttons[i].contains(mouseX, mouseY);
            buttons[i].hovered = hovered;

            if(hovered){
                hoveredIndex = i;
                if(buttons[i] != lastHoveredButton){
                    gp.playSE(8);
                    lastHoveredButton = buttons[i];
                }
            }
        }

        return  hoveredIndex;
    }

    private void drawMenuButton(UIButton button, String text, int x, int y, boolean selected){
        FontMetrics fm = g2.getFontMetrics();
        int width = (int) fm.getStringBounds(text, g2).getWidth();
        int ascent = fm.getAscent();
        int descent = fm.getDescent();

        //Padded Hitbox - a few px larger than the text itself, easier to click
        button.setBounds(x - 10, y - ascent - 4, width + 20, ascent + descent + 8);

        if(button.hovered || selected){
            g2.setColor(new Color(255,255,255,60));
            g2.fillRoundRect(button.bounds.x, button.bounds.y,button.bounds.width,button.bounds.height,10,10);
        }

        g2.setColor(Color.white);
        g2.drawString(text, x, y);
    }

    private void drawSkillHotbar(){
        int slotSize = 50;
        int spacing = 10;
        int totalWidth = (slotSize * 5) + (spacing * 4);
        int startX = (gp.screenWidth - totalWidth) / 2;
        int y = gp.screenHeight - slotSize - 20;

        Player local=gp.localPlayer();
        for(int i = 0; i < local.skills.length; i++){
            Skill skill = local.skills[i];
            if(skill == null) continue;

            int x = startX + i * (slotSize + spacing);

            //SLOT BACKGROUND
            g2.setColor(Color.BLACK);
            g2.fillRoundRect(x, y, slotSize, slotSize, 8, 8);

            //ICON
            if(skill.icon != null){
                int inset=local.isRanger() || local.isAssassin() || local.usesMana() || local.isBerserker() || local.isPaladin()?4:9;
                g2.drawImage(skill.icon,x+inset,y+inset,slotSize-2*inset,slotSize-2*inset,null);
            }

            //COOLDOWN OVERLAY - darkens bottom-up as it wipes away, like a classic ability timer
            if(!skill.isReady()){
                float cooldownFraction = (float) skill.cooldownRemaining / skill.cooldownDuration;
                int overlayHeight = (int)(slotSize * cooldownFraction);
                g2.setColor(new Color(0, 0, 0, 160));
                g2.fillRect(x, y, slotSize, overlayHeight);

                int secondsLeft = (int) Math.ceil(skill.cooldownRemaining / 60.0);
                g2.setFont(g2.getFont().deriveFont(Font.BOLD, 16f));
                g2.setColor(Color.white);
                String text = String.valueOf(secondsLeft);
                int textWidth = (int) g2.getFontMetrics().getStringBounds(text, g2).getWidth();
                g2.drawString(text, x + (slotSize - textWidth) / 2, y + (slotSize / 2) + 6);
            }

            //INSUFFICIENT RESOURCE TINT
            int resource=local.skillResource();
            boolean insufficientResource=resource<skill.resourceCost
                    || (local.isMage() && local.arcaneCharge<local.mage.runesRequired(i));
            if(insufficientResource){
                g2.setColor(new Color(255, 0, 0, 80));
                g2.fillRoundRect(x, y, slotSize, slotSize, 8, 8);
            }

            //BORDER
            g2.setColor(Color.white);
            g2.setStroke(new BasicStroke(2));
            g2.drawRoundRect(x, y, slotSize, slotSize, 8, 8);

            //KEYBIND NUMBER
            g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 12f));
            g2.drawString(String.valueOf(i + 1), x + 4, y + 14);
        }
        int mx=gp.mouseH.getScaledX(),my=gp.mouseH.getScaledY();
        for(int i=0;i<5;i++)if(new Rectangle(startX+i*(slotSize+spacing),y,slotSize,slotSize).contains(mx,my)){
            Skill skill=local.skills[i];if(skill==null)continue;
            int runes=local.isMage()?local.mage.runesRequired(i):0;
            String cost=skill.resourceCost+" "+local.resourceName()+(runes==0?"":runes==1?" + 1+ runes":" + "+runes+" runes");
            gp.merchant.tooltip(g2,java.util.List.of(skill.name,cost+" | "+skill.cooldownDuration/60+"s cooldown",skill.description),mx+16,my-115);
        }
    }

    private void drawStatusEffects(Player local){
        if(local.statusEffects.isEmpty()) return;

        int iconSize = 32;
        int spacing = 6;
        int x = gp.tileSize / 2;
        int y = gp.tileSize / 2 + 22 + 8 + 22 + 10; // below the HP bar + spacing + MP bar + gap

        for(entity.StatusEffect effect : local.statusEffects){
            BufferedImage icon = local.statusEffectIcons.get(effect.type);
            if(icon==null)icon=entity.PriestCombat.statusIcon(effect.type);

            g2.setColor(Color.BLACK);
            g2.fillRoundRect(x, y, iconSize, iconSize, 6, 6);

            if(icon != null){
                g2.drawImage(icon, x + 4, y + 4, iconSize - 8, iconSize - 8, null);
            }

            g2.setColor(Color.white);
            g2.setStroke(new BasicStroke(1));
            g2.drawRoundRect(x, y, iconSize, iconSize, 6, 6);

            int secondsLeft = (int) Math.ceil(effect.duration / 60.0);
            g2.setFont(jetbrainsMono.deriveFont(Font.BOLD, 11f));
            String text = String.valueOf(secondsLeft);
            int tw = (int) g2.getFontMetrics().getStringBounds(text, g2).getWidth();
            g2.setColor(Color.black);
            g2.drawString(text, x + iconSize - tw - 1, y + iconSize - 1);
            g2.setColor(Color.white);
            g2.drawString(text, x + iconSize - tw - 2, y + iconSize - 2);

            x += iconSize + spacing;
        }
    }

    private void updateInventoryDrag(int mouseX, int mouseY){
        if(gp.mouseH.rightClicked){
            handleRightClick(mouseX, mouseY);
            gp.mouseH.rightClicked = false;
        }

        if(gp.mouseH.leftPressed && !draggingItem){
            if(undressBounds.contains(mouseX,mouseY)){
                net.InventoryAction action=new net.InventoryAction();
                action.playerId=gp.localPlayer().playerId;action.actionType=InventoryActionType.UNDRESS;
                gp.requestInventoryAction(action);gp.mouseH.leftPressed=false;return;
            }
            for(int i=0;i<equipmentBounds.length;i++){
                Entity item=gp.localPlayer().equippedItem(i+1);
                if(equipmentBounds[i]!=null && equipmentBounds[i].contains(mouseX,mouseY) && item!=null && !item.isPlaceholder){
                    draggingItem=true;draggedItem=item;dragSourceType=i+1;dragSourceIndex=-1;break;
                }
            }
            if(!draggingItem){
                for(int i = 0; i < inventorySlotBounds.length; i++){
                    if(inventorySlotBounds[i] != null && inventorySlotBounds[i].contains(mouseX, mouseY) && gp.localPlayer().inventorySlots[i] != null){
                        draggingItem = true;
                        draggedItem = gp.localPlayer().inventorySlots[i];
                        dragSourceType = SLOT_BAG;
                        dragSourceIndex = i;
                        break;
                    }
                }
            }
        }

        hoveredItem = draggingItem ? null : getItemAtPosition(mouseX, mouseY);

        if(!gp.mouseH.leftPressed && draggingItem){
            resolveDrop(mouseX, mouseY);
            draggingItem = false;
            draggedItem = null;
            dragSourceIndex = -1;
        }
    }

    private void handleRightClick(int mouseX, int mouseY){
        for(int i = 0; i < inventorySlotBounds.length; i++){
            if(inventorySlotBounds[i] != null && inventorySlotBounds[i].contains(mouseX, mouseY)){
                net.InventoryAction action = new net.InventoryAction();
                action.playerId = gp.localPlayer().playerId;
                action.actionType = InventoryActionType.USE;
                action.sourceIndex = i;
                gp.requestInventoryAction(action);
                return;
            }
        }
    }

    private void resolveDrop(int mouseX, int mouseY){
        Player local = gp.localPlayer();
        net.InventoryAction action = new net.InventoryAction();
        action.playerId = local.playerId;

        for(int i=0;i<equipmentBounds.length;i++){
            if(equipmentBounds[i]!=null && equipmentBounds[i].contains(mouseX,mouseY)){
                if(dragSourceType==SLOT_BAG){
                    action.actionType=InventoryActionType.EQUIP;action.sourceIndex=dragSourceIndex;action.slotType=i+1;
                    gp.requestInventoryAction(action);
                }
                return;
            }
        }

        for(int i = 0; i < inventorySlotBounds.length; i++){
            if(inventorySlotBounds[i] != null && inventorySlotBounds[i].contains(mouseX, mouseY)){
                if(dragSourceType == SLOT_BAG){
                    action.actionType = InventoryActionType.SWAP;
                    action.sourceIndex = dragSourceIndex;
                    action.targetIndex = i;
                } else {
                    action.actionType = InventoryActionType.UNEQUIP_TO_BAG;
                    action.slotType = dragSourceType;
                    action.targetIndex = i;
                }
                gp.requestInventoryAction(action);
                return;
            }
        }

        if(!inventoryWindowBounds.contains(mouseX, mouseY)){
            if(dragSourceType == SLOT_BAG){
                action.actionType = InventoryActionType.DROP;
                action.sourceIndex = dragSourceIndex;
            } else {
                action.actionType = InventoryActionType.UNEQUIP_TO_WORLD;
                action.slotType = dragSourceType;
            }
            gp.requestInventoryAction(action);
        }
    }

    private Entity getItemAtPosition(int mouseX, int mouseY){
        for(int i=0;i<equipmentBounds.length;i++){
            if(equipmentBounds[i]!=null && equipmentBounds[i].contains(mouseX,mouseY)){
                Entity item=gp.localPlayer().equippedItem(i+1);
                return item!=null && !item.isPlaceholder ? item : null;
            }
        }
        for(int i = 0; i < inventorySlotBounds.length; i++){
            if(inventorySlotBounds[i] != null && inventorySlotBounds[i].contains(mouseX, mouseY) && gp.localPlayer().inventorySlots[i] != null){
                return gp.localPlayer().inventorySlots[i];
            }
        }
        return null;
    }

    public void startHostFlow(){
        System.out.println("[UI] startHostFlow() called");
        try{
            gp.hostGame(Gamepanel.NETWORK_PORT);
            gp.gameState = gp.playState;
            gp.playMusic(0);
            addMessage("Hosting on port " + Gamepanel.NETWORK_PORT + " — waiting for players to join.", new Color(150, 220, 255));
        } catch(Exception e){ // widen from IOException — catch anything that goes wrong here
            e.printStackTrace();
            joinStatusMessage = "Failed to host: " + e;
            gp.shutdownNetwork();
        }
    }

    public void startJoinFlow(){
        System.out.println("[UI] startJoinFlow() called");
        gp.keyH.beginEnteringNetworkAddress();
    }

    public void submitJoinAddress(String address){
        System.out.println("[UI] submitJoinAddress() called with address='" + address + "'");
        if(address.isEmpty()){
            joinStatusMessage = "Enter a host IP address.";
            return;
        }
        try{
            gp.joinGame(address, Gamepanel.NETWORK_PORT);
            gp.gameState = gp.playState;
            gp.playMusic(0);
        } catch(Exception e){
            e.printStackTrace();
            gp.shutdownNetwork();
            joinStatusMessage = "Failed to connect: " + e;
        }
    }

    public void cancelJoinAddress(){
        joinStatusMessage = null;
    }

    private void drawDeathScreen(){
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, gp.screenWidth, gp.screenHeight);

        g2.setFont(jetbrainsMono.deriveFont(Font.BOLD, 60f));
        g2.setColor(Color.red);
        String text = "YOU DIED";
        int x = getXforCenteredText(text);
        int y = gp.screenHeight/2 - 60;
        g2.drawString(text, x, y);

        g2.setColor(Color.white);
        g2.setFont(jetbrainsMono.deriveFont(Font.PLAIN, 26f));
        text = "Return to Spawn";
        x = getXforCenteredText(text);
        y = gp.screenHeight/2 + 20;
        drawMenuButton(respawnButton, text, x, y, false);
    }

    public void confirmCharacterCreation(int classIndex, int genderIndex){
        if(characterSlots.creationSlot>=0){
            try{
                gp.saves.create(characterSlots.creationSlot,pendingCharacterName,classIndex,genderIndex,customization.style,customization.color);
                characterSlots.creationSlot=-1;creationError=null;startSelectedCharacter();
            }catch(Exception e){
                creationError="Could not create character: "+e.getMessage();gp.keyH.characterCreationStep=2;
            }
            return;
        }
        Player local = gp.localPlayer();
        local.playerName = pendingCharacterName;
        local.setCharacterDesign(entity.CharacterClass.at(classIndex).id, genderIndex == 1 ? "female" : "male");

        local.setDefaultValues();
        if(local.usesMana()){
            local.getMagePlayerImage();
        } else {
            local.getPlayerImage();
        }
        local.setItems();
        local.refreshWeaponAnimation();

        startSelectedCharacter();
    }

    public void startSelectedCharacter(){
        switch(pendingTitleAction){
            case 0 -> {
                gp.gameState = gp.playState;
                gp.playMusic(0);
            }
            case 1 -> {
                gp.gameState = gp.titleState;
                startHostFlow();
            }
            case 2 -> {
                gp.gameState = gp.titleState;
                startJoinFlow();
            }
        }
        pendingTitleAction = -1;
    }

    public void clearCreationError(){creationError=null;}

    private void drawCharacterCreationScreen(){
        g2.setColor(Color.black);
        g2.fillRect(0, 0, gp.screenWidth, gp.screenHeight);

        if(gp.keyH.characterCreationStep == 0){
            drawNameEntryStep();
        } else if(gp.keyH.characterCreationStep==1) {
            drawClassGenderStep();
        } else customization.draw(g2,jetbrainsMono);
        if(creationError!=null){
            g2.setColor(new Color(245,160,150));g2.setFont(jetbrainsMono.deriveFont(12f));
            String text=creationError;while(text.length()>4 && g2.getFontMetrics().stringWidth(text)>gp.screenWidth-48)text=text.substring(0,text.length()-4)+"...";
            g2.drawString(text,24,gp.screenHeight-10);
        }
    }

    private void drawNameEntryStep(){
        g2.setFont(jetbrainsMono.deriveFont(Font.BOLD, 30f));
        g2.setColor(Color.white);
        String title = "Name your character";
        int x = getXforCenteredText(title);
        int y = gp.tileSize * 5;
        g2.drawString(title, x, y);

        int boxWidth = 340;
        int boxHeight = 40;
        int boxX = gp.screenWidth/2 - boxWidth/2;
        int boxY = y + gp.tileSize;
        drawSubWindow(boxX, boxY, boxWidth, boxHeight);

        g2.setFont(jetbrainsMono.deriveFont(18f));
        String display = gp.keyH.characterNameInput.toString();
        if((System.currentTimeMillis() / 400) % 2 == 0){
            display += "_";
        }
        g2.drawString(display, boxX + 12, boxY + 26);

        g2.setFont(jetbrainsMono.deriveFont(14f));
        String hint = "Type a name, then choose Next";
        x = getXforCenteredText(hint);
        g2.drawString(hint, x, boxY + boxHeight + 40);
        drawCreationButton(nameNext(),"Next");drawCreationButton(nameBack(),"Back");
    }

    private Rectangle nameNext(){return new Rectangle(gp.screenWidth/2+10,400,160,44);}
    private Rectangle nameBack(){return new Rectangle(gp.screenWidth/2-170,400,160,44);}
    private void drawCreationButton(Rectangle r,String label){
        WindowStyle.draw(g2,r.x,r.y,r.width,r.height);g2.setColor(Color.WHITE);g2.setFont(jetbrainsMono.deriveFont(15f));
        g2.drawString(label,r.x+(r.width-g2.getFontMetrics().stringWidth(label))/2,r.y+28);
    }
    public void confirmCharacterName(){
        String name=gp.keyH.characterNameInput.toString().trim();pendingCharacterName=name.isEmpty()?"Adventurer":name;
        gp.keyH.enteringCharacterName=false;gp.keyH.characterCreationStep=1;
    }

    private final java.util.Map<String, BufferedImage> creationPreviews = new java.util.HashMap<>();

    private BufferedImage creationPreview(entity.CharacterClass choice, boolean female, boolean side){
        String key=choice.id+"|"+female+"|"+side;
        return creationPreviews.computeIfAbsent(key,k -> {
            art.CharacterArt.Look look=new art.CharacterArt.Look();
            look.outfit=choice.outfit(female);look.shadow=false;
            look.weapon=choice==entity.CharacterClass.HUNTER?art.WeaponArt.Kind.DAGGERS:choice==entity.CharacterClass.MAGE?art.WeaponArt.Kind.STAFF:art.WeaponArt.Kind.SWORD;
            if(choice==entity.CharacterClass.RANGER){
                look.weapon=art.WeaponArt.Kind.NONE;
                look.rangerBow=look.rangerOutfit=art.RangerGear.TRAIL;
            }
            if(choice==entity.CharacterClass.HUNTER)look.assassinOutfit=art.AssassinGear.ALLEY;
            if(choice==entity.CharacterClass.MAGE)look.mageOutfit=art.MageGear.HEARTH;
            if(choice==entity.CharacterClass.PALADIN){
                look.weapon=art.WeaponArt.Kind.GATEKEEPER;look.paladinOutfit=look.paladinShield=art.PaladinGear.GATE;
            }
            if(choice==entity.CharacterClass.BERSERKER){
                look.weapon=art.WeaponArt.Kind.FIELDBREAKER;look.berserkerOutfit=art.BerserkerGear.ROAD;
            }
            if(choice==entity.CharacterClass.PRIEST){
                look.weapon=art.WeaponArt.Kind.WAYLIGHT_STAFF;look.priestOutfit=art.PriestGear.WAY;
            }
            look.shieldStyle=choice==entity.CharacterClass.WARRIOR?1:0;
            return art.CharacterArt.render(side?art.CharacterArt.Dir.RIGHT:art.CharacterArt.Dir.DOWN,
                    new art.CharacterArt.Pose(),look,144);
        });
    }

    private void drawClassGenderStep(){
        g2.setColor(Color.BLACK);g2.fillRect(0,0,gp.screenWidth,gp.screenHeight);
        g2.setFont(jetbrainsMono.deriveFont(Font.BOLD,30f));g2.setColor(Color.white);
        g2.drawString("Choose your class",getXforCenteredText("Choose your class"),70);
        g2.setFont(jetbrainsMono.deriveFont(14f));g2.setColor(new Color(180,185,199));
        String subtitle="Choose your class and appearance.";
        g2.drawString(subtitle,getXforCenteredText(subtitle),100);
        boolean female=gp.keyH.selectedGenderIndex==1;
        var choices=entity.CharacterClass.CREATION_CHOICES;
        int gap=18,cardWidth=(gp.screenWidth-120-(choices.size()-1)*gap)/choices.size(),top=133,cardHeight=340;
        int preview=Math.min(144,(cardWidth-8)/2);
        int mx=gp.mouseH.getScaledX(),my=gp.mouseH.getScaledY();
        boolean clicked=gp.mouseH.leftClicked;
        for(int index=0;index<choices.size();index++){
            var choice=choices.get(index);
            int x=60+index*(cardWidth+gap);
            Rectangle card=new Rectangle(x,top,cardWidth,cardHeight);
            if(clicked && card.contains(mx,my))gp.keyH.selectedClassIndex=index;
            boolean selected=gp.keyH.selectedClassIndex==index;
            g2.setColor(Color.BLACK);
            g2.fillRoundRect(x,top,cardWidth,cardHeight,20,20);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(selected?3:1));g2.drawRoundRect(x,top,cardWidth,cardHeight,20,20);
            g2.setFont(jetbrainsMono.deriveFont(Font.BOLD,20f));
            int labelWidth=g2.getFontMetrics().stringWidth(choice.label);
            if(labelWidth>cardWidth-12)g2.setFont(jetbrainsMono.deriveFont(Font.BOLD,20f*(cardWidth-12)/labelWidth));
            g2.setColor(Color.WHITE);
            g2.drawString(choice.label,x+(cardWidth-g2.getFontMetrics().stringWidth(choice.label))/2,top+43);
            int center=x+cardWidth/2;
            g2.drawImage(creationPreview(choice,female,false),center-preview,top+85,preview,preview,null);
            g2.drawImage(creationPreview(choice,female,true),center,top+85,preview,preview,null);
            g2.setFont(jetbrainsMono.deriveFont(14f));g2.setColor(new Color(219,222,232));
            String type=choice.armorLabel+" type";
            g2.drawString(type,center-g2.getFontMetrics().stringWidth(type)/2,top+251);
            g2.setFont(jetbrainsMono.deriveFont(12f));g2.setColor(new Color(165,173,190));
            int weaponWidth=g2.getFontMetrics().stringWidth(choice.weaponLabel);
            if(weaponWidth>cardWidth-8)g2.setFont(jetbrainsMono.deriveFont(12f*(cardWidth-8)/weaponWidth));
            g2.drawString(choice.weaponLabel,center-g2.getFontMetrics().stringWidth(choice.weaponLabel)/2,top+281);
            g2.setFont(jetbrainsMono.deriveFont(10f));
            if(choice==entity.CharacterClass.PALADIN){
                String controls="Hold right click to Guard";
                g2.drawString(controls,center-g2.getFontMetrics().stringWidth(controls)/2,top+296);
            }
            if(choice==entity.CharacterClass.BERSERKER){
                String controls="Sweep groups for more Rage";
                g2.drawString(controls,center-g2.getFontMetrics().stringWidth(controls)/2,top+296);
            }
            if(choice==entity.CharacterClass.RANGER){
                String controls="Click to shoot · Hold to draw";
                g2.drawString(controls,center-g2.getFontMetrics().stringWidth(controls)/2,top+296);
            }
            if(choice==entity.CharacterClass.HUNTER){
                String controls="Land two pairs for a critical hit";
                g2.drawString(controls,center-g2.getFontMetrics().stringWidth(controls)/2,top+296);
            }
            if(choice==entity.CharacterClass.MAGE){
                String controls="Land bolts to build runes";
                g2.drawString(controls,center-g2.getFontMetrics().stringWidth(controls)/2,top+296);
            }
            if(choice==entity.CharacterClass.PRIEST){
                String controls="Mend links an ally";
                g2.drawString(controls,center-g2.getFontMetrics().stringWidth(controls)/2,top+296);
            }
            if(selected){g2.setColor(Color.WHITE);g2.drawString("SELECTED",center-g2.getFontMetrics().stringWidth("SELECTED")/2,top+312);}
        }
        String[] labels={"Male preset","Female preset"};
        for(int i=0;i<2;i++){
            int x=gp.screenWidth/2-264+i*274,y=505;
            Rectangle box=new Rectangle(x,y,254,46);
            if(clicked && box.contains(mx,my))gp.keyH.selectedGenderIndex=i;
            boolean selected=gp.keyH.selectedGenderIndex==i;
            g2.setColor(Color.BLACK);g2.fillRoundRect(x,y,254,46,12,12);
            g2.setColor(selected?new Color(0xD9B77D):Color.WHITE);g2.drawRoundRect(x,y,254,46,12,12);
            g2.setFont(jetbrainsMono.deriveFont(15f));g2.setColor(Color.WHITE);
            g2.drawString(labels[i],x+(254-g2.getFontMetrics().stringWidth(labels[i]))/2,y+29);
        }
        g2.setFont(jetbrainsMono.deriveFont(13f));g2.setColor(new Color(165,173,190));
        String hint="Click a class and preset, then customize your hair";
        g2.drawString(hint,getXforCenteredText(hint),594);
        String note="All 40 hairstyles are available with either preset.";
        g2.drawString(note,getXforCenteredText(note),621);
        Rectangle confirm=new Rectangle(gp.screenWidth/2-110,646,220,42);
        WindowStyle.draw(g2,confirm.x,confirm.y,220,42);
        g2.setColor(Color.WHITE);g2.setFont(jetbrainsMono.deriveFont(Font.BOLD,16f));
        g2.drawString("Customize hair",confirm.x+(220-g2.getFontMetrics().stringWidth("Customize hair"))/2,confirm.y+27);
        Rectangle back=new Rectangle(40,646,160,42);drawCreationButton(back,"Back");
        gp.mouseH.leftClicked=false;
        if(clicked && confirm.contains(mx,my)){
            customization.open();
        }
        if(clicked && back.contains(mx,my)){gp.keyH.characterCreationStep=0;gp.keyH.enteringCharacterName=true;}
    }

    private String capitalize(String s){
        if(s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private void drawArcaneCharge(Player local, int x, int barTopY, int barHeight){
        int pipSize = 14;
        int spacing = 4;
        int y = barTopY + (barHeight - pipSize) / 2;

        for(int i = 0; i < local.maxArcaneCharge; i++){
            int px = x + i * (pipSize + spacing);
            boolean filled = i < local.arcaneCharge;
            int cx=px+pipSize/2,cy=y+pipSize/2;
            var rune=new Polygon(new int[]{cx,px+pipSize,cx,px},new int[]{y,cy,y+pipSize,cy},4);
            g2.setColor(filled ? new Color(190,120,255) : new Color(0,0,0,140));
            g2.fillPolygon(rune);
            g2.setColor(filled?new Color(0xF6E9FF):new Color(0xBDA9D1));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawPolygon(rune);
            if(filled)g2.fillPolygon(new int[]{cx,cx+3,cx,cx-3},new int[]{cy-4,cy,cy+4,cy},4);
        }

        if(local.arcaneCharge >= local.maxArcaneCharge){
            g2.setFont(jetbrainsMono.deriveFont(Font.BOLD, 12f));
            g2.setColor(new Color(190, 120, 255));
            int textX = x + local.maxArcaneCharge * (pipSize + spacing) + 6;
            g2.drawString("READY", textX, barTopY + barHeight - 6);
        }
    }

    public void showTitleMessage(String message){
        joinStatusMessage = message;
    }

    public void showSaveError(String message){
        joinStatusMessage=message;characterSlots.showMessage(message);
        if(gp.gameState==gp.characterState)creationError=message;
        addMessage(message,Color.RED);
    }

    public void openOptions(){
        subState = 0;
        commandNum = 0;
        gp.optionsOpen = true;
    }

    public void closeOptions(){
        gp.optionsOpen = false;
        subState = 0;
        commandNum = 0;
    }

    private void activateOptionItem(int index){
        switch(subState){
            case 0 -> {
                switch(index){
                    case 0 -> {
                        displayDraftFullscreen=gp.fullScreenOn;
                        displayDraftResolution=gp.display.selected();
                        subState=1;commandNum=0;
                    }
                    case 3 -> { subState = 2; commandNum = 0; }
                    case 4 -> { subState = 3; commandNum = 0; }
                    case 5 -> closeOptions();
                    // 1 and 2 are the volume rows: adjusted with A/D, nothing to activate
                }
            }
            case 1 -> {
                if(index<=1)adjustDisplaySelection(1);
                if(index==2){
                    gp.display.apply(displayDraftFullscreen,displayDraftResolution);
                    gp.config.saveConfigSafe();subState=0;commandNum=0;
                }
                if(index==3){subState=0;commandNum=0;}
            }
            case 2 -> { subState = 0; commandNum = 3; }
            case 3 -> {
                if(index == 0){
                    if(gp.returnToTitle())closeOptions();
                } else {
                    subState = 0;
                    commandNum = 4;
                }
            }
        }
    }

    public void confirmOptionSelection(){
        activateOptionItem(commandNum);
    }

    private void updateCombatLog(int mx, int my){
        int boxX = chatLogMargin;
        int boxY = gp.screenHeight - chatLogHeight - chatLogMargin;
        combatLogBounds.setBounds(boxX, boxY, chatLogWidth, chatLogHeight);
        combatLogHovered = combatLogBounds.contains(mx, my);

        if(combatLogHovered && gp.mouseH.wheelRotation != 0){
            chatScrollOffset -= gp.mouseH.wheelRotation; // scroll up = view older history
            int maxOffset = Math.max(0, chatHistory.size() - chatVisibleLines);
            chatScrollOffset = Math.max(0, Math.min(chatScrollOffset, maxOffset));
        }
        gp.mouseH.wheelRotation = 0; // consumed every tick, so wheel turns elsewhere no longer pile up

        // age only the visible lines: history you scrolled back to stays fully readable
        int endIndex = chatHistory.size() - chatScrollOffset;
        int startIndex = Math.max(0, endIndex - chatVisibleLines);
        for(int i = startIndex; i < endIndex; i++){
            chatHistory.get(i).ageTicks++;
        }
    }

    private void drawChatInput(){
        int boxX = combatLogBounds.x;
        int boxHeight = 30;
        int boxY = combatLogBounds.y - boxHeight - 6;

        g2.setColor(Color.BLACK);
        g2.fillRoundRect(boxX, boxY, chatLogWidth, boxHeight, 10, 10);
        g2.setColor(Color.white);
        g2.setStroke(new BasicStroke(1));
        g2.drawRoundRect(boxX, boxY, chatLogWidth, boxHeight, 10, 10);

        g2.setFont(jetbrainsMono.deriveFont(14f));
        String display = gp.keyH.chatInput.toString();
        if((System.currentTimeMillis() / 400) % 2 == 0){
            display += "_";
        }
        FontMetrics fm = g2.getFontMetrics();
        while(display.length() > 1 && fm.stringWidth(display) > chatLogWidth - 20){
            display = display.substring(1); // keep the tail visible when the text outgrows the box
        }
        g2.drawString(display, boxX + 10, boxY + 20);
    }
}
