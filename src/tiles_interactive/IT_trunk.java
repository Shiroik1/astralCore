package tiles_interactive;

import main.Gamepanel;

public class IT_trunk extends InteractiveTile {
    /** Creates a IT_trunk instance and initializes the state required by its collaborators. */
    public IT_trunk(Gamepanel gp, int col, int row) {
        super(gp, col, row);

        this.worldX = gp.tileSize * col;
        this.worldY = gp.tileSize * row;

        down1 = setup("/tiles_Interactive/trunk", gp.tileSize, gp.tileSize);

        solidArea.x = 0;
        solidArea.y = 0;
        solidArea.width = 0;
        solidArea.height = 0;
        solidAreaDefaultX = solidArea.x;
        solidAreaDefaultY = solidArea.y;
    }
}
