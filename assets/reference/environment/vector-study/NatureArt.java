package art;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

/** Hand-authored vector shapes, using the same surfaces and palette as the older foliage. */
public final class NatureArt {
    private NatureArt(){}
    private static final Color SHADOW=new Color(46,84,70,42);
    private static final Color[] FLOWERS={Palette.FLOWERS[1],Palette.FLOWERS[0],Palette.FLOWERS[3],
            Palette.FLOWERS[2],Palette.ROOF_SLATE};

    public static BufferedImage sprite(NatureProp prop){
        ArtKit.Surface surface=new ArtKit.Surface(prop.width*NatureProp.EXPORT_SCALE,prop.height*NatureProp.EXPORT_SCALE);
        Graphics2D g=surface.g;
        g.scale(NatureProp.EXPORT_SCALE,NatureProp.EXPORT_SCALE);
        g.translate(prop.footX,prop.footY);
        int index=prop.ordinal();
        if(index<6)flowers(g,index);
        else if(index<12){
            int color=(index-6)%3;
            if(index<9)mushroom(g,0,0,1,color);
            else {mushroom(g,0,-7,1,color);mushroom(g,-12,0,.67,color);mushroom(g,12,1,.67,color);}
        } else if(index<18)stump(g,index>=15,(index-12)%3);
        else twig(g);
        return surface.finish();
    }

    private static void oval(Graphics2D g,Color color,double x,double y,double width,double height){
        g.setColor(color);g.fill(new Ellipse2D.Double(x,y,width,height));
    }

    /** Broad cel crescents rather than pixel thresholds or painted grain. */
    private static void volume(Graphics2D g,Shape shape,Color base,double depth){
        g.setColor(base);g.fill(shape);
        Area shade=new Area(shape);
        shade.subtract(new Area(AffineTransform.getTranslateInstance(-depth,-depth*.55).createTransformedShape(shape)));
        g.setColor(ArtKit.shade(base));g.fill(shade);
        Area light=new Area(shape);
        light.subtract(new Area(AffineTransform.getTranslateInstance(depth*.45,depth*.25).createTransformedShape(shape)));
        g.setColor(ArtKit.light(base));g.fill(light);
    }

    private static void flowers(Graphics2D g,int variant){
        oval(g,SHADOW,-25,-5,50,7);
        for(int i=0;i<9;i++){
            double angle=i*Math.PI*2/9,x=Math.cos(angle)*18,y=-9+Math.sin(angle)*7;
            AffineTransform transform=AffineTransform.getTranslateInstance(x,y);
            transform.rotate(angle*.35);
            Shape leaf=transform.createTransformedShape(new Ellipse2D.Double(-8,-3,16,6));
            volume(g,leaf,i%2==0?Palette.FOLIAGE:Palette.FOLIAGE_DARK,1.8);
        }
        double[][] positions={{-17,-12,4.8},{0,-20,5.4},{17,-13,4.8},{-6,-7,4.4},{9,-5,3.6}};
        for(int i=0;i<positions.length;i++){
            double[] p=positions[i];Color petals=FLOWERS[variant==5?i%FLOWERS.length:variant];
            Area bloom=new Area();
            for(int petal=0;petal<5;petal++){
                double angle=petal*Math.PI*2/5;
                double x=p[0]+Math.cos(angle)*p[2]*.55,y=p[1]+Math.sin(angle)*p[2]*.40;
                bloom.add(new Area(new Ellipse2D.Double(x-p[2]*.52,y-p[2]*.40,p[2]*1.04,p[2]*.80)));
            }
            volume(g,bloom,petals,1.1);
            oval(g,Palette.FLOWERS[2],p[0]-1.6,p[1]-1.2,3.2,2.4);
        }
    }

    private static void mushroom(Graphics2D parent,double x,double y,double scale,int color){
        Graphics2D g=(Graphics2D)parent.create();g.translate(x,y);g.scale(scale,scale);
        oval(g,SHADOW,-11,-3,22,5);
        Shape stem=new RoundRectangle2D.Double(-3,-14,6,13,4,4);
        volume(g,stem,Palette.PLASTER,1.5);
        Path2D cap=new Path2D.Double();
        cap.moveTo(-12,-11);cap.curveTo(-11,-19,-6,-25,0,-25);
        cap.curveTo(7,-25,12,-18,12,-11);cap.curveTo(7,-7,-7,-7,-12,-11);cap.closePath();
        Color capColor=color==0?Palette.ROOF_CORAL:color==1?Palette.WOOD:Palette.PLASTER;
        volume(g,cap,capColor,2.4);
        if(color==0){
            oval(g,Palette.FLOWERS[1],-7,-18,4,3);oval(g,Palette.FLOWERS[1],1,-22,4,3);
            oval(g,Palette.FLOWERS[1],5,-15,3.2,2.4);
        } else oval(g,ArtKit.light(capColor),-6,-21,7,3.2);
        g.dispose();
    }

    private static void stump(Graphics2D g,boolean pine,int variant){
        double width=pine?48:68,top=pine?-43:-37;
        oval(g,SHADOW,-width*.46,-5,width*.92,9);
        Path2D body=new Path2D.Double();
        body.moveTo(-width*.30,top);body.curveTo(-width*.31,top+14,-width*.30,-13,-width*.49,-3);
        body.quadTo(-width*.52,1,-width*.29,-2);body.quadTo(-width*.22,-1,-width*.15,3);
        body.quadTo(-width*.05,5,width*.07,0);body.quadTo(width*.28,4,width*.25,-2);
        body.quadTo(width*.51,2,width*.49,-3);body.curveTo(width*.31,-15,width*.29,top+11,width*.30,top);
        body.closePath();volume(g,body,Palette.TRUNK,5);
        for(int i=0;i<3;i++){
            double x=(i-1)*width*.18;
            Path2D root=new Path2D.Double();root.moveTo(x-2,top+11);
            root.curveTo(x-1,-15,x-4,-6,x-8,-2);root.quadTo(x-2,0,x+5,-2);
            root.curveTo(x+1,-9,x+3,-20,x+2,top+10);root.closePath();
            g.setColor(i==0?ArtKit.light(Palette.TRUNK):ArtKit.shade(Palette.TRUNK));g.fill(root);
        }
        Shape cut=new Ellipse2D.Double(-width*.31,top-5,width*.62,13);
        volume(g,cut,Palette.WOOD_LIGHT,2);
        g.setStroke(new BasicStroke(1.1f));g.setColor(ArtKit.mix(Palette.WOOD,Palette.WOOD_LIGHT,.35f));
        g.draw(new Ellipse2D.Double(-width*.22,top-2,width*.44,7));
        g.draw(new Ellipse2D.Double(-width*.11,top,width*.22,3.6));
        if(variant==2){
            Path2D broken=new Path2D.Double();broken.moveTo(-width*.28,top+4);
            broken.lineTo(-width*.29,top-10);broken.lineTo(-width*.20,top-3);broken.lineTo(-width*.13,top-9);
            broken.lineTo(-width*.09,top+1);broken.lineTo(width*.08,top+3);broken.lineTo(width*.18,top-8);
            broken.lineTo(width*.23,top-1);broken.lineTo(width*.30,top-7);broken.lineTo(width*.29,top+7);
            broken.lineTo(width*.13,top+5);broken.lineTo(0,top+8);broken.closePath();
            volume(g,broken,Palette.TRUNK,1.8);
        }
        if(variant>0){
            Area moss=new Area(new Ellipse2D.Double(-width*.34,top-3,width*.22,9));
            moss.add(new Area(new Ellipse2D.Double(-width*.33,top+2,width*.14,15)));
            volume(g,moss,Palette.FOLIAGE,1.8);
            volume(g,new Ellipse2D.Double(width*.13,-8,width*.20,7),Palette.FOLIAGE,1.5);
        }
    }

    private static void twig(Graphics2D g){
        Path2D branch=new Path2D.Double();branch.moveTo(-15,-2);branch.curveTo(-5,-5,5,-10,14,-14);
        branch.moveTo(-3,-6);branch.quadTo(-4,-11,-2,-16);
        branch.moveTo(5,-10);branch.quadTo(11,-9,16,-10);
        g.setStroke(new BasicStroke(3.8f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.setColor(Palette.TRUNK);g.draw(branch);
        g.translate(-.3,-.7);g.setStroke(new BasicStroke(1.2f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.setColor(Palette.WOOD_LIGHT);g.draw(branch);
    }

    public static void field(Graphics2D g,int type,int variant,double bend){
        double scale=variant==0?1:.84;
        g.translate(48,96);g.scale(scale,scale);
        Color base=type==5?Palette.ROOF_THATCH:type==4?Palette.FOLIAGE_DARK:Palette.FOLIAGE;
        Color shade=type==5?ArtKit.mix(Palette.GOLD,Palette.WOOD,.4f):ArtKit.shade(base);
        Color light=type==5?Palette.GOLD:ArtKit.mix(base,Palette.FOLIAGE_LIGHT,.55f);
        oval(g,new Color(46,84,70,28),-20,-4,40,7);
        for(int i=0;i<13;i++){
            double spread=i/12.0-.5,x=spread*23;
            double height=43+((i*17+variant*11)%29),lean=spread*44+bend*height*.20;
            double width=7+(i%3)*1.5;
            Path2D blade=new Path2D.Double();blade.moveTo(x-width*.5,0);
            blade.curveTo(x-width*.7,-height*.45,x+lean*.35-width*.55,-height*.78,x+lean,-height);
            blade.curveTo(x+lean*.57+width*.65,-height*.70,x+width*.7,-height*.30,x+width*.5,0);
            blade.closePath();g.setColor(i%3==0?shade:i%3==1?base:light);g.fill(blade);
            // A single narrow curved plane gives the broad leaves the same depth as older foliage.
            if(i%3==1){
                Path2D plane=new Path2D.Double();plane.moveTo(x,0);
                plane.curveTo(x+1,-height*.4,x+lean*.4,-height*.8,x+lean,-height);
                plane.curveTo(x+lean*.5+1,-height*.7,x+width*.45,-height*.3,x+width*.45,0);plane.closePath();
                g.setColor(shade);g.fill(plane);
            }
        }
        for(int i=0;i<3;i++){
            double x=(i-1)*12,h=76+i*3,lean=(i-1)*7+bend*h*.18;
            Path2D stem=new Path2D.Double();stem.moveTo(x,0);stem.quadTo(x+lean*.35,-h*.55,x+lean,-h);
            g.setStroke(new BasicStroke(1.3f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.setColor(base);g.draw(stem);
            for(int grain=0;grain<4;grain++){
                double y=-h+grain*2.7,cx=x+lean*(1-grain*.018);
                oval(g,Palette.GLOW,cx-3.1,y,3.8,4.3);oval(g,Palette.WOOD_LIGHT,cx+.1,y+.8,3.5,4);
            }
        }
    }
}
