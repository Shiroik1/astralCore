package world;

import entity.Monster;
import entity.Player;
import main.Gamepanel;
import monster.MON_GreenSlime;
import java.awt.Rectangle;
import java.util.SplittableRandom;

/** Small bounded overworld population. Uses normal monsters, collision, drops and save snapshots. */
public final class SlimeSpawner {
    private final Gamepanel gp;
    private SplittableRandom random;
    private long elapsedTicks;

    public SlimeSpawner(Gamepanel gp){this.gp=gp;}

    private boolean active(){
        return gp.worldConfig.procedural() && (!gp.isNetworked || gp.isHost) && !gp.quests.inDungeon();
    }

    public void resetPopulation(){
        elapsedTicks=0;
        random=new SplittableRandom(gp.worldConfig.seed() ^ 0x534C494D45534CL);
        if(!active())return;
        int limit=Math.min(gp.worldConfig.slimeCount(),gp.monster.length);
        for(int i=0;i<limit;i++)if(!spawnOne())break;
    }

    public void update(){
        if(!active())return;
        if(random==null)random=new SplittableRandom(gp.worldConfig.seed() ^ 0x534C494D45534CL);
        if(++elapsedTicks<gp.worldConfig.slimeRespawnSeconds()*60L)return;
        elapsedTicks=0;
        int count=0;for(Monster monster:gp.monster)if(monster!=null)count++;
        if(count<Math.min(gp.worldConfig.slimeCount(),gp.monster.length))spawnOne();
    }

    private boolean spawnOne(){
        int slot=-1;
        for(int i=0;i<gp.monster.length;i++)if(gp.monster[i]==null){slot=i;break;}
        if(slot<0)return false;
        MON_GreenSlime slime=new MON_GreenSlime(gp);
        int ts=gp.tileSize;
        // Bounded attempts keep dense/small maps from stalling the game loop.
        for(int attempt=0;attempt<160;attempt++){
            int col=random.nextInt(1,gp.maxWorldCol-1),row=random.nextInt(1,gp.maxWorldRow-1);
            slime.worldX=col*ts;slime.worldY=row*ts;
            Rectangle body=new Rectangle(slime.worldX+slime.solidArea.x,slime.worldY+slime.solidArea.y,
                    slime.solidArea.width,slime.solidArea.height);
            Rectangle clearance=new Rectangle(body);clearance.grow(ts/4,ts/4);
            if(!clear(clearance))continue;
            boolean nearby=false;
            for(Player player:gp.players){
                if(player==null || player.inWell)continue;
                Rectangle safety=new Rectangle(player.worldX+player.solidArea.x,player.worldY+player.solidArea.y,
                        player.solidArea.width,player.solidArea.height);safety.grow(ts*7,ts*7);
                Rectangle view=new Rectangle(player.worldX-player.screenX-ts,player.worldY-player.screenY-ts,
                        gp.screenWidth+ts*2,gp.screenHeight+ts*2);
                if(safety.intersects(body) || view.intersects(body)){nearby=true;break;}
            }
            if(nearby)continue;
            for(Monster monster:gp.monster)if(monster!=null){
                Rectangle other=new Rectangle(monster.worldX+monster.solidArea.x,monster.worldY+monster.solidArea.y,
                        monster.solidArea.width,monster.solidArea.height);other.grow(ts,ts);
                if(other.intersects(body)){nearby=true;break;}
            }
            if(nearby)continue;
            slime.direction=switch(random.nextInt(4)){case 0->"up";case 1->"down";case 2->"left";default->"right";};
            gp.monster[slot]=slime;
            return true;
        }
        return false;
    }

    private boolean clear(Rectangle area){
        if(gp.tileM.blocksMovement(area.x,area.y,area.x+area.width-1,area.y+area.height-1))return false;
        int ts=gp.tileSize;
        for(int row=Math.floorDiv(area.y,ts);row<=Math.floorDiv(area.y+area.height-1,ts);row++)
            for(int col=Math.floorDiv(area.x,ts);col<=Math.floorDiv(area.x+area.width-1,ts);col++)
                if(gp.generatedWorld.reserved(col,row))return false;
        for(WorldObject object:gp.worldObjects)if(object.blocksArea(area.x,area.y,area.width,area.height))return false;
        for(var tile:gp.interactable)if(tile!=null && tile.collision &&
                area.intersects(tile.worldX+tile.solidArea.x,tile.worldY+tile.solidArea.y,tile.solidArea.width,tile.solidArea.height))return false;
        for(var npc:gp.npc)if(npc!=null && area.intersects(npc.worldX+npc.solidArea.x,npc.worldY+npc.solidArea.y,
                npc.solidArea.width,npc.solidArea.height))return false;
        return true;
    }
}
