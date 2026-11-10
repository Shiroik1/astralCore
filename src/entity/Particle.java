package entity;

import art.ArtKit;
import main.Gamepanel;

import java.awt.*;
import java.awt.geom.*;
import java.util.concurrent.ThreadLocalRandom;

/** A short-lived vector mark. Gameplay emits it once and the game clock handles motion and fading. */
public final class Particle extends Entity {
    private static final int MAX_ACTIVE = 180;
    private static final int AMBIENT_LIMIT = 24;
    public enum Style {
        DUST(0.86f, -0.015f, 1f, dust()),
        SMOKE(0.992f, -0.004f, 0.76f, DUST.shape),
        SQUARE(0.96f, 0, 1f, null),
        CHIP(0.94f, 0.12f, 1f, polygon(-0.68f, -0.24f, 0.2f, -0.7f, 0.76f, 0.06f, 0.13f, 0.58f, -0.52f, 0.35f)),
        SPARK(0.89f, 0, 1f, star()),
        MOTE(0.98f, -0.01f, 0.95f, new Ellipse2D.Float(-0.56f, -0.68f, 1.12f, 1.36f)),
        RING(1f, 0, 0.90f, ring());

        final float drag, gravity, opacity;
        final Shape shape;

        Style(float drag, float gravity, float opacity, Shape shape){
            this.drag = drag;
            this.gravity = gravity;
            this.opacity = opacity;
            this.shape = shape;
        }
    }

    private static final Color IVORY = new Color(0xFFF1D7);
    private static final Color DUST_SHADE = new Color(0xDFE2EA);
    private static final Color SMOKE_SHADE = DUST_SHADE.darker();
    private static final Shape DUST_TOP = dustTop();
    private static final Shape CHIP_FACE = polygon(-0.68f, -0.24f, 0.2f, -0.7f, 0.42f, 0.04f, -0.52f, 0.35f);

    private final Style style;
    private final Color color, light;
    private final float size;
    private final float angle;
    private final int lifetime, depth;
    private float x, y, vx, vy;
    private int age;

    private Particle(Entity source, Style style, Color color, float size, int lifetime,
                    float vx, float vy, float offsetX, float offsetY){
        this(source.gp, style, color, size, lifetime, vx, vy,
                source.worldX + source.solidArea.x + source.solidArea.width * 0.5f + offsetX,
                source.worldY + source.solidArea.y + source.solidArea.height * (style == Style.DUST ? 1f : 0.5f) + offsetY,
                source.sortY() + 1);
    }

    private Particle(Gamepanel gp, Style style, Color color, float size, int lifetime,
                     float vx, float vy, float x, float y, int depth){
        super(gp);
        this.style = style;
        this.color = color;
        this.light = switch(style){
            case CHIP -> ArtKit.mix(color, IVORY, 0.5f);
            case SMOKE -> ArtKit.mix(color, SMOKE_SHADE, 0.55f);
            default -> color;
        };
        this.size = size;
        this.lifetime = lifetime;
        this.vx = vx;
        this.vy = vy;
        this.angle = (float)Math.atan2(vy, vx);
        this.x = x;
        this.y = y;
        worldX = Math.round(x);
        worldY = Math.round(y);
        this.depth = depth;
        groundEffect = style == Style.DUST;
    }

    public static void emitAmbient(Gamepanel gp, Style style, Color color, float size, int lifetime,
                                   float vx, float vy, float worldX, float worldY, int depth){
        if(gp.ambientParticles.size() < AMBIENT_LIMIT){
            gp.ambientParticles.add(new Particle(gp, style, color, size, lifetime, vx, vy, worldX, worldY, depth));
        }
    }

    public static void emit(Entity source, Style style, Color color, float size, int lifetime,
                            float vx, float vy, float offsetX, float offsetY){
        if(source.gp.particleList.size() < MAX_ACTIVE){
            source.gp.particleList.add(new Particle(source, style, color, size, lifetime, vx, vy, offsetX, offsetY));
        }
    }

    public static void burst(Entity source, Style style, Color color, int count, float size, int lifetime, float speed){
        spray(source, style, color, count, size, lifetime, speed, 0, Math.PI, 0, 0);
    }

    /** Emits individual particles across a cone. Heading and half-angle are in radians. */
    public static void spray(Entity source, Style style, Color color, int count, float size, int lifetime, float speed,
                             double heading, double halfAngle, float offsetX, float offsetY){
        var random = ThreadLocalRandom.current();
        int available = Math.min(count, MAX_ACTIVE - source.gp.particleList.size());
        for(int i = 0; i < available; i++){
            double angle = heading + halfAngle * random.nextDouble(-1, 1);
            float dx = (float)Math.cos(angle), dy = (float)Math.sin(angle);
            float travel = speed * random.nextFloat(0.55f, 1.4f);
            float start = random.nextFloat(0, 4);
            int life = Math.max(1, Math.round(lifetime * random.nextFloat(0.72f, 1.28f)));
            emit(source, style, color, size * random.nextFloat(0.58f, 1.25f), life,
                    dx * travel, dy * travel, offsetX + dx * start, offsetY + dy * start);
        }
    }

    public static void impact(Entity target, String direction, Color criticalColor){
        boolean critical = criticalColor != null;
        int dx = "right".equals(direction) ? 1 : "left".equals(direction) ? -1 : 0;
        int dy = "down".equals(direction) ? 1 : "up".equals(direction) ? -1 : 0;
        var random = ThreadLocalRandom.current();
        int count = random.nextInt(critical ? 12 : 5, critical ? 17 : 8);
        if(critical){
            emit(target, Style.RING, criticalColor, 17, 13, 0, 0, 0, 0);
        }
        int available = Math.min(count, MAX_ACTIVE - target.gp.particleList.size());
        for(int i = 0; i < available; i++){
            float forwardX = dx, forwardY = dy;
            if(dx == 0 && dy == 0){
                double angle = random.nextDouble(2 * Math.PI);
                forwardX = (float)Math.cos(angle);
                forwardY = (float)Math.sin(angle);
            }
            int life = random.nextInt(18, critical ? 27 : 24);
            float distance = target.gp.tileSize * random.nextFloat(critical ? 2.65f : 2.15f, critical ? 3.45f : 2.65f);
            // Sum the velocity decay over the visible ticks so even the slowest dot clears two tiles.
            float drag = Style.SQUARE.drag;
            float travelFactor = (float)((1 - Math.pow(drag, life - 1)) / (1 - drag));
            float forward = distance / travelFactor;
            float spread = critical ? 0.58f : 0.40f;
            float side = forward * random.nextFloat(-spread, spread);
            emit(target, Style.SQUARE, Color.WHITE, random.nextFloat(critical ? 7.5f : 4f, critical ? 10.5f : 6.5f), life,
                    forwardX * forward - forwardY * side, forwardY * forward + forwardX * side,
                    random.nextFloat(-3, 3), random.nextFloat(-3, 3));
        }
    }

    @Override public void update(){
        if(++age >= lifetime){
            alive = false;
            return;
        }
        x += vx;
        y += vy;
        vx *= style.drag;
        vy = vy * style.drag + style.gravity;
        worldX = Math.round(x);
        worldY = Math.round(y);
    }

    @Override public int sortY(){ return depth; }

    @Override public void draw(Graphics2D g){
        Player camera = gp.localPlayer();
        if(camera == null || !alive) return;
        float sx = x - camera.worldX + camera.screenX;
        float sy = y - camera.worldY + camera.screenY;
        if(sx < -40 || sy < -40 || sx > gp.screenWidth + 40 || sy > gp.screenHeight + 40) return;

        float progress = age / (float)lifetime;
        float fade = 1 - progress;
        Composite composite = g.getComposite();
        if(style == Style.SQUARE){
            // Integer edges keep tiny squares crisp as their positions change between ticks.
            int side = Math.max(4, Math.round(size));
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, fade * 3f)));
            g.setColor(color);
            g.fillRect(Math.round(sx - side * 0.5f), Math.round(sy - side * 0.5f), side, side);
            g.setComposite(composite);
            return;
        }

        float scale = size * switch(style){
            case DUST, SMOKE -> 0.74f + 0.58f * progress;
            case RING -> 0.60f + progress * 1.05f;
            case MOTE -> 0.62f + 0.38f * (float)Math.sin(progress * Math.PI);
            default -> 0.45f + 0.55f * fade;
        };
        if(style == Style.MOTE || style == Style.SPARK) scale = Math.max(3.5f, scale);
        AffineTransform transform = g.getTransform();
        if(style == Style.DUST || style == Style.SMOKE) g.translate(sx, sy);
        else g.translate(Math.round(sx), Math.round(sy));
        switch(style){
            case CHIP -> g.rotate(angle + age * 0.15f);
            case SPARK -> g.rotate(age * 0.045f);
            default -> { }
        }
        g.scale(scale, scale);
        float opacity = switch(style){
            case DUST, SMOKE -> style.opacity * fade * fade * (3 - 2 * fade);
            case MOTE, SPARK, RING -> style.opacity * Math.min(1f, fade * 1.5f);
            default -> style.opacity * fade;
        };
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
        g.setColor(style == Style.DUST ? DUST_SHADE : style == Style.SMOKE ? light : color);
        g.fill(style.shape);
        if(style == Style.DUST || style == Style.SMOKE){
            g.setColor(color);
            g.fill(DUST_TOP);
        } else if(style == Style.CHIP){
            g.setColor(light);
            g.fill(CHIP_FACE);
        }
        g.setTransform(transform);
        g.setComposite(composite);
    }

    private static Shape polygon(float... points){
        Path2D shape = new Path2D.Float();
        shape.moveTo(points[0], points[1]);
        for(int i = 2; i < points.length; i += 2) shape.lineTo(points[i], points[i+1]);
        shape.closePath();
        return shape;
    }

    private static Shape star(){
        Path2D shape = new Path2D.Float();
        shape.moveTo(0, -1);
        shape.quadTo(0.24, -0.20, 0.93, 0);
        shape.quadTo(0.24, 0.20, 0, 1);
        shape.quadTo(-0.22, 0.20, -0.93, 0);
        shape.quadTo(-0.22, -0.20, 0, -1);
        shape.closePath();
        return shape;
    }

    private static Shape dust(){
        Path2D shape = new Path2D.Float();
        shape.moveTo(-0.94, 0.18);
        shape.curveTo(-1.08, -0.19, -0.87, -0.53, -0.58, -0.56);
        shape.curveTo(-0.47, -0.96, 0.11, -1.03, 0.36, -0.74);
        shape.curveTo(0.71, -0.80, 0.96, -0.47, 0.91, -0.18);
        shape.curveTo(1.14, 0.13, 0.96, 0.55, 0.64, 0.59);
        shape.curveTo(0.49, 0.91, 0.02, 0.92, -0.18, 0.74);
        shape.curveTo(-0.58, 0.91, -0.99, 0.60, -0.94, 0.18);
        shape.closePath();
        return shape;
    }

    private static Shape dustTop(){
        Area top = new Area(dust());
        top.intersect(new Area(new AffineTransform(0.89, 0, 0, 0.85, -0.08, -0.12)
                .createTransformedShape(top)));
        return top;
    }

    private static Shape ring(){
        Area ring = new Area(new Ellipse2D.Float(-1, -0.75f, 2, 1.5f));
        ring.subtract(new Area(new Ellipse2D.Float(-0.84f, -0.60f, 1.68f, 1.20f)));
        return ring;
    }
}
