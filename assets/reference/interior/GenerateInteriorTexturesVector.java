package art;

import main.MiniJson;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import java.util.Map;

/** Offline furniture vector source. Uses the game palette directly; no painted texture or noise. */
public final class GenerateInteriorTexturesVector {
    private static final Color WOOD=Palette.WOOD,LIGHT=Palette.WOOD_LIGHT,DARK=Palette.TIMBER,
            CREAM=Palette.PLASTER,CORAL=Palette.ROOF_CORAL,BLUE=Palette.ROOF_SLATE,GREEN=Palette.GROUND[2],
            STONE=Palette.STONES[0],MORTAR=Palette.MORTAR,IRON=Palette.IRON,GOLD=Palette.GOLD;
    private final Graphics2D g;
    private GenerateInteriorTexturesVector(Graphics2D g){this.g=g;}
    public static void main(String[] args)throws Exception{
        Path output=Path.of(args.length==0?"res/building/props":args[0]);Files.createDirectories(output);
        var root=(Map<?,?>)MiniJson.parse(Files.readString(Path.of("res/building/props.json")));
        List<?> props=(List<?>)root.get("props");
        BufferedImage board=new BufferedImage(1000,((props.size()+4)/5)*210,BufferedImage.TYPE_INT_ARGB);
        Graphics2D preview=board.createGraphics();quality(preview);preview.setColor(new Color(0xE8E7DD));preview.fillRect(0,0,board.getWidth(),board.getHeight());
        preview.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,12));int index=0;
        for(Object value:props){
            var p=(Map<?,?>)value;String id=(String)p.get("id");int w=((Number)p.get("width")).intValue(),h=((Number)p.get("height")).intValue();
            BufferedImage high=new BufferedImage(w*4,h*4,BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=high.createGraphics();quality(g);g.scale(w*4.0/100,h*4.0/100);
            new GenerateInteriorTexturesVector(g).paint(id);g.dispose();
            BufferedImage image=new BufferedImage(w*2,h*2,BufferedImage.TYPE_INT_ARGB);g=image.createGraphics();quality(g);
            g.drawImage(high,0,0,w*2,h*2,null);g.dispose();ImageIO.write(image,"png",output.resolve(id+".png").toFile());
            double scale=Math.min(170.0/image.getWidth(),168.0/image.getHeight());int pw=(int)(image.getWidth()*scale),ph=(int)(image.getHeight()*scale);
            int col=index%5,row=index/5;preview.drawImage(image,col*200+(200-pw)/2,row*210+8+168-ph,pw,ph,null);
            preview.setColor(DARK);preview.drawString(id,col*200+12,row*210+197);index++;
        }
        preview.dispose();ImageIO.write(board,"png",Path.of("assets/reference/interior/catalog-preview.png").toFile());
    }
    private static void quality(Graphics2D g){
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
    }
    private void paint(String id){
        if(id.startsWith("bed_"))bed(tone(id));
        else if(id.startsWith("bookshelf_"))bookshelf(id.endsWith("full")?2:id.endsWith("sparse")?1:0);
        else if(id.startsWith("table_"))table(id.substring(6));
        else if(id.startsWith("rug_"))rug(tone(id),id.substring(4));
        else switch(id){
            case "fireplace" -> fireplace();case "wall_lamp" -> lamp();case "potted_plant" -> plant();
            case "bar_counter" -> counter();case "bar_shelf_empty" -> barShelf(false);case "bar_shelf_full" -> barShelf(true);
            default -> throw new IllegalArgumentException("No vector source for "+id);
        }
    }
    private static Color tone(String id){return id.endsWith("blue")?BLUE:id.endsWith("coral")?CORAL:GREEN;}
    private void rect(Color c,double x,double y,double w,double h){g.setColor(c);g.fill(new Rectangle2D.Double(x,y,w,h));}
    private void round(Color c,double x,double y,double w,double h,double r){g.setColor(c);g.fill(new RoundRectangle2D.Double(x,y,w,h,r,r));}
    private void oval(Color c,double x,double y,double w,double h){g.setColor(c);g.fill(new Ellipse2D.Double(x,y,w,h));}
    private void poly(Color c,double... points){
        Path2D p=new Path2D.Double();p.moveTo(points[0],points[1]);for(int i=2;i<points.length;i+=2)p.lineTo(points[i],points[i+1]);p.closePath();g.setColor(c);g.fill(p);
    }
    private void line(Color c,float width,double... points){
        Path2D p=new Path2D.Double();p.moveTo(points[0],points[1]);for(int i=2;i<points.length;i+=2)p.lineTo(points[i],points[i+1]);
        g.setColor(c);g.setStroke(new BasicStroke(width,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.draw(p);
    }
    private void flower(double x,double y,double size,Color petals){
        for(int i=0;i<4;i++){double a=i*Math.PI/2;oval(petals,x+Math.cos(a)*size*.25-size*.22,y+Math.sin(a)*size*.25-size*.22,size*.44,size*.44);}
        oval(GOLD,x-size*.12,y-size*.12,size*.24,size*.24);
    }
    private void arch(Color c,double x,double y,double w,double h){
        Path2D p=new Path2D.Double();p.moveTo(x,y+h);p.lineTo(x,y+h*.28);p.quadTo(x+w*.5,y-h*.17,x+w,y+h*.28);p.lineTo(x+w,y+h);p.closePath();g.setColor(c);g.fill(p);
    }
    private void bed(Color cloth){
        round(DARK,7,8,86,88,5);arch(WOOD,7,5,86,24);arch(LIGHT,8,4,84,12);
        round(CREAM,13,24,74,65,6);round(cloth,13,43,74,43,5);
        // One large shadow plane rather than many cloth folds.
        poly(cloth==BLUE?IRON:cloth==CORAL?Palette.BRICK:Palette.FOLIAGE,13,82,83,69,87,82,87,87,13,87);
        round(LIGHT,27,20,46,19,6);round(CREAM,26,18,48,19,6);rect(CREAM,13,39,74,6);
        flower(50,61,12,CREAM);
        round(WOOD,5,6,11,92,3);round(WOOD,84,6,11,92,3);
        round(LIGHT,3,4,15,13,3);round(LIGHT,82,4,15,13,3);
        rect(DARK,15,88,70,8);round(WOOD,12,85,76,8,2);rect(LIGHT,15,85,70,2);
        round(LIGHT,3,83,15,12,3);round(LIGHT,82,83,15,12,3);
    }
    private void book(double x,double y,double w,double h,Color color){round(color,x,y,w,h,1.5);}
    private void bookshelf(int fill){
        arch(DARK,6,5,88,91);arch(WOOD,4,4,92,17);arch(LIGHT,5,3,90,9);
        rect(DARK,10,16,80,66);
        Color[] colors={BLUE,CREAM,GREEN,CORAL,LIGHT,Palette.FOLIAGE};
        for(int row=0;row<3;row++){
            double y=18+row*21;
            int count=fill==2?9:fill==1?(row==1?3:4):0;
            for(int i=0;i<count;i++){
                double x=fill==1 && row==1?64+i*6.7:15+i*7.8;
                book(x,y+2+(i%3),6,16-(i%3),colors[(i+row)%colors.length]);
            }
            rect(WOOD,10,y+18,80,4);rect(LIGHT,10,y+18,80,1.2);
        }
        rect(WOOD,10,82,80,14);rect(DARK,49,83,1.3,13);round(LIGHT,42,87,4,4,1);round(LIGHT,54,87,4,4,1);
        round(WOOD,4,12,7,84,2);round(WOOD,89,12,7,84,2);
        round(LIGHT,2,3,12,12,3);round(LIGHT,86,3,12,12,3);round(WOOD,2,94,96,4,1);
    }
    private void table(String style){
        round(DARK,8,59,8,39,2);round(DARK,84,59,8,39,2);
        round(WOOD,3,25,94,55,5);round(LIGHT,3,22,94,50,5);
        line(WOOD,1.1f,5,39,95,39);line(WOOD,1.1f,5,56,95,56);
        if(style.equals("empty"))return;
        if(!style.equals("map")){
            rect(CREAM,38,24,24,54);rect(CORAL,40,24,2,54);rect(CORAL,58,24,2,54);flower(50,71,7,CORAL);
        }
        switch(style){
            case "feast" -> {
                candle(16,28,14);oval(Palette.FOLIAGE,29,38,32,20);oval(WOOD,32,34,25,21);
                oval(Palette.ROOF_THATCH,34,35,18,13);oval(WOOD,53,39,9,7);round(CREAM,60,39,5,2.5,1);
                oval(DARK,69,46,19,13);oval(WOOD,69,42,19,10);
                oval(CORAL,71,36,7,9);oval(GOLD,79,37,7,9);oval(BLUE,75,31,7,9);
                poly(Palette.FOLIAGE,76,34,74,29,80,30,81,35);
            }
            case "dining" -> {
                for(double x:new double[]{22,78})for(double y:new double[]{38,62}){
                    oval(LIGHT,x-8,y-5,16,10);oval(CREAM,x-7,y-5,14,8);cup(x+7,y-10,5,8,CREAM);
                }
                oval(DARK,42,47,18,12);oval(CREAM,41,43,18,11);oval(LIGHT,43,42,14,7);oval(WOOD,48,39,4,4);
            }
            case "map" -> {
                poly(CREAM,30,28,68,30,70,66,28,65);line(Palette.WATER,1.8f,34,58,42,51,51,55,57,43,65,38);
                poly(LIGHT,35,40,41,33,47,40);poly(LIGHT,49,48,56,39,63,48);
                oval(WOOD,12,31,14,18);poly(CREAM,19,32,22,40,19,47,16,40);poly(CORAL,19,32,22,40,19,40);
                book(77,54,13,13,GREEN);book(76,50,13,13,BLUE);flower(82,56,5,CREAM);
                round(CREAM,9,61,15,5,2);line(LIGHT,1,12,61,12,66);
                oval(IRON,80,30,8,6);poly(CREAM,82,31,84,15,92,12,86,30);line(IRON,.8f,84,29,88,17);
            }
            default -> throw new IllegalArgumentException(style);
        }
    }
    private void candle(double x,double y,double height){
        oval(WOOD,x-3,y+height-2,12,5);round(CREAM,x,y,6,height,1.5);oval(Palette.GLOW,x+1,y-7,4,8);
        poly(GOLD,x+1,y-3,x+4,y-10,x+5,y-3);
    }
    private void cup(double x,double y,double w,double h,Color color){
        oval(color,x+w-2,y+2,5,h-2);oval(DARK,x+w,y+3,2,h-4);round(color,x,y,w,h,2);oval(LIGHT,x+.7,y+.5,w-1.4,2);
    }
    private void rug(Color color,String style){
        round(color==CORAL?Palette.BRICK:color==BLUE?IRON:Palette.FOLIAGE,2,3,96,95,6);round(color,2,2,96,94,6);
        g.setColor(CREAM);g.setStroke(new BasicStroke(2));g.draw(new Rectangle2D.Double(9,11,82,76));
        for(int x:new int[]{12,83})for(int y:new int[]{15,77}){
            int sx=x<50?1:-1,sy=y<50?1:-1;line(CREAM,2.8f,x,y,x+5*sx,y,x+5*sx,y+6*sy);
        }
        switch(style){
            case "coral" -> {
                poly(CREAM,49,25,51,25,55,37,45,37);poly(CREAM,43,40,57,40,61,48,39,48);poly(CREAM,36,51,64,51,68,59,32,59);
                petal(50,69,38,62,CREAM);petal(50,69,62,62,CREAM);petal(50,69,50,59,CREAM);
            }
            case "blue" -> {
                Path2D flame=new Path2D.Double();flame.moveTo(50,28);flame.curveTo(65,45,39,45,58,60);flame.curveTo(70,54,60,38,65,39);
                flame.curveTo(80,65,56,77,50,77);flame.curveTo(25,72,28,51,38,42);flame.curveTo(34,61,48,65,46,53);flame.curveTo(41,44,43,37,50,28);flame.closePath();g.setColor(CREAM);g.fill(flame);
            }
            case "sage" -> {
                for(int i=0;i<6;i++){double a=i*Math.PI/3;petal(50,51,50+Math.cos(a)*19,51+Math.sin(a)*23,CREAM);}
                oval(GOLD,47,48,6,6);
            }
            default -> throw new IllegalArgumentException(style);
        }
        flower(23,50,6,CREAM);flower(77,50,6,CREAM);
    }
    private void petal(double x,double y,double tx,double ty,Color color){
        double dx=tx-x,dy=ty-y,len=Math.max(1,Math.hypot(dx,dy)),nx=-dy/len*5,ny=dx/len*5;
        Path2D p=new Path2D.Double();p.moveTo(x,y);p.quadTo((x+tx)/2+nx,(y+ty)/2+ny,tx,ty);
        p.quadTo((x+tx)/2-nx,(y+ty)/2-ny,x,y);g.setColor(color);g.fill(p);
    }
    private void fireplace(){
        poly(CREAM,34,3,66,3,82,39,18,39);rect(MORTAR,40,5,20,5);round(STONE,43,20,12,5,1);round(STONE,61,30,11,5,1);
        rect(MORTAR,12,48,76,45);rect(STONE,13,49,74,43);arch(MORTAR,25,53,50,39);arch(DARK,30,58,40,34);
        rect(Palette.STONES[2],14,50,12,13);rect(Palette.STONES[1],14,65,12,13);rect(Palette.STONES[2],14,80,12,12);
        rect(Palette.STONES[1],74,50,12,13);rect(Palette.STONES[2],74,65,12,13);rect(Palette.STONES[1],74,80,12,12);
        poly(Palette.STONES[2],29,55,38,51,40,58,32,63);poly(Palette.STONES[2],42,50,57,50,56,58,43,58);poly(Palette.STONES[1],61,51,71,55,68,63,60,58);
        flame(50,87,25,26);round(DARK,35,88,17,4,2);round(WOOD,51,88,16,4,2);
        round(WOOD,4,38,92,11,3);rect(LIGHT,6,38,88,3);round(STONE,7,92,86,6,1);rect(Palette.STONES[2],7,92,86,2);
    }
    private void flame(double x,double y,double w,double h){
        Path2D p=new Path2D.Double();p.moveTo(x-w*.45,y);p.quadTo(x-w*.65,y-h*.5,x-w*.14,y-h*.68);
        p.quadTo(x+w*.08,y-h*.82,x,y-h);p.quadTo(x+w*.48,y-h*.6,x+w*.34,y-h*.46);p.quadTo(x+w*.65,y-h*.24,x+w*.36,y);p.closePath();g.setColor(Palette.FLOWERS[4]);g.fill(p);
        poly(Palette.GLOW,x-w*.24,y,x,y-h*.63,x+w*.25,y);
    }
    private void lamp(){
        round(WOOD,13,8,64,86,6);round(LIGHT,22,15,10,6,2);round(LIGHT,22,82,10,6,2);
        oval(IRON,53,3,12,10);poly(IRON,19,34,43,17,74,17,96,34);rect(IRON,22,33,71,4);
        poly(IRON,26,37,89,37,81,79,35,79);poly(Palette.GLOW,39,42,75,42,71,71,43,71);
        poly(CREAM,30,41,36,42,40,70,35,68);poly(CREAM,79,42,85,41,79,68,76,70);
        round(IRON,31,77,54,8,3);oval(IRON,48,83,18,10);
    }
    private void plant(){
        line(Palette.FOLIAGE_DARK,2,50,69,51,22);
        double[][] tips={{22,23},{48,5},{80,19},{89,42},{13,44},{57,29}};
        for(int i=0;i<tips.length;i++){
            double x=50,y=65-i*3,tx=tips[i][0],ty=tips[i][1];
            double dx=tx-x,dy=ty-y,length=Math.hypot(dx,dy),nx=-dy/length*14,ny=dx/length*14;
            Path2D p=new Path2D.Double();p.moveTo(x,y);
            p.curveTo(x+dx*.3+nx,y+dy*.3+ny,tx+nx*.45,ty+ny*.45,tx,ty);
            p.quadTo(x+dx*.5-nx,y+dy*.5-ny,x,y);
            g.setColor(i%3==0?Palette.FOLIAGE_DARK:Palette.FOLIAGE);g.fill(p);
        }
        flower(35,37,11,CREAM);flower(65,28,11,CREAM);flower(61,53,10,CREAM);
        poly(WOOD,21,68,79,68,71,96,29,96);oval(LIGHT,21,62,58,12);oval(DARK,27,65,46,6);
        rect(CREAM,26,82,48,3);flower(50,89,8,CREAM);
    }
    private void counter(){
        round(DARK,4,36,92,61,3);rect(WOOD,8,46,84,48);line(DARK,1.3f,26,47,26,91);line(DARK,1.3f,74,47,74,91);
        round(WOOD,2,21,96,29,4);round(LIGHT,2,19,96,22,4);round(WOOD,1,38,98,9,2);
        round(WOOD,1,19,9,78,2);round(WOOD,90,19,9,78,2);round(LIGHT,1,17,10,15,3);round(LIGHT,89,17,10,15,3);
        rect(LIGHT,9,90,82,3);round(CREAM,39,19,22,48,2);rect(CORAL,41,19,2,48);rect(CORAL,57,19,2,48);flower(50,54,7,CORAL);
    }
    private void barShelf(boolean stocked){
        round(DARK,7,13,86,82,2);arch(WOOD,5,5,90,17);arch(LIGHT,6,4,88,9);
        round(WOOD,4,13,7,82,2);round(WOOD,89,13,7,82,2);
        for(int row=0;row<3;row++){rect(WOOD,10,35+row*27,80,5);rect(LIGHT,10,35+row*27,80,1.4);}
        if(stocked){
            Color[] colors={Palette.BRICK,Palette.FOLIAGE_DARK,Palette.FOLIAGE,GOLD,GOLD,Palette.BRICK};
            for(int i=0;i<6;i++){double x=17+i*11;rect(colors[i],x+2,17,3,5);round(colors[i],x,21,7,13,2);rect(LIGHT,x+2,16,3,2);}
            for(int i=0;i<3;i++)cup(17+i*12,46,8,13,LIGHT);
            for(int i=0;i<3;i++){cup(58+i*10,47,7,12,CREAM);flower(61+i*10,53,4,CORAL);}
            for(int i=0;i<2;i++)for(int j=0;j<3;j++)oval(CREAM,16+i*20,79-j*3,17,4);
            for(int i=0;i<2;i++){round(CREAM,62+i*14,76,11,10,3);oval(LIGHT,62+i*14,75,11,4);}
        }
        round(LIGHT,2,4,11,12,3);round(LIGHT,87,4,11,12,3);round(WOOD,2,93,96,5,2);
    }
}
