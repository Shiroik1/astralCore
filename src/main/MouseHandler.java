package main;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

public class MouseHandler implements MouseListener, MouseMotionListener {

    Gamepanel gp;

    public int mouseX, mouseY;
    public boolean leftClicked; //One-shot flag FOR ATTACK
    public boolean rightClicked;
    public boolean leftPressed; //Held state, CHARGE

    public MouseHandler(Gamepanel gp){
        this.gp = gp;
    }

    @Override
    public void mouseClicked(MouseEvent e) {

    }

    @Override
    public void mousePressed(MouseEvent e) {
        if(e.getButton() == MouseEvent.BUTTON1){
            leftPressed = true;
            leftClicked = true;

            if(gp.gameState == gp.pauseState){
                gp.gameState = gp.playState;
            }
        }
        if(e.getButton() == MouseEvent.BUTTON3){
            rightClicked = true;
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if(e.getButton() == MouseEvent.BUTTON1){
            leftPressed = false;
        }
    }

    @Override
    public void mouseEntered(MouseEvent e) {

    }

    @Override
    public void mouseExited(MouseEvent e) {

    }

    @Override
    public void mouseDragged(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
    }

    //COORD SCALING FOR FULLSCREEN
    public int getScaledX(){
        return (int)(mouseX * ((double) gp.screenWidth / gp.screenWidth2));
    }

    public int getScaledY(){
        return (int)(mouseY * ((double) gp.screenHeight / gp.screenHeight2));
    }
}
