package art;

import entity.SpriteAnimation;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.Map;

/** Sage jelly domes, soft ivory rims and pose-driven crowns; no mouth/spit body animation. */
public final class SlimeArt {
    private SlimeArt() {}
    public static final int CANVAS_SIZE = 72;
    private static final int HI = 320;
    private record AtlasKey(int size,float scale,float offset,boolean king) {}
    private static final java.util.concurrent.ConcurrentMap<AtlasKey,Map<String,SpriteAnimation>> ATLASES=
            new java.util.concurrent.ConcurrentHashMap<>();
    public static final Color SAGE = Palette.FOLIAGE, INK = new Color(0x49524F);
    private static final Color RIM = new Color(0xC9D6B2), IVORY = new Color(0xF4EDD6);

    private record Pose(float width, float height, float bob, float lean, float anger,
                        float dust, float death, boolean hurt) {}

    public static void buildAnimations(Map<String, SpriteAnimation> sprites, int size, float scale) {
        buildAnimations(sprites,size,scale,0,false);
    }
    public static void buildAnimations(Map<String, SpriteAnimation> sprites, int size, float scale, float offset) {
        buildAnimations(sprites,size,scale,offset,false);
    }
    public static void buildBossAnimations(Map<String, SpriteAnimation> sprites, int size, float scale, float offset) {
        buildAnimations(sprites,size,scale,offset,true);
    }
    private static final java.util.Map<BufferedImage,BufferedImage> HELL_FRAMES=new java.util.concurrent.ConcurrentHashMap<>();
    /** Recolour the jelly, preserving the gold crown, jewel and dark facial details. Cached before play. */
    public static BufferedImage hellFrame(BufferedImage source){
        return HELL_FRAMES.computeIfAbsent(source,original->{
            BufferedImage red=new BufferedImage(original.getWidth(),original.getHeight(),BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<original.getHeight();y++)for(int x=0;x<original.getWidth();x++){
                int pixel=original.getRGB(x,y);int r=(pixel>>>16)&255,g=(pixel>>>8)&255,b=pixel&255;
                if(g>r && g>b){
                    float[] hsb=Color.RGBtoHSB(r,g,b,null);
                    pixel=(pixel&0xFF000000)|(Color.HSBtoRGB(.005f,hsb[1]<.2f?.3f:Math.max(.75f,hsb[1]),Math.min(1,hsb[2]*1.15f))&0xFFFFFF);
                }red.setRGB(x,y,pixel);
            }return red;
        });
    }
    public static void buildHellBossAnimations(Map<String,SpriteAnimation> sprites,int size,float scale,float offset){
        buildBossAnimations(sprites,size,scale,offset);
        for(SpriteAnimation animation:sprites.values())for(int i=0;i<animation.frames.length;i++)
            animation.frames[i]=hellFrame(animation.frames[i]);
    }

    private static void buildAnimations(Map<String, SpriteAnimation> sprites,int size,float scale,float offset,boolean king) {
        AtlasKey key=new AtlasKey(size,scale,offset,king);
        Map<String,SpriteAnimation> atlas=ATLASES.computeIfAbsent(key,k -> {
            Map<String,SpriteAnimation> generated=new java.util.HashMap<>();
            generateAnimations(generated,k.size,k.scale,k.offset,k.king);
            return Map.copyOf(generated);
        });
        SpriteAnimation.copyInto(sprites,atlas);
    }

    private static void generateAnimations(Map<String, SpriteAnimation> sprites,int size,float scale,float offset,boolean king) {
        for(String dir:new String[]{"down","up","left","right"}) {
            for(String state:new String[]{"idle","walk","attack","death","hurt","slam","charge"}) {
                if(!king && (state.equals("slam") || state.equals("charge")))continue;
                int count=state.equals("attack") || state.equals("death")?10:state.equals("hurt")?4:8;
                BufferedImage[] frames=new BufferedImage[count];
                for(int i=0;i<count;i++)frames[i]=render(dir,pose(state,i,king),size,scale,offset,king);
                SpriteAnimation anim=new SpriteAnimation(frames,size,size);
                anim.frameDelay=state.equals("walk")?5:state.equals("idle")?9:6;
                sprites.put(state+"_"+dir,anim);
            }
        }
    }
    public static BufferedImage idleFrame(int frame) { return render("down",pose("idle",frame,false),CANVAS_SIZE,1,0,false); }
    public static BufferedImage walkFrame(String dir,int frame) { return render(dir,pose("walk",frame,false),CANVAS_SIZE,1,0,false); }
    public static BufferedImage attackFrame(String dir,int frame) { return render(dir,pose("attack",frame,false),CANVAS_SIZE,1,0,false); }

    private static Pose pose(String state,int frame,boolean king) {
        float phase=(frame%8)/8f;
        float wave=(float)Math.sin(phase*Math.PI*2);
        return switch(state) {
            case "walk" -> new Pose(at(frame,1,.98f,1.06f,1.16f,1.08f,.96f,.94f,.98f),
                    at(frame,1,1.03f,.95f,.83f,.92f,1.08f,1.12f,1.05f),
                    at(frame,0,-.4f,0,.3f,-.4f,-1.3f,-1.7f,-.8f),wave*.7f,king?1:0,
                    at(frame,0,0,.1f,.5f,.1f,0,0,0),0,false);
            case "attack" -> new Pose(at(frame,1,.98f,1.04f,1.14f,1.19f,1.12f,1.02f,1.08f,1.03f,1),
                    at(frame,1,1.02f,.94f,.82f,.77f,.9f,1.07f,.97f,.99f,1),
                    at(frame,0,0,.2f,.4f,.3f,-.7f,-1,.1f,0,0),
                    at(frame,0,-.3f,-.7f,-.8f,-.5f,2.4f,4,3,.8f,0),
                    king?1:at(frame,0,.3f,.7f,1,1,1,1,.8f,.3f,0),
                    at(frame,0,0,0,0,0,.7f,1,.55f,0,0),0,false);
            case "slam" -> new Pose(at(frame,1,1.12f,.94f,.88f,1.28f,1.18f,1.07f,1),
                    at(frame,1,.83f,1.18f,1.27f,.56f,.7f,.91f,1),
                    at(frame,0,.5f,-3,-5,0,0,-.3f,0),0,1,
                    at(frame,0,0,0,0,1,.75f,.3f,0),0,false);
            case "charge" -> new Pose(1+wave*.025f,1-wave*.025f,0,0,1,0,0,false);
            case "hurt" -> new Pose(1.05f, .94f,0,0,1,0,0,true);
            case "death" -> {float t=Math.min(9,Math.max(0,frame))/9f;
                yield new Pose(1+t*.24f,1-t*.60f,0,0,0,0,t,false);}
            default -> new Pose(1-wave*.018f,1+wave*.03f,0,wave*.15f,king?1:0,0,0,false);
        };
    }
    private static float at(int frame,float... values){return values[Math.floorMod(frame,values.length)];}

    private static BufferedImage render(String direction,Pose p,int size,float scale,float offset,boolean king) {
        BufferedImage hi=new BufferedImage(HI,HI,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=hi.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_PURE);
        g.scale(HI/64f,HI/64f);
        int dx=direction.equals("left")?-1:direction.equals("right")?1:0;
        int dy=direction.equals("up")?-1:direction.equals("down")?1:0;
        // Respect the existing hitbox alignment, reserving a transparent margin for the contour.
        float halfWidth=15.8f*scale*p.width;
        float cx=Math.max(halfWidth+1,Math.min(63-halfWidth,32+offset+dx*p.lean));
        float foot=Math.min(62.3f,52+(size-CANVAS_SIZE)/6f);
        float cy=foot+p.bob+dy*Math.max(0,p.lean)*.22f;
        cy=Math.min(62.3f,cy);
        AffineTransform canvas=g.getTransform();
        g.translate(cx,cy);g.scale(scale*p.width,scale*p.height);g.translate(-32,-52);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,1-p.death*.2f));
        body(g,direction,p,king);
        g.setTransform(canvas);g.setComposite(AlphaComposite.SrcOver);
        if(king) {
            g.translate(cx,cy);g.scale(scale,scale);g.translate(-32,-52);
            g.translate(p.death*5,14*p.death+(1-p.height)*(1-p.death)*20);
            g.rotate(p.death*.9f+(p.width-1)*.18f,32,29);
            crown(g,direction);g.setTransform(canvas);
        }
        if(p.dust>.01f) splash(g,cx,foot,Math.min(20,18*scale),p.dust,dx);
        if(p.death>.35f)steam(g,cx,cy-14*scale,p.death);
        g.dispose();
        BufferedImage out=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
        Graphics2D og=out.createGraphics();og.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        og.drawImage(hi,0,0,size,size,null);og.dispose();return out;
    }

    private static Path2D dome(float left,float top,float right,float bottom) {
        float mid=(left+right)/2,w=right-left,h=bottom-top;
        Path2D p=new Path2D.Float();p.moveTo(left,bottom-h*.22f);
        p.curveTo(left,top+h*.32f,mid-w*.27f,top,mid,top);
        p.curveTo(mid+w*.27f,top,right,top+h*.32f,right,bottom-h*.22f);
        p.quadTo(right,bottom,mid+w*.28f,bottom);p.quadTo(mid,bottom+.3f,mid-w*.28f,bottom);
        p.quadTo(left,bottom,left,bottom-h*.22f);p.closePath();return p;
    }
    private static void body(Graphics2D g,String dir,Pose p,boolean king) {
        Shape outer=dome(17,29,47,52),inner=dome(18.6f,30.5f,45.5f,50.7f);
        g.setPaint(new GradientPaint(21,30,RIM,44,52,new Color(0xB8CAA9)));g.fill(outer);
        g.setPaint(new GradientPaint(24,31,SAGE,40,51,Palette.FOLIAGE_DARK));g.fill(inner);
        Shape clip=g.getClip();g.clip(inner);
        g.setColor(new Color(188,211,171,140));
        g.fill(new Ellipse2D.Float(18,48,7,5));g.fill(new Ellipse2D.Float(28,49,8,4));g.fill(new Ellipse2D.Float(41,47.5f,7,6));
        g.setClip(clip);g.setStroke(new BasicStroke(1.15f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.setColor(INK);g.draw(outer);
        AffineTransform old=g.getTransform();g.rotate(.65,24,35);
        g.setColor(IVORY);g.fill(new Ellipse2D.Float(22.5f,32.8f,2.2f,4.3f));g.setTransform(old);
        g.fill(new Ellipse2D.Float(21.4f,37.5f,.9f,1.1f));
        if(king || dir.equals("up")){
            g.setColor(new Color(244,237,214,180));g.rotate(-.6,41,35);
            g.fill(new Ellipse2D.Float(40,33,1.7f,3.7f));g.setTransform(old);
        }
        if(dir.equals("up"))return;
        if(dir.equals("left")){eye(g,20.5f,43,p,-1);}
        else if(dir.equals("right")){eye(g,42,43,p,1);}
        else {eye(g,27,43,p,-1);eye(g,37,43,p,1);}
    }
    private static void eye(Graphics2D g,float x,float y,Pose p,int side){
        g.setColor(new Color(0x514951));g.setStroke(new BasicStroke(1.2f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        if(p.death>.55f){
            AffineTransform saved=g.getTransform();g.translate(x,y);
            // Keep X eyes readable as the jelly collapses into a flat puddle.
            g.scale(1/p.width,1/p.height);
            g.draw(new Line2D.Float(-1.2f,-1.2f,1.2f,1.2f));g.draw(new Line2D.Float(1.2f,-1.2f,-1.2f,1.2f));
            g.setTransform(saved);
        }else if(p.hurt){
            Path2D q=new Path2D.Float();q.moveTo(x+side,y-2);q.lineTo(x-side,y);q.lineTo(x+side,y+2);g.draw(q);
        }else{
            g.fill(new RoundRectangle2D.Float(x-1.25f,y-2.8f,2.5f,5.6f,2.5f,2.5f));
            if(p.anger>.3f){g.setStroke(new BasicStroke(1.4f));g.draw(new Line2D.Float(x+side*1.9f,y-3.2f,x-side*1.1f,y-1.5f));}
        }
    }
    private static void crown(Graphics2D g,String dir){
        Path2D p=new Path2D.Float();p.moveTo(25,28.6f);p.lineTo(24.5f,22.3f);p.lineTo(28.4f,24.2f);
        p.lineTo(32,18.8f);p.lineTo(35.6f,24.2f);p.lineTo(39.5f,22.3f);p.lineTo(39,28.6f);p.closePath();
        g.setPaint(new GradientPaint(26,21,new Color(0xF1D59A),38,29,new Color(0xD5A957)));g.fill(p);
        g.setColor(new Color(0x8A7255));g.setStroke(new BasicStroke(.8f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.draw(p);
        g.setColor(new Color(0xFBE8B5));g.draw(new Line2D.Float(25.7f,23.8f,26.2f,27.6f));
        if(!dir.equals("up")){
            float x=dir.equals("left")?28.5f:dir.equals("right")?35.5f:32;
            g.setColor(new Color(0x81778B));g.fill(new Polygon(new int[]{(int)x,(int)x+1,(int)x,(int)x-1},new int[]{22,24,26,24},4));
        }
    }
    private static void splash(Graphics2D g,float cx,float ground,float radius,float power,int direction){
        g.setColor(new Color(244,237,214,Math.round(230*power)));
        float base=Math.min(62,ground);
        radius=Math.min(radius,Math.min(cx-2,62-cx)/1.05f);
        for(int sign:new int[]{-1,1}){
            float x=cx+sign*radius*.7f;
            Path2D p=new Path2D.Float();p.moveTo(x,base);p.quadTo(x+sign*radius*.5f,base-1,x+sign*radius*.35f,base-7*power);
            p.quadTo(x+sign*radius*.22f,base-3*power,x-sign*3,base-1);p.closePath();g.fill(p);
            g.fill(new Ellipse2D.Float(x+sign*4,base-8*power,1.4f,1.7f));
        }
    }
    private static void steam(Graphics2D g,float x,float y,float progress){
        g.setColor(new Color(244,237,214,Math.round(180*progress)));g.setStroke(new BasicStroke(1,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        Path2D p=new Path2D.Float();p.moveTo(x,y);p.curveTo(x-3,y-3,x+3,y-5,x,y-8);g.draw(p);
    }

    /** A separate jelly droplet with trailing beads, oriented along its flight direction. */
    public static BufferedImage projectileFrame(String dir,int frame,int size){
        BufferedImage out=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);Graphics2D g=out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.scale(size/32f,size/32f);
        double a=switch(dir){case "down"->Math.PI/2;case "left"->Math.PI;case "up"->-Math.PI/2;default->0;};
        g.rotate(a,16,16);float wobble=(float)Math.sin(frame*Math.PI/3)*.9f;
        float trail=(float)Math.cos(frame*Math.PI/3)*.8f;
        Path2D p=new Path2D.Float();p.moveTo(8,16);p.curveTo(12,14,13,10+wobble,18,10+wobble);
        p.curveTo(29,10,29,22,18,22);p.curveTo(13,22,12,18,8,16);p.closePath();
        g.setPaint(new GradientPaint(12,10,RIM,23,23,SAGE));g.fill(p);g.setColor(INK);g.setStroke(new BasicStroke(.9f));g.draw(p);
        g.setColor(IVORY);g.fill(new Ellipse2D.Float(16,12,6,2.2f));g.setColor(SAGE);
        g.fill(new Ellipse2D.Float(3+trail,12+wobble,3,2));g.fill(new Ellipse2D.Float(5-trail,19-wobble,2.5f,2));g.dispose();return out;
    }
}
