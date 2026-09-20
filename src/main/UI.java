package main;

import entity.Entity;
import object.OBJ_crystal;
import object.OBJ_heart;
import entity.Player;

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
    public String currentDialogue = "";
    public String pendingCharacterName = "";
    public int commandNum = 0;

    private Rectangle[] inventorySlotBounds = new Rectangle[20];

    private UIButton[] titleButtons = {new UIButton(), new UIButton(), new UIButton(), new UIButton()};
    private UIButton[] optionTopButtons = {new UIButton(), new UIButton(), new UIButton(), new UIButton(), new UIButton(), new UIButton()};
    private UIButton[] fullscreenNotiButtons = {new UIButton()};
    private UIButton[] controlButtons = {new UIButton()};
    private UIButton[] endgameConfirmButtons = {new UIButton(), new UIButton()};
    private UIButton[] gameoverButtons = {new UIButton(), new UIButton()};
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
    private Rectangle weaponSlotBounds = new Rectangle();
    private Rectangle shieldSlotBounds = new Rectangle();

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
        Player local = gp.localPlayer();
        if(local != null && local.isDead){
            int hovered = processButtonHover(new UIButton[]{respawnButton}, mouseX, mouseY);
            if(hovered != -1 && clicked){
                gp.broadcastPlayerEvent(local.playerId, "respawn_request",
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
        else if(gp.gameState == gp.optionState){
            UIButton[] buttons = currentOptionButtons();
            int hovered = processButtonHover(buttons, mouseX, mouseY);
            if(hovered != -1){
                commandNum = hovered;
                if(clicked){
                    gp.keyH.enterPressed = true;
                }
            }
            gp.mouseH.leftClicked = false;
        }
        else if(gp.gameState == gp.gameOverState){
            int hovered = processButtonHover(gameoverButtons, mouseX, mouseY);
            if(hovered != -1){
                commandNum = hovered;
                if(clicked){
                    confirmGameoverSelection(hovered);
                }
            }
            gp.mouseH.leftClicked = false;
        }
        else if(gp.inventoryOpen){
            updateInventoryDrag(mouseX, mouseY);
        }
    }

    private UIButton[] currentOptionButtons(){
        return switch (subState){
            case 0 -> optionTopButtons;
            case 1 -> fullscreenNotiButtons;
            case 2 -> controlButtons;
            case 3 -> endgameConfirmButtons;
            default -> new UIButton[0];
        };
    }

    void confirmTitleSelection(int index){
        if(index == 0 || index == 1 || index == 2){
            pendingTitleAction = index;
            gp.gameState = gp.characterState;
            gp.keyH.beginEnteringCharacterName();
        }
        if(index == 3){
            System.exit(0);
        }
    }

    private void confirmGameoverSelection(int index){
        if(index == 0){
            gp.gameState = gp.playState;
            gp.localPlayer().setDefaultPosition();
            gp.localPlayer().resetHPandMP();
            gp.playMusic(0);
        }
        if(index == 1){
            gp.gameState = gp.titleState;
            gp.stopMusic();
        }
    }

    public void draw(Graphics2D g2) throws IOException {

        this.g2 = g2;

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

        //PLAY STATE
        if(gp.gameState == gp.playState){
            //PLAYTIME
            playTime += (double) 1/60;

            //Toggle debug menu with [ T ] key
            if(gp.keyH.showDebug){
                drawDebug(g2, playTime);
            }

            drawPlayerHP();
            Player local = gp.localPlayer();
            if(local != null){
                drawStatusEffects(local);
                drawOtherPlayersHUD(local);
            }
            drawCombatLog();
            drawSkillHotbar();
            if(gp.inventoryOpen){
                drawCharacterScreen();
                drawInventory();
            }

            if(local != null && local.isDead){
                drawDeathScreen();
            }
            if(local != null && local.inDialogue){
                drawPersonalDialogue(local.getRevealedDialogueText(), local.isDialogueFullyRevealed());
            }
        }

        //PAUSE STATE
        if(gp.gameState == gp.pauseState){
            drawPauseScreen();
            drawPlayerHP();
        }

        //DIALOGUE STATE
        if(gp.gameState == gp.dialogueState){
            drawDialogueScreen();
        }

        //OPTION STATE
        if(gp.gameState == gp.optionState){
            drawOptionScreen();
        }

        //GAMEOVER STATE
        if(gp.gameState == gp.gameOverState){
            drawGameOverScreen();
        }

    }

    private void drawGameOverScreen() {
        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRect(0, 0, gp.screenWidth, gp.screenHeight);

        int x;
        int y;
        String text;
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 80));
        text = "GAME OVER";
        g2.setColor(Color.black);
        x = getXforCenteredText(text);
        y = gp.tileSize * 5;
        g2.drawString(text, x, y);

        g2.setColor(Color.white);
        g2.drawString(text, x-4, y-4);

        //RETRY
        g2.setFont(g2.getFont().deriveFont(30f));
        text = "RETRY";
        x = getXforCenteredText(text);
        y = gp.tileSize * 10;
        drawMenuButton(gameoverButtons[0],text,x,y,commandNum == 0 );

        text = "QUIT";
        x = getXforCenteredText(text);
        y += 50;
        drawMenuButton(gameoverButtons[1],text,x,y,commandNum == 1 );

    }

    private void drawOptionScreen() throws IOException {
        g2.setColor(Color.white);
        g2.setFont(g2.getFont().deriveFont(15f));

        int frameX = gp.tileSize * 9;
        int frameY = gp.tileSize;
        int frameWidth = gp.tileSize * 8;
        int frameHeight = gp.tileSize * 10;
        drawSubWindow(frameX, frameY, frameWidth, frameHeight);

        switch (subState){
            case 0 -> option_top(frameX, frameY);
            case 1 -> option_fullScreenNotification(frameX, frameY);
            case 2 -> option_control(frameX, frameY);
            case 3 -> option_endgameConfirm(frameX, frameY);
        }

        gp.keyH.enterPressed = false;
    }

    private void option_endgameConfirm(int frameX, int frameY) {
        int textX = frameX + gp.tileSize;
        int textY = frameY + gp.tileSize*3;

        currentDialogue = "Quit the game and\nreturn to the title\nscreen?";

        for(String line: currentDialogue.split("\n")){
            g2.drawString(line, textX, textY);
            textY += 40;
        }

        //YES
        String text = "Yes";
        textX = getXforCenteredText(text);
        textY += gp.tileSize * 3;
        drawMenuButton(endgameConfirmButtons[0], text, textX, textY,  commandNum == 0 );
        if(commandNum == 0){
            if(gp.keyH.enterPressed || gp.mouseH.leftClicked){
                subState = 0;
                gp.stopMusic();
                gp.gameState = gp.titleState;
            }
        }

        //NO
        text = "No";
        textX = getXforCenteredText(text);
        textY += gp.tileSize;
        drawMenuButton(endgameConfirmButtons[1],text,textX, textY,  commandNum == 1 );
        if(commandNum == 1){
            if(gp.keyH.enterPressed || gp.mouseH.leftClicked){
                subState = 0;
                commandNum = 4;
            }
        }
    }

    private void option_top(int frameX, int frameY) throws IOException {
        int textX;
        int textY;
        String text = "OPTIONS";

        //TITLE
        textX = getXforCenteredText(text);
        textY =frameY + gp.tileSize;
        g2.drawString(text, textX, textY);

        //FULLSCREEN ON/OFF
        textX = frameX + gp.tileSize;
        textY += gp.tileSize * 2;
        drawMenuButton(optionTopButtons[0],"Full Screen", textX, textY,  commandNum == 0 );
        if(commandNum == 0){
            if(gp.keyH.enterPressed || gp.mouseH.leftClicked){
                if(!gp.fullScreenOn){
                    gp.fullScreenOn = true;
                }
                else if(gp.fullScreenOn){
                    gp.fullScreenOn = false;
                }
                subState = 1;
            }
        }

        //MUSIC
        textY += gp.tileSize;
        drawMenuButton(optionTopButtons[1],"Music", textX, textY,  commandNum == 1 );

        //SE
        textY += gp.tileSize;
        drawMenuButton(optionTopButtons[2],"SE", textX, textY,  commandNum == 2 );

        //CONTROL
        textY += gp.tileSize;
        drawMenuButton(optionTopButtons[3],"Control", textX, textY,  commandNum == 3 );
        if(commandNum == 3){
            if(gp.keyH.enterPressed || gp.mouseH.leftClicked){
                subState = 2;
                commandNum = 0;
            }
        }

        //ENDGAME
        textY += gp.tileSize * 2;
        drawMenuButton(optionTopButtons[4],"END GAME", textX, textY,  commandNum == 4 );
        if(commandNum == 4){
            if(gp.keyH.enterPressed || gp.mouseH.leftClicked){
                subState = 3;
                commandNum = 0;
            }
        }

        //BACK
        textY += gp.tileSize;
        drawMenuButton(optionTopButtons[5],"BACK", textX, textY,  commandNum == 5 );
        if(commandNum == 5){
            if(gp.keyH.enterPressed || gp.mouseH.leftClicked){
                gp.gameState = gp.playState;
                commandNum = 0;
            }
        }

        //FULLSCREEN CHECKBOX
        textX = frameX + gp.tileSize*5;
        textY = frameY + gp.tileSize*2 + 24;
        g2.setStroke(new BasicStroke(3));
        g2.drawRect(textX, textY, 20, 20);
        if(gp.fullScreenOn){
            g2.fillRect(textX, textY, 20, 20);
        }

        //MUSIC VOLUMN
        textY += gp.tileSize;
        g2.drawRect(textX, textY, 120, 20);
        int volumeWidth = 24 * gp.music.volumeScale;
        g2.fillRect(textX, textY, volumeWidth, 20);

        //SOUND EFFECT VOLUMN
        textY += gp.tileSize;
        g2.drawRect(textX, textY, 120, 20);
        volumeWidth = 24 * gp.soundEffect.volumeScale;
        g2.fillRect(textX, textY, volumeWidth, 20);

        gp.config.saveConfig();
    }

    private void drawInventory() {
        int bagRows = (int) Math.ceil((double) gp.localPlayer().inventorySlots.length / bagColumns);
        int bagGridWidth = bagColumns * slotStride - slotSpacing;
        int bagGridHeight = bagRows * slotStride - slotSpacing;
        int equipColumnHeight = 2 * slotIconSize + equipSlotGap;

        int frameWidth = windowPadding + slotIconSize + equipBagGap + bagGridWidth + windowPadding;
        int frameHeight = windowPadding + Math.max(equipColumnHeight, bagGridHeight) + windowPadding;
        int frameX = gp.screenWidth - frameWidth - screenRightMargin;
        int frameY = gp.tileSize;

        drawSubWindow(frameX, frameY, frameWidth, frameHeight);
        inventoryWindowBounds.setBounds(frameX, frameY, frameWidth, frameHeight);

        //EQUIPMENT PAPERDOLL
        int equipX = frameX + windowPadding;
        int weaponSlotY = frameY + windowPadding;
        int shieldSlotY = weaponSlotY + slotIconSize + equipSlotGap;

        weaponSlotBounds.setBounds(equipX, weaponSlotY, slotIconSize, slotIconSize);
        shieldSlotBounds.setBounds(equipX, shieldSlotY, slotIconSize, slotIconSize);

        drawEquipSlot(weaponSlotBounds, (draggingItem && dragSourceType == Player.EQUIP_WEAPON) ? null : gp.localPlayer().currentWeapon);
        drawEquipSlot(shieldSlotBounds, (draggingItem && dragSourceType == Player.EQUIP_SHIELD) ? null : gp.localPlayer().currentShield);

        //BAG GRID
        final int slotStartX = equipX + slotIconSize + equipBagGap;
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
        g2.setColor(new Color(255, 200, 0, 60));
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
        int boxX = chatLogMargin;
        int boxY = gp.screenHeight - chatLogHeight - chatLogMargin;
        combatLogBounds.setBounds(boxX, boxY, chatLogWidth, chatLogHeight);

        int mx = gp.mouseH.getScaledX();
        int my = gp.mouseH.getScaledY();
        boolean hovering = combatLogBounds.contains(mx, my);

        if(hovering && gp.mouseH.wheelRotation != 0){
            chatScrollOffset -= gp.mouseH.wheelRotation; // scroll up = view older history
            int maxOffset = Math.max(0, chatHistory.size() - chatVisibleLines);
            chatScrollOffset = Math.max(0, Math.min(chatScrollOffset, maxOffset));
            gp.mouseH.wheelRotation = 0;
        }

        //Background only shows while actively interacting — otherwise messages float transparently over gameplay
        boolean boxVisible = hovering || chatScrollOffset > 0;
        if(boxVisible){
            g2.setColor(new Color(0, 0, 0, 80));
            g2.fillRoundRect(boxX, boxY, chatLogWidth, chatLogHeight, 12, 12);
        }

        g2.setFont(jetbrainsMono.deriveFont(14f));
        int lineHeight = 20;
        int textX = boxX + 10;
        int textY = boxY + chatLogHeight - 10;

        int endIndex = chatHistory.size() - chatScrollOffset;
        int startIndex = Math.max(0, endIndex - chatVisibleLines);

        for(int i = endIndex - 1; i >= startIndex; i--){
            ChatMessage msg = chatHistory.get(i);

            float alpha = 1f;
            if(chatScrollOffset == 0 && !hovering && msg.ageTicks > chatFadeStartTicks){
                float fadeProgress = (msg.ageTicks - chatFadeStartTicks) / (float) chatFadeDurationTicks;
                alpha = Math.max(0f, 1f - fadeProgress);
            }

            if(alpha > 0f){
                Color c = msg.color;
                g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int)(alpha * 255)));
                g2.drawString(msg.text, textX, textY);
            }

            textY -= lineHeight;
            msg.ageTicks++; // only ages while it's one of the visible latest lines — history you scroll back to stays fully readable, never fades
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
        g2.drawString(gp.localPlayer().playerClass.equals("mage") ? "MP" : "Rage", textX, textY);
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

        if(gp.localPlayer().playerClass.equals("mage")){
            value = gp.localPlayer().MP + "/" + gp.localPlayer().maxMP;
        } else {
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
        if(local.playerClass.equals("mage")){
            drawStatBar(barX, secondaryY, barWidth, barHeight, local.MP, local.maxMP,
                    new Color(20, 30, 60), new Color(60, 130, 230), "MP");
        } else {
            drawStatBar(barX, secondaryY, barWidth, barHeight, local.rage, local.maxRage,
                    new Color(50, 15, 15), new Color(220, 60, 30), "RAGE");
        }
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

            int mpY = y + hpHeight + 3;
            drawMiniStatBar(barX, mpY, barWidth, mpHeight, p.MP, p.maxMP, new Color(20, 30, 60), new Color(60, 130, 230));

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

        text = "NEW GAME";
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

    private void drawDialogueScreen() {
        //WINDOW
        int x = gp.tileSize * 2;
        int y = gp.tileSize / 2;
        int width = gp.screenWidth - (gp.tileSize * 4);
        int height = gp.tileSize * 5;

        drawSubWindow(x, y, width, height);

        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 20F));
        x += gp.tileSize;
        y += gp.tileSize;

        for(String line: currentDialogue.split("\n")){
            g2.drawString(line, x, y);
            y += 40;
        }

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
        Color color = new Color(0,0,0, 200);
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

    public void option_fullScreenNotification(int frameX, int frameY){
        int textX = frameX + gp.tileSize;
        int textY = frameY + gp.tileSize * 3;

        currentDialogue = "The changes will take\neffect after\nrestarting the game!";

        for(String line: currentDialogue.split("\n")){
            g2.drawString(line, textX, textY);
            textY += 40;
        }

        //BACK
        textY = frameY + gp.tileSize * 9;
        drawMenuButton(fullscreenNotiButtons[0], "BACK", textX, textY,  commandNum == 0 );
        if(commandNum == 0){
            if(gp.keyH.enterPressed || gp.mouseH.leftClicked){
                subState = 0;
            }
        }
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
        g2.drawString("Confirm/Pause",textX, textY);
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
        g2.drawString("ENTER", textX, textY);
        textY += gp.tileSize;
        g2.drawString("E", textX, textY);
        textY += gp.tileSize;
        g2.drawString("F", textX, textY);
        textY += gp.tileSize;
        g2.drawString("C", textX, textY);
        textY += gp.tileSize;
        g2.drawString("ESC", textX, textY);

        //BACK
        textX = frameX + gp.tileSize;
        textY = frameY + gp.tileSize*9;
        drawMenuButton(controlButtons[0], "BACK", textX, textY,  commandNum == 0);
        if(commandNum == 0){
            if(gp.keyH.enterPressed || gp.mouseH.leftClicked){
                subState = 0;
                commandNum = 3;
            }
        }
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

        for(int i = 0; i < gp.localPlayer().skills.length; i++){
            Skill skill = gp.localPlayer().skills[i];
            if(skill == null) continue;

            int x = startX + i * (slotSize + spacing);

            //SLOT BACKGROUND
            g2.setColor(new Color(0, 0, 0, 150));
            g2.fillRoundRect(x, y, slotSize, slotSize, 8, 8);

            //ICON
            if(skill.icon != null){
                g2.drawImage(skill.icon, x + 9, y + 9, 32, 32, null);
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
            boolean insufficientResource = gp.localPlayer().playerClass.equals("mage")
                    ? gp.localPlayer().MP < skill.resourceCost
                    : gp.localPlayer().rage < skill.resourceCost;
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
    }

    private void drawStatusEffects(Player local){
        if(local.statusEffects.isEmpty()) return;

        int iconSize = 32;
        int spacing = 6;
        int x = gp.tileSize / 2;
        int y = gp.tileSize / 2 + 22 + 8 + 22 + 10; // below the HP bar + spacing + MP bar + gap

        for(entity.StatusEffect effect : local.statusEffects){
            BufferedImage icon = local.statusEffectIcons.get(effect.type);

            g2.setColor(new Color(0, 0, 0, 150));
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
            if(weaponSlotBounds.contains(mouseX, mouseY) && gp.localPlayer().currentWeapon != null && !gp.localPlayer().currentWeapon.isPlaceholder){
                draggingItem = true;
                draggedItem = gp.localPlayer().currentWeapon;
                dragSourceType = Player.EQUIP_WEAPON;
                dragSourceIndex = -1;
            }
            else if(shieldSlotBounds.contains(mouseX, mouseY) && gp.localPlayer().currentShield != null && !gp.localPlayer().currentShield.isPlaceholder){
                draggingItem = true;
                draggedItem = gp.localPlayer().currentShield;
                dragSourceType = Player.EQUIP_SHIELD;
                dragSourceIndex = -1;
            }
            else {
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
                action.actionType = "use";
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

        if(weaponSlotBounds.contains(mouseX, mouseY)){
            if(dragSourceType == SLOT_BAG){
                action.actionType = "equip";
                action.sourceIndex = dragSourceIndex;
                action.slotType = Player.EQUIP_WEAPON;
                gp.requestInventoryAction(action);
            }
            return;
        }
        if(shieldSlotBounds.contains(mouseX, mouseY)){
            if(dragSourceType == SLOT_BAG){
                action.actionType = "equip";
                action.sourceIndex = dragSourceIndex;
                action.slotType = Player.EQUIP_SHIELD;
                gp.requestInventoryAction(action);
            }
            return;
        }

        for(int i = 0; i < inventorySlotBounds.length; i++){
            if(inventorySlotBounds[i] != null && inventorySlotBounds[i].contains(mouseX, mouseY)){
                if(dragSourceType == SLOT_BAG){
                    action.actionType = "swap";
                    action.sourceIndex = dragSourceIndex;
                    action.targetIndex = i;
                } else {
                    action.actionType = "unequip_to_bag";
                    action.slotType = dragSourceType;
                    action.targetIndex = i;
                }
                gp.requestInventoryAction(action);
                return;
            }
        }

        if(!inventoryWindowBounds.contains(mouseX, mouseY)){
            if(dragSourceType == SLOT_BAG){
                action.actionType = "drop";
                action.sourceIndex = dragSourceIndex;
            } else {
                action.actionType = "unequip_to_world";
                action.slotType = dragSourceType;
            }
            gp.requestInventoryAction(action);
        }
    }

    private Entity getItemAtPosition(int mouseX, int mouseY){
        if(weaponSlotBounds.contains(mouseX, mouseY)){
            return (gp.localPlayer().currentWeapon != null && !gp.localPlayer().currentWeapon.isPlaceholder) ? gp.localPlayer().currentWeapon : null;
        }
        if(shieldSlotBounds.contains(mouseX, mouseY)){
            return (gp.localPlayer().currentShield != null && !gp.localPlayer().currentShield.isPlaceholder) ? gp.localPlayer().currentShield : null;
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
            gp.isNetworked = false;
            gp.isHost = false;
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
            joinStatusMessage = "Failed to connect: " + e;
        }
    }

    public void cancelJoinAddress(){
        joinStatusMessage = null;
    }

    private void drawDeathScreen(){
        g2.setColor(new Color(0, 0, 0, 180));
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
        Player local = gp.localPlayer();
        local.playerName = pendingCharacterName;
        local.playerClass = (classIndex == 1) ? "mage" : "swordsman";
        local.gender = (genderIndex == 1) ? "female" : "male";

        local.setDefaultValues();
        local.setItems();
        local.getPlayerAttackImage();

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

    private void drawCharacterCreationScreen(){
        g2.setColor(Color.black);
        g2.fillRect(0, 0, gp.screenWidth, gp.screenHeight);

        if(gp.keyH.characterCreationStep == 0){
            drawNameEntryStep();
        } else {
            drawClassGenderStep();
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
        String hint = "Press ENTER to confirm";
        x = getXforCenteredText(hint);
        g2.drawString(hint, x, boxY + boxHeight + 40);
    }

    private void drawClassGenderStep(){
        g2.setFont(jetbrainsMono.deriveFont(Font.BOLD, 30f));
        g2.setColor(Color.white);
        String title = "Choose your class";
        int x = getXforCenteredText(title);
        int y = gp.tileSize * 4;
        g2.drawString(title, x, y);

        g2.setFont(jetbrainsMono.deriveFont(22f));
        String[] classNames = {"Swordsman", "Mage"};
        y += gp.tileSize * 2;
        for(int i = 0; i < classNames.length; i++){
            boolean selected = gp.keyH.selectedClassIndex == i;
            g2.setColor(selected ? new Color(255, 230, 120) : Color.gray);
            String label = (selected ? "> " : "  ") + classNames[i];
            x = getXforCenteredText(label);
            g2.drawString(label, x, y);
            y += 40;
        }

        y += gp.tileSize;
        String[] genderNames = {"Male", "Female"};
        for(int i = 0; i < genderNames.length; i++){
            boolean selected = gp.keyH.selectedGenderIndex == i;
            g2.setColor(selected ? new Color(255, 230, 120) : Color.gray);
            String label = (selected ? "> " : "  ") + genderNames[i];
            x = getXforCenteredText(label);
            g2.drawString(label, x, y);
            y += 40;
        }

        g2.setFont(jetbrainsMono.deriveFont(14f));
        g2.setColor(Color.white);
        String hint = "A/D: class   W/S: gender   ENTER: confirm";
        x = getXforCenteredText(hint);
        g2.drawString(hint, x, y + 40);
    }

    private String capitalize(String s){
        if(s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
