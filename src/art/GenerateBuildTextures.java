package art;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;

/** Exact grid artwork exported offline. Geometry is shared with the gameplay collision shapes. */
public final class GenerateBuildTextures {
    private static Color WOOD=new Color(0xB48B62), LIGHT=new Color(0xD9B88C),
            DARK=new Color(0x8A6B58), INNER=new Color(0x795E49), IRON=new Color(0x6E6A78);
    private static final int SS=4, EXPORT=2;
    private static String material="wood";
    private GenerateBuildTextures(){}
    public static void main(String[] args)throws Exception{
        Path out=Path.of(args.length==0?"res/building":args[0]);Files.createDirectories(out);
        for(String mat:new String[]{"wood","cobblestone","reinforced","concrete"}){
            material=mat;
            boolean stone=mat.equals("cobblestone") || mat.equals("concrete");
            WOOD=new Color(stone?0xBDB6B2:0xB48B62);LIGHT=new Color(stone?0xDFD8CF:0xD9B88C);
            DARK=new Color(stone?0x948D90:0x8A6B58);INNER=new Color(stone?0x7E787C:0x795E49);
            for(String kind:mat.equals("wood")?new String[]{"floor","wall","door","door-open"}:new String[]{"floor","wall"}){
            boolean floor=kind.equals("floor");int height=floor?48:BuildGeometry.IMAGE_HEIGHT,count=floor?16:32;
            BufferedImage atlas=new BufferedImage(48*EXPORT*count,height*EXPORT,BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=atlas.createGraphics();
            for(int variant=0;variant<count;variant++)g.drawImage(tile(kind,variant&15,variant>=16,height),variant*48*EXPORT,0,null);
            g.dispose();ImageIO.write(atlas,"png",out.resolve((mat.equals("wood")?"":mat+"-")+kind+".png").toFile());
            }
        }
        for(String mat:new String[]{"wood","stone","concrete"}){
            BufferedImage atlas=new BufferedImage(48*EXPORT*16,48*EXPORT,BufferedImage.TYPE_INT_ARGB);Graphics2D g=atlas.createGraphics();
            for(int mask=0;mask<16;mask++){
                BufferedImage high=new BufferedImage(48*SS,48*SS,BufferedImage.TYPE_INT_ARGB);Graphics2D s=high.createGraphics();s.scale(SS,SS);
                s.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);stairs(s,mask,mat);s.dispose();
                BufferedImage stair=new BufferedImage(48*EXPORT,48*EXPORT,BufferedImage.TYPE_INT_ARGB);
                Graphics2D scaled=stair.createGraphics();scaled.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                scaled.drawImage(high,0,0,48*EXPORT,48*EXPORT,null);scaled.dispose();
                g.drawImage(stair,mask*48*EXPORT,0,null);
            }
            g.dispose();ImageIO.write(atlas,"png",out.resolve("stairs-"+mat+".png").toFile());
        }
    }
    private static BufferedImage tile(String kind,int mask,boolean vertical,int height){
        BufferedImage high=new BufferedImage(48*SS,height*SS,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=high.createGraphics();g.scale(SS,SS);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        if(kind.equals("floor"))floor(g,mask);
        else{
            g.translate(0,BuildGeometry.HEIGHT);
            if(kind.startsWith("door"))door(g,mask,vertical,kind.equals("door-open"));
            else wall(g,mask,vertical);
        }
        g.dispose();BufferedImage image=new BufferedImage(48*EXPORT,height*EXPORT,BufferedImage.TYPE_INT_ARGB);
        g=image.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(high,0,0,image.getWidth(),image.getHeight(),null);g.dispose();
        return image;
    }
    private static void floor(Graphics2D g,int mask){
        if(!material.equals("wood") && !material.equals("reinforced")){masonryFloor(g,mask);return;}
        rect(g,WOOD,0,0,48,48);
        Color[] planks={new Color(0xC29B72),new Color(0xBC946C),new Color(0xC7A17A),new Color(0xB99168)};
        for(int row=0;row<6;row++){
            rect(g,planks[row%4],0,row*8,48,7.4);
            rect(g,new Color(0xAC855F),0,row*8+7.4,48,.6);
            int joint=row%2==0?17:35;rect(g,new Color(0xAC855F),joint,row*8,.65,7.4);
            rect(g,new Color(0xD0AD87),0,row*8,48,.6);
        }
        if((mask&1)==0){rect(g,DARK,0,0,48,3);rect(g,LIGHT,1,0,46,1);}
        if((mask&4)==0){rect(g,DARK,0,45,48,3);rect(g,WOOD,1,45,46,1.5);}
        if((mask&8)==0){rect(g,DARK,0,0,3,48);rect(g,LIGHT,0,1,1,46);}
        if((mask&2)==0){rect(g,DARK,45,0,3,48);rect(g,WOOD,45,1,1.5,46);}
        if(material.equals("reinforced"))for(int y:new int[]{5,43})for(int x:new int[]{5,43}){
            if(((mask&(x<24?8:2))==0) || ((mask&(y<24?1:4))==0)){
                rect(g,new Color(0x948D90),x-2,y-2,4,4);rect(g,new Color(0xD3CCC8),x-.6,y-.6,1.2,1.2);
            }
        }
    }
    private static Area area(List<Rectangle> pieces){
        Area result=new Area();for(Rectangle r:pieces)result.add(new Area(r));return result;
    }
    private static void wall(Graphics2D g,int mask,boolean vertical){
        Area plan=area(BuildGeometry.footprint(mask,vertical,false,false));
        // Extrude only exposed south-facing edges. Connected vertical modules share one continuous cap.
        for(int y:new int[]{32,48}){
            int start=-1;
            for(int x=0;x<=48;x++){
                boolean face=x<48 && plan.contains(x+.5,y-.5) && !plan.contains(x+.5,y+.5);
                if(y==48 && (mask&4)!=0 && x>=16 && x<32)face=false;
                if(face && start<0)start=x;
                if(!face && start>=0){face(g,start,y,x-start,!(start==0 && (mask&8)!=0),!(x==48 && (mask&2)!=0));start=-1;}
            }
        }
        cap(g,plan,mask);
    }
    private static void cap(Graphics2D g,Area plan,int mask){
        int h=BuildGeometry.HEIGHT;
        Area cap=new Area(plan);cap.transform(AffineTransform.getTranslateInstance(0,-h));
        g.setColor(WOOD);g.fill(cap);
        Shape original=g.getClip();g.clip(cap);
        // Stroke exposed boundary segments, not the boundaries of individual atlas cells.
        // Butt-ended strokes keep the long inner border continuous at every connected seam.
        PathIterator path=plan.getPathIterator(null);double[] point=new double[6];
        double startX=0,startY=0,lastX=0,lastY=0;
        while(!path.isDone()){
            int type=path.currentSegment(point);
            if(type==PathIterator.SEG_MOVETO){startX=lastX=point[0];startY=lastY=point[1];}
            else if(type==PathIterator.SEG_LINETO){
                capEdge(g,plan,mask,lastX,lastY,point[0],point[1],h);lastX=point[0];lastY=point[1];
            }else if(type==PathIterator.SEG_CLOSE)capEdge(g,plan,mask,lastX,lastY,startX,startY,h);
            path.next();
        }
        g.setClip(original);
    }
    private static void capEdge(Graphics2D g,Area plan,int mask,double x1,double y1,double x2,double y2,int height){
        if(x1==x2 && ((x1==0 && (mask&8)!=0) || (x1==48 && (mask&2)!=0)))return;
        if(y1==y2 && y1==0 && (mask&1)!=0)return;
        if(y1==y2 && y1==48 && (mask&4)!=0){
            double left=Math.min(x1,x2),right=Math.max(x1,x2);
            if(left<16)capEdge(g,plan,mask&~4,left,y1,Math.min(16,right),y2,height);
            if(right>32)capEdge(g,plan,mask&~4,Math.max(32,left),y1,right,y2,height);
            return;
        }
        Line2D edge=new Line2D.Double(x1,y1-height,x2,y2-height);
        g.setColor(INNER);
        g.setStroke(new BasicStroke(5f,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER));g.draw(edge);
        g.setColor(DARK);g.setStroke(new BasicStroke(.8f,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER));g.draw(edge);
    }
    private static void face(Graphics2D g,int x,int bottom,int width,boolean leftEnd,boolean rightEnd){
        int top=bottom-BuildGeometry.HEIGHT;
        if(material.equals("cobblestone") || material.equals("concrete")){masonryFace(g,x,top,width,bottom);return;}
        rect(g,DARK,x,top,width,BuildGeometry.HEIGHT);
        Color[] boards={new Color(0xBE956D),new Color(0xB99068),new Color(0xC29B73),new Color(0xB58D65)};
        for(int y=top,row=0;y<bottom-6;y+=10,row++){
            rect(g,boards[row%4],x,y,width,Math.min(9,bottom-6-y));
            rect(g,new Color(0xD0AD87),x,y,width,.8);
        }
        if(material.equals("reinforced")){
            rect(g,new Color(0x948D90),x,bottom-24,width,18);
            for(int row=0;row<2;row++)for(int bx=x-12;bx<x+width;bx+=16){
                Shape clip=g.getClip();g.clip(new Rectangle2D.Double(x,bottom-24,width,18));
                rect(g,new Color(row==0?0xCEC8C5:0xBDB6B2),bx+(row%2)*8,bottom-23+row*9,15,8);g.setClip(clip);
            }
        }
        rect(g,DARK,x,bottom-6,width,6);rect(g,WOOD,x,bottom-6,width,3);rect(g,LIGHT,x,bottom-6,width,1);
        if(leftEnd)rect(g,new Color(0x9B7657),x,top,1.5,BuildGeometry.HEIGHT);
        if(rightEnd)rect(g,new Color(0xA27C59),x+width-1.5,top,1.5,BuildGeometry.HEIGHT);
    }
    private static void door(Graphics2D g,int mask,boolean preferred,boolean open){
        boolean vertical=BuildGeometry.vertical(mask,preferred);int h=BuildGeometry.HEIGHT;
        if(vertical){
            // Side doors use the same continuous raised cap as the north/south walls.
            if(!open){
                wall(g,mask&5,true);
                rect(g,new Color(0xA17B59),20,3-h,8,42);
                for(int y=8-h;y<48-h;y+=8)rect(g,LIGHT,20,y,8,.8);
                g.setColor(IRON);g.fill(new Ellipse2D.Double(23,22-h,4,5));
            }else{
                // Cut away the side-facing leaf so its open passage is visibly clear.
                rect(g,DARK,16,-h,16,6);rect(g,WOOD,17,1-h,14,4);rect(g,LIGHT,17,1-h,1,4);
                rect(g,DARK,16,42-h,16,6);rect(g,WOOD,17,43-h,14,4);rect(g,LIGHT,17,43-h,1,4);
                Path2D leaf=new Path2D.Double();leaf.moveTo(16,5-h);leaf.lineTo(6,11-h);leaf.lineTo(6,43-h);leaf.lineTo(16,40-h);leaf.closePath();
                g.setColor(DARK);g.fill(leaf);rect(g,WOOD,8,13-h,4,24);
            }
            return;
        }
        int top=48-h;
        if(!open){
            rect(g,DARK,5,top,38,h);
            for(int x=5;x<43;x+=6){rect(g,WOOD,x,top+1,Math.min(5.4,43-x),h-3);rect(g,new Color(0xC49D74),x,top+1,1,h-3);}
            for(int y:new int[]{top+19,top+57}){
                g.setColor(IRON);g.fill(new RoundRectangle2D.Double(6,y,21,4,1.5,1.5));
                rect(g,new Color(0x96909A),10,y+1,1.3,1.3);rect(g,new Color(0x96909A),23,y+1,1.3,1.3);
            }
            g.setColor(IRON);g.setStroke(new BasicStroke(1.6f));g.draw(new Ellipse2D.Double(33,top+39,5,7));
        }else{
            Path2D leaf=new Path2D.Double();leaf.moveTo(5,top);leaf.lineTo(11,top+6);leaf.lineTo(11,46);leaf.lineTo(5,48);leaf.closePath();
            g.setColor(DARK);g.fill(leaf);rect(g,WOOD,6,top+5,2,h-8);
        }
        // 38 px of clear width for the player's 32 px collision box.
        rect(g,DARK,0,top,5,h);rect(g,WOOD,1,top,3,h);rect(g,LIGHT,1,top,1,h);
        rect(g,DARK,43,top,5,h);rect(g,WOOD,44,top,3,h);
        cap(g,area(BuildGeometry.footprint(mask,false,true,false)),mask);
    }
    private static void masonryFloor(Graphics2D g,int mask){
        rect(g,new Color(0x948D90),0,0,48,48);
        if(material.equals("concrete")){
            for(int row=0;row<2;row++)for(int col=0;col<2;col++){
                rect(g,new Color((row+col)%2==0?0xD3CCC8:0xCEC8C5),col*24+.4,row*24+.4,23.2,23.2);
                rect(g,new Color(0xDFD8CF),col*24+1,row*24+1,22,.7);
            }
        }else for(int row=0;row<4;row++)for(int col=-1;col<4;col++){
            double x=col*16+(row%2)*8,y=row*12;
            g.setColor(new Color((col+row+4)%3==0?0xD3CCC8:(col+row+4)%3==1?0xCEC8C5:0xC1BAB8));
            g.fill(new RoundRectangle2D.Double(x+.6,y+.6,14.8,10.8,4,3));
        }
        border(g,mask);
    }
    private static void border(Graphics2D g,int mask){
        if((mask&1)==0){rect(g,DARK,0,0,48,3);rect(g,LIGHT,0,0,48,1);}
        if((mask&2)==0)rect(g,DARK,45,0,3,48);
        if((mask&4)==0)rect(g,DARK,0,45,48,3);
        if((mask&8)==0){rect(g,DARK,0,0,3,48);rect(g,LIGHT,0,0,1,48);}
    }
    private static void masonryFace(Graphics2D g,int x,int top,int width,int bottom){
        rect(g,DARK,x,top,width,bottom-top);
        Shape clip=g.getClip();g.clip(new Rectangle2D.Double(x,top,width,bottom-top));
        if(material.equals("concrete")){
            rect(g,new Color(0xD3CCC8),x,top,width,bottom-top-5);rect(g,new Color(0xCEC8C5),x,top+3,width,2);
        }else for(int row=0;row<7;row++)for(int col=-1;col<4;col++){
            g.setColor(new Color((row+col+4)%3==0?0xD3CCC8:(row+col+4)%3==1?0xCEC8C5:0xBDB6B2));
            g.fill(new RoundRectangle2D.Double(col*18+(row%2)*9+.6,top+row*11+.6,16.8,9.7,3,3));
        }
        rect(g,DARK,x,bottom-5,width,5);rect(g,LIGHT,x,bottom-5,width,1);g.setClip(clip);
    }
    private static void stairs(Graphics2D g,int mask,String mat){
        Color base=new Color(mat.equals("wood")?0xB48B62:mat.equals("stone")?0xCEC8C5:0xD3CCC8);
        Color dark=new Color(mat.equals("wood")?0x8A6B58:0x948D90);
        rect(g,base,0,0,48,48);
        for(int y=0;y<48;y+=8){rect(g,dark,0,y+5,48,3);rect(g,new Color(mat.equals("wood")?0xD9B88C:0xE0D9D1),0,y,48,1.3);}
        if((mask&8)==0){rect(g,dark,0,0,5,48);rect(g,base,1,0,3,48);}
        if((mask&2)==0){rect(g,dark,43,0,5,48);rect(g,base,44,0,3,48);}
        if((mask&1)==0)rect(g,dark,0,0,48,2);
        if((mask&4)==0)rect(g,dark,0,46,48,2);
    }
    private static void rect(Graphics2D g,Color color,double x,double y,double w,double h){g.setColor(color);g.fill(new Rectangle2D.Double(x,y,w,h));}
}
