package art;

import java.awt.*;
import java.awt.geom.*;

/**
 * Held weapons for the cutout rig. Each weapon is built around its GRIP at (0,0) with the tip pointing up (-y).
 * The rig places the grip on the hand and rotates the whole weapon, so it follows idle / run / attack poses.
 * Self-contained apart from CharacterArt's painter, so adding a weapon = one enum entry + one shape method.
 */
public final class WeaponArt {
    private WeaponArt() {}

    static final float HELD_SWORD_HIT=3/7f;
    public static final int DAGGER_FIRST_HIT_FRAME = 4;
    public static final int DAGGER_SECOND_HIT_FRAME = 6;
    enum DaggerHand { MAIN, OFF, NONE }

    /**
     * Swing keyframes in degrees.
     * front*: weapon angle seen from front/back (0 = straight up, + = clockwise on screen)
     * side*:  weapon angle in profile (facing right)
     * arm*:   profile arm angle (0 = hanging down, 180 = straight up, 270 = straight forward)
     */
    public enum Kind {
        // front view chops from over the head DOWN toward the camera; back view chops UP and away from it
        NONE (0, 0, 0,    0, 0, 0,     25, -85,   0, 0),
        SWORD(0, 8, 165,   28, -30, 120,   150, 300,   170, 15),
        AXE  (0, 5, 170,   30, -40, 125,   150, 300,   175, 10),
        STAFF(0, -10, 22,    10, 18, 42,    -140, -105,   -10, 22),
        DAGGERS(148, 8, 165, -148, -30, 120, 150, 300, 170, 15),
        ROYAL_SWORD(SWORD), ROYAL_DAGGER(SWORD), ROYAL_STAFF(STAFF),
        HELL_SWORD(SWORD), HELL_DAGGER(SWORD), HELL_STAFF(STAFF),
        BRIAR_DAGGER(DAGGERS), ASH_DAGGER(DAGGERS), MOON_DAGGER(DAGGERS), GLOAM_DAGGER(DAGGERS),
        RIMEGLASS_STAFF(STAFF), PRISMWEAVE_STAFF(STAFF), STARWARD_STAFF(STAFF), ASTRAL_CROWN_STAFF(STAFF),
        WAYLIGHT_STAFF(STAFF), GREENBOUGH_STAFF(STAFF), VESPER_STAFF(STAFF), DAWNRING_STAFF(STAFF), HEARTHCROWN_STAFF(STAFF),
        FIELDBREAKER(SWORD), IRONWAKE(SWORD), RIDGECLEAVER(SWORD), CINDERMAW(SWORD), FAULTLINE(SWORD),
        GATEKEEPER(SWORD), WAYGUARD(SWORD), VOWSTEEL(SWORD), DAWNWARD(SWORD), CITADEL(SWORD);

        public static Kind forPaladin(PaladinGear gear){
            return switch(gear){
                case GATE->GATEKEEPER;case ROAD->WAYGUARD;case VOW->VOWSTEEL;case DAWN->DAWNWARD;case CITADEL->CITADEL;
            };
        }

        PaladinGear paladinGear(){
            return switch(this){
                case GATEKEEPER->PaladinGear.GATE;case WAYGUARD->PaladinGear.ROAD;case VOWSTEEL->PaladinGear.VOW;
                case DAWNWARD->PaladinGear.DAWN;case CITADEL->PaladinGear.CITADEL;default->null;
            };
        }

        public static Kind forBerserker(BerserkerGear gear){
            return switch(gear){
                case ROAD->FIELDBREAKER;case IRON->IRONWAKE;case RIDGE->RIDGECLEAVER;
                case CINDER->CINDERMAW;case FAULT->FAULTLINE;
            };
        }

        BerserkerGear greatswordGear(){
            return switch(this){
                case FIELDBREAKER->BerserkerGear.ROAD;case IRONWAKE->BerserkerGear.IRON;
                case RIDGECLEAVER->BerserkerGear.RIDGE;case CINDERMAW->BerserkerGear.CINDER;
                case FAULTLINE->BerserkerGear.FAULT;default->null;
            };
        }

        public static Kind forPriest(PriestGear gear){
            return switch(gear){
                case WAY->WAYLIGHT_STAFF;case GROVE->GREENBOUGH_STAFF;case BELL->VESPER_STAFF;
                case DAWN->DAWNRING_STAFF;case HEARTH->HEARTHCROWN_STAFF;
            };
        }

        private PriestGear priestGear(){
            return switch(this){
                case WAYLIGHT_STAFF->PriestGear.WAY;case GREENBOUGH_STAFF->PriestGear.GROVE;
                case VESPER_STAFF->PriestGear.BELL;case DAWNRING_STAFF->PriestGear.DAWN;
                case HEARTHCROWN_STAFF->PriestGear.HEARTH;default->null;
            };
        }

        public static Kind forMage(MageGear gear){
            return switch(gear){
                case HEARTH->STAFF;case FROST->RIMEGLASS_STAFF;case PRISM->PRISMWEAVE_STAFF;
                case STAR->STARWARD_STAFF;case ASTRAL->ASTRAL_CROWN_STAFF;
            };
        }

        private MageGear mageGear(){
            return switch(this){
                case STAFF->MageGear.HEARTH;case RIMEGLASS_STAFF->MageGear.FROST;case PRISMWEAVE_STAFF->MageGear.PRISM;
                case STARWARD_STAFF->MageGear.STAR;case ASTRAL_CROWN_STAFF->MageGear.ASTRAL;default->null;
            };
        }

        public static Kind forAssassin(AssassinGear gear){
            return switch(gear){
                case ALLEY->DAGGERS;case BRIAR->BRIAR_DAGGER;case ASH->ASH_DAGGER;
                case MOON->MOON_DAGGER;case GLOAM->GLOAM_DAGGER;
            };
        }

        AssassinGear assassinGear(){
            return switch(this){
                case DAGGERS->AssassinGear.ALLEY;case BRIAR_DAGGER->AssassinGear.BRIAR;
                case ASH_DAGGER->AssassinGear.ASH;case MOON_DAGGER->AssassinGear.MOON;
                case GLOAM_DAGGER->AssassinGear.GLOAM;default->null;
            };
        }

        public boolean isHeldSword(){return isGreatsword() || paladinGear()!=null;}
        public boolean isGreatsword(){return greatswordGear()!=null;}
        public boolean isDaggers(){return this==DAGGERS || name().endsWith("_DAGGER");}
        public boolean isStaff(){return this==STAFF || name().endsWith("_STAFF");}
        public boolean isHell(){return name().startsWith("HELL_");}
        Kind(Kind base){
            this(base.restFront,base.windFront,base.strikeFront,base.restSide,base.windSide,base.strikeSide,
                    base.windArm,base.strikeArm,base.windBack,base.strikeBack);
        }
        public final float restFront, windFront, strikeFront, restSide, windSide, strikeSide, windArm, strikeArm, windBack, strikeBack;
        Kind(float rf, float wf, float sf, float rs, float ws, float ss, float wa, float sa, float wb, float sb) {
            restFront = rf; windFront = wf; strikeFront = sf; restSide = rs; windSide = ws; strikeSide = ss; windArm = wa; strikeArm = sa;
            windBack = wb; strikeBack = sb;
        }
    }

    /** Pose values for one frame. */
    public static final class Swing {
        public float lift;        // 0..1 how far the weapon arm is raised (front/back view)
        public float front;       // weapon angle, front/back view
        public float side;        // weapon angle, profile
        public float arm;         // arm angle, profile (NaN = not attacking, use the walk swing)
        public float reach;       // 0..1 weapon pushes forward and grows during the strike
        public float smear;       // 0..1 intensity of the slash smear (peaks on the first hit frames)
        public float back, prevBack;        // weapon angle seen from behind
        public float prevFront, prevSide;   // angles a moment earlier: the smear sweeps from these to the current angle
        DaggerHand daggerHand = DaggerHand.NONE;
    }

    /** t = attack progress 0..1: wind-up 0-0.35, strike 0.35-0.75 (matches hit frames 4-6 of 8), recover 0.75-1. */
    public static Swing swing(Kind k, boolean attacking, float t) {
        Swing s = new Swing();
        if (!attacking) {
            s.lift = 0; s.front = k.restFront; s.back = k.restFront; s.side = k.restSide; s.arm = Float.NaN;
            return s;
        }
        t = Math.max(0, Math.min(1, t));
        if (!k.isStaff()) return meleeSwing(k, t);
        if (t < 0.35f) {
            float e = smooth(t / 0.35f);
            s.lift = e;
            s.front = lerp(k.restFront, k.windFront, e);
            s.back = lerp(k.restFront, k.windBack, e);
            s.side = lerp(k.restSide, k.windSide, e);
            s.arm = lerp(0, k.windArm, e);
        } else if (t < 0.75f) {
            float e = (t - 0.35f) / 0.40f; e = e * e * (1.6f - 0.6f * e);   // ease-in: accelerates into the hit
            s.lift = 1 - 0.5f * e;
            s.front = lerp(k.windFront, k.strikeFront, e);
            s.back = lerp(k.windBack, k.strikeBack, e);
            s.side = lerp(k.windSide, k.strikeSide, e);
            s.arm = lerp(k.windArm, k.strikeArm, e);
        } else {
            float e = smooth((t - 0.75f) / 0.25f);
            s.lift = 0.5f * (1 - e);
            s.front = lerp(k.strikeFront, k.restFront, e);
            s.back = lerp(k.strikeBack, k.restFront, e);
            s.side = lerp(k.strikeSide, k.restSide, e);
            s.arm = lerp(k.strikeArm, k.strikeArm > 0 ? 360 : 0, e);
        }
        // strike extras: reach + smear bump up to a peak at t = 0.57 (the middle hit frame)
        float bump = Math.max(0f, 1f - Math.abs(t - 0.60f) / 0.32f);
        s.reach = bump;
        s.smear = bump;
        Swing before = swingAt(k, Math.max(0.35f, t - 0.22f));
        s.prevFront = before.front;
        s.prevBack = before.back;
        s.prevSide = before.side;
        return s;
    }

    /** Eight-frame melee beat: anticipate, snap forward on frame 4, impact on 5,
     * follow through on 6, then settle. These are Player's active hit frames 4–6.
     */
    private static Swing meleeSwing(Kind k, float t) {
        Swing s = new Swing();
        float wind, cut;
        if (k.isDaggers()) {
            s.daggerHand = daggerHand(t);
            wind = keyed(t, 0, 0.42f, 0.72f, 0.1f, 0.42f, 0, 0, 0);
            cut = keyed(t, 0, 0, 0, 1, 0.15f, 1, 0.5f, 0);
            s.reach = cut;
            s.smear = keyed(t, 0, 0, 0, 1, 0.15f, 1, 0.32f, 0);
        } else {
            wind = keyed(t, 0, 0.55f, 1, 0.55f, 0.12f, 0, 0, 0);
            cut = keyed(t, 0, 0, 0, 0.65f, 1, 1, 0.45f, 0);
            s.reach = keyed(t, 0, 0, 0, 0.75f, 1, 0.6f, 0.2f, 0);
            if(k.isHeldSword()){
                s.smear=t<HELD_SWORD_HIT?0:Math.max(0,1-(t-HELD_SWORD_HIT)/0.18f);
            }else{
                s.smear = keyed(t, 0, 0, 0, 1, 0.72f, 0.24f, 0, 0);
            }
        }
        s.lift = wind;
        s.front = k.restFront + (k.windFront - k.restFront) * wind
                + (k.strikeFront - k.restFront) * cut;
        s.back = k.restFront + (k.windBack - k.restFront) * wind
                + (k.strikeBack - k.restFront) * cut;
        s.side = k.restSide + (k.windSide - k.restSide) * wind
                + (k.strikeSide - k.restSide) * cut;
        // Punch from the shoulder; armed strikes sweep from overhead into a forward extension.
        s.arm = (k == Kind.NONE ? 25 : 145) * wind - 80 * cut;
        return s;
    }

    private static DaggerHand daggerHand(float progress) {
        int frame = Math.round(progress * 7) + 1;
        if (frame <= DAGGER_FIRST_HIT_FRAME) return DaggerHand.MAIN;
        if (frame <= DAGGER_SECOND_HIT_FRAME + 1) return DaggerHand.OFF;
        return DaggerHand.NONE;
    }

    private static float keyed(float t, float... values) {
        float frame = t * (values.length - 1);
        int index = Math.min((int) frame, values.length - 2);
        return lerp(values[index], values[index + 1], smooth(frame - index));
    }

    private static Swing swingAt(Kind k, float t) {
        Swing s = new Swing();
        float e = (t - 0.35f) / 0.40f; e = Math.max(0, Math.min(1, e)); e = e * e * (1.6f - 0.6f * e);
        s.front = lerp(k.windFront, k.strikeFront, e);
        s.back = lerp(k.windBack, k.strikeBack, e);
        s.side = lerp(k.windSide, k.strikeSide, e);
        return s;
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float smooth(float t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }

    // ------------------------------------------------------------ drawing
    private static final Color STEEL = new Color(0xD3DCE9), STEEL_DARK = new Color(0x9FAFC6);
    private static final Color WOOD = new Color(0xB48B62), WOOD_DARK = new Color(0x8A6B58);
    private static final Color GOLD = new Color(0xE2B84E), LEATHER = new Color(0x7A5A48);
    private static final Color ORB = new Color(0xB894FF);

    /**
     * Draw a weapon with its grip at (hx,hy). angle in degrees; mirror = -1 draws it flipped (left-hand use).
     * reach (0..1) grows the blade during extension. The directional impact crescent
     * is rendered separately by the body rig so it stays on the forward-facing side.
     */
    public static void draw(Graphics2D g, Kind kind, float hx, float hy, float angle, int mirror,
                            float reach, float smear, float prevAngle, float size) {
        if (kind == Kind.NONE) return;
        AffineTransform at = new AffineTransform();
        at.translate(hx, hy);
        at.scale(mirror, 1);
        at.rotate(Math.toRadians(angle));
        float grow = size * (1f + 0.28f * reach);
        at.scale(grow, grow);
        PaladinGear paladin=kind.paladinGear();
        if(paladin!=null){paladin.drawSword(g,at);return;}
        BerserkerGear greatsword=kind.greatswordGear();
        if(greatsword!=null){greatsword.drawSword(g,at);return;}
        AssassinGear gear=kind.assassinGear();
        if(gear!=null){gear.drawDagger(g,at,mirror<0);return;}
        PriestGear priestStaff=kind.priestGear();
        if(priestStaff!=null){priestStaff.drawStaff(g,at);return;}
        MageGear staffGear=kind.mageGear();
        if(staffGear!=null){staffGear.drawStaff(g,at);return;}
        switch (kind) {
            case SWORD -> sword(g, at);
            case AXE   -> axe(g, at);
            default -> relic(g,at,kind);
        }
    }

    private static void P(Graphics2D g, AffineTransform at, Shape s, Color c, int seed) {
        CharacterArt.softPart(g, at.createTransformedShape(s), c);
    }

    public static void shield(Graphics2D g,float x,float y,int style){
        AffineTransform at=AffineTransform.getTranslateInstance(x,y);
        Path2D shape=new Path2D.Float();shape.moveTo(-7,-9);shape.quadTo(0,-12,7,-9);shape.lineTo(6,3);shape.quadTo(3,8,0,10);shape.quadTo(-3,8,-6,3);shape.closePath();
        P(g,at,shape,new Color(0xCDD2D2),81);
        Path2D inner=new Path2D.Float();inner.moveTo(-5,-7);inner.lineTo(5,-7);inner.lineTo(4,2);inner.quadTo(1,6,0,7);inner.quadTo(-1,6,-4,2);inner.closePath();
        P(g,at,inner,style==2?new Color(0x527AAB):new Color(0x946C4E),82);
        P(g,at,new Ellipse2D.Float(-2,-3,4,4),new Color(0xECE0B9),83);
    }

    private static void relic(Graphics2D g,AffineTransform at,Kind kind){
        Color gem=kind.isHell()?new Color(0xF05A3F):new Color(0x72C29A);
        Color metal=kind.isHell()?new Color(0xB26960):new Color(0xD5E8CF);
        if(kind.isStaff()){
            staff(g,at);
            P(g,at,new Ellipse2D.Float(-3.5f,-21.5f,7,7),gem,70);
            P(g,at,new Rectangle2D.Float(-3,-15,6,2),GOLD,71);
        }else{
            boolean dagger=kind.isDaggers();
            Path2D blade=new Path2D.Float();blade.moveTo(-2,-2);blade.lineTo(-3,dagger?-8:-12);
            blade.lineTo(0,dagger?-14:-20);blade.lineTo(3,dagger?-8:-12);blade.lineTo(2,-2);blade.closePath();
            P(g,at,blade,metal,72);
            P(g,at,new RoundRectangle2D.Float(-1.4f,0,2.8f,7,1,1),LEATHER,73);
            P(g,at,new RoundRectangle2D.Float(-5,-3,10,2,1,1),GOLD,74);
            P(g,at,new Ellipse2D.Float(-1.8f,-6,3.6f,3.6f),gem,75);
        }
    }

    private static void sword(Graphics2D g, AffineTransform at) {
        Path2D blade = new Path2D.Float();
        blade.moveTo(-1.55, -1.6); blade.lineTo(-1.55, -13.2);
        blade.lineTo(0, -16.0);
        blade.lineTo(1.55, -13.2); blade.lineTo(1.55, -1.6); blade.closePath();
        P(g, at, blade, STEEL, 41);
        // fuller (centre groove)
        g.setColor(CharacterArt.withAlpha(STEEL_DARK, 150));
        g.fill(at.createTransformedShape(new Rectangle2D.Float(-0.3f, -12.2f, 0.6f, 10.2f)));
        P(g, at, new RoundRectangle2D.Float(-1.5f, 2.2f, 3.0f, 3.2f, 1.2f, 1.2f), LEATHER, 42);           // grip below the hand
        P(g, at, new RoundRectangle2D.Float(-3.5f, -2.6f, 7.0f, 1.9f, 1.2f, 1.2f), GOLD, 43);             // cross-guard
        P(g, at, new Ellipse2D.Float(-1.2f, 4.9f, 2.4f, 2.4f), GOLD, 44);                                 // pommel
    }

    private static void axe(Graphics2D g, AffineTransform at) {
        P(g, at, new RoundRectangle2D.Float(-0.95f, -13.6f, 1.9f, 20.0f, 1.2f, 1.2f), WOOD, 51);          // haft
        Path2D head = new Path2D.Float();
        head.moveTo(0.9, -13.8);
        head.curveTo(3.5, -16.2, 7.6, -15.4, 8.0, -12.0);
        head.curveTo(8.4, -8.6, 5.0, -6.2, 0.9, -8.0);
        head.closePath();
        P(g, at, head, STEEL, 52);
        Path2D spike = new Path2D.Float();
        spike.moveTo(-0.9, -13.0); spike.lineTo(-3.4, -11.4); spike.lineTo(-0.9, -9.6); spike.closePath();
        P(g, at, spike, STEEL_DARK, 53);
        P(g, at, new RoundRectangle2D.Float(-1.3f, -1.2f, 2.6f, 1.6f, 0.8f, 0.8f), LEATHER, 54);           // wrap
    }

    private static void staff(Graphics2D g, AffineTransform at) {
        P(g, at, new RoundRectangle2D.Float(-0.9f, -15.0f, 1.8f, 30.0f, 1.2f, 1.2f), WOOD, 61);           // long haft to the ground
        // halo behind the orb
        g.setColor(CharacterArt.withAlpha(ORB, 70));
        g.fill(at.createTransformedShape(new Ellipse2D.Float(-5.6f, -23.4f, 11.2f, 11.2f)));
        P(g, at, new RoundRectangle2D.Float(-2.4f, -16.0f, 4.8f, 2.0f, 1.0f, 1.0f), GOLD, 62);            // ring
        P(g, at, new Ellipse2D.Float(-3.2f, -21.2f, 6.4f, 6.4f), ORB, 63);                                // orb
        g.setColor(new Color(255, 255, 255, 190));
        g.fill(at.createTransformedShape(new Ellipse2D.Float(-1.6f, -20.2f, 1.8f, 1.8f)));
    }
}
