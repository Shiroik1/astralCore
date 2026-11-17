package main;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

public class MouseHandler implements MouseListener, MouseMotionListener, MouseWheelListener {

    Gamepanel gp;

    public volatile int mouseX, mouseY; // written by the EDT, read by the game thread

    // Everything below is touched by the game thread only
    public boolean
            leftClicked; // one-shot, lives exactly one tick (cleared in Gamepanel.processInput)
    public boolean rightClicked; // one-shot, same lifetime
    public boolean leftPressed; // held state
    public boolean rightPressed;
    public int wheelRotation = 0;

    /** Creates a MouseHandler instance and initializes the state required by its collaborators. */
    public MouseHandler(Gamepanel gp) {
        this.gp = gp;
    }

    // EDT: RECORD ONLY
    /** Records the pressed mouse button for the current input mode. */
    @Override
    public void mousePressed(MouseEvent e) {
        gp.requestFocusInWindow();
        mouseX = e.getX();
        mouseY = e.getY();
        gp.inputQueue.add(RawInput.mousePressed(e.getButton()));
    }

    /** Releases the corresponding mouse input state. */
    @Override
    public void mouseReleased(MouseEvent e) {
        gp.inputQueue.add(RawInput.mouseReleased(e.getButton()));
    }

    /** Routes wheel movement to the active selection or scrolling control. */
    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        gp.inputQueue.add(RawInput.mouseWheel(e.getWheelRotation()));
    }

    /** Updates pointer input while a mouse button is held. */
    @Override
    public void mouseDragged(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
    }

    /** Updates the pointer position used by aiming and interface hit testing. */
    @Override
    public void mouseMoved(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
    }

    /** Receives a completed mouse click for this listener. */
    @Override
    public void mouseClicked(MouseEvent e) {}

    /** Receives the pointer-enter callback required by the mouse listener. */
    @Override
    public void mouseEntered(MouseEvent e) {}

    /** Receives the pointer-exit callback required by the mouse listener. */
    @Override
    public void mouseExited(MouseEvent e) {}

    // GAME THREAD: INTERPRET
    /** Performs the handle mouse pressed operation for MouseHandler. */
    void handleMousePressed(int button) {
        if (getScaledX() < 0 || getScaledY() < 0) return;
        if (button == MouseEvent.BUTTON1) {
            leftPressed = true;
            leftClicked = true;

            if (gp.paused) {
                gp.paused = false;
                leftClicked = false; // the click that resumes must not also attack
            }
        }
        if (button == MouseEvent.BUTTON3) {
            rightPressed = true;
            rightClicked = true;
        }
    }

    /** Performs the handle mouse released operation for MouseHandler. */
    void handleMouseReleased(int button) {
        if (button == MouseEvent.BUTTON1) {
            leftPressed = false;
        }
        if (button == MouseEvent.BUTTON3) {
            rightPressed = false;
        }
    }

    /** Performs the handle wheel operation for MouseHandler. */
    void handleWheel(int rotation) {
        if (gp.ui.game.canControl()) {
            gp.ui.game.wheel(rotation);
            return;
        }
        wheelRotation += rotation;
    }

    /** Resolves the viewport result used by MouseHandler. */
    private DisplaySettings.Viewport viewport() {
        return gp.display.gameViewport(
                gp.getWidth() > 0 ? gp.getWidth() : gp.screenWidth2,
                gp.getHeight() > 0 ? gp.getHeight() : gp.screenHeight2);
    }

    /** Returns the scaled x for MouseHandler. */
    public int getScaledX() {
        DisplaySettings.Viewport view = viewport();
        return view.contains(mouseX, mouseY)
                ? (int) ((mouseX - view.x()) * (double) gp.screenWidth / view.width())
                : -1;
    }

    /** Returns the scaled y for MouseHandler. */
    public int getScaledY() {
        DisplaySettings.Viewport view = viewport();
        return view.contains(mouseX, mouseY)
                ? (int) ((mouseY - view.y()) * (double) gp.screenHeight / view.height())
                : -1;
    }
}
