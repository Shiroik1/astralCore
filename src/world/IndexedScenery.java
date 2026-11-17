package world;

import java.awt.Rectangle;
import java.util.*;
import java.util.function.Predicate;
import world.spatial.SpatialIndex;

/** Existing canonical scenery list with identity membership and local spatial queries. */
public final class IndexedScenery extends ArrayList<WorldObject> {
    private final SpatialIndex<WorldObject> index;
    private long revision;
    public IndexedScenery(int tileSize){index=new SpatialIndex<>(tileSize*4);}
    public long revision(){return revision;}
    public List<WorldObject> query(Rectangle area){return index.query(area);}
    @Override public boolean contains(Object value){return value instanceof WorldObject o && index.contains(o);}
    @Override public boolean add(WorldObject value){if(index.contains(value))return false;boolean added=super.add(value);index.add(value,value.spatialBounds());revision++;return added;}
    @Override public void add(int at,WorldObject value){if(index.contains(value))return;super.add(at,value);index.add(value,value.spatialBounds());revision++;}
    @Override public boolean addAll(Collection<? extends WorldObject> values){boolean changed=false;for(WorldObject value:values)changed|=add(value);return changed;}
    @Override public boolean addAll(int at,Collection<? extends WorldObject> values){int before=size();for(WorldObject value:values)if(!contains(value))add(at++,value);return before!=size();}
    @Override public WorldObject remove(int at){WorldObject old=super.remove(at);index.remove(old);revision++;return old;}
    @Override public boolean remove(Object value){int at=indexOf(value);if(at<0)return false;remove(at);return true;}
    @Override public WorldObject set(int at,WorldObject value){WorldObject old=super.set(at,value);index.remove(old);index.add(value,value.spatialBounds());revision++;return old;}
    @Override public void clear(){super.clear();index.clear();revision++;}
    @Override public boolean removeIf(Predicate<? super WorldObject> predicate){List<WorldObject> removed=new ArrayList<>();boolean changed=super.removeIf(value->{if(!predicate.test(value))return false;removed.add(value);return true;});for(WorldObject value:removed)index.remove(value);if(changed)revision++;return changed;}
    @Override public boolean removeAll(Collection<?> values){Set<Object> set=Collections.newSetFromMap(new IdentityHashMap<>());set.addAll(values);return removeIf(set::contains);}
    @Override public boolean retainAll(Collection<?> values){return removeIf(v->!values.contains(v));}
}
