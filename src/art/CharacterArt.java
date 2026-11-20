package art;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

/**
 * Base chibi character, cutout-puppet rig, Upgraded Vector style. The 64-unit rig (feet at y=60)
 * sits inside an 80-unit canvas with headgear clearance. Self-contained: no dependency on ArtKit so
 * it can be dropped in as-is.
 */
public final class CharacterArt {
    /** Prevents instantiation of this utility class. */
    private CharacterArt() {}

    public enum Dir {
        DOWN,
        UP,
        LEFT,
        RIGHT
    }

    /** Look of the body. Colours only, so hair/clothes layers can reuse the same rig later. */
    public static final class Look {
        public Color skin = new Color(0xFBDDC6);
        public Color eye = new Color(0x56403F);
        public Color blush = new Color(0xF4A99A);
        public WeaponArt.Kind weapon = WeaponArt.Kind.NONE; // held weapon
        public RangerGear rangerBow, rangerOutfit;
        public AssassinGear assassinOutfit;
        public MageGear mageOutfit;
        public PriestGear priestOutfit;
        public BerserkerGear berserkerOutfit;
        public PaladinGear paladinOutfit, paladinShield;
        public int shieldStyle;
        public float weaponScale =
                1f; // >1 makes held weapons and their motion chunkier (used in pixel mode)
        public CharacterOutfit outfit = CharacterOutfit.BARE;
        public boolean shadow = true; // set false when the game draws its own ground shadow
    }

    /** Animation pose. phase 0..1 is the walk cycle, attack 0..1 is a swing (0 = none). */
    public static final class Pose {
        public float walk = 0f; // 0..1 cycle phase
        public boolean moving = false;
        public float idle = 0f; // 0..1 breathing phase
        public float attack = 0f; // attack progress 0..1
        public boolean attacking = false;
        public boolean basicSword;
        public float guard;
        public float greatswordRaise;
        public boolean chargePose, shoutPose, retaliationPose;
        public float retaliationTurn;
        public float shoutRaise;
        public int bullStage;
        public float bullTime, bullCharge, bullBreath;

        /** Selects the cached Bullthrust-specific pose family, independently of basic swings. */
        public Pose bullthrust(int index) {
            if (index < 9) {
                bullStage = 1;
                bullTime = index / 8f;
            } else if (index < 34) {
                bullStage = 2;
                bullCharge = Math.min(1, ((index - 9) / 4) / 5f);
                bullBreath = (index - 9) % 4 / 4f;
            } else if (index < 51) {
                bullStage = 3;
                bullTime = (index - 34) / 16f;
            } else {
                bullStage = 4;
                bullTime = (index - 51) / 12f;
            }
            return this;
        }

        /** Dedicated Charge samples share clothing/limb helpers, with their own authored motion. */
        public Pose charge(int index) {
            chargePose = true;
            if (index < 6) {
                bullStage = 1;
                bullTime = index / 5f;
            } else if (index < 14) {
                bullStage = 2;
                bullTime = (index - 6) / 7f;
            } else if (index < 24) {
                bullStage = 3;
                bullTime = (index - 14) / 9f;
            } else {
                bullStage = 4;
                bullTime = (index - 24) / 7f;
            }
            return this;
        }

        /** One fast planted 360-degree sword reaction, separate from activation shouts. */
        public Pose retaliation(int index) {
            retaliationPose = true;
            bullStage = 2;
            float t = index / 15f;
            retaliationTurn = Math.clamp((t - .16f) / .62f, 0, 1);
            bullTime = t;
            return this;
        }

        /** Shared raised-arm shout, sampled through preparation, hold and recovery. */
        public Pose shout(int index) {
            shoutPose = true;
            bullStage = 2; // Reuse the planted-leg rig, with distinct arms and torso below.
            shoutRaise =
                    index < 8 ? ease(index / 7f) : index < 16 ? 1 : 1 - ease((index - 16) / 7f);
            return this;
        }

        /** Resolves the walk result used by Pose. */
        public Pose walk(float p) {
            walk = p;
            moving = true;
            return this;
        }

        /** Resolves the idle result used by Pose. */
        public Pose idle(float p) {
            idle = p;
            return this;
        }

        /** Resolves the attack result used by Pose. */
        public Pose attack(float t) {
            attack = t;
            attacking = true;
            return this;
        }
    }

    private static final float W = 64f, H = 64f, FOOT_Y = 60f;
    public static final float VIEW_SIZE = 80f, OFFSET_X = 8f, OFFSET_Y = 16f;
    public static final float FOOT_FRACTION = (FOOT_Y + OFFSET_Y) / VIEW_SIZE;
    private static final int SS = 4;

    // ------------------------------------------------------------ public api
    /** Renders this component into the supplied graphics context. */
    public static BufferedImage render(Dir dir, Pose pose, Look look, int size) {
        int n = size * SS;
        BufferedImage hi = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = hi.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        float k = n / VIEW_SIZE;
        g.scale(k, k);
        g.translate(OFFSET_X, OFFSET_Y);
        draw(g, dir, pose, look);
        g.dispose();
        BufferedImage out = boxDown(hi, SS);
        grain(out, 3);
        return out;
    }

    // ------------------------------------------------------------ rig
    /** Renders this component into the supplied Java2D graphics context. */
    private static void draw(Graphics2D g, Dir dir, Pose p, Look look) {
        float swing = p.moving ? (float) Math.cos(p.walk * Math.PI * 2) : 0f;
        float lift = p.moving ? Math.abs((float) Math.sin(p.walk * Math.PI * 2)) : 0f;
        float bob = p.moving ? -lift * 1.3f : (float) Math.sin(p.idle * Math.PI * 2) * 0.35f;
        float squash = p.moving ? 0f : (float) Math.sin(p.idle * Math.PI * 2) * 0.012f;

        if (p.moving && !p.attacking) {
            // Contact, compression, passing, flight; repeat with the opposite foot.
            bob = runValue(p.walk, new float[] {0, 1.1f, -0.5f, -1.5f});
            squash = runValue(p.walk, new float[] {0.01f, 0.035f, -0.005f, -0.015f});
        }

        if (p.attacking && look.rangerBow == null) {
            WeaponArt.Swing attack =
                    (p.basicSword
                            ? WeaponArt.basicSword(p.attack)
                            : WeaponArt.swing(look.weapon, true, p.attack));
            squash = 0.025f * attack.reach - (look.weapon.isStaff() ? 0.012f * attack.lift : 0);
            bob = 0;
        }

        // soft ground shadow (stays on the ground; shrinks as body lifts)
        float sh = 1f - lift * 0.08f;
        if (look.shadow) softShadow(g, 32, FOOT_Y + 0.5f, 15 * sh, 3.6f * sh);

        AffineTransform base = g.getTransform();
        g.translate(0, bob);
        g.translate(32, FOOT_Y);
        g.scale(1 + squash, (p.moving && !p.attacking ? 0.95f : 1) * (1 - squash));
        g.translate(-32, -FOOT_Y);

        if (p.attacking && !look.weapon.isStaff() && look.rangerBow == null) {
            float reach =
                    (p.basicSword
                                    ? WeaponArt.basicSword(p.attack)
                                    : WeaponArt.swing(look.weapon, true, p.attack))
                            .reach;
            float dx = dir == Dir.RIGHT ? 2.5f : dir == Dir.LEFT ? -2.5f : 0;
            float dy = dir == Dir.DOWN ? 1.2f : dir == Dir.UP ? -0.3f : 0;
            g.translate(dx * reach, dy * reach);
            if (p.basicSword) {
                g.translate(32, FOOT_Y);
                g.shear((dir == Dir.RIGHT ? -.07 : dir == Dir.LEFT ? .07 : 0) * reach, 0);
                g.translate(-32, -FOOT_Y);
            }
        }
        if (p.bullStage > 0) {
            float lean = bullLean(p);
            float crouch =
                    p.bullStage == 4
                            ? .10f * (1 - ease(p.bullTime))
                            : p.bullStage == 1
                                    ? .10f * ease(p.bullTime)
                                    : p.bullStage == 2
                                            ? .10f + .025f * p.bullCharge
                                            : .10f - .04f * (float) Math.sin(p.bullTime * Math.PI);
            if (p.shoutPose) crouch = .04f * (1 - p.shoutRaise) - .015f * p.shoutRaise;
            float hop =
                    p.bullStage == 1 && !p.chargePose
                            ? -4 * (float) Math.sin(p.bullTime * Math.PI)
                            : 0;
            g.translate(32, FOOT_Y + hop);
            g.scale(1 + crouch * .25, 1 - crouch);
            g.shear(dir == Dir.RIGHT ? -lean : dir == Dir.LEFT ? lean : 0, 0);
            g.translate(-32, -FOOT_Y);
            if (p.bullStage == 2) g.translate(0, .25 * Math.sin(p.bullBreath * Math.PI * 2));
        }
        baseBody(g, dir, look, swing, p);
        g.setTransform(base);
    }

    /**
     * Reference base: seamless limbs, a domed head, flat capsule eyes and soft shading. Coordinates
     * retain the existing feet and weapon grips for the game's sprite alignment.
     */
    private static void baseBody(Graphics2D g, Dir dir, Look look, float swing, Pose pose) {
        boolean side = dir == Dir.LEFT || dir == Dir.RIGHT;
        boolean back = dir == Dir.UP;
        AffineTransform saved = g.getTransform();
        if (dir == Dir.LEFT) {
            g.translate(W, 0);
            g.scale(-1, 1);
        }
        boolean bow = look.rangerBow != null;
        boolean bowAim = bow && pose.attacking;
        WeaponArt.Swing sw =
                pose.basicSword
                        ? WeaponArt.basicSword(pose.attack)
                        : WeaponArt.swing(look.weapon, pose.attacking && !bow, pose.attack);
        boolean running = pose.moving && !pose.attacking;
        float step = running ? (float) Math.sin(pose.walk * Math.PI * 2) : 0;
        float nearLift = Math.max(0, -step) * 5;
        float farLift = Math.max(0, step) * 5;
        float armDeg = pose.attacking && !bow ? sw.arm : -swing * 48f;
        int hs = back ? 1 : -1;
        boolean offhandCut = sw.daggerHand == WeaponArt.DaggerHand.OFF;
        int strikeSide = offhandCut ? -hs : hs;
        float armS = (hs < 0 ? -swing : swing) * (running ? 3.2f : 0.8f);
        float armLift = sw.lift * (look.weapon.isStaff() ? 2 : 8) + (back ? sw.reach * 4 : 0);
        boolean melee = pose.attacking && !look.weapon.isStaff() && !bow;
        float handX, handY, angle, previous;
        if (side) {
            double ar = Math.toRadians(armDeg);
            handX = 32.5f - (float) Math.sin(ar) * 8.5f + sw.reach * (melee ? 5 : 2);
            handY = 38 + (float) Math.cos(ar) * 8.5f;
            angle = sw.side;
            previous = sw.prevSide;
        } else {
            handX = (strikeSide < 0 ? 26 : 38) + strikeSide * (4.2f + armS * 0.3f + sw.reach * 2);
            handY = 45.5f - armLift + armS + (back ? -4 : 5) * sw.reach;
            angle = (back ? sw.back : sw.front) * hs;
            previous = (back ? sw.prevBack : sw.prevFront) * hs;
            if (melee) {
                handX += (32 + strikeSide * 3 - handX) * sw.reach;
                handY = 45.5f - sw.lift * 7 + (back ? -17 : 7) * sw.reach;
            }
        }
        if (pose.basicSword) {
            float cut = pose.attack < .4f ? 0 : pose.attack < .6f ? (pose.attack - .4f) / .2f : 1;
            float raise = Math.clamp(pose.attack / .25f, 0, 1);
            raise = raise * raise * (3 - 2 * raise);
            float settle = Math.clamp((pose.attack - .75f) / .25f, 0, 1);
            settle = settle * settle * (3 - 2 * settle);
            float restX = handX, restY = 45.5f;
            if (side) {
                handX = 38;
                handY = 35 + 9 * cut;
                angle = -25 + 155 * cut;
            } else {
                handX = back ? 23 + 18 * cut : 41 - 18 * cut;
                handY = back ? 32 : 44;
                angle = back ? -65 + 130 * cut : 65 - 130 * cut;
            }
            float visibility = raise * (1 - settle);
            handX = restX + (handX - restX) * visibility;
            handY = restY + (handY - restY) * visibility;
            angle =
                    (side ? look.weapon.restSide : look.weapon.restFront)
                            + (angle - (side ? look.weapon.restSide : look.weapon.restFront))
                                    * visibility;
        }
        if (look.weapon.isStaff()) {
            if (side) {
                handX = Math.max(handX, 39);
                angle = Math.min(angle + 15, 42);
                previous = Math.min(previous + 15, 42);
            } else {
                handX += hs * 6;
                angle += 20;
                previous += 20;
            }
        }
        boolean greatsword = look.weapon.isGreatsword();
        boolean heldSword = look.weapon.isHeldSword();
        float swordScale = 1;
        if (heldSword && !pose.basicSword && pose.bullStage == 0) {
            SwordGrip grip = swordGrip(greatsword, side, back, pose, swing, step);
            handX = grip.x;
            handY = grip.y;
            angle = grip.angle;
            swordScale = grip.scale;
        }
        if (pose.bullStage > 0) {
            SwordGrip grip = bullGrip(pose, side, back, handX, handY, angle);
            handX = grip.x;
            handY = grip.y;
            angle = grip.angle;
            previous = angle;
        }
        double gripAngle = Math.toRadians(angle);
        float gripDX = -(float) Math.sin(gripAngle) * swordScale,
                gripDY = (float) Math.cos(gripAngle) * swordScale;
        float secondX = handX + gripDX * 4.9f, secondY = handY + gripDY * 4.9f;
        boolean pairedAttack = melee && look.weapon.isDaggers();
        boolean frontDaggers = !side && !back && look.weapon.isDaggers();
        float frontDaggerRest = -WeaponArt.Kind.DAGGERS.restFront;
        boolean showMain =
                pose.bullStage > 0
                        ? !(pose.bullStage == 3 && pose.bullTime > .18f && pose.bullTime < .72f)
                        : pose.basicSword
                                ? sw.smear == 0
                                : !melee
                                        || heldSword && sw.smear < 0.01f
                                        || pairedAttack
                                                && sw.daggerHand != WeaponArt.DaggerHand.MAIN;
        // During the offhand cut, the main blade stays in its resting grip.
        // On these swords the center of the wrapped grip is below the artwork origin.
        float gripInset = greatsword ? 1.45f : 2.45f;
        final float weaponX =
                heldSword
                        ? handX - gripDX * gripInset
                        : offhandCut ? (side ? 32.5f : (hs < 0 ? 26 : 38) + hs * 4.2f) : handX;
        final float weaponY =
                heldSword
                        ? handY - gripDY * gripInset
                        : offhandCut ? (side ? 46.5f : 45.5f) : handY;
        final float weaponAngle =
                frontDaggers
                        ? frontDaggerRest
                        : pairedAttack
                                ? (side ? look.weapon.restSide : look.weapon.restFront * hs)
                                : angle;
        final float priorAngle = previous, weaponScale = heldSword ? swordScale : look.weaponScale;
        Runnable weapon =
                () ->
                        WeaponArt.draw(
                                g,
                                look.weapon,
                                weaponX,
                                weaponY,
                                weaponAngle,
                                pose.basicSword || side || frontDaggers || heldSword ? 1 : hs,
                                pairedAttack || heldSword ? 0 : sw.reach,
                                pose.bullStage > 0 ? 0 : sw.smear,
                                priorAngle,
                                weaponScale);
        Runnable offhand =
                () -> {
                    float ox = side ? 27 : (back ? 21 : 43), oy = 45 + step * 1.5f;
                    if (look.paladinShield != null) {
                        float sx = side ? 45 + pose.guard * 4 : back ? 22 : 46;
                        float sy = 45 + step * 1.1f - pose.guard * 7;
                        look.paladinShield.drawShield(g, sx, sy, 0.91f, side);
                    } else if (look.shieldStyle > 0) WeaponArt.shield(g, ox, oy, look.shieldStyle);
                    if (!side && look.weapon.isDaggers() && !offhandCut) {
                        float rest =
                                frontDaggers
                                        ? frontDaggerRest
                                        : look.weapon.assassinGear() == null
                                                ? 0
                                                : look.weapon.restFront * hs;
                        WeaponArt.draw(g, look.weapon, ox, oy, rest, -1, 0, 0, 0, look.weaponScale);
                    }
                };
        if (back) offhand.run();
        CharacterClothing.cloak(
                g,
                look.outfit,
                side,
                back,
                swing,
                look.rangerOutfit,
                look.assassinOutfit,
                look.mageOutfit,
                look.priestOutfit,
                look.berserkerOutfit,
                look.paladinOutfit);
        if (!back) CharacterClothing.longHair(g, look.outfit, side, false, swing);
        boolean weaponBehind =
                pose.retaliationPose && pose.retaliationTurn > 0 && pose.retaliationTurn < 1
                        ? Math.cos(pose.retaliationTurn * Math.PI * 2) > 0
                        : back;
        if (showMain && weaponBehind) weapon.run();
        if (side) {
            softPart(
                    g,
                    pose.bullStage > 0
                            ? bullLeg(29.5f, -1, pose, true)
                            : running
                                    ? runLeg(29.5f, -swing * 6.5f, farLift)
                                    : sideLeg(29.5f, 48.5f, -swing * 3.2f),
                    shade(look.skin, running ? 0.94f : 0.97f));
            CharacterClothing.leg(
                    g,
                    look.outfit,
                    pose.bullStage > 0
                            ? bullLeg(29.5f, -1, pose, true)
                            : running
                                    ? runLeg(29.5f, -swing * 6.5f, farLift)
                                    : sideLeg(29.5f, 48.5f, -swing * 3.2f),
                    57 - farLift);
            if (pose.bullStage > 0) {
                float balance =
                        pose.bullStage == 4
                                ? 1 - ease(pose.bullTime)
                                : pose.bullStage == 1 ? ease(pose.bullTime) : 1;
                float ox = pose.shoutPose ? 28 - 11 * pose.shoutRaise : 28 + 14 * balance;
                float oy = pose.shoutPose ? 46 - 19 * pose.shoutRaise : 46 - 7 * balance;
                softPart(g, handArm(31, 38, ox, oy), shade(look.skin, .94f));
                CharacterClothing.arm(g, look.outfit, 31, 38, ox, oy);
            }
            if (running && !greatsword) {
                double farArm = Math.toRadians(swing * 48);
                softPart(
                        g,
                        handArm(
                                32.5f,
                                38,
                                32.5f - (float) Math.sin(farArm) * 8.5f,
                                38 + (float) Math.cos(farArm) * 8.5f),
                        shade(look.skin, 0.94f));
                CharacterClothing.arm(
                        g,
                        look.outfit,
                        32.5f,
                        38,
                        32.5f - (float) Math.sin(farArm) * 8.5f,
                        38 + (float) Math.cos(farArm) * 8.5f);
            }
            Path2D torso = new Path2D.Float();
            torso.moveTo(29, 33);
            torso.curveTo(26, 36, 25, 44, 25, 47);
            torso.curveTo(25, 51, 28, 52, 33, 52);
            torso.curveTo(39, 52, 40, 49, 39, 44);
            torso.curveTo(38, 37, 37, 34, 35, 33);
            torso.closePath();
            Area body = new Area(torso);
            body.add(
                    new Area(
                            pose.bullStage > 0
                                    ? bullLeg(32, 1, pose, true)
                                    : running
                                            ? runLeg(32, swing * 6.5f, nearLift)
                                            : sideLeg(32, 48.5f, swing * 3.2f)));
            softPart(g, body, look.skin);
            CharacterClothing.leg(
                    g,
                    look.outfit,
                    pose.bullStage > 0
                            ? bullLeg(32, 1, pose, true)
                            : running
                                    ? runLeg(32, swing * 6.5f, nearLift)
                                    : sideLeg(32, 48.5f, swing * 3.2f),
                    57 - nearLift);
            CharacterClothing.torso(
                    g,
                    look.outfit,
                    true,
                    look.mageOutfit,
                    look.priestOutfit,
                    look.berserkerOutfit,
                    look.paladinOutfit);
            CharacterClothing.longyi(g, look.outfit, true, back, swing, running);

        } else {
            // Union the limbs with the torso so there are no outlines at the joints.
            Path2D torso = new Path2D.Float();
            torso.moveTo(28, 33);
            torso.curveTo(25, 35, 24, 37, 24, 42);
            torso.lineTo(22.5, 58.5);
            torso.quadTo(22.3, 60, 25, 60);
            torso.lineTo(28, 60);
            torso.quadTo(29.7, 60, 30, 58);
            torso.lineTo(31, 53);
            torso.quadTo(32, 51, 33, 53);
            torso.lineTo(34, 58);
            torso.quadTo(34.3, 60, 36, 60);
            torso.lineTo(39, 60);
            torso.quadTo(41.7, 60, 41.5, 58.5);
            torso.lineTo(40, 42);
            torso.curveTo(40, 37, 39, 35, 36, 33);
            torso.closePath();
            Area body = new Area(torso);
            if (pose.bullStage > 0) {
                body.intersect(new Area(new Rectangle2D.Float(0, 0, 64, 50)));
                body.add(new Area(bullLeg(23, -1, pose, false)));
                body.add(new Area(bullLeg(34, 1, pose, false)));
            }
            if (running) {
                // Replace the resting feet, rather than stacking moving feet over them.
                body.intersect(new Area(new Rectangle2D.Float(0, 0, 64, 51)));
                body.add(new Area(runLeg(23, -swing * 0.7f, nearLift)));
                body.add(new Area(runLeg(34, swing * 0.7f, farLift)));
            }
            if (!bowAim) {
                body.add(
                        new Area(
                                strikeSide < 0
                                        ? handArm(26, 37.5f, handX, handY)
                                        : greatsword
                                                ? handArm(26, 37.5f, secondX, secondY)
                                                : pose.bullStage > 0
                                                        ? handArm(
                                                                26,
                                                                37.5f,
                                                                pose.shoutPose
                                                                        ? 19 - 7 * pose.shoutRaise
                                                                        : 19,
                                                                pose.shoutPose
                                                                        ? 45 - 20 * pose.shoutRaise
                                                                        : 40)
                                                        : arm(
                                                                26,
                                                                37.5f,
                                                                -1,
                                                                8,
                                                                -swing * (running ? 3.2f : 0.8f))));
                body.add(
                        new Area(
                                strikeSide > 0
                                        ? handArm(38, 37.5f, handX, handY)
                                        : greatsword
                                                ? handArm(38, 37.5f, secondX, secondY)
                                                : pose.bullStage > 0
                                                        ? handArm(
                                                                38,
                                                                37.5f,
                                                                pose.shoutPose
                                                                        ? 45 + 7 * pose.shoutRaise
                                                                        : 45,
                                                                pose.shoutPose
                                                                        ? 45 - 20 * pose.shoutRaise
                                                                        : 40)
                                                        : arm(
                                                                38,
                                                                37.5f,
                                                                1,
                                                                8,
                                                                swing * (running ? 3.2f : 0.8f))));
            }
            softPart(g, body, look.skin);
            for (int i = 0; i < 2; i++) {
                Area legShape = new Area(body);
                legShape.intersect(new Area(new Rectangle2D.Float(i == 0 ? 21 : 33, 49, 11, 14)));
                CharacterClothing.leg(g, look.outfit, legShape, 57 - (i == 0 ? nearLift : farLift));
            }
            CharacterClothing.torso(
                    g,
                    look.outfit,
                    false,
                    look.mageOutfit,
                    look.priestOutfit,
                    look.berserkerOutfit,
                    look.paladinOutfit);
            CharacterClothing.longyi(g, look.outfit, false, back, swing, running);
            if (!bowAim) {
                float leftS = -swing * (running ? 3.2f : 0.8f), rightS = -leftS;
                CharacterClothing.arm(
                        g,
                        look.outfit,
                        26,
                        37.5f,
                        strikeSide < 0
                                ? handX
                                : greatsword
                                        ? secondX
                                        : pose.bullStage > 0
                                                ? (pose.shoutPose ? 19 - 7 * pose.shoutRaise : 19)
                                                : 26 - (4.2f + leftS * 0.3f),
                        strikeSide < 0
                                ? handY
                                : greatsword
                                        ? secondY
                                        : pose.bullStage > 0
                                                ? (pose.shoutPose ? 45 - 20 * pose.shoutRaise : 40)
                                                : 45.5f + leftS,
                        strikeSide < 0 ? look.berserkerOutfit : null);
                CharacterClothing.arm(
                        g,
                        look.outfit,
                        38,
                        37.5f,
                        strikeSide > 0
                                ? handX
                                : greatsword
                                        ? secondX
                                        : pose.bullStage > 0
                                                ? (pose.shoutPose ? 45 + 7 * pose.shoutRaise : 45)
                                                : 38 + (4.2f + rightS * 0.3f),
                        strikeSide > 0
                                ? handY
                                : greatsword
                                        ? secondY
                                        : pose.bullStage > 0
                                                ? (pose.shoutPose ? 45 - 20 * pose.shoutRaise : 40)
                                                : 45.5f + rightS,
                        strikeSide > 0 ? look.berserkerOutfit : null);
            }
        }
        if (back)
            CharacterClothing.cloak(
                    g,
                    look.outfit,
                    false,
                    true,
                    swing,
                    look.rangerOutfit,
                    look.assassinOutfit,
                    look.mageOutfit,
                    look.priestOutfit,
                    look.berserkerOutfit,
                    look.paladinOutfit);
        if (!back && look.paladinShield == null) offhand.run();
        CharacterClothing.collar(g, look.outfit, side, look.berserkerOutfit);
        if (!back && look.outfit.cloak() != null && look.rangerOutfit == RangerGear.SUN) {
            g.setColor(look.rangerOutfit.trim);
            g.fill(new Ellipse2D.Float(side ? 35 : 30, 36, 3.7f, 3.7f));
        }
        if (!back && look.outfit.cloak() != null && look.assassinOutfit != null) {
            g.setColor(look.assassinOutfit.trim);
            g.fill(new RoundRectangle2D.Float(side ? 34 : 30, 38, 4, 2.6f, 1, 1));
        }
        fillSoft(g, ellipse(32, 33.9f, 4.5f, 1.4f), shade(look.skin, 0.97f));
        boolean behindHead =
                side
                        && heldSword
                        && (pose.greatswordRaise >= 0.5f
                                || pose.attacking
                                        && pose.attack > 0.24f
                                        && pose.attack < WeaponArt.HELD_SWORD_HIT);
        if (showMain && behindHead) weapon.run();
        drawHead(g, side, back, look);
        // Long back hair overlays the cape, collar, and head before the short crown layer.
        if (back) CharacterClothing.longHair(g, look.outfit, false, true, swing);
        CharacterClothing.hair(g, look.outfit, side, back);
        if (side && !bow) {
            softPart(g, handArm(32.5f, 38, handX, handY), lighten(look.skin, 0.10f));
            CharacterClothing.arm(g, look.outfit, 32.5f, 38, handX, handY, look.berserkerOutfit);
        }
        if (greatsword && side) {
            softPart(g, handArm(31.5f, 38, secondX, secondY), shade(look.skin, 0.96f));
            CharacterClothing.arm(g, look.outfit, 31.5f, 38, secondX, secondY);
        }
        if (showMain && !weaponBehind && !behindHead) weapon.run();
        if (showMain && heldSword) {
            // Wrap the fingers over the same grip that the arm and weapon use.
            Color glove = look.outfit.gloves() == null ? look.skin : look.outfit.gloves();
            fillSoft(g, ellipse(handX, handY, 2.55f, 2.15f), glove);
            if (greatsword) fillSoft(g, ellipse(secondX, secondY, 2.25f, 2f), shade(glove, 0.92f));
        }
        if (!back && look.paladinShield != null) offhand.run();
        if (bow && !bowAim && !back) {
            if (side) {
                float bowY = 44 + step;
                softPart(g, handArm(33, 38, 43, bowY), look.skin);
                CharacterClothing.arm(g, look.outfit, 33, 38, 43, bowY);
                look.rangerBow.drawBow(g, 43, bowY, 0.83f, 0);
            } else {
                AffineTransform held = g.getTransform();
                g.translate(32, 47 + step);
                g.rotate(Math.PI / 2);
                look.rangerBow.drawBow(g, 0, 0, 0.83f, 0);
                g.setTransform(held);
            }
        }
        // Headgear is the final character layer, above hair, limbs, weapons, and effects.
        CharacterClothing.headpiece(g, look.outfit);
        g.setTransform(saved);
    }

    /** Dedicated anticipation and release lean; feet remain at the rig's common anchor. */
    private static float bullLean(Pose p) {
        if (p.retaliationPose) return .07f * (float) Math.sin(p.retaliationTurn * Math.PI * 2);
        if (p.shoutPose) return 0;
        if (p.chargePose)
            return switch (p.bullStage) {
                case 1 -> .18f * ease(p.bullTime);
                case 2 -> .18f + .16f * (float) Math.sin(Math.PI * p.bullTime);
                case 3 -> .18f * (1 - .4f * ease(p.bullTime));
                default -> .108f * (1 - ease(p.bullTime));
            };
        return switch (p.bullStage) {
            case 1 -> -.12f * (float) Math.sin(p.bullTime * Math.PI) + .10f * ease(p.bullTime);
            case 2 -> .10f + .07f * p.bullCharge;
            case 3 -> .17f + .13f * (float) Math.sin(p.bullTime * Math.PI);
            default -> .17f * (1 - ease(p.bullTime));
        };
    }

    /** Bent, planted legs become a stretched trailing leg and forward foot during release. */
    private static Shape bullLeg(float hip, int side, Pose p, boolean profile) {
        float stance =
                p.bullStage == 1 ? ease(p.bullTime) : p.bullStage == 4 ? 1 - ease(p.bullTime) : 1;
        if (p.shoutPose) stance = p.shoutRaise;
        if (p.retaliationPose) stance = .2f;
        float stride = (profile ? 5 : 3) * side * stance;
        float lift = 0;
        if (p.bullStage == 3 && profile) {
            stride = side < 0 ? -7 : 4;
            lift = side < 0 ? 3 * (float) Math.sin(p.bullTime * Math.PI) : 0;
        }
        if (p.chargePose && p.bullStage == 2) {
            stride = profile ? (side < 0 ? -8 : 5) : side * 4;
            lift = side < 0 ? 3 + (float) Math.sin(p.bullTime * Math.PI * 2) : 0;
        }
        return runLeg(hip, stride, lift);
    }

    /** Sword hand moves from drawn-back preparation through sweep into a long finishing thrust. */
    private static SwordGrip bullGrip(
            Pose p, boolean side, boolean back, float restX, float restY, float restAngle) {
        SwordGrip rest = new SwordGrip(restX, restY, restAngle, 1);
        if (p.retaliationPose) {
            double turn = p.retaliationTurn * Math.PI * 2;
            SwordGrip sweep =
                    new SwordGrip(
                            32 + (float) Math.sin(turn) * 15,
                            40 - (float) Math.cos(turn) * 10,
                            restAngle + p.retaliationTurn * 360,
                            1);
            if (p.bullTime < .16f) return rest.toward(sweep, ease(p.bullTime / .16f));
            if (p.bullTime > .78f)
                return sweep.toward(
                        new SwordGrip(restX, restY, restAngle + 360, 1),
                        ease((p.bullTime - .78f) / .22f));
            return sweep;
        }
        if (p.shoutPose)
            return rest.toward(new SwordGrip(side ? 49 : back ? 52 : 12, 26, 0, 1), p.shoutRaise);
        if (p.chargePose) {
            SwordGrip sprint =
                    new SwordGrip(
                            side ? 25 : back ? 42 : 22,
                            side ? 47 : back ? 43 : 40,
                            side ? -105 : back ? 135 : -60,
                            1);
            SwordGrip cut =
                    new SwordGrip(
                            side ? 43 : back ? 23 : 41,
                            side ? 44 : back ? 29 : 47,
                            side ? 120 : back ? -65 : 65,
                            1);
            return switch (p.bullStage) {
                case 1 -> rest.toward(sprint, ease(p.bullTime));
                case 2 ->
                        new SwordGrip(
                                sprint.x,
                                sprint.y + .8f * (float) Math.sin(p.bullTime * Math.PI * 2),
                                sprint.angle,
                                1);
                case 3 -> sprint.toward(cut, ease(Math.clamp(p.bullTime / .7f, 0, 1)));
                default -> cut.toward(rest, ease(p.bullTime));
            };
        }
        SwordGrip ready =
                new SwordGrip(
                        side ? 25 : back ? 41 : 23,
                        side ? 43 : back ? 46 : 38,
                        side ? -110 : back ? 150 : -30,
                        1);
        SwordGrip finish =
                new SwordGrip(
                        side ? 47 : 32, side ? 41 : back ? 16 : 45, side ? 90 : back ? 0 : 180, 1);
        return switch (p.bullStage) {
            case 1 -> rest.toward(ready, ease(p.bullTime));
            case 2 ->
                    new SwordGrip(
                            ready.x - (side ? p.bullCharge : 0),
                            ready.y,
                            ready.angle
                                    + .8f
                                            * p.bullCharge
                                            * (float) Math.sin(p.bullBreath * Math.PI * 2),
                            1);
            case 3 -> ready.toward(finish, ease(Math.clamp(p.bullTime / .85f, 0, 1)));
            default -> finish.toward(rest, ease(p.bullTime));
        };
    }

    private record SwordGrip(float x, float y, float angle, float scale) {
        /** Resolves the toward result used by SwordGrip. */
        SwordGrip toward(SwordGrip next, float amount) {
            return new SwordGrip(
                    x + (next.x - x) * amount,
                    y + (next.y - y) * amount,
                    angle + (next.angle - angle) * amount,
                    scale + (next.scale - scale) * amount);
        }
    }

    /** Resolves the sword grip result used by CharacterArt. */
    private static SwordGrip swordGrip(
            boolean greatsword, boolean side, boolean back, Pose pose, float swing, float step) {
        float sideAngle = greatsword ? -116 : -128;
        float frontAngle = greatsword ? -114 : -137;
        float restX =
                side
                        ? (greatsword ? 34 : 33)
                        : back ? (greatsword ? 37 : 40) : (greatsword ? 27 : 23.5f);
        float restY = greatsword ? 44 : 45;
        float restAngle = side ? sideAngle : back ? -frontAngle : frontAngle;
        var rest = new SwordGrip(restX, restY, restAngle, 1);
        if (pose.greatswordRaise > 0) {
            var overhead = new SwordGrip(side ? 37 : back ? 36 : 29, 14, back ? 63 : -62, 1);
            return rest.toward(overhead, ease(pose.greatswordRaise));
        }
        if (!pose.attacking) {
            return new SwordGrip(
                    restX + (pose.moving ? swing * (side ? 1.6f : 0.65f) : 0),
                    restY + step + (greatsword ? 0 : pose.guard * 2),
                    restAngle + swing * (side ? 8 : 4),
                    1);
        }

        var windup = new SwordGrip(side ? 27 : back ? 38 : 25, side ? 34 : 37, back ? 51 : -51, 1);
        var contact =
                new SwordGrip(
                        side ? (greatsword ? 45 : 43) : back ? 39 : 28,
                        side ? 39 : back ? 32 : 43,
                        side ? 73 : back ? 88 : -91,
                        1);
        var follow =
                new SwordGrip(
                        side ? (greatsword ? 43 : 41) : back ? 40 : 26,
                        side ? 43 : 45,
                        side ? 106 : back ? 124 : -124,
                        1);
        float t = pose.attack;
        float hit = WeaponArt.HELD_SWORD_HIT;
        if (t < 0.32f) return rest.toward(windup, ease(t / 0.32f));
        if (t < hit) {
            float release = ease((t - 0.32f) / (hit - 0.32f));
            return windup.toward(contact, side ? release * release * release : release);
        }
        if (t < 0.59f) return contact.toward(follow, ease((t - hit) / (0.59f - hit)));
        if (!side) return follow.toward(rest, ease((t - 0.59f) / 0.41f));

        // In profile the blade sweeps down and back, foreshortening as it turns toward the camera.
        var turn = new SwordGrip(37, 38, 176, greatsword ? 0.68f : 0.82f);
        if (t < 0.78f) return follow.toward(turn, ease((t - 0.59f) / 0.19f));
        var finish = new SwordGrip(restX, restY, restAngle + 360, 1);
        return turn.toward(finish, ease((t - 0.78f) / 0.22f));
    }

    /** Resolves the ease result used by CharacterArt. */
    private static float ease(float t) {
        return t * t * (3 - 2 * t);
    }

    /** Shared head layer for body sprites and uncluttered hair-selection thumbnails. */
    static void drawHead(Graphics2D g, boolean side, boolean back, Look look) {
        Path2D head = new Path2D.Float();
        head.moveTo(32, 2);
        head.curveTo(20, 2, 14.5, 8.5, 14.5, 18.5);
        head.curveTo(14.5, 29, 19, 34.8, 32, 34);
        head.curveTo(44, 34.8, 49.5, 31, 49.5, 26.8);
        head.curveTo(49.5, 24.5, 49, 24, 49, 23);
        head.curveTo(51.5, 9, 46, 2, 32, 2);
        head.closePath();
        if (!side) {
            head.reset();
            head.moveTo(32, 2);
            head.curveTo(20, 2, 14.5, 8, 14.5, 18.5);
            head.curveTo(14.5, 29.5, 19, 34, 32, 34);
            head.curveTo(45, 34, 49.5, 29.5, 49.5, 18.5);
            head.curveTo(49.5, 8, 44, 2, 32, 2);
            head.closePath();
        }
        Area headShape = new Area(head);
        if (!side) {
            headShape.add(new Area(ellipse(15.5f, 27, 4, 3.7f)));
            headShape.add(new Area(ellipse(48.5f, 27, 4, 3.7f)));
        }
        softPart(g, headShape, look.skin);
        if (side) fillSoft(g, ellipse(32, 27, 3.7f, 3.8f), lighten(look.skin, 0.14f));
        g.setColor(look.eye);
        if (side) {
            g.fill(new RoundRectangle2D.Float(43.2f, 22, 3.1f, 7.3f, 3.1f, 3.1f));
        } else if (!back) {
            g.fill(new RoundRectangle2D.Float(23, 22, 3.6f, 7.3f, 3.6f, 3.6f));
            g.fill(new RoundRectangle2D.Float(37.4f, 22, 3.6f, 7.3f, 3.6f, 3.6f));
        }
    }

    /** Continuous interpolation keeps the eight authored poses seamless at the loop boundary. */
    private static float runValue(float phase, float[] values) {
        float frame = (phase - (float) Math.floor(phase)) * 8;
        int index = (int) frame;
        float blend = frame - index;
        return values[index % values.length] * (1 - blend)
                + values[(index + 1) % values.length] * blend;
    }

    /** Resolves the run leg result used by CharacterArt. */
    private static Shape runLeg(float hipX, float stride, float lift) {
        float footX = hipX + stride, footY = 60 - lift;
        Path2D p = new Path2D.Float();
        p.moveTo(hipX, 48.5f);
        p.lineTo(hipX + 6.5f, 48.5f);
        p.curveTo(
                hipX + 6.5f + stride * 0.3f, 53, footX + 6.8f, footY - 4, footX + 7, footY - 1.5f);
        p.quadTo(footX + 7.5f, footY, footX + 5.5f, footY);
        p.lineTo(footX + 1.5f, footY);
        p.quadTo(footX, footY, footX, footY - 1.5f);
        p.quadTo(hipX + stride * 0.2f, 53, hipX, 48.5f);
        p.closePath();
        return p;
    }

    /** Resolves the hand arm result used by CharacterArt. */
    private static Shape handArm(float sx, float sy, float hx, float hy) {
        return new BasicStroke(5.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                .createStrokedShape(new Line2D.Float(sx, sy, hx, hy));
    }

    /** A bold warm contour remains visible after the 24px pixel conversion. */
    static void softPart(Graphics2D g, Shape shape, Color skin) {
        Stroke saved = g.getStroke();
        g.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0x644B46));
        g.draw(shape);
        fillSoft(g, shape, skin);
        g.setStroke(saved);
    }

    /** Shade each fill gently from screen top-left, including mirrored and rotated parts. */
    static void fillSoft(Graphics2D g, Shape shape, Color color) {
        Paint saved = g.getPaint();
        try {
            AffineTransform transform = g.getTransform();
            Rectangle2D bounds =
                    transform.createTransformedShape(shape.getBounds2D()).getBounds2D();
            if (bounds.isEmpty()) return;
            AffineTransform inverse = transform.createInverse();
            Point2D light =
                    inverse.transform(new Point2D.Double(bounds.getMinX(), bounds.getMinY()), null);
            Point2D shade =
                    inverse.transform(new Point2D.Double(bounds.getMaxX(), bounds.getMaxY()), null);
            // A white sheen fades out near the top-left without adding another alpha layer.
            Color highlight = withAlpha(lighten(color, 0.30f), color.getAlpha());
            Color softHighlight = withAlpha(lighten(color, 0.10f), color.getAlpha());
            Color shadow =
                    new Color(
                            Math.round(color.getRed() * 0.95f),
                            Math.round(color.getGreen() * 0.95f),
                            Math.round(color.getBlue() * 0.95f),
                            color.getAlpha());
            g.setPaint(
                    new LinearGradientPaint(
                            light,
                            shade,
                            new float[] {0, 0.24f, 0.52f, 1},
                            new Color[] {highlight, softHighlight, color, shadow}));
            g.fill(shape);
        } catch (NoninvertibleTransformException ignored) {
            // A collapsed transform has no visible area; retain a safe flat fill.
            g.setColor(color);
            g.fill(shape);
        } finally {
            g.setPaint(saved);
        }
    }

    // ------------------------------------------------------------ shapes
    /** Draws or constructs the leg shape used by this artwork. */
    private static Shape leg(float x, float y, float w, float h, float stride) {
        Path2D p = new Path2D.Float();
        float yy = y + Math.min(0, -stride * 0.0f);
        p.moveTo(x, yy);
        p.lineTo(x + w, yy);
        p.lineTo(x + w - 0.2f, yy + h + stride * 0.5f - 0.8f);
        p.quadTo(
                x + w - 0.4f,
                yy + h + stride * 0.5f + 0.5f,
                x + w - 1.6f,
                yy + h + stride * 0.5f + 0.5f);
        p.lineTo(x + 1.6f, yy + h + stride * 0.5f + 0.5f);
        p.quadTo(x + 0.4f, yy + h + stride * 0.5f + 0.5f, x + 0.2f, yy + h + stride * 0.5f - 0.8f);
        p.closePath();
        return p;
    }

    /** Resolves the side leg result used by CharacterArt. */
    private static Shape sideLeg(float x, float y, float swing) {
        Path2D p = new Path2D.Float();
        float footX = x + swing * 0.8f;
        p.moveTo(x, y);
        p.lineTo(x + 6.2f, y);
        p.lineTo(footX + 6.0f, y + 10.0f);
        p.quadTo(footX + 6.0f, y + 11.5f, footX + 4.4f, y + 11.5f);
        p.lineTo(footX + 1.8f, y + 11.5f);
        p.quadTo(footX + 0.2f, y + 11.5f, footX + 0.2f, y + 10.0f);
        p.closePath();
        return p;
    }

    /** front arm: capsule from shoulder to mitten; dir -1 = left side of screen. */
    private static Shape arm(float sx, float sy, int dir, float len, float swing) {
        float ex = sx + dir * (4.2f + swing * 0.3f), ey = sy + len + swing;
        return new BasicStroke(5.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                .createStrokedShape(new Line2D.Float(sx, sy, ex, ey));
    }

    /** Draws or constructs the ellipse shape used by this artwork. */
    private static Shape ellipse(float cx, float cy, float rx, float ry) {
        return new Ellipse2D.Float(cx - rx, cy - ry, rx * 2, ry * 2);
    }

    // ------------------------------------------------------------ painting
    /** one rig part: fill, shade crescent, rim light, wobbly soft outline */
    static void part(Graphics2D g, Shape raw, Color fill, int seed) {
        part(g, raw, fill, seed, 1f);
    }

    /** detail < 1 shrinks the shade/rim offsets and wobble, for thin parts like blades */
    static void part(Graphics2D g, Shape raw, Color fill, int seed, float detail) {
        Shape s = wobble(raw, seed, 0.10f * detail);
        g.setColor(fill);
        g.fill(s);

        Area a = new Area(s);
        // shade: bottom-right crescent
        Area sh = new Area(s);
        sh.subtract(
                new Area(
                        AffineTransform.getTranslateInstance(-1.5 * detail, -1.9 * detail)
                                .createTransformedShape(s)));
        g.setColor(withAlpha(shade(fill, 0.86f), 255));
        Shape old = g.getClip();
        g.clip(a);
        g.fill(sh);
        // rim light: top-left thin crescent
        Area rim = new Area(s);
        rim.subtract(
                new Area(
                        AffineTransform.getTranslateInstance(0.9 * detail, 1.1 * detail)
                                .createTransformedShape(s)));
        g.setColor(withAlpha(lighten(fill, 0.55f), 210));
        g.fill(rim);
        g.setClip(old);

        g.setStroke(
                new BasicStroke(
                        1.35f * (0.6f + 0.4f * detail),
                        BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));
        g.setColor(outline(fill));
        g.draw(s);
    }

    /** Resolves the wobble result used by CharacterArt. */
    private static Shape wobble(Shape s, int seed, float amp) {
        Path2D.Float out = new Path2D.Float();
        PathIterator it = new FlatteningPathIterator(s.getPathIterator(null), 0.12);
        float[] c = new float[6];
        boolean first = true;
        while (!it.isDone()) {
            int t = it.currentSegment(c);
            if (t == PathIterator.SEG_CLOSE) {
                out.closePath();
            } else {
                float x = c[0], y = c[1];
                float dx = (noise(x * 0.35f, y * 0.35f, seed) - 0.5f) * 2 * amp;
                float dy = (noise(x * 0.35f + 9.1f, y * 0.35f + 4.7f, seed) - 0.5f) * 2 * amp;
                if (t == PathIterator.SEG_MOVETO) {
                    out.moveTo(x + dx, y + dy);
                    first = false;
                } else out.lineTo(x + dx, y + dy);
            }
            it.next();
        }
        return out;
    }

    /** Performs the soft shadow operation for CharacterArt. */
    private static void softShadow(Graphics2D g, float cx, float cy, float rx, float ry) {
        for (int i = 6; i >= 1; i--) {
            float f = i / 6f;
            g.setColor(new Color(40, 30, 60, (int) (16 * (1.2 - f) + 6)));
            g.fill(ellipse(cx, cy, rx * (0.55f + 0.45f * f), ry * (0.55f + 0.45f * f)));
        }
    }

    // ------------------------------------------------------------ colour + noise
    /** Resolves the with alpha result used by CharacterArt. */
    static Color withAlpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    /** Resolves the lighten result used by CharacterArt. */
    static Color lighten(Color c, float t) {
        return new Color(
                Math.round(c.getRed() + (255 - c.getRed()) * t),
                Math.round(c.getGreen() + (255 - c.getGreen()) * t),
                Math.round(c.getBlue() + (255 - c.getBlue()) * t));
    }

    /** warm-shift shade: darker, slightly more saturated and warmer (reads as cozy, not grey) */
    static Color shade(Color c, float k) {
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        float b = Math.max(0, hsb[2] * k);
        float s = Math.min(1, hsb[1] * 1.18f + 0.03f);
        return Color.getHSBColor(hsb[0] - 0.012f, s, b);
    }

    /** Resolves the outline result used by CharacterArt. */
    static Color outline(Color c) {
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        return Color.getHSBColor(
                hsb[0] - 0.01f, Math.min(1, hsb[1] * 1.25f + 0.08f), hsb[2] * 0.66f);
    }

    /** Resolves the noise result used by CharacterArt. */
    private static float noise(float x, float y, int seed) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
        float fx = x - xi, fy = y - yi;
        float a = h(xi, yi, seed),
                b = h(xi + 1, yi, seed),
                c = h(xi, yi + 1, seed),
                d = h(xi + 1, yi + 1, seed);
        float u = fx * fx * (3 - 2 * fx), v = fy * fy * (3 - 2 * fy);
        return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
    }

    /** Resolves the h result used by CharacterArt. */
    private static float h(int x, int y, int seed) {
        int n = x * 374761393 + y * 668265263 + seed * 1442695041;
        n = (n ^ (n >>> 13)) * 1274126177;
        n ^= n >>> 16;
        return (n & 0xFFFF) / 65535f;
    }

    // ------------------------------------------------------------ image helpers
    /** Resolves the box down result used by CharacterArt. */
    static BufferedImage boxDown(BufferedImage src, int f) {
        int w = src.getWidth() / f, hgt = src.getHeight() / f;
        BufferedImage out = new BufferedImage(w, hgt, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < hgt; y++)
            for (int x = 0; x < w; x++) {
                float a = 0, r = 0, gg = 0, b = 0;
                for (int j = 0; j < f; j++)
                    for (int i = 0; i < f; i++) {
                        int p = src.getRGB(x * f + i, y * f + j);
                        float pa = (p >>> 24) / 255f;
                        a += pa;
                        r += ((p >> 16) & 255) * pa;
                        gg += ((p >> 8) & 255) * pa;
                        b += (p & 255) * pa;
                    }
                if (a > 0) {
                    r /= a;
                    gg /= a;
                    b /= a;
                }
                a /= f * f;
                out.setRGB(
                        x,
                        y,
                        (Math.round(a * 255) << 24)
                                | (Math.round(r) << 16)
                                | (Math.round(gg) << 8)
                                | Math.round(b));
            }
        return out;
    }

    /** paper grain: tiny luminance noise on opaque pixels */
    static void grain(BufferedImage img, int amp) {
        for (int y = 0; y < img.getHeight(); y++)
            for (int x = 0; x < img.getWidth(); x++) {
                int p = img.getRGB(x, y);
                int a = p >>> 24;
                if (a == 0) continue;
                int d = Math.round((h(x, y, 77) - 0.5f) * 2 * amp);
                int r = clamp(((p >> 16) & 255) + d),
                        g = clamp(((p >> 8) & 255) + d),
                        b = clamp((p & 255) + d);
                img.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
    }

    /** Resolves the clamp result used by CharacterArt. */
    private static int clamp(int v) {
        return v < 0 ? 0 : Math.min(255, v);
    }
}
