package builder;

import art.BuildGeometry;
import art.BuildTextures;
import building.*;
import building.BuildElements.Element;
import building.BuildingLayout.Cell;
import building.BuildingLayout.Tile;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Standalone JavaFX authoring app. Uses default JavaFX controls, without custom CSS. */
public final class BuilderApp extends Application {
    private static final int TILE=48,HEAD=BuildGeometry.HEIGHT,PAD=24,HISTORY_LIMIT=100;
    private enum Tool { PAINT, ERASE, RECTANGLE, ANCHOR, TOGGLE_DOOR }
    private record Snapshot(BuildingLayout layout,int width,int height,Cell anchor){}
    private Stage stage;
    private Path project,presetDirectory,file;
    private BuildingLayout layout=new BuildingLayout();
    private int width=16,height=16;
    private Cell anchor=new Cell(8,12),hover,strokeStart,lastCell;
    private Snapshot strokeBefore;
    private final Deque<Snapshot> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    private final BuildTextures textures=new BuildTextures(TILE);
    private final Canvas canvas=new Canvas();
    private final ListView<Element> palette=new ListView<>();
    private final ListView<PropCatalog.Prop> furnishings=new ListView<>();
    private final Tab tileTab=new Tab("Tiles",palette),propTab=new Tab("Furnishings",furnishings);
    private final TabPane catalogs=new TabPane(tileTab,propTab);
    private double propX,propY;private boolean propHover;
    private final ComboBox<Tool> tool=new ComboBox<>();
    private final TextField id=new TextField("new_building"),name=new TextField("New building"),category=new TextField("house");
    private final Spinner<Integer> columns=new Spinner<>(1,64,16),rows=new Spinner<>(1,64,16);
    private int propRotation;
    private final CheckBox vertical=new CheckBox("Prefer vertical (R)"),open=new CheckBox("Door starts open"),grid=new CheckBox("Grid");
    private final CheckBox showFloors=new CheckBox("Floors"),showStructures=new CheckBox("Walls / doors / furniture");
    private final Label status=new Label(),position=new Label(" ");
    private final Slider zoom=new Slider(.5,2,1);
    private final Button undoButton=new Button("Undo"),redoButton=new Button("Redo");
    private boolean dirty,loading,renderQueued,strokeChanged;
    private Image rendered;
    private final Map<java.awt.image.BufferedImage,Image> imageCache=new IdentityHashMap<>();

    @Override public void start(Stage primary){
        stage=primary;project=findProject();presetDirectory=project.resolve("maps/blueprints");
        tool.getItems().setAll(Tool.values());tool.getSelectionModel().select(Tool.PAINT);
        tool.setConverter(new javafx.util.StringConverter<>(){
            @Override public String toString(Tool value){return value==null?"":switch(value){
                case PAINT -> "Paint";case ERASE -> "Erase selected layer";case RECTANGLE -> "Rectangle";case ANCHOR -> "Set anchor";case TOGGLE_DOOR -> "Toggle door";
            };}
            @Override public Tool fromString(String text){throw new UnsupportedOperationException();}
        });
        grid.setSelected(true);showFloors.setSelected(true);showStructures.setSelected(true);
        palette.getItems().setAll(BuildElements.all());palette.getSelectionModel().selectFirst();
        palette.setCellFactory(list->new ListCell<>(){
            @Override protected void updateItem(Element element,boolean empty){
                super.updateItem(element,empty);
                if(empty || element==null){setText(null);setGraphic(null);return;}
                setText(element.name()+"\n"+element.id());
                ImageView icon=new ImageView(image(textures.get(element.id(),0,false,false)));
                icon.setFitWidth(30);icon.setFitHeight(48);icon.setPreserveRatio(true);setGraphic(icon);
            }
        });
        palette.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{
            boolean floor=b==null || b.shape().ground();
            vertical.setDisable(floor);open.setDisable(b==null || b.shape()!=BuildElements.Shape.DOOR);draw();
        });
        tileTab.setClosable(false);propTab.setClosable(false);
        furnishings.getItems().setAll(PropCatalog.all());furnishings.getSelectionModel().selectFirst();
        furnishings.setCellFactory(list->new ListCell<>(){
            @Override protected void updateItem(PropCatalog.Prop p,boolean empty){
                super.updateItem(p,empty);if(empty || p==null){setText(null);setGraphic(null);return;}
                setText(p.name()+"\n"+p.id());ImageView icon=new ImageView(image(textures.prop(p.id(),false).image()));
                icon.setFitWidth(48);icon.setFitHeight(48);icon.setPreserveRatio(true);setGraphic(icon);
            }
        });
        catalogs.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{
            finishStroke();
            if(objectMode())tool.getSelectionModel().select(Tool.PAINT);
            vertical.setText(objectMode()?"Mirror furnishing":"Prefer vertical (R)");
            vertical.setDisable(!objectMode() && (selected()==null || selected().shape().ground()));
            open.setDisable(objectMode() || selected()==null || selected().shape()!=BuildElements.Shape.DOOR);draw();
        });
        furnishings.getSelectionModel().selectedItemProperty().addListener((o,a,b)->draw());
        vertical.setDisable(true);open.setDisable(true);
        Button newButton=button("New",this::newDocument),openButton=button("Open…",this::openDocument);
        Button saveButton=button("Save",()->save(false)),saveAs=button("Save As…",()->save(true));
        undoButton.setOnAction(e->undo());redoButton.setOnAction(e->redo());
        zoom.setPrefWidth(120);
        ToolBar toolbar=new ToolBar(newButton,openButton,saveButton,saveAs,new Separator(),undoButton,redoButton,new Separator(),tool,new Label("Zoom"),zoom,grid);
        VBox metadata=new VBox(6,new Label("Blueprint ID"),id,new Label("Display name"),name,new Label("Category"),category,
                new Label("Grid size (tiles)"),new HBox(6,columns,new Label("×"),rows),button("Apply size",this::resize),
                new Separator(),new Label("Building elements"),catalogs,vertical,button("Rotate furnishing (R)",this::rotateFurnishing),open,new Separator(),showFloors,showStructures);
        columns.setPrefWidth(86);rows.setPrefWidth(86);metadata.setPadding(new Insets(10));metadata.setPrefWidth(245);VBox.setVgrow(catalogs,Priority.ALWAYS);
        Label help=new Label("Tiles: drag to paint · Furnishings: click for free placement · Right click: erase · R: rotate / mirror\nRectangle: filled floor or wall outline · Set anchor: click entrance tile · Ctrl/⌘ Z: undo");
        help.setWrapText(true);
        VBox footer=new VBox(3,help,new HBox(12,status,position));footer.setPadding(new Insets(8));
        ScrollPane scroll=new ScrollPane(new Pane(canvas));scroll.setPannable(false);
        BorderPane root=new BorderPane(scroll,toolbar,null,footer,metadata);
        Scene scene=new Scene(root,1180,820);
        scene.addEventFilter(KeyEvent.KEY_PRESSED,this::key);
        canvas.setOnMousePressed(this::mousePressed);canvas.setOnMouseDragged(this::mouseDragged);canvas.setOnMouseReleased(this::mouseReleased);
        canvas.setOnMouseMoved(e->{hover=cell(e);propPoint(e);propHover=true;position.setText(objectMode()?String.format(Locale.ROOT,"Position %.1f, %.1f",propX,propY):inside(hover)?"Tile "+hover.col()+", "+hover.row():" ");draw();});
        canvas.setOnMouseExited(e->{hover=null;propHover=false;draw();});
        zoom.valueProperty().addListener((o,a,b)->draw());grid.setOnAction(e->draw());vertical.setOnAction(e->draw());open.setOnAction(e->draw());tool.setOnAction(e->draw());
        showFloors.setOnAction(e->requestRender());showStructures.setOnAction(e->requestRender());
        for(TextField field:List.of(id,name,category))field.textProperty().addListener((o,a,b)->{if(!loading)markDirty();});
        stage.setOnCloseRequest(e->{finishStroke();if(!confirmDiscard())e.consume();});
        stage.setScene(scene);stage.setMinWidth(850);stage.setMinHeight(600);refreshHistory();updateTitle();requestRender();stage.show();
        status.setText("Ready. Save blueprints in maps/blueprints.");
    }
    private static Path findProject(){
        Path start=Path.of(System.getProperty("astral.projectDir",System.getProperty("user.dir"))).toAbsolutePath();
        for(Path p=start;p!=null;p=p.getParent())if(Files.isDirectory(p.resolve("res/building")) && Files.isDirectory(p.resolve("src/building")))return p;
        return start;
    }
    private Image image(java.awt.image.BufferedImage source){return imageCache.computeIfAbsent(source,key->SwingFXUtils.toFXImage(key,null));}
    private Button button(String text,Runnable action){Button b=new Button(text);b.setOnAction(e->{finishStroke();action.run();});return b;}
    private boolean objectMode(){return propTab.isSelected();}
    private void propPoint(MouseEvent e){double z=zoom.getValue();propX=e.getX()/z-PAD;propY=e.getY()/z-PAD-HEAD;}
    private Element selected(){return palette.getSelectionModel().getSelectedItem();}
    private boolean inside(Cell c){return c!=null && BuildingBlueprint.inside(c,width,height);}
    private Cell cell(MouseEvent e){double z=zoom.getValue();return new Cell((int)Math.floor((e.getX()/z-PAD)/TILE),(int)Math.floor((e.getY()/z-PAD-HEAD)/TILE));}
    private Tile brush(){Element e=selected();return new Tile(e.id(),!e.shape().ground() && vertical.isSelected(),e.shape()==BuildElements.Shape.DOOR && open.isSelected());}
    private Snapshot snapshot(){return new Snapshot(new BuildingLayout(layout),width,height,anchor);}
    private boolean same(Snapshot state){return state.width()==width && state.height()==height && state.anchor().equals(anchor) && state.layout().floors().equals(layout.floors()) && state.layout().structures().equals(layout.structures()) && state.layout().decorations().equals(layout.decorations());}
    private void checkpoint(Snapshot before){
        if(same(before))return;
        undo.push(before);if(undo.size()>HISTORY_LIMIT)undo.removeLast();redo.clear();markDirty();refreshHistory();requestRender();
    }
    private void restore(Snapshot state){layout=new BuildingLayout(state.layout());width=state.width();height=state.height();anchor=state.anchor();columns.getValueFactory().setValue(width);rows.getValueFactory().setValue(height);markDirty();requestRender();refreshHistory();}
    private void undo(){finishStroke();if(undo.isEmpty())return;redo.push(snapshot());restore(undo.pop());}
    private void redo(){finishStroke();if(redo.isEmpty())return;undo.push(snapshot());restore(redo.pop());}
    private void refreshHistory(){undoButton.setDisable(undo.isEmpty());redoButton.setDisable(redo.isEmpty());}
    private void markDirty(){dirty=true;updateTitle();}
    private void updateTitle(){stage.setTitle("AstralCore Builder — "+(file==null?name.getText():file.getFileName())+(dirty?" *":""));}
    private void mousePressed(MouseEvent e){
        finishStroke();canvas.requestFocus();hover=cell(e);propPoint(e);propHover=true;
        if(objectMode()){
            Snapshot before=snapshot();
            if(e.getButton()==MouseButton.SECONDARY || tool.getValue()==Tool.ERASE)eraseObject();
            else if(e.getButton()==MouseButton.PRIMARY)placeObject();
            checkpoint(before);draw();e.consume();return;
        }
        if(!inside(hover) || selected()==null)return;
        strokeBefore=snapshot();strokeStart=hover;lastCell=hover;strokeChanged=false;
        if(e.getButton()==MouseButton.SECONDARY)apply(hover,true);
        else if(e.getButton()==MouseButton.PRIMARY)switch(tool.getValue()){
            case ANCHOR -> {anchor=hover;strokeChanged=true;}
            case TOGGLE_DOOR -> {Tile t=layout.structures().get(hover);if(t!=null && t.shape()==BuildElements.Shape.DOOR){BuildingLayout candidate=new BuildingLayout(layout);candidate.put(hover,new Tile(t.element(),t.vertical(),!t.open()));
                String conflict=candidate.decorationConflict();if(conflict==null){layout=candidate;strokeChanged=true;}else status.setText(conflict);}}
            case PAINT,ERASE -> apply(hover,tool.getValue()==Tool.ERASE);
            case RECTANGLE -> {}
        }
        if(strokeChanged)requestRender();draw();e.consume();
    }
    private void mouseDragged(MouseEvent e){
        hover=cell(e);if(objectMode()){propPoint(e);draw();e.consume();return;}if(strokeBefore==null)return;
        if(inside(hover) && (e.isSecondaryButtonDown() || tool.getValue()==Tool.PAINT || tool.getValue()==Tool.ERASE)){
            // Bresenham interpolation avoids gaps when the cursor moves quickly.
            Cell from=lastCell==null?hover:lastCell;int x=from.col(),y=from.row(),dx=Math.abs(hover.col()-x),dy=-Math.abs(hover.row()-y),sx=x<hover.col()?1:-1,sy=y<hover.row()?1:-1,error=dx+dy;
            while(true){apply(new Cell(x,y),e.isSecondaryButtonDown() || tool.getValue()==Tool.ERASE);if(x==hover.col() && y==hover.row())break;int twice=2*error;if(twice>=dy){error+=dy;x+=sx;}if(twice<=dx){error+=dx;y+=sy;}}
            lastCell=hover;
        }
        if(strokeChanged)requestRender();draw();e.consume();
    }
    private void mouseReleased(MouseEvent e){
        hover=cell(e);
        if(strokeBefore!=null && e.getButton()==MouseButton.PRIMARY && tool.getValue()==Tool.RECTANGLE && inside(hover)){
            Snapshot beforeRectangle=snapshot();boolean valid=true;
            int left=Math.min(strokeStart.col(),hover.col()),right=Math.max(strokeStart.col(),hover.col()),top=Math.min(strokeStart.row(),hover.row()),bottom=Math.max(strokeStart.row(),hover.row());
            for(int r=top;r<=bottom;r++)for(int c=left;c<=right;c++)if(selected().shape().ground() || r==top || r==bottom || c==left || c==right)valid&=apply(new Cell(c,r),false);
            if(!valid){layout=new BuildingLayout(beforeRectangle.layout());status.setText("Rectangle cancelled: remove conflicting structures or leave room for corner joins.");requestRender();}
        }
        finishStroke();e.consume();
    }
    /** A complete placement plan is validated before mutating either layer. */
    private boolean apply(Cell c,boolean erase){
        if(!inside(c))return false;
        boolean floor=selected().shape().ground();
        if(erase){
            if((floor?layout.floors():layout.structures()).containsKey(c)){
                BuildingLayout candidate=new BuildingLayout(layout);candidate.erase(c,floor);String conflict=candidate.decorationConflict();
                if(conflict!=null){status.setText(conflict);return false;}
                layout.erase(c,floor);strokeChanged=true;
            }return true;
        }
        Tile tile=brush();Tile old=layout.structures().get(c);
        if(!floor && old!=null && old.shape()!=tile.shape()){status.setText("Erase the wall or door before replacing its type.");return false;}
        Map<Cell,Tile> plan=layout.plan(c,tile);
        for(Cell at:plan.keySet())if(!inside(at)){status.setText("The automatic corner would be outside the grid.");return false;}
        BuildingLayout candidate=new BuildingLayout(layout);plan.forEach(candidate::put);
        String conflict=candidate.decorationConflict();if(conflict!=null){status.setText(conflict);return false;}
        for(var e:plan.entrySet())if(!e.getValue().equals((floor?layout.floors():layout.structures()).get(e.getKey()))){layout.put(e.getKey(),e.getValue());strokeChanged=true;}
        return true;
    }
    private void rotateFurnishing(){
        var p=furnishings.getSelectionModel().getSelectedItem();
        if(objectMode() && p!=null && p.rotatable()){
            propRotation=(propRotation+1)%4;status.setText("Rotation: "+(propRotation*90)+"°");
        }else if(!(objectMode() && p!=null && p.id().startsWith("table_")))vertical.setSelected(!vertical.isSelected());
        draw();
    }
    private void placeObject(){
        var p=furnishings.getSelectionModel().getSelectedItem();if(p==null)return;
        Decoration d=Decoration.create(p.id(),propX,propY,vertical.isSelected(),propRotation);String error=objectError(d);
        if(error!=null){status.setText(error);return;}
        layout.addDecoration(d);status.setText("Placed "+p.name()+" at a free position.");
    }
    private void eraseObject(){
        List<Decoration> items=layout.decorations();
        for(int i=items.size()-1;i>=0;i--){
            Decoration d=items.get(i);var p=PropCatalog.get(d.element());
            if(p.visualBounds(d.x(),d.y(),d.flipped(),d.rotation()).contains(propX,propY)){
                layout.removeDecoration(d.id());status.setText("Removed furnishing.");return;
            }
        }
    }
    private String objectError(Decoration d){
        if(!BuildingBlueprint.decorationInside(d,width,height))return "Keep the furnishing footprint inside the blueprint.";
        var def=PropCatalog.get(d.element());var bounds=def.bounds(d.x(),d.y(),d.flipped(),d.rotation());
        if(def.mounted()){
            boolean supported=false;
            for(var e:layout.structures().entrySet())if(e.getValue().shape()==BuildElements.Shape.WALL){
                Cell c=e.getKey();if(d.x()>=c.col()*48 && d.x()<=(c.col()+1)*48 && d.y()>=c.row()*48+48-BuildGeometry.HEIGHT && d.y()<=(c.row()+1)*48)supported=true;
            }
            if(!supported)return "Mount the lamp on a wall.";
        }
        if(def.collisionWidth()>0){
            for(Decoration other:layout.decorations()){
                var p=PropCatalog.get(other.element());if(p.collisionWidth()>0 && bounds.intersects(p.bounds(other.x(),other.y(),other.flipped(),other.rotation())))return "Furnishings overlap.";
            }
            for(var e:layout.structures().entrySet())for(java.awt.Rectangle r:BuildingLayout.footprint(e.getKey(),e.getValue(),layout.structures()::get)){
                var at=e.getKey();r.translate(at.col()*48,at.row()*48);if(r.intersects(bounds))return "Leave room between the furnishing and wall/door.";
            }
        }
        return null;
    }
    private void finishStroke(){
        if(strokeBefore==null)return;Snapshot before=strokeBefore;strokeBefore=null;strokeStart=null;lastCell=null;checkpoint(before);draw();
    }
    private void resize(){
        int w=columns.getValue(),h=rows.getValue();
        for(var layer:List.of(layout.floors(),layout.structures()))for(Cell c:layer.keySet())if(!BuildingBlueprint.inside(c,w,h)){error("Resize refused","The new size would cut off building elements. Erase or move those elements first.");return;}
        for(Decoration d:layout.decorations())if(!BuildingBlueprint.decorationInside(d,w,h)){error("Resize refused","The new size would cut off furnishings.");return;}
        Snapshot before=snapshot();width=w;height=h;anchor=new Cell(Math.min(anchor.col(),w-1),Math.min(anchor.row(),h-1));checkpoint(before);
    }
    private void requestRender(){
        if(renderQueued)return;renderQueued=true;
        Platform.runLater(()->{
            renderQueued=false;
            try{
                rendered=SwingFXUtils.toFXImage(new BuildingRenderer(layout,TILE,textures).image(width,height,showFloors.isSelected(),showStructures.isSelected()),null);draw();
            }catch(RuntimeException e){error("Could not render building",e.getMessage());}
        });
    }
    private void draw(){
        double z=zoom.getValue();canvas.setWidth((width*TILE+PAD*2)*z);canvas.setHeight((height*TILE+HEAD+PAD*2)*z);
        GraphicsContext g=canvas.getGraphicsContext2D();g.setTransform(z,0,0,z,0,0);
        g.setFill(Color.rgb(226,230,222));g.fillRect(0,0,canvas.getWidth()/z,canvas.getHeight()/z);
        g.setFill(Color.rgb(205,216,193));g.fillRect(PAD,PAD+HEAD,width*TILE,height*TILE);
        if(rendered!=null)g.drawImage(rendered,PAD,PAD);
        if(grid.isSelected()){
            g.setStroke(Color.rgb(92,104,85,.28));g.setLineWidth(1/z);
            for(int c=0;c<=width;c++)g.strokeLine(PAD+c*TILE,PAD+HEAD,PAD+c*TILE,PAD+HEAD+height*TILE);
            for(int r=0;r<=height;r++)g.strokeLine(PAD,PAD+HEAD+r*TILE,PAD+width*TILE,PAD+HEAD+r*TILE);
        }
        double ax=PAD+(anchor.col()+.5)*TILE,ay=PAD+HEAD+(anchor.row()+.5)*TILE;
        g.setStroke(Color.DARKBLUE);g.setLineWidth(2/z);g.strokeOval(ax-8,ay-8,16,16);g.strokeLine(ax-12,ay,ax+12,ay);g.strokeLine(ax,ay-12,ax,ay+12);
        if(objectMode() && propHover && furnishings.getSelectionModel().getSelectedItem()!=null){
            var p=furnishings.getSelectionModel().getSelectedItem();Decoration d=Decoration.create(p.id(),propX,propY,vertical.isSelected(),propRotation);
            var sprite=textures.prop(d.element(),d.flipped(),d.rotation());g.setGlobalAlpha(.5);
            g.drawImage(image(sprite.image()),PAD+d.x()-sprite.anchorX(),PAD+HEAD+d.y()-sprite.anchorY());g.setGlobalAlpha(1);
            var bounds=p.placementBounds(d.x(),d.y(),d.flipped(),d.rotation());g.setStroke(objectError(d)==null?Color.DARKGREEN:Color.FIREBRICK);
            g.strokeRect(PAD+bounds.getX(),PAD+HEAD+bounds.getY(),Math.max(4,bounds.getWidth()),Math.max(4,bounds.getHeight()));
            return;
        }
        if(inside(hover)){
            g.setStroke(Color.DARKGREEN);g.setLineWidth(2/z);g.strokeRect(PAD+hover.col()*TILE,PAD+HEAD+hover.row()*TILE,TILE,TILE);
            if(strokeStart!=null && tool.getValue()==Tool.RECTANGLE){
                int left=Math.min(strokeStart.col(),hover.col()),top=Math.min(strokeStart.row(),hover.row());
                g.strokeRect(PAD+left*TILE,PAD+HEAD+top*TILE,(Math.abs(strokeStart.col()-hover.col())+1)*TILE,(Math.abs(strokeStart.row()-hover.row())+1)*TILE);
            }else if(strokeBefore==null && selected()!=null && tool.getValue()==Tool.PAINT){
                Map<Cell,Tile> plan=layout.plan(hover,brush());BuildingLayout preview=new BuildingLayout(layout);plan.forEach(preview::put);
                boolean valid=plan.keySet().stream().allMatch(this::inside);
                Tile existing=layout.structures().get(hover);valid&=brush().shape().ground() || existing==null || existing.shape()==brush().shape();
                valid&=preview.decorationConflict()==null;
                g.setGlobalAlpha(.5);
                for(var e:plan.entrySet()){
                    Cell c=e.getKey();Tile t=e.getValue();int mask=t.shape().ground()?BuildingLayout.mask(c,preview.floors().keySet()):preview.structureMask(c);
                    int[] extension=t.shape()==BuildElements.Shape.DOOR?BuildingLayout.doorExtensions(c,t,preview.structures()::get):new int[2];
                    art.BuildTextures.StructureSprite sprite=textures.structure(t.element(),mask,t.open(),t.vertical(),extension[0],extension[1]);
                    Image ghost=image(sprite.image());
                    g.drawImage(ghost,PAD+c.col()*TILE+sprite.offsetX(),PAD+HEAD+(c.row()+1)*TILE-ghost.getHeight()+sprite.offsetY());
                }
                g.setGlobalAlpha(1);g.setStroke(valid?Color.DARKGREEN:Color.FIREBRICK);g.strokeRect(PAD+hover.col()*TILE,PAD+HEAD+hover.row()*TILE,TILE,TILE);
            }
        }
    }
    private void key(KeyEvent e){
        if(e.isShortcutDown()){
            if(e.getTarget() instanceof TextInputControl && (e.getCode()==KeyCode.Z || e.getCode()==KeyCode.Y))return;
            switch(e.getCode()){
                case S -> {finishStroke();save(e.isShiftDown());e.consume();}
                case O -> {finishStroke();openDocument();e.consume();}
                case N -> {finishStroke();newDocument();e.consume();}
                case Z -> {if(e.isShiftDown())redo();else undo();e.consume();}
                case Y -> {redo();e.consume();}
                default -> {}
            }
            return;
        }
        if(e.getTarget() instanceof TextInputControl || e.getTarget() instanceof Spinner<?>)return;
        switch(e.getCode()){
            case R -> {rotateFurnishing();e.consume();}
            case DIGIT1 -> selectShape(BuildElements.Shape.FLOOR);
            case DIGIT2 -> selectShape(BuildElements.Shape.WALL);
            case DIGIT3 -> selectShape(BuildElements.Shape.DOOR);
            default -> {}
        }
    }
    private void selectShape(BuildElements.Shape shape){catalogs.getSelectionModel().select(tileTab);palette.getSelectionModel().select(BuildElements.get(shape.name().toLowerCase(Locale.ROOT)));}
    private BuildingBlueprint blueprint(){return new BuildingBlueprint(id.getText().trim(),name.getText().trim(),category.getText().trim(),width,height,anchor,layout);}
    private boolean save(boolean as){
        try{
            BuildingBlueprint preset=blueprint();
            if(preset.layout().empty()){error("Cannot save empty blueprint","Place at least one element first.");return false;}
            Path target=file;
            if(as || target==null){
                FileChooser chooser=chooser("Save building blueprint");chooser.setInitialFileName(preset.id()+".json");
                java.io.File picked=chooser.showSaveDialog(stage);if(picked==null)return false;
                target=picked.toPath();if(!target.getFileName().toString().endsWith(".json"))target=target.resolveSibling(target.getFileName()+".json");
                if(!target.equals(picked.toPath()) && Files.exists(target)){
                    Alert overwrite=new Alert(Alert.AlertType.CONFIRMATION,"Replace "+target.getFileName()+"?",ButtonType.YES,ButtonType.NO);overwrite.initOwner(stage);
                    if(overwrite.showAndWait().orElse(ButtonType.NO)!=ButtonType.YES)return false;
                }
            }
            preset.save(target);file=target;dirty=false;updateTitle();status.setText("Saved "+target.getFileName());return true;
        }catch(IOException | RuntimeException e){error("Could not save blueprint",e.getMessage());return false;}
    }
    private FileChooser chooser(String title)throws IOException{
        Files.createDirectories(presetDirectory);FileChooser chooser=new FileChooser();chooser.setTitle(title);chooser.setInitialDirectory(presetDirectory.toFile());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Building blueprint (*.json)","*.json"));return chooser;
    }
    private void openDocument(){
        if(!confirmDiscard())return;
        try{
            java.io.File picked=chooser("Open building blueprint").showOpenDialog(stage);if(picked==null)return;
            BuildingBlueprint preset=BuildingBlueprint.read(picked.toPath());
            // Read and decode fully before replacing the current document.
            BuildingLayout next=preset.layout();
            for(var layer:List.of(next.floors(),next.structures()))for(Tile t:layer.values()){
                textures.get(t.element(),0,t.open(),t.vertical());
                if(t.shape()==BuildElements.Shape.DOOR)textures.get(t.element(),0,!t.open(),t.vertical());
            }
            for(Decoration d:next.decorations())textures.prop(d.element(),d.flipped(),d.rotation());
            loading=true;
            try{id.setText(preset.id());name.setText(preset.name());category.setText(preset.category());}finally{loading=false;}
            layout=next;width=preset.width();height=preset.height();anchor=preset.anchor();file=picked.toPath();resetDocument();status.setText("Opened "+file.getFileName());
        }catch(IOException | RuntimeException e){error("Could not open blueprint",e.getMessage());}
    }
    private void newDocument(){
        if(!confirmDiscard())return;
        loading=true;try{id.setText("new_building");name.setText("New building");category.setText("house");}finally{loading=false;}
        layout=new BuildingLayout();width=16;height=16;anchor=new Cell(8,12);file=null;resetDocument();status.setText("New blueprint");
    }
    private void resetDocument(){columns.getValueFactory().setValue(width);rows.getValueFactory().setValue(height);undo.clear();redo.clear();hover=null;dirty=false;refreshHistory();updateTitle();requestRender();}
    private boolean confirmDiscard(){
        if(!dirty)return true;
        ButtonType save=new ButtonType("Save"),discard=new ButtonType("Discard"),cancel=ButtonType.CANCEL;
        Alert alert=new Alert(Alert.AlertType.CONFIRMATION,"Save changes to this blueprint?",save,discard,cancel);alert.initOwner(stage);alert.setHeaderText("Unsaved building");
        ButtonType result=alert.showAndWait().orElse(cancel);return result==discard || result==save && save(false);
    }
    private void error(String title,String message){Alert alert=new Alert(Alert.AlertType.ERROR,message==null?title:message,ButtonType.OK);alert.setTitle(title);alert.setHeaderText(title);alert.initOwner(stage);alert.showAndWait();}
}
