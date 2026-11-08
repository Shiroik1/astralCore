package art;

import java.awt.*;
import java.awt.geom.*;

/** Equipment layers share the base rig's posed limbs; nothing is baked into the skin. */
final class CharacterClothing {
    private CharacterClothing() {}
    static void cloak(Graphics2D g, CharacterOutfit o, boolean side, boolean back, float swing) {
        if (o.cloak() == null) return;
        float trail = Math.abs(swing) * 3;
        Path2D p = new Path2D.Float();
        if (side) {
            p.moveTo(29, 34); p.lineTo(35, 36); p.lineTo(30, 56);
            p.quadTo(25, 57, 17 - trail, 53); p.quadTo(24 - trail, 44, 29, 34);
        } else {
            p.moveTo(25, 35); p.lineTo(39, 35); p.lineTo(45 + trail, 55);
            p.quadTo(32, 58, 19 - trail, 55); p.closePath();
        }
        p.closePath(); CharacterArt.softPart(g, p, o.cloak());
        if(o.theme(CharacterOutfit.Slot.CLOAK)!=CharacterOutfit.Theme.LEATHER){
            Shape clip=g.getClip();g.clip(p);Stroke old=g.getStroke();
            g.setColor(new Color(0xD9B77D));g.setStroke(new BasicStroke(2.5f));g.draw(p);
            g.setStroke(old);g.setClip(clip);
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

    static void torso(Graphics2D g, CharacterOutfit o, boolean side) {
        if (o.torso() == null) return;
        if(o.theme(CharacterOutfit.Slot.TORSO)==CharacterOutfit.Theme.CASTER){
            robe(g,o,side);return;
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
        Shape clip=g.getClip();g.clip(p);
        g.setColor(new Color(0x93623F));g.fill(new Rectangle2D.Float(20, 46.5f, 25, 3.1f));g.setClip(clip);
        g.setColor(new Color(0xE9B45F));
        g.fill(new RoundRectangle2D.Float(side ? 37 : 30, 46, side ? 2 : 4, 4.2f, 0.7f, 0.7f));
        if (!side) {g.setColor(new Color(0x93623F));g.fill(new Rectangle2D.Float(31.1f, 47, 1.8f, 2));}
    }

    static void arm(Graphics2D g, CharacterOutfit o, float sx, float sy, float hx, float hy) {
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
                CharacterArt.softPart(g,plate,CharacterArt.lighten(o.torso(),0.12f));
            }
        }
        if (o.gloves()!=null) {
            Shape glove=new BasicStroke(5.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                    .createStrokedShape(new Line2D.Float(sx+(hx-sx)*0.68f, sy+(hy-sy)*0.68f, hx, hy));
            CharacterArt.softPart(g, glove, o.gloves());
        }
        g.setStroke(old);
    }

    static void collar(Graphics2D g, CharacterOutfit o, boolean side) {
        if(o.cloak()==null)return;
        Path2D p=new Path2D.Float();p.moveTo(side?28:24,34.5);p.lineTo(side?39:40,34.5);
        p.lineTo(side?39:40,39);p.lineTo(side?33:32,41);p.lineTo(side?27:24,38);p.closePath();
        CharacterArt.softPart(g,p,o.cloak());
        if(o.theme(CharacterOutfit.Slot.CLOAK)==CharacterOutfit.Theme.CASTER){
            g.setColor(new Color(0xD9B77D));g.fill(new Ellipse2D.Float(side?34:30,36,4,4));
        }
    }

    private static void robe(Graphics2D g, CharacterOutfit o, boolean side){
        Path2D p=new Path2D.Float();
        p.moveTo(side?28:26,34);p.lineTo(side?37:38,34);
        p.lineTo(side?41:43,56);p.lineTo(side?25:21,56);p.closePath();
        CharacterArt.softPart(g,p,o.torso());
        Path2D panel=new Path2D.Float();panel.moveTo(side?35:31,36);panel.lineTo(side?36:33,36);
        panel.lineTo(side?38:36,56);panel.lineTo(side?34:28,56);panel.closePath();
        CharacterArt.softPart(g,panel,new Color(0x655A73));
        Stroke old=g.getStroke();g.setStroke(new BasicStroke(1.5f));g.setColor(new Color(0xD9B77D));
        g.draw(new Line2D.Float(side?34:30,37,side?33:27,55));
        if(!side)g.draw(new Line2D.Float(34,37,37,55));
        g.draw(new Line2D.Float(side?26:22,54,side?40:42,54));g.setStroke(old);
    }

    static void longHair(Graphics2D g, CharacterOutfit o, boolean side, boolean back, float swing){
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
