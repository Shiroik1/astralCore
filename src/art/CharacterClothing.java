package art;

import java.awt.*;
import java.awt.geom.*;

/** Equipment layers share the base rig's posed limbs; nothing is baked into the skin. */
final class CharacterClothing {
    private CharacterClothing() {}
    static void cloak(Graphics2D g, CharacterOutfit o, boolean side, boolean back, float swing, RangerGear ranger,AssassinGear assassin,MageGear mage,PriestGear priest,BerserkerGear berserker,PaladinGear paladin) {
        if (o.cloak() == null) return;
        float trail = Math.abs(swing) * 3;
        Path2D p = new Path2D.Float();
        if(paladin!=null){
            if(side){
                p.moveTo(29,34);p.lineTo(35,37);p.lineTo(29,53);p.lineTo(17-trail,51);p.quadTo(22,43,29,34);
            }else{
                p.moveTo(25,34);p.lineTo(39,34);p.lineTo(45,54);p.lineTo(35,54);
                p.lineTo(32,51);p.lineTo(29,54);p.lineTo(19,54);p.closePath();
            }
            p.closePath();CharacterArt.softPart(g,p,o.cloak());
            Stroke old=g.getStroke();g.setColor(paladin.trim);g.setStroke(new BasicStroke(1.4f));
            if(side)g.draw(new Line2D.Float(18-trail,51,28,53));
            if(back){
                g.draw(new Line2D.Float(20,53,29,53));g.draw(new Line2D.Float(35,53,44,53));
                g.fill(PaladinGear.star(32,44,4,6));
            }
            g.setStroke(old);return;
        }
        if(berserker!=null){
            // The scarf tails hang behind the shoulder in profile and across the back.
            if(!side && !back && berserker.ordinal()<BerserkerGear.CINDER.ordinal())return;
            if(side){
                p.moveTo(33,34);p.quadTo(27,42,15-trail,45);p.lineTo(18-trail,41);
                p.lineTo(13-trail,41);p.quadTo(23,35,30,33);
            }else{
                float hem=berserker.ordinal()>=BerserkerGear.CINDER.ordinal()?4:0;
                p.moveTo(24,34);p.lineTo(36,33);p.quadTo(37,40,47+trail,47+hem);
                p.lineTo(41+trail,48+hem);p.lineTo(36,44);p.lineTo(34,49+hem);
                p.quadTo(29,42,27,37);
            }
            p.closePath();CharacterArt.softPart(g,p,o.cloak());
            return;
        }
        if(assassin!=null){
            float hem=assassin==AssassinGear.GLOAM?5:assassin==AssassinGear.MOON?3:0;
            if(side){
                p.moveTo(30,34);p.lineTo(35,37);p.quadTo(31,43,20-trail,53+hem);
                p.lineTo(23-trail,47);p.lineTo(14-trail,49+hem);p.quadTo(23,43,30,34);
            }else{
                p.moveTo(25,35);p.lineTo(39,35);p.lineTo(44+trail,53+hem);
                p.quadTo(35,54+hem,32,49);p.quadTo(27,55+hem,19-trail,53+hem);p.closePath();
            }
            p.closePath();CharacterArt.softPart(g,p,o.cloak());
            if(hem>0){
                Shape clip=g.getClip();Stroke old=g.getStroke();g.clip(p);g.setColor(assassin.trim);
                g.setStroke(new BasicStroke(2.2f));g.draw(p);g.setClip(clip);g.setStroke(old);
            }
            return;
        }
        int hem=ranger==RangerGear.SUN?5:ranger==RangerGear.MOON?3:0;
        if (side) {
            p.moveTo(29, 34); p.lineTo(35, 36); p.lineTo(30, 56+hem);
            p.quadTo(25, 57+hem, 17 - trail, 53+hem); p.quadTo(24 - trail, 44, 29, 34);
        } else {
            p.moveTo(25, 35); p.lineTo(39, 35); p.lineTo(45 + trail, 55+hem);
            if(priest==null)p.quadTo(32, 58+hem, 19 - trail, 55+hem);
            else{
                p.quadTo(40,57,37,55);p.quadTo(32,60,27,55);p.quadTo(23,57,19-trail,55);
            }
            p.closePath();
        }
        p.closePath(); CharacterArt.softPart(g, p, o.cloak());
        if(hem>0 || o.theme(CharacterOutfit.Slot.CLOAK)!=CharacterOutfit.Theme.LEATHER){
            Shape clip=g.getClip();g.clip(p);Stroke old=g.getStroke();
            g.setColor(hem>0?ranger.trim:priest!=null?priest.trim:mage!=null?mage.trim:new Color(0xD9B77D));g.setStroke(new BasicStroke(2.5f));g.draw(p);
            g.setStroke(old);g.setClip(clip);
        }
        if(back && (mage!=null || priest!=null)){
            var clasp=new Path2D.Float();
            clasp.moveTo(32,42);clasp.lineTo(36.5,47);clasp.lineTo(32,52);clasp.lineTo(27.5,47);clasp.closePath();
            g.setColor(priest!=null?priest.trim:mage.trim);g.fill(clasp);
        }
        if (back || side) {
            Stroke old = g.getStroke();g.setStroke(new BasicStroke(0.8f));
            g.setColor(CharacterArt.shade(o.cloak(), 0.86f));
            g.draw(new Line2D.Float(side ? 29 : 27, 39, side ? 24 - trail : 24, 53));g.setStroke(old);
        }
    }

    static void leg(Graphics2D g, CharacterOutfit o, Shape shape, float bootTop) {
        if(o.theme(CharacterOutfit.Slot.BOOTS)==CharacterOutfit.Theme.ARMOR)bootTop-=2;
        Shape clip = g.getClip();g.clip(shape);
        if (o.pants() != null) CharacterArt.softPart(g, shape, o.pants());
        if (o.boots() != null) {
            g.clip(new Rectangle2D.Float(0, bootTop, 64, 64 - bootTop));
            CharacterArt.softPart(g, shape, o.boots());
            g.setColor(CharacterArt.lighten(o.boots(), 0.18f));
            g.fill(new Rectangle2D.Float(0, bootTop, 64, 1.3f));
        }
        g.setClip(clip);
    }

    static void torso(Graphics2D g, CharacterOutfit o, boolean side,MageGear mage,PriestGear priest,BerserkerGear berserker,PaladinGear paladin) {
        if (o.torso() == null) return;
        if(o.theme(CharacterOutfit.Slot.TORSO)==CharacterOutfit.Theme.CASTER){
            robe(g,o,side,mage,priest);return;
        }
        Path2D p = new Path2D.Float();
        if (side) {
            p.moveTo(29, 34);p.lineTo(36, 34);p.quadTo(40, 40, 39, 47);
            p.lineTo(40, 52);p.lineTo(25, 52);p.lineTo(27, 43);p.closePath();
        } else {
            p.moveTo(27, 34);p.lineTo(37, 34);p.quadTo(40, 38, 40, 44);
            p.lineTo(42, 52);p.lineTo(33, 52);p.lineTo(32, 49);p.lineTo(31, 52);
            p.lineTo(22, 52);p.lineTo(24, 44);p.quadTo(24, 38, 27, 34);p.closePath();
        }
        CharacterArt.softPart(g, p, o.torso());
        if(o.theme(CharacterOutfit.Slot.TORSO)==CharacterOutfit.Theme.ARMOR){
            Stroke old=g.getStroke();g.setStroke(new BasicStroke(0.9f));
            g.setColor(CharacterArt.shade(o.torso(),0.83f));
            if(!side){
                Path2D chest=new Path2D.Float();chest.moveTo(26,38);chest.lineTo(32,41);chest.lineTo(38,38);
                g.draw(chest);g.draw(new Line2D.Float(32,41,32,46));
            }
            g.setStroke(old);
        }
        if(paladin!=null){
            var tabard=new Path2D.Float();
            float x=side?35:32;
            tabard.moveTo(x-3,36);tabard.lineTo(x+3,36);tabard.lineTo(x+4.3f,53);
            tabard.lineTo(x,54.6f);tabard.lineTo(x-4.3f,53);tabard.closePath();
            CharacterArt.softPart(g,tabard,new Color(0xE4D1B7));
            g.setColor(paladin.trim);g.fill(PaladinGear.star(x,41,2.6f,3.4f));
        }
        if(berserker!=null){
            berserkerArmor(g,p,side,berserker);
            return;
        }
        Shape clip=g.getClip();g.clip(p);
        g.setColor(new Color(0x93623F));g.fill(new Rectangle2D.Float(20, 46.5f, 25, 3.1f));g.setClip(clip);
        g.setColor(new Color(0xE9B45F));
        g.fill(new RoundRectangle2D.Float(side ? 37 : 30, 46, side ? 2 : 4, 4.2f, 0.7f, 0.7f));
        if (!side) {g.setColor(new Color(0x93623F));g.fill(new Rectangle2D.Float(31.1f, 47, 1.8f, 2));}
    }

    private static void berserkerArmor(Graphics2D graphics,Shape torso,boolean side,BerserkerGear gear){
        var g=(Graphics2D)graphics.create();
        g.clip(torso);
        Color leather=new Color(0x876451);
        g.setStroke(new BasicStroke(2.2f));g.setColor(leather);
        g.draw(new Line2D.Float(side?29:25,36,37,46.5f));
        if(!side)g.draw(new Line2D.Float(39,36,27,46.5f));
        g.setColor(new Color(0x41373D));g.fill(new Rectangle2D.Float(19,48,27,5.3f));
        g.setColor(gear.trim);g.setStroke(new BasicStroke(0.95f));
        g.draw(new Line2D.Float(side?28:24,49.5f,side?39:40,49.5f));
        if(gear.ordinal()>=BerserkerGear.RIDGE.ordinal()){
            g.draw(new Line2D.Float(25,52,29,52));
            if(!side)g.draw(new Line2D.Float(35,52,39,52));
        }
        g.setColor(leather);g.fill(new Rectangle2D.Float(19,45.8f,27,3.25f));
        float x=side?36.3f:32;
        g.setColor(gear.trim);g.fill(new RoundRectangle2D.Float(x-2.7f,45.1f,5.4f,4.65f,0.8f,0.8f));
        g.setColor(new Color(0x513C36));g.fill(new Rectangle2D.Float(x-1.55f,46.15f,3.1f,2.35f));
        g.dispose();
    }

    static void arm(Graphics2D g, CharacterOutfit o, float sx, float sy, float hx, float hy) {
        arm(g,o,sx,sy,hx,hy,null);
    }

    static void arm(Graphics2D g, CharacterOutfit o, float sx, float sy, float hx, float hy,BerserkerGear berserker) {
        Stroke old=g.getStroke();
        if (o.torso()!=null) {
            Shape sleeve=new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                    .createStrokedShape(new Line2D.Float(sx, sy, sx+(hx-sx)*0.38f, sy+(hy-sy)*0.38f));
            CharacterArt.softPart(g, sleeve, o.torso());
            if(o.theme(CharacterOutfit.Slot.TORSO)==CharacterOutfit.Theme.ARMOR){
                float angle=(float)Math.atan2(hy-sy,hx-sx);
                AffineTransform at=AffineTransform.getTranslateInstance(sx,sy+1);
                at.rotate(angle-Math.PI/2);
                Shape plate=at.createTransformedShape(new Ellipse2D.Float(-4,-2,8,6));
                if(berserker==null)CharacterArt.softPart(g,plate,CharacterArt.lighten(o.torso(),0.12f));
                else{
                    Shape lower=at.createTransformedShape(new RoundRectangle2D.Float(-3.7f,0.7f,7.4f,4.6f,3,3));
                    CharacterArt.softPart(g,lower,berserker.trim);
                    Color metal=ArtKit.mix(berserker.trim,new Color(0xE5CFB9),0.55f);
                    CharacterArt.softPart(g,plate,metal);
                    g.setColor(ArtKit.mix(berserker.trim,new Color(0x604742),0.25f));
                    g.fill(at.createTransformedShape(new Ellipse2D.Float(-0.8f,1.7f,1.6f,1.6f)));
                }
            }
        }
        if (o.gloves()!=null) {
            Shape glove=new BasicStroke(5.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                    .createStrokedShape(new Line2D.Float(sx+(hx-sx)*0.68f, sy+(hy-sy)*0.68f, hx, hy));
            CharacterArt.softPart(g, glove, o.gloves());
        }
        g.setStroke(old);
    }

    static void collar(Graphics2D g, CharacterOutfit o, boolean side){
        collar(g,o,side,null);
    }

    static void collar(Graphics2D g, CharacterOutfit o, boolean side,BerserkerGear berserker) {
        if(o.cloak()==null)return;
        Path2D p=new Path2D.Float();p.moveTo(side?28:24,34.5);p.lineTo(side?39:40,34.5);
        p.lineTo(side?39:40,39);p.lineTo(side?33:32,41);p.lineTo(side?27:24,38);p.closePath();
        CharacterArt.softPart(g,p,o.cloak());
        if(berserker!=null){
            Stroke old=g.getStroke();
            g.setStroke(new BasicStroke(1.05f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            g.setColor(CharacterArt.lighten(o.cloak(),0.21f));
            g.draw(new QuadCurve2D.Float(side?29:26,35.8f,side?34:33,38.3f,side?38:39,36.3f));
            g.setStroke(old);
        }
        if(o.theme(CharacterOutfit.Slot.CLOAK)==CharacterOutfit.Theme.CASTER){
            g.setColor(new Color(0xD9B77D));g.fill(new Ellipse2D.Float(side?34:30,36,4,4));
        }
    }

    private static void robe(Graphics2D g, CharacterOutfit o, boolean side,MageGear mage,PriestGear priest){
        Path2D p=new Path2D.Float();
        p.moveTo(side?28:26,34);p.lineTo(side?37:38,34);
        p.lineTo(side?41:43,56);p.lineTo(side?25:21,56);p.closePath();
        CharacterArt.softPart(g,p,o.torso());
        Path2D panel=new Path2D.Float();panel.moveTo(side?35:31,36);panel.lineTo(side?36:33,36);
        panel.lineTo(side?38:36,56);panel.lineTo(side?34:28,56);panel.closePath();
        CharacterArt.softPart(g,panel,priest!=null?priest.mantle:mage==null || mage==MageGear.HEARTH?new Color(0x655A73):new Color(0xE6D5C1));
        Stroke old=g.getStroke();g.setStroke(new BasicStroke(1.5f));g.setColor(priest!=null?priest.trim:mage==null?new Color(0xD9B77D):mage.trim);
        g.draw(new Line2D.Float(side?34:30,37,side?33:27,55));
        if(!side)g.draw(new Line2D.Float(34,37,37,55));
        g.draw(new Line2D.Float(side?26:22,54,side?40:42,54));
        if(priest!=null && priest!=PriestGear.WAY){
            float x=side?36:32;
            float size=priest==PriestGear.HEARTH?2.6f:1.8f;
            var insignia=new Path2D.Float();
            insignia.moveTo(x,43-size);insignia.lineTo(x+size,43);
            insignia.lineTo(x,43+size);insignia.lineTo(x-size,43);insignia.closePath();
            g.fill(insignia);
            if(priest==PriestGear.DAWN || priest==PriestGear.HEARTH){
                g.setStroke(new BasicStroke(0.8f));
                g.draw(new Line2D.Float(side?27:23,51.7f,side?39:41,51.7f));
                g.draw(new Ellipse2D.Float(x-3.4f,39.6f,6.8f,6.8f));
            }
        }
        g.setStroke(old);
    }

    static void longHair(Graphics2D g, CharacterOutfit o, boolean side, boolean back, float swing){
        HairArt.rear(g,o,side,back,swing);
    }

    /** Authoring only: older save appearances are exported alongside custom hairstyles. */
    static void legacyLongHair(Graphics2D g, CharacterOutfit o, boolean side, boolean back, float swing){
        if(o.hair()==null || !o.longHair())return;
        float flow=Math.abs(swing)*1.5f;
        Path2D p=new Path2D.Float();
        if(side){
            p.moveTo(16,18);p.lineTo(31,22);p.quadTo(29,34,32,42);
            p.quadTo(26,46,23,42);p.quadTo(20,47,16-flow,42);p.quadTo(12-flow,37,16,18);
        }else if(back){
            p.moveTo(15,18);p.lineTo(49,18);p.lineTo(49+flow,40);
            p.quadTo(47,46,43,42);p.quadTo(32,47,21,42);p.quadTo(16,46,14-flow,40);p.closePath();
        }else{
            p.moveTo(16,20);p.lineTo(23,24);p.lineTo(22,42);p.quadTo(17,47,14-flow,41);p.closePath();
            p.moveTo(41,24);p.lineTo(48,20);p.lineTo(50+flow,41);p.quadTo(47,47,42,42);p.closePath();
        }
        p.closePath();CharacterArt.softPart(g,p,CharacterArt.shade(o.hair(),0.94f));
    }

    static void hair(Graphics2D g, CharacterOutfit o, boolean side, boolean back) {
        HairArt.crown(g,o,side,back);
    }

    static void legacyHair(Graphics2D g, CharacterOutfit o, boolean side, boolean back) {
        if(o.hair()==null)return;
        Path2D p=new Path2D.Float();
        p.moveTo(14,17);p.lineTo(11,15);p.lineTo(18,5);
        p.quadTo(21,1.7,29,2.7);p.lineTo(33,2.7);p.lineTo(36,1.7);
        p.lineTo(35.5,2);p.quadTo(45,1,49,9);p.lineTo(52,15);p.lineTo(49,17);
        if(back) {
            p.lineTo(49,27);p.quadTo(47,33,41,33);p.lineTo(38,31);
            p.quadTo(27,35,19,31);p.lineTo(15,32);p.lineTo(14,25);
        } else if(side) {
            p.lineTo(48,24);p.lineTo(45,22);p.lineTo(43,11);
            p.quadTo(40,18,34,21);p.lineTo(32,25);p.lineTo(27,22);
            p.lineTo(22,29);p.lineTo(17,31);p.lineTo(14,26);
        } else {
            p.lineTo(48,24);p.lineTo(45,26);p.lineTo(44,20);
            p.quadTo(39,16,37,12);p.lineTo(34,22);p.quadTo(30,21,29,18);
            p.lineTo(27,14);p.lineTo(22,19);p.lineTo(21,26);p.lineTo(17,24);p.lineTo(16,19);
        }
        p.closePath();CharacterArt.softPart(g,p,o.hair());

    }

    /** A separate equipment layer perched above the crown, never part of the hair. */
    static void headpiece(Graphics2D g, CharacterOutfit outfit){
        if(outfit.headpiece()==null)return;
        Color fabric=outfit.headpiece(),gold=new Color(0xD9B77D);
        Path2D crown=new Path2D.Float();crown.moveTo(17,5);crown.lineTo(22,-4);crown.lineTo(18,-2);
        crown.quadTo(24,-13,29,-12);crown.quadTo(38,-9,45,3);crown.closePath();
        CharacterArt.softPart(g,crown,fabric);
        Stroke old=g.getStroke();g.setStroke(new BasicStroke(2.6f));g.setColor(new Color(0xF4E6CC));
        g.draw(new Line2D.Float(19,1,40,-3));g.setStroke(old);
        AffineTransform at=AffineTransform.getRotateInstance(-0.20,32,5);
        Shape brim=at.createTransformedShape(new Ellipse2D.Float(9,1,46,8));
        CharacterArt.softPart(g,brim,fabric);g.setStroke(new BasicStroke(1));g.setColor(gold);g.draw(brim);g.setStroke(old);
    }
}
