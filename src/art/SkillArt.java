package art;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.ConcurrentHashMap;

/** Cached, readable pictograms that depict the actual class ability. */
public final class SkillArt {
    private SkillArt(){}
    private static final ConcurrentHashMap<String,BufferedImage> CACHE=new ConcurrentHashMap<>();
    public static BufferedImage icon(String path){return CACHE.computeIfAbsent(path.substring(path.lastIndexOf('/')+1),SkillArt::draw);}
    private static BufferedImage draw(String key){
        BufferedImage image=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.BLACK);g.fillRoundRect(1,1,62,62,10,10);g.setStroke(new BasicStroke(2));g.setColor(Color.WHITE);g.drawRoundRect(2,2,60,60,9,9);
        Color accent=key.startsWith("mage")?new Color(0xB7A3F0):key.startsWith("hunter")?new Color(0x8DD0AB):new Color(0xAACBE1);
        g.setColor(accent);g.setStroke(new BasicStroke(4,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        switch(key){
            case "hunter_twinfang" -> {blade(g,24,35,-25,22);blade(g,41,35,25,22);}
            case "hunter_focus" -> {g.draw(new Ellipse2D.Float(12,22,40,21));g.fillOval(27,27,10,10);g.drawLine(32,12,32,20);g.drawLine(32,45,32,52);}
            case "hunter_haste" -> {boot(g);g.drawLine(9,18,28,18);g.drawLine(12,26,26,26);g.drawLine(16,34,25,34);}
            case "hunter_rend" -> {for(int i=0;i<3;i++)g.drawLine(20+i*11,16,11+i*11,44);g.setColor(new Color(0xE36F72));g.fillOval(39,42,10,13);}
            case "hunter_evasion" -> {boot(g);g.drawArc(8,8,44,44,70,220);g.drawLine(7,31,8,45);g.drawLine(8,45,20,42);}
            case "mage_manainfusion" -> {g.fillOval(22,24,21,21);g.drawLine(32,9,32,20);g.drawLine(27,14,37,14);for(int x:new int[]{13,50})g.fillOval(x-3,28,6,6);}
            case "mage_detonation" -> {for(int i=0;i<8;i++){double a=i*Math.PI/4;g.drawLine(32+(int)(Math.cos(a)*17),32+(int)(Math.sin(a)*17),32+(int)(Math.cos(a)*24),32+(int)(Math.sin(a)*24));}g.fillOval(21,21,22,22);}
            case "mage_haste" -> {g.drawOval(10,12,42,42);Path2D p=new Path2D.Float();p.moveTo(35,9);p.lineTo(22,33);p.lineTo(34,33);p.lineTo(29,54);p.lineTo(46,27);p.lineTo(35,27);g.fill(p);}
            case "mage_ignite" -> {Path2D p=new Path2D.Float();p.moveTo(32,8);p.curveTo(28,26,13,26,17,44);p.curveTo(24,61,51,52,47,34);p.lineTo(37,21);p.lineTo(34,37);p.closePath();g.setColor(new Color(0xEEA46F));g.fill(p);}
            case "mage_manashield" -> {shield(g,32,33,18);g.setColor(Color.WHITE);g.drawOval(25,23,14,14);}
            case "warrior_bash" -> {shield(g,28,33,17);for(int y:new int[]{18,30,42})g.drawLine(46,y,56,y-3);}
            case "warrior_bulwark" -> {g.drawLine(11,52,53,52);shield(g,32,30,21);g.setColor(Color.WHITE);g.drawLine(32,16,32,40);g.drawLine(22,27,42,27);}
            case "warrior_retaliation" -> {shield(g,28,34,15);g.drawArc(14,8,42,44,-15,220);g.drawLine(54,24,54,38);g.drawLine(54,38,42,33);}
            case "warrior_breaker" -> {shield(g,38,37,16);g.setColor(Color.BLACK);g.drawLine(38,22,33,36);g.drawLine(33,36,42,38);g.drawLine(42,38,34,52);g.setColor(accent);blade(g,20,31,30,24);}
            case "warrior_rally" -> {g.drawLine(19,12,19,54);g.fillRect(21,12,29,22);g.setColor(Color.WHITE);g.drawLine(34,16,34,29);g.drawLine(28,22,40,22);}
            default -> {g.drawOval(14,14,36,36);g.drawLine(32,22,32,42);g.drawLine(22,32,42,32);}
        }
        g.dispose();return image;
    }
    private static void shield(Graphics2D g,float x,float y,float r){Path2D p=new Path2D.Float();p.moveTo(x-r,y-r);p.quadTo(x,y-r-7,x+r,y-r);p.lineTo(x+r-3,y+5);p.quadTo(x,y+r+9,x-r+3,y+5);p.closePath();g.draw(p);}
    private static void blade(Graphics2D g,float x,float y,float angle,float size){AffineTransform old=g.getTransform();g.translate(x,y);g.rotate(Math.toRadians(angle));g.drawLine(0,10,0,-(int)size);g.drawLine(-5,4,5,4);g.setTransform(old);}
    private static void boot(Graphics2D g){Path2D p=new Path2D.Float();p.moveTo(29,17);p.lineTo(42,17);p.lineTo(40,38);p.lineTo(51,42);p.lineTo(51,50);p.lineTo(24,50);p.lineTo(24,38);p.closePath();g.draw(p);}
}
