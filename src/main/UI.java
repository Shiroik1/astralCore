package main;

import entity.Entity;
import entity.Monster;
import entity.Player;

import net.GameEventType;

import object.OBJ_crystal;
import object.OBJ_heart;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;

public class UI {
    Gamepanel gp;
    Graphics2D g2;

    // FONTS
    Font solomonKey;
    Font jetbrainsMono;

    public boolean messageOn = false;
    public boolean gameFinished = false;
    public String pendingCharacterName = "";
    public final CharacterSlots characterSlots;
    public final CharacterCustomization customization;
    public final ui.GameUI game;
    private String creationError;
    public int commandNum = 0;

    private UIButton[] titleButtons = {
        new UIButton(), new UIButton(), new UIButton(), new UIButton()
    };
    private UIButton[] optionTopButtons = {
        new UIButton(),
        new UIButton(),
        new UIButton(),
        new UIButton(),
        new UIButton(),
        new UIButton()
    };
    private UIButton[] displayButtons = {
        new UIButton(), new UIButton(), new UIButton(), new UIButton()
    };
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

    // CHAT MESSAGE
    private static class ChatMessage {
        String text;
        Color color;
        int ageTicks = 0;

        /**
         * Creates a ChatMessage instance and initializes the state required by its collaborators.
         */
        ChatMessage(String text, Color color) {
            this.text = text;
            this.color = color;
        }
    }

    private ArrayList<ChatMessage> chatHistory = new ArrayList<>();
    private int chatScrollOffset = 0; // 0 = live view; >0 = scrolled back into history
    private Rectangle combatLogBounds = new Rectangle();
    private boolean combatLogHovered = false;

    private final int chatLogWidth = 420;
    private final int chatLogHeight = 160;
    private final int chatLogMargin = 16;
    private final int chatVisibleLines = 7;
    private final int chatFadeStartTicks = 300; // ~5s before an unread message starts fading
    private final int chatFadeDurationTicks = 60; // ~1s fade-out
    private final int chatMaxHistory = 200; // scrollback cap
    private String joinStatusMessage = null;

    /** Creates a UI instance and initializes the state required by its collaborators. */
    public UI(Gamepanel gp) {
        this.gp = gp;
        game = new ui.GameUI(gp);
        characterSlots = new CharacterSlots(gp);
        customization = new CharacterCustomization(gp);

        try {
            InputStream is = getClass().getResourceAsStream("/font/SolomonsKey.ttf");
            solomonKey = Font.createFont(Font.TRUETYPE_FONT, is);
            InputStream is2 = getClass().getResourceAsStream("/font/JetBrainsMono-Regular.ttf");
            jetbrainsMono = Font.createFont(Font.TRUETYPE_FONT, is2);
        } catch (FontFormatException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // CREATE HUD OBJECTS
        Entity heart = new OBJ_heart(gp);
        Entity crystal = new OBJ_crystal(gp);
    }

    /** Adds the message for UI. */
    public void addMessage(String text) {
        addMessage(text, Color.white);
    }

    /** Adds the message for UI. */
    public void addMessage(String text, Color color) {
        chatHistory.add(new ChatMessage(text, color));
        if (chatHistory.size() > chatMaxHistory) {
            chatHistory.remove(0);
        }
        chatScrollOffset = 0; // new activity snaps the view back to live, like WoW
    }

    /** Advances this component for one simulation tick. */
    public void update(int mouseX, int mouseY, boolean clicked) {
        if (gp.gameState == gp.playState && game.update(mouseX, mouseY, clicked)) return;
        if (gp.gameState == gp.characterState) {
            int step = gp.keyH.characterCreationStep;
            if (gp.keyH.characterCreationStep == 2) customization.update(mouseX, mouseY, clicked);
            else if (gp.keyH.characterCreationStep == 0 && clicked) {
                if (nameNext().contains(mouseX, mouseY)) confirmCharacterName();
                else if (nameBack().contains(mouseX, mouseY)) {
                    gp.keyH.enteringCharacterName = false;
                    characterSlots.open();
                }
            }
            if (step != 1) gp.mouseH.leftClicked = false;
            return;
        }
        if (gp.gameState == gp.characterSelectState) {
            characterSlots.update(mouseX, mouseY, clicked);
            gp.mouseH.leftClicked = false;
            return;
        }
        if (gp.gameState == gp.playState) {
            playTime += 1.0 / 60;
            updateCombatLog(mouseX, mouseY);
        }

        Player local = gp.localPlayer();
        if (local != null && local.isDead && gp.gameState == gp.playState && !gp.optionsOpen) {
            int hovered = processButtonHover(new UIButton[] {respawnButton}, mouseX, mouseY);
            if (hovered != -1 && clicked) {
                gp.broadcastPlayerEvent(
                        local.playerId,
                        GameEventType.RESPAWN_REQUEST,
                        "Player " + (local.playerId + 1) + " has respawned.",
                        null);
            }
            gp.mouseH.leftClicked = false;
            return;
        }

        if (gp.gameState == gp.titleState) {
            if (gp.keyH.enteringNetworkAddress) {
                gp.mouseH.leftClicked = false;
                return;
            }
            int hovered = processButtonHover(titleButtons, mouseX, mouseY);
            if (hovered != -1) {
                commandNum = hovered;
                if (clicked) {
                    confirmTitleSelection(hovered);
                }
            }
            gp.mouseH.leftClicked = false;
        } else if (gp.gameState == gp.playState && gp.optionsOpen) {
            int hovered = processButtonHover(currentOptionButtons(), mouseX, mouseY);
            if (hovered != -1) {
                commandNum = hovered;
                if (clicked) {
                    activateOptionItem(hovered);
                }
            }
            gp.mouseH.leftClicked = false; // the options overlay swallows every click
        } else if (local != null && local.merchantOpen) {
            gp.merchant.update(mouseX, mouseY, clicked);
            gp.mouseH.leftClicked = false;
        }
    }

    /** Resolves the current option buttons result used by UI. */
    private UIButton[] currentOptionButtons() {
        return switch (subState) {
            case 0 -> optionTopButtons;
            case 1 -> displayButtons;
            case 2 -> controlButtons;
            case 3 -> endgameConfirmButtons;
            default -> new UIButton[0];
        };
    }

    /** Performs the confirm title selection operation for UI. */
    void confirmTitleSelection(int index) {
        joinStatusMessage = null;
        if (index == 0 || index == 1 || index == 2) {
            pendingTitleAction = index;
            characterSlots.open();
        }
        if (index == 3) {
            gp.requestClose();
        }
    }

    /** Renders this component into the supplied Java2D graphics context. */
    public void draw(Graphics2D g2) throws IOException {
        long performanceStart = gp.performance.start();
        try {

            this.g2 = g2;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(
                    RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(
                    RenderingHints.KEY_FRACTIONALMETRICS,
                    RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g2.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

            g2.setFont(jetbrainsMono);
            g2.setColor(Color.white);

            // TITLE STATE
            if (gp.gameState == gp.titleState) {
                drawTitleScreen();
            }

            // CHARACTER StATE
            if (gp.gameState == gp.characterState) {
                drawCharacterCreationScreen();
            }
            if (gp.gameState == gp.characterSelectState) characterSlots.draw(g2, jetbrainsMono);

            // PLAY STATE
            if (gp.gameState == gp.playState) {

                // Toggle debug menu with [ T ] key
                if (gp.keyH.showDebug) {
                    drawDebug(g2, playTime);
                }

                ui.UITheme.baseFont(jetbrainsMono);
                game.status(g2);
                drawBossHealth();
                if (gp.arena.training) {
                    ui.UITheme.text(
                            g2,
                            "Combat Arena · Invincibility "
                                    + (gp.arena.invulnerable ? "ON" : "OFF")
                                    + "   F6 toggle   F7 refill   F8 wave   /arena leave",
                            24,
                            gp.screenHeight - 95,
                            13,
                            Color.WHITE);
                } else drawQuestTracker();
                Player local = gp.localPlayer();
                if (local != null) {
                    drawStatusEffects(local);
                    drawOtherPlayersHUD(local);
                }
                drawCombatLog();
                gp.grassEditor.drawHud(g2, jetbrainsMono);
                gp.buildings.drawHud(g2, jetbrainsMono);
                game.draw(g2, jetbrainsMono);
                gp.slimeSpawner.drawDebug(g2);
                gp.slimeSpawner.drawSkeletonDebug(g2);
                gp.performance.draw(gp, g2);
                if (local != null && local.merchantOpen) gp.merchant.draw(g2);

                if (local != null && local.isDead) {
                    drawDeathScreen();
                }
                if (local != null && local.inDialogue) {
                    drawPersonalDialogue(
                            local.getRevealedDialogueText(), local.isDialogueFullyRevealed());
                }
            }

            if (gp.gameState == gp.playState) {
                if (gp.paused) {
                    drawPauseScreen();
                }
                if (gp.optionsOpen) {
                    drawOptionScreen();
                }
            }

        } finally {
            gp.performance.end(main.PerformanceMetrics.Section.UI, performanceStart);
        }
    }

    /** Draws the combat log for UI. */
    private void drawCombatLog() {
        int boxX = combatLogBounds.x;
        int boxY = combatLogBounds.y;
        boolean hovering = combatLogHovered;
        boolean chatActive = gp.keyH.enteringChat;

        // Background only shows while actively interacting
        boolean boxVisible = hovering || chatActive || chatScrollOffset > 0;
        if (boxVisible) {
            g2.setColor(Color.BLACK);
            g2.fillRoundRect(boxX, boxY, chatLogWidth, chatLogHeight, 12, 12);
        }

        g2.setFont(jetbrainsMono.deriveFont(14f));
        int lineHeight = 20;
        int textX = boxX + 10;
        int textY = boxY + chatLogHeight - 10;

        int endIndex = chatHistory.size() - chatScrollOffset;
        int startIndex = Math.max(0, endIndex - chatVisibleLines);

        for (int i = endIndex - 1; i >= startIndex; i--) {
            ChatMessage msg = chatHistory.get(i); // msg only exists from here, inside the loop

            float alpha = 1f;
            if (chatScrollOffset == 0
                    && !hovering
                    && !chatActive
                    && msg.ageTicks > chatFadeStartTicks) {
                float fadeProgress =
                        (msg.ageTicks - chatFadeStartTicks) / (float) chatFadeDurationTicks;
                alpha = Math.max(0f, 1f - fadeProgress);
            }

            if (alpha > 0f) {
                Color c = msg.color;
                g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (alpha * 255)));
                g2.drawString(msg.text, textX, textY);
            }

            textY -= lineHeight;
        }
    }

    private Monster hudBoss;
    private Monster smoothedBoss;
    private float bossHpDisplay;

    /**
     * An encounter begins when its boss enters view and remains visible until that boss is gone.
     */
    private Monster findBossForHud() {
        if (hudBoss != null && hudBoss.showBossHealth()) {
            for (Monster monster : gp.monster) if (monster == hudBoss) return hudBoss;
        }
        hudBoss = null;
        Player camera = gp.localPlayer();
        if (camera == null) return null;
        double nearest = Double.MAX_VALUE;
        for (Monster monster : gp.monster) {
            if (monster == null || !monster.showBossHealth()) continue;
            int x =
                    monster.worldX
                            - camera.worldX
                            + camera.screenX
                            + monster.solidArea.x
                            + monster.solidArea.width / 2;
            int y =
                    monster.worldY
                            - camera.worldY
                            + camera.screenY
                            + monster.solidArea.y
                            + monster.solidArea.height / 2;
            if (!gp.quests.inDungeon()
                    && (x < 0 || x > gp.screenWidth || y < 0 || y > gp.screenHeight)) continue;
            double distance = Math.hypot(x - gp.screenWidth / 2.0, y - gp.screenHeight / 2.0);
            if (distance < nearest) {
                hudBoss = monster;
                nearest = distance;
            }
        }
        return hudBoss;
    }

    /** Draws the quest tracker for UI. */
    private void drawQuestTracker() {
        int x = gp.screenWidth - 340, y = 110, w = 316, h = 108;
        ui.UITheme.panel(g2, new Rectangle(x, y, w, h));
        g2.setFont(ui.UITheme.font(14));
        g2.setColor(Color.WHITE);
        g2.drawString(gp.quests.definition().title(), x + 14, y + 25);
        g2.setFont(jetbrainsMono.deriveFont(Font.PLAIN, 12f));
        g2.setColor(Color.WHITE);
        String objective = gp.quests.objective();
        if (g2.getFontMetrics().stringWidth(objective) > w - 28) {
            int split = objective.lastIndexOf(' ', objective.length() / 2 + 6);
            g2.drawString(objective.substring(0, split), x + 14, y + 47);
            g2.drawString(objective.substring(split + 1), x + 14, y + 63);
        } else g2.drawString(objective, x + 14, y + 51);
        g2.setColor(new Color(169, 192, 198));
        String location =
                gp.quests.inDungeon()
                        ? "Well Depths  |  Ladder: return [F]"
                        : world.InnInterior.contains(gp, gp.localPlayer())
                                ? "Astravale Inn  |  Door: return [F]"
                                : "Astravale";
        g2.drawString(location, x + 14, y + 88);
    }

    /** Draws the boss health for UI. */
    private void drawBossHealth() {
        Monster boss = findBossForHud();
        if (boss == null) return;
        if (smoothedBoss != boss) {
            smoothedBoss = boss;
            bossHpDisplay = boss.HP;
        }
        bossHpDisplay += (boss.HP - bossHpDisplay) * .15f;
        int width = 460, x = (gp.screenWidth - width) / 2, y = 48;
        String name = boss.name == null ? "Boss" : boss.name;
        ui.UITheme.text(g2, name, x, y - 10, 14, ui.UITheme.TEXT);
        ui.UITheme.bar(
                g2,
                new Rectangle(x, y, width, 18),
                bossHpDisplay,
                boss.maxHP,
                ui.UITheme.HP,
                boss.HP + " / " + boss.maxHP);
    }

    /** Draws the paladin resolve for UI. */
    private void drawPaladinResolve(Player local, int x, int y) {
        g2.setFont(jetbrainsMono.deriveFont(Font.BOLD, 12f));
        g2.setColor(Color.WHITE);
        g2.drawString("RESOLVE", x, y + 15);
        for (int i = 0; i < local.maxResolve; i++) {
            int cx = x + 77 + i * 22;
            var pip =
                    new Polygon(
                            new int[] {cx - 7, cx, cx + 7, cx + 5, cx, cx - 5},
                            new int[] {y + 4, y + 2, y + 4, y + 14, y + 18, y + 14},
                            6);
            g2.setColor(i < local.resolve ? new Color(0xF3C98A) : new Color(0x344355));
            g2.fill(pip);
            g2.setStroke(new BasicStroke(1.5f));
            g2.setColor(new Color(0xF1DEB9));
            g2.draw(pip);
        }
        if (local.paladin.guarding()) {
            g2.setColor(new Color(0xB4DBE9));
            g2.drawString("GUARDING", x + 205, y + 15);
        }
        if (new Rectangle(x, y, 184, 22).contains(gp.mouseH.getScaledX(), gp.mouseH.getScaledY()))
            gp.merchant.tooltip(
                    g2,
                    java.util.List.of(
                            "Resolve",
                            "Hold right click to Guard.",
                            "Each mitigated frontal hit grants one Resolve."),
                    x,
                    y + 30);
    }

    /** Draws the other players hud for UI. */
    private void drawOtherPlayersHUD(Player local) {
        int startX = gp.tileSize / 2;
        int y = 154; // status effects row baseline
        y += 32 + 14; // below the status icon row, plus a gap

        for (Player p : gp.players) {
            if (p == null || p == local) continue;

            int barWidth = 140;
            int hpHeight = 14;
            int mpHeight = 12;

            g2.setFont(jetbrainsMono.deriveFont(Font.BOLD, 13f));
            g2.setColor(Color.white);
            String label = "P" + (p.playerId + 1) + (p.isDead ? " (Down)" : "");
            g2.drawString(label, startX, y + hpHeight - 3);

            int barX = startX + 60;
            drawMiniStatBar(
                    barX,
                    y,
                    barWidth,
                    hpHeight,
                    p.HP,
                    p.maxHP,
                    new Color(60, 20, 20),
                    ui.UITheme.HP);

            int secondY = y + hpHeight + 3;
            if (p.usesMana()) {
                drawMiniStatBar(
                        barX,
                        secondY,
                        barWidth,
                        mpHeight,
                        p.MP,
                        p.maxMP,
                        new Color(20, 30, 60),
                        ui.UITheme.MP);
            } else if (p.isPaladin()) {
                drawMiniStatBar(
                        barX,
                        secondY,
                        barWidth,
                        mpHeight,
                        p.resolve,
                        p.maxResolve,
                        new Color(0x344355),
                        new Color(0xF3C98A));
            } else {
                drawMiniStatBar(
                        barX,
                        secondY,
                        barWidth,
                        mpHeight,
                        p.rage,
                        p.maxRage,
                        ui.UITheme.SECONDARY,
                        ui.UITheme.ACCENT);
            }

            y += hpHeight + 3 + mpHeight + 14; // advance to the next player's row
        }
    }

    /** Draws the mini stat bar for UI. */
    private void drawMiniStatBar(
            int x,
            int y,
            int width,
            int height,
            int current,
            int max,
            Color bgFill,
            Color barFill) {
        ui.UITheme.bar(g2, new Rectangle(x, y, width, height), current, max, barFill, null);
    }

    /** Draws the title screen for UI. */
    private void drawTitleScreen() {
        g2.setFont(solomonKey.deriveFont(Font.BOLD, 90F));
        String text = "AstralCore";
        int x = getXforCenteredText(text);
        int y = gp.tileSize * 5;

        g2.setColor(Color.gray);
        g2.drawString(text, x + 10, y + 10);
        g2.setColor(Color.white);
        g2.drawString(text, x, y);

        x = gp.screenWidth / 2 - (gp.tileSize * 2) / 2;
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

        // NETWORK STATUS / INPUT — anchored to the bottom of the screen, independent of menu length
        int networkUIY = gp.screenHeight - 100;

        if (joinStatusMessage != null) {
            g2.setFont(jetbrainsMono.deriveFont(16f));
            g2.setColor(Color.red);
            int mx = getXforCenteredText(joinStatusMessage);
            g2.drawString(joinStatusMessage, mx, networkUIY);
        }

        if (gp.keyH.enteringNetworkAddress) {
            drawJoinAddressInput(networkUIY + 20);
        }
    }

    /** Draws the join address input for UI. */
    private void drawJoinAddressInput(int y) {
        int boxWidth = 340;
        int boxHeight = 36;
        int boxX = gp.screenWidth / 2 - boxWidth / 2;

        drawSubWindow(boxX, y, boxWidth, boxHeight);

        g2.setFont(jetbrainsMono.deriveFont(16f));
        g2.setColor(Color.white);
        String label = "Host IP  (Enter to join, Esc to cancel)";
        g2.drawString(label, getXforCenteredText(label), y - 10);

        String display = gp.keyH.networkAddressInput.toString();
        if ((System.currentTimeMillis() / 400) % 2 == 0) {
            display += "_";
        }
        g2.drawString(display, boxX + 12, y + 24);
    }

    /** Draws the personal dialogue for UI. */
    private void drawPersonalDialogue(String text, boolean showArrow) {
        int x = gp.tileSize * 2;
        int y = gp.tileSize / 2;
        int width = gp.screenWidth - (gp.tileSize * 4);
        int height = gp.tileSize * 5;

        drawSubWindow(x, y, width, height);

        g2.setFont(jetbrainsMono.deriveFont(Font.PLAIN, 20F));
        int textX = x + gp.tileSize;
        int textY = y + gp.tileSize;

        for (String line : text.split("\n")) {
            g2.drawString(line, textX, textY);
            textY += 40;
        }

        if (showArrow) {
            drawContinueArrow(x + width - 50, y + height - 40);
        }
    }

    /** Draws the continue arrow for UI. */
    private void drawContinueArrow(int baseX, int baseY) {
        double bounce = Math.abs(Math.sin(System.currentTimeMillis() / 200.0)) * 6;
        int ax = baseX;
        int ay = (int) (baseY + bounce);

        int[] xs = {ax, ax + 14, ax + 7};
        int[] ys = {ay, ay, ay + 10};

        g2.setColor(Color.white);
        g2.fillPolygon(xs, ys, 3);
    }

    /** Draws the sub window for UI. */
    public void drawSubWindow(int x, int y, int width, int height) {
        Color color = Color.BLACK;
        g2.setColor(color);
        g2.fillRoundRect(x, y, width, height, 35, 35);

        color = new Color(255, 255, 255);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(5));
        g2.drawRoundRect(x + 5, y + 5, width - 10, height - 10, 25, 25);
    }

    /** Draws the pause screen for UI. */
    public void drawPauseScreen() {
        String text = "PAUSE";

        int x = getXforCenteredText(text);
        int y = gp.screenHeight / 2;

        g2.drawString(text, x, y);
    }

    /** Draws the option screen for UI. */
    private void drawOptionScreen() {
        g2.setColor(Color.white);
        g2.setFont(g2.getFont().deriveFont(15f));

        int frameWidth = subState == 1 ? 576 : gp.tileSize * 8;
        int frameX = (gp.screenWidth - frameWidth) / 2;
        int frameY = gp.tileSize;
        int frameHeight = gp.tileSize * 10;
        drawSubWindow(frameX, frameY, frameWidth, frameHeight);

        switch (subState) {
            case 0 -> option_top(frameX, frameY);
            case 1 -> option_display(frameX, frameY);
            case 2 -> option_control(frameX, frameY);
            case 3 -> option_endgameConfirm(frameX, frameY);
        }
    }

    /** Performs the option top operation for UI. */
    private void option_top(int frameX, int frameY) {
        int textX;
        int textY;
        String text = "OPTIONS";

        // TITLE
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

        textX = frameX + gp.tileSize * 5;
        textY = frameY + gp.tileSize * 2 + 24;
        g2.setFont(jetbrainsMono.deriveFont(13f));
        g2.drawString(gp.fullScreenOn ? "Fullscreen" : "Windowed", textX, textY + 17);
        // MUSIC VOLUME
        textY += gp.tileSize;
        g2.drawRect(textX, textY, 120, 20);
        int volumeWidth = 24 * gp.music.volumeScale;
        g2.fillRect(textX, textY, volumeWidth, 20);

        // SOUND EFFECT VOLUME
        textY += gp.tileSize;
        g2.drawRect(textX, textY, 120, 20);
        volumeWidth = 24 * gp.soundEffect.volumeScale;
        g2.fillRect(textX, textY, volumeWidth, 20);
    }

    /** Performs the option display operation for UI. */
    private void option_display(int frameX, int frameY) {
        if (displayDraftResolution == null) displayDraftResolution = gp.display.selected();
        g2.setFont(jetbrainsMono.deriveFont(Font.BOLD, 22f));
        g2.setColor(Color.white);
        String title = "DISPLAY";
        g2.drawString(title, getXforCenteredText(title), frameY + 52);
        int x = frameX + 48, y = frameY + 118;
        g2.setFont(jetbrainsMono.deriveFont(17f));
        drawMenuButton(
                displayButtons[0],
                "Mode: " + (displayDraftFullscreen ? "Fullscreen" : "Windowed"),
                x,
                y,
                commandNum == 0);
        String nativeLabel =
                displayDraftResolution.equals(gp.display.nativeResolution()) ? " (Native)" : "";
        drawMenuButton(
                displayButtons[1],
                "Resolution: < " + displayDraftResolution + nativeLabel + " >",
                x,
                y + 64,
                commandNum == 1);
        g2.setFont(jetbrainsMono.deriveFont(13f));
        g2.setColor(Color.lightGray);
        g2.drawString("A/D changes the selected setting", x, y + 112);
        g2.drawString("Resolutions come from this display.", x, y + 139);
        g2.setFont(jetbrainsMono.deriveFont(18f));
        drawMenuButton(displayButtons[2], "Apply", x, y + 216, commandNum == 2);
        drawMenuButton(displayButtons[3], "Back", x, y + 280, commandNum == 3);
    }

    /** Performs the adjust display selection operation for UI. */
    public void adjustDisplaySelection(int delta) {
        if (subState != 1) return;
        if (commandNum == 0) displayDraftFullscreen = !displayDraftFullscreen;
        if (commandNum == 1)
            displayDraftResolution = gp.display.cycle(displayDraftResolution, delta);
    }

    /** Performs the option endgame confirm operation for UI. */
    private void option_endgameConfirm(int frameX, int frameY) {
        int textX = frameX + gp.tileSize;
        int textY = frameY + gp.tileSize * 3;

        String message = "Save your character\nand return to the\ntitle screen?";
        for (String line : message.split("\n")) {
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

    /** Performs the option control operation for UI. */
    public void option_control(int frameX, int frameY) {
        g2.setFont(ui.UITheme.font(19));
        g2.setColor(ui.UITheme.TEXT);
        g2.drawString("Controls", frameX + 40, frameY + 48);
        String[][] rows = {
            {"Move", "W A S D"},
            {"Attack / cast", "Left mouse"},
            {"Interact / gather", "F"},
            {"Inventory & Crafting", "E / I / K"},
            {"Character", "C"},
            {"Item shortcuts", "1–9 / 0 / Wheel"},
            {"Weapon abilities", "Shift + 1–5"},
            {"Recover station", "G"},
            {"Chat / pause", "Enter / P"},
            {"Close / options", "Esc"}
        };
        for (int i = 0; i < rows.length; i++) {
            ui.UITheme.text(
                    g2, rows[i][0], frameX + 32, frameY + 86 + i * 30, 12, ui.UITheme.MUTED);
            ui.UITheme.text(
                    g2, rows[i][1], frameX + 255, frameY + 86 + i * 30, 12, ui.UITheme.TEXT);
        }
        drawMenuButton(
                controlButtons[0], "Back", frameX + 40, frameY + gp.tileSize * 9, commandNum == 0);
    }

    /** Returns the xfor centered text for UI. */
    public int getXforCenteredText(String text) {
        int length = (int) g2.getFontMetrics().getStringBounds(text, g2).getWidth();
        int x = gp.screenWidth / 2 - length / 2;
        return x;
    }

    /** Returns the xfor right align text for UI. */
    public int getXforRightAlignText(String text, int tailX) {
        int length = (int) g2.getFontMetrics().getStringBounds(text, g2).getWidth();
        int x = tailX - length;
        return x;
    }

    /** Draws the debug for UI. */
    public void drawDebug(Graphics2D g2, double playTime) {
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
        y += lineHeight;
        g2.drawString("Clock: " + gp.lighting.cycle.clock(), x, y);
    }

    /** Resolves the process button hover result used by UI. */
    private int processButtonHover(UIButton[] buttons, int mouseX, int mouseY) {
        int hoveredIndex = -1;

        for (int i = 0; i < buttons.length; i++) {
            boolean hovered = buttons[i].contains(mouseX, mouseY);
            buttons[i].hovered = hovered;

            if (hovered) {
                hoveredIndex = i;
                if (buttons[i] != lastHoveredButton) {
                    gp.playSE(8);
                    lastHoveredButton = buttons[i];
                }
            }
        }

        return hoveredIndex;
    }

    /** Draws the menu button for UI. */
    private void drawMenuButton(UIButton button, String text, int x, int y, boolean selected) {
        FontMetrics fm = g2.getFontMetrics();
        int width = (int) fm.getStringBounds(text, g2).getWidth();
        int ascent = fm.getAscent();
        int descent = fm.getDescent();

        // Padded Hitbox - a few px larger than the text itself, easier to click
        button.setBounds(x - 10, y - ascent - 4, width + 20, ascent + descent + 8);

        if (button.hovered || selected) {
            g2.setColor(new Color(255, 255, 255, 60));
            g2.fillRoundRect(
                    button.bounds.x,
                    button.bounds.y,
                    button.bounds.width,
                    button.bounds.height,
                    10,
                    10);
        }

        g2.setColor(Color.white);
        g2.drawString(text, x, y);
    }

    /** Draws the status effects for UI. */
    private void drawStatusEffects(Player local) {
        if (local.statusEffects.isEmpty()) return;

        int iconSize = 32;
        int spacing = 6;
        int x = gp.tileSize / 2;
        int y = 154; // below status and existing combat-resource indicators

        for (entity.StatusEffect effect : local.statusEffects) {
            BufferedImage icon = local.statusEffectIcons.get(effect.type);
            if (icon == null) icon = entity.PriestCombat.statusIcon(effect.type);

            g2.setColor(Color.BLACK);
            g2.fillRoundRect(x, y, iconSize, iconSize, 6, 6);

            if (icon != null) {
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

    /** Starts the host flow for UI. */
    public void startHostFlow() {
        Log.debug("[UI] startHostFlow() called");
        try {
            gp.hostGame(Gamepanel.NETWORK_PORT);
            gp.gameState = gp.playState;
            gp.playMusic(0);
            addMessage(
                    "Hosting on port " + Gamepanel.NETWORK_PORT + " — waiting for players to join.",
                    new Color(150, 220, 255));
        } catch (Exception e) { // widen from IOException — catch anything that goes wrong here
            e.printStackTrace();
            joinStatusMessage = "Failed to host: " + e;
            gp.shutdownNetwork();
        }
    }

    /** Starts the join flow for UI. */
    public void startJoinFlow() {
        Log.debug("[UI] startJoinFlow() called");
        gp.keyH.beginEnteringNetworkAddress();
    }

    /** Performs the submit join address operation for UI. */
    public void submitJoinAddress(String address) {
        Log.debug("[UI] submitJoinAddress() called with address='" + address + "'");
        if (address.isEmpty()) {
            joinStatusMessage = "Enter a host IP address.";
            return;
        }
        try {
            gp.joinGame(address, Gamepanel.NETWORK_PORT);
            gp.gameState = gp.playState;
            gp.playMusic(0);
        } catch (Exception e) {
            e.printStackTrace();
            gp.shutdownNetwork();
            joinStatusMessage = "Failed to connect: " + e;
        }
    }

    /** Cancels the join address for UI. */
    public void cancelJoinAddress() {
        joinStatusMessage = null;
    }

    /** Draws the death screen for UI. */
    private void drawDeathScreen() {
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, gp.screenWidth, gp.screenHeight);

        g2.setFont(jetbrainsMono.deriveFont(Font.BOLD, 60f));
        g2.setColor(Color.red);
        String text = "YOU DIED";
        int x = getXforCenteredText(text);
        int y = gp.screenHeight / 2 - 60;
        g2.drawString(text, x, y);

        g2.setColor(Color.white);
        g2.setFont(jetbrainsMono.deriveFont(Font.PLAIN, 26f));
        text = "Return to Spawn";
        x = getXforCenteredText(text);
        y = gp.screenHeight / 2 + 20;
        drawMenuButton(respawnButton, text, x, y, false);
    }

    /** Performs the confirm character creation operation for UI. */
    public void confirmCharacterCreation(int classIndex, int genderIndex) {
        classIndex = 0; // Legacy API field only; new characters have no fixed class.
        if (characterSlots.creationSlot >= 0) {
            try {
                gp.saves.create(
                        characterSlots.creationSlot,
                        pendingCharacterName,
                        classIndex,
                        genderIndex,
                        customization.style,
                        customization.color);
                characterSlots.creationSlot = -1;
                creationError = null;
                startSelectedCharacter();
            } catch (Exception e) {
                creationError = "Could not create character: " + e.getMessage();
                gp.keyH.characterCreationStep = 2;
            }
            return;
        }
        Player local = gp.localPlayer();
        local.playerName = pendingCharacterName;
        local.setCharacterDesign("warrior", genderIndex == 1 ? "female" : "male");

        local.setDefaultValues();
        if (local.usesMana()) {
            local.getMagePlayerImage();
        } else {
            local.getPlayerImage();
        }
        local.setItems();
        local.refreshWeaponAnimation();

        startSelectedCharacter();
    }

    /** Starts the selected character for UI. */
    public void startSelectedCharacter() {
        switch (pendingTitleAction) {
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

    /** Clears the creation error for UI. */
    public void clearCreationError() {
        creationError = null;
    }

    /** Draws the character creation screen for UI. */
    private void drawCharacterCreationScreen() {
        g2.setColor(Color.black);
        g2.fillRect(0, 0, gp.screenWidth, gp.screenHeight);

        if (gp.keyH.characterCreationStep == 0) {
            drawNameEntryStep();
        } else customization.draw(g2, jetbrainsMono);
        if (creationError != null) {
            g2.setColor(new Color(245, 160, 150));
            g2.setFont(jetbrainsMono.deriveFont(12f));
            String text = creationError;
            while (text.length() > 4 && g2.getFontMetrics().stringWidth(text) > gp.screenWidth - 48)
                text = text.substring(0, text.length() - 4) + "...";
            g2.drawString(text, 24, gp.screenHeight - 10);
        }
    }

    /** Draws the name entry step for UI. */
    private void drawNameEntryStep() {
        g2.setFont(jetbrainsMono.deriveFont(Font.BOLD, 30f));
        g2.setColor(Color.white);
        String title = "Name your character";
        int x = getXforCenteredText(title);
        int y = gp.tileSize * 5;
        g2.drawString(title, x, y);

        int boxWidth = 340;
        int boxHeight = 40;
        int boxX = gp.screenWidth / 2 - boxWidth / 2;
        int boxY = y + gp.tileSize;
        drawSubWindow(boxX, boxY, boxWidth, boxHeight);

        g2.setFont(jetbrainsMono.deriveFont(18f));
        String display = gp.keyH.characterNameInput.toString();
        if ((System.currentTimeMillis() / 400) % 2 == 0) {
            display += "_";
        }
        g2.drawString(display, boxX + 12, boxY + 26);

        g2.setFont(jetbrainsMono.deriveFont(14f));
        String hint = "Type a name, then choose Next";
        x = getXforCenteredText(hint);
        g2.drawString(hint, x, boxY + boxHeight + 40);
        drawCreationButton(nameNext(), "Next");
        drawCreationButton(nameBack(), "Back");
    }

    /** Resolves the name next result used by UI. */
    private Rectangle nameNext() {
        return new Rectangle(gp.screenWidth / 2 + 10, 400, 160, 44);
    }

    /** Resolves the name back result used by UI. */
    private Rectangle nameBack() {
        return new Rectangle(gp.screenWidth / 2 - 170, 400, 160, 44);
    }

    /** Draws the creation button for UI. */
    private void drawCreationButton(Rectangle r, String label) {
        WindowStyle.draw(g2, r.x, r.y, r.width, r.height);
        g2.setColor(Color.WHITE);
        g2.setFont(jetbrainsMono.deriveFont(15f));
        g2.drawString(
                label, r.x + (r.width - g2.getFontMetrics().stringWidth(label)) / 2, r.y + 28);
    }

    /** Performs the confirm character name operation for UI. */
    public void confirmCharacterName() {
        String name = gp.keyH.characterNameInput.toString().trim();
        pendingCharacterName = name.isEmpty() ? "Adventurer" : name;
        gp.keyH.enteringCharacterName = false;
        gp.keyH.selectedClassIndex = 0;
        customization.open();
    }

    /** Performs the show title message operation for UI. */
    public void showTitleMessage(String message) {
        joinStatusMessage = message;
    }

    /** Performs the show save error operation for UI. */
    public void showSaveError(String message) {
        joinStatusMessage = message;
        characterSlots.showMessage(message);
        if (gp.gameState == gp.characterState) creationError = message;
        addMessage(message, Color.RED);
    }

    /** Performs the open options operation for UI. */
    public void openOptions() {
        subState = 0;
        commandNum = 0;
        gp.optionsOpen = true;
    }

    /** Performs the close options operation for UI. */
    public void closeOptions() {
        gp.optionsOpen = false;
        subState = 0;
        commandNum = 0;
    }

    /** Performs the activate option item operation for UI. */
    private void activateOptionItem(int index) {
        switch (subState) {
            case 0 -> {
                switch (index) {
                    case 0 -> {
                        displayDraftFullscreen = gp.fullScreenOn;
                        displayDraftResolution = gp.display.selected();
                        subState = 1;
                        commandNum = 0;
                    }
                    case 3 -> {
                        subState = 2;
                        commandNum = 0;
                    }
                    case 4 -> {
                        subState = 3;
                        commandNum = 0;
                    }
                    case 5 -> closeOptions();
                        // 1 and 2 are the volume rows: adjusted with A/D, nothing to activate
                }
            }
            case 1 -> {
                if (index <= 1) adjustDisplaySelection(1);
                if (index == 2) {
                    gp.display.apply(displayDraftFullscreen, displayDraftResolution);
                    gp.config.saveConfigSafe();
                    subState = 0;
                    commandNum = 0;
                }
                if (index == 3) {
                    subState = 0;
                    commandNum = 0;
                }
            }
            case 2 -> {
                subState = 0;
                commandNum = 3;
            }
            case 3 -> {
                if (index == 0) {
                    if (gp.returnToTitle()) closeOptions();
                } else {
                    subState = 0;
                    commandNum = 4;
                }
            }
        }
    }

    /** Performs the confirm option selection operation for UI. */
    public void confirmOptionSelection() {
        activateOptionItem(commandNum);
    }

    /** Updates the combat log for UI. */
    private void updateCombatLog(int mx, int my) {
        int boxX = chatLogMargin;
        int boxY = gp.screenHeight - chatLogHeight - chatLogMargin - 82;
        combatLogBounds.setBounds(boxX, boxY, chatLogWidth, chatLogHeight);
        combatLogHovered = combatLogBounds.contains(mx, my);

        if (combatLogHovered && gp.mouseH.wheelRotation != 0) {
            chatScrollOffset -= gp.mouseH.wheelRotation; // scroll up = view older history
            int maxOffset = Math.max(0, chatHistory.size() - chatVisibleLines);
            chatScrollOffset = Math.max(0, Math.min(chatScrollOffset, maxOffset));
        }
        gp.mouseH.wheelRotation =
                0; // consumed every tick, so wheel turns elsewhere no longer pile up

        // age only the visible lines: history you scrolled back to stays fully readable
        int endIndex = chatHistory.size() - chatScrollOffset;
        int startIndex = Math.max(0, endIndex - chatVisibleLines);
        for (int i = startIndex; i < endIndex; i++) {
            chatHistory.get(i).ageTicks++;
        }
    }

    /** Draws the chat input for UI. */
    private void drawChatInput() {
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
        if ((System.currentTimeMillis() / 400) % 2 == 0) {
            display += "_";
        }
        FontMetrics fm = g2.getFontMetrics();
        while (display.length() > 1 && fm.stringWidth(display) > chatLogWidth - 20) {
            display = display.substring(1); // keep the tail visible when the text outgrows the box
        }
        g2.drawString(display, boxX + 10, boxY + 20);
    }
}
