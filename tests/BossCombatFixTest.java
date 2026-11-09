import art.SlimeArt;
import entity.*;
import monster.*;
import object.*;
import main.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class BossCombatFixTest {
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        var game=new AppearanceEquipmentTest.QuietGame();Player player=game.localPlayer();
        MON_SlimeBoss king=new MON_SlimeBoss(game);
        require(king.isBoss() && !king.canBeKnockedBack(),"boss knockback immunity");
        king.startKnockback("right",200);require(!king.knockbackActive,"direct knockback blocked");
        king.knockbackActive=true;king.knockbackRemaining=200;int previousX=king.worldX;king.updateKnockback();
        require(!king.knockbackActive && king.knockbackRemaining==0 && king.worldX==previousX,"stale knockback cancelled");
        Monster futureBoss=new Monster(game){@Override public boolean isBoss(){return true;}};
        futureBoss.startKnockback("left",20);require(!futureBoss.knockbackActive,"immunity shared by all boss types");
        Monster normal=new Monster(game);normal.startKnockback("left",20);require(normal.knockbackActive,"normal monsters still knock back");
        king.worldX=player.worldX+80;king.worldY=player.worldY;game.monster[0]=king;
        int x=king.worldX,y=king.worldY;player.HP=100;player.maxHP=100;player.defense=0;player.invincible=false;
        king.bossState="telegraph_slam";king.bossStateTimer=0;king.attackState=true;
        var contact=Player.class.getDeclaredMethod("contactMonster",int.class);contact.setAccessible(true);
        for(int tick=1;tick<=60;tick++){
            contact.invoke(player,0);
            king.update();require(king.worldX==x && king.worldY==y,"stationary slam tick "+tick);
            require(!king.attackState,"contact attack cannot interrupt jump");
            if(tick>=28 && tick<44)require(king.slamHeight()==110,"airborne hold");
            if(tick<60)require(player.HP==100,"no premature slam damage");
        }
        require(king.bossState.equals("cooldown") && king.slamHeight()==0,"land at end of telegraph");
        require(player.HP==100-king.attack,"slam deals damage once on landing");
        for(int tick=0;tick<20;tick++){king.update();require(king.worldX==x && king.worldY==y,"stationary landing recovery");}
        require(player.HP==100-king.attack,"landing recovery does not repeat damage");
        game.hitStopCounter=0;game.monster[0]=null;player.knockbackActive=false;player.invincible=false;
        player.setCharacterDesign("mage","male");player.setDefaultValues();player.refreshWeaponAnimation();
        require(player.hasStaff(),"equipped staff enables cast");
        player.attacking=true;player.animState="attack";player.spriteNum=4;player.attacking();
        require(game.projectileList.size()==1 && player.arcaneCharge==0,"staff fires one Staff Bolt without granting charge before impact");
        player.inventorySlots[1]=null;player.unequipToBagIndex(Player.EQUIP_WEAPON,1);
        int charge=player.arcaneCharge;player.attacking=true;player.spriteNum=4;player.attacking();
        require(!player.hasStaff() && !player.attacking,"unequipping cancels active cast");
        require(game.projectileList.size()==1 && player.arcaneCharge==charge,"no unequipped projectile or charge gain");
        player.currentInput.leftClicked=true;player.update();
        require(!player.attacking && game.projectileList.size()==1,"unequipped input cannot begin casting");
        player.currentWeapon=new OBJ_sword_normal(game);player.attacking=true;player.spriteNum=4;player.attacking();
        require(!player.hasStaff() && game.projectileList.size()==1,"wrong weapon cannot cast");
        player.tryEquipFromBag(1,Player.EQUIP_WEAPON);player.attacking=true;player.spriteNum=4;player.attacking();
        require(player.hasStaff() && game.projectileList.size()==2,"re-equipping staff restores casting");
        var hud=UI.class.getDeclaredMethod("findBossForHud");hud.setAccessible(true);
        require(hud.invoke(game.ui)==null,"no boss bar without encounter");
        game.monster[0]=normal;require(hud.invoke(game.ui)==null,"normal enemies do not show boss bar");
        game.monster[0]=king;king.worldX=player.worldX+5000;king.worldY=player.worldY;
        require(hud.invoke(game.ui)==null,"unseen boss does not begin encounter");
        king.worldX=player.worldX+100;require(hud.invoke(game.ui)==king,"visible boss starts named bar");
        king.worldX+=5000;require(hud.invoke(game.ui)==king,"bar persists during encounter");
        king.alive=false;require(hud.invoke(game.ui)==null,"bar disappears when boss is gone");
        king.alive=true;king.worldX=player.worldX+100;king.maxHP=60;king.HP=60;
        BufferedImage image=new BufferedImage(game.screenWidth,game.screenHeight,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();var graphics=UI.class.getDeclaredField("g2");graphics.setAccessible(true);graphics.set(game.ui,g);
        var draw=UI.class.getDeclaredMethod("drawBossHealth");draw.setAccessible(true);draw.invoke(game.ui);
        int full=AppearanceEquipmentTest.pixels(image);king.HP=30;draw.invoke(game.ui);
        require(full!=AppearanceEquipmentTest.pixels(image),"boss bar tracks current HP");
        king.maxHP=0;draw.invoke(game.ui);g.dispose();
        Color green=new Color(SlimeArt.idleFrame(0).getRGB(36,45),true);
        require(green.getGreen()>green.getRed()+20 && green.getGreen()>green.getBlue()+20,"greener slime palette");
        System.out.println("PASS: grounded stationary slam and airtime, landing-only damage, boss immunity, staff-required casts, encounter HUD and greener palette.");
    }
}
