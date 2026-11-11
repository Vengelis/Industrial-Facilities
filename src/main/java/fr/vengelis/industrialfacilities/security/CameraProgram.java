package fr.vengelis.industrialfacilities.security;

import java.util.*;
import java.util.function.BiConsumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** A closed, typed language: only event entity reads and linked output requests are executable. */
public final class CameraProgram {
    public static final int MAX_EVENTS=16,MAX_NODES=64,MAX_DEPTH=6,MAX_DURATION_TICKS=72000,MAX_JOBS=128;
    public static final int MAX_ITEM_ID_LENGTH=256,MAX_INVENTORY_COUNT=1_000_000;
    private static final int MAX_INVENTORY_SLOTS=256,MAX_EQUIPMENT_SLOTS=16,COUNT_OVERFLOW=MAX_INVENTORY_COUNT+1;
    @FunctionalInterface public interface ActionOutput { void accept(UUID target, String action, String text); }
    public record Validation(boolean ok,String error,CompoundTag program) {}
    private record Duration(int ticks,String unit,boolean explicit) {}
    /** Values are captured once at entry. A sleeping sequence never retains a live entity. */
    private record Snapshot(String type,String category,String name,String uuid,String team,double distance,Double health,Map<String,Integer> inventory) {
        static Snapshot capture(Entity entity,Vec3 origin,Set<String> items){return new Snapshot(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),CameraProgram.category(entity),entity.getName().getString(),entity.getUUID().toString(),entity.getTeam()==null?"":entity.getTeam().getName(),entity.position().distanceTo(origin),entity instanceof LivingEntity living?(double)living.getHealth():null,inventorySnapshot(entity,items));}
    }
    /** Read only materialized native slots. Null means unknown, never an empty inventory. */
    private static Map<String,Integer> inventorySnapshot(Entity entity,Set<String> requested){
        if(requested.isEmpty())return Map.of();
        Map<String,Integer> counts=new HashMap<>();Set<ItemStack> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        try{
            if(entity instanceof Player player){countContainer(player.getInventory(),requested,counts,seen);return Map.copyOf(counts);}
            // Vehicle getItem() can unpack a loot table. Its raw list is deliberately used instead.
            if(entity instanceof ContainerEntity vehicle){var stacks=vehicle.getItemStacks();int size=stacks.size();if(size<0||size>MAX_INVENTORY_SLOTS)return null;for(int slot=0;slot<size;slot++)countStack(stacks.get(slot),requested,counts,seen);}
            else if(entity instanceof Container container)countContainer(container,requested,counts,seen);
            else if(entity instanceof InventoryCarrier carrier)countContainer(carrier.getInventory(),requested,counts,seen);
            else if(entity instanceof AbstractHorse horse)countContainer(horse.getInventory(),requested,counts,seen);
            if(entity instanceof LivingEntity living){int slots=0;var equipment=living.getAllSlots().iterator();while(equipment.hasNext()){if(slots++>=MAX_EQUIPMENT_SLOTS)return null;countStack(equipment.next(),requested,counts,seen);}}
            return Map.copyOf(counts);
        }catch(RuntimeException unreadable){return null;}
    }
    private static void countContainer(Container container,Set<String> requested,Map<String,Integer> counts,Set<ItemStack> seen){
        int size=container.getContainerSize();if(size<0||size>MAX_INVENTORY_SLOTS)throw new IllegalArgumentException("Unbounded inventory");
        for(int slot=0;slot<size;slot++)countStack(container.getItem(slot),requested,counts,seen);
    }
    private static void countStack(ItemStack stack,Set<String> requested,Map<String,Integer> counts,Set<ItemStack> seen){
        if(stack.isEmpty()||!seen.add(stack))return;String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if(requested.contains(id)){long sum=(long)counts.getOrDefault(id,0)+stack.getCount();counts.put(id,(int)Math.min(COUNT_OVERFLOW,sum));}
    }
    private static void inventoryQueries(List<Stmt> body,Set<String> items){for(Stmt node:body)if(node instanceof If branch){inventoryQueries(branch.condition,items);inventoryQueries(branch.yes,items);inventoryQueries(branch.no,items);}}
    private static void inventoryQueries(Cond condition,Set<String> items){if(condition instanceof Compare compare){if(compare.getter.equals("inventory_item"))items.add(compare.text);}else if(condition instanceof BooleanCondition bool){inventoryQueries(bool.left,items);if(bool.right!=null)inventoryQueries(bool.right,items);}}
    private static final class Frame {final List<Stmt> nodes;int index;Frame(List<Stmt> nodes){this.nodes=nodes;}}
    /** A bounded coroutine, resumed with game ticks; it performs no sleeping or world lookup. */
    public static final class Job {
        private final UUID camera;
        private final boolean empty,scheduled;
        private final Snapshot snapshot;
        private final Deque<Frame> stack=new ArrayDeque<>();
        private long readyAt;
        private Job(Event event,Snapshot snapshot,long now){camera=event.camera;empty=event.trigger.equals("empty");this.snapshot=snapshot;scheduled=event.delay.ticks>0||hasWait(event.body);readyAt=after(now,event.delay.ticks);stack.addLast(new Frame(event.body));}
        public UUID camera(){return camera;}
        public boolean empty(){return empty;}
        public long readyAt(){return readyAt;}
        public boolean advance(long now,BiConsumer<UUID,String> output){ return advance(now,(id,action,text)->output.accept(id,action)); }
        public boolean advance(long now,ActionOutput output){
            if(now<readyAt)return false;int visited=0;
            while(!stack.isEmpty()){
                Frame frame=stack.peekLast();if(frame.index>=frame.nodes.size()){stack.removeLast();continue;}
                if(++visited>MAX_NODES)throw new IllegalStateException("Invalid compiled camera sequence");Stmt node=frame.nodes.get(frame.index++);
                if(node instanceof Action action)action.request(output);
                else if(node instanceof Wait wait){readyAt=after(now,wait.duration.ticks);return false;}
                else if(node instanceof If branch){
                    // Unknown inventory must not turn NOT or an ELSE branch into a fabricated absence.
                    if(branch.condition.requiresInventory()&&(snapshot==null||snapshot.inventory==null))continue;
                    stack.addLast(new Frame(branch.condition.test(snapshot)?branch.yes:branch.no));
                }
            }return true;
        }
    }
    private static long after(long now,int ticks){return now>Long.MAX_VALUE-ticks?Long.MAX_VALUE:now+ticks;}
    private static boolean hasWait(List<Stmt> body){for(Stmt node:body)if(node instanceof Wait||node instanceof If branch&&(hasWait(branch.yes)||hasWait(branch.no)))return true;return false;}
    public static final class Program {
        private final List<Event> events;
        private final Set<String> inventoryItems;
        private Program(List<Event> events){this.events=List.copyOf(events);Set<String> selected=new HashSet<>();for(Event event:events)inventoryQueries(event.body,selected);inventoryItems=Set.copyOf(selected);}
        public Set<UUID> cameras(){Set<UUID> ids=new HashSet<>();for(Event event:events)ids.add(event.camera);return Set.copyOf(ids);}
        public boolean requiresStages(UUID camera){return events.stream().anyMatch(event->event.camera.equals(camera)&&event.trigger.startsWith("stage"));}
        public List<Job> entryJobs(UUID camera,Entity entity,Vec3 origin,long now){if(entity==null)return List.of();List<Event> matching=events.stream().filter(event->event.trigger.equals("entry")&&event.camera.equals(camera)).toList();if(matching.isEmpty())return List.of();Snapshot snapshot=Snapshot.capture(entity,origin,inventoryItems);List<Job> jobs=new ArrayList<>();for(Event event:matching)jobs.add(new Job(event,snapshot,now));return List.copyOf(jobs);}
        public List<Job> emptyJobs(UUID camera,long now){List<Job> jobs=new ArrayList<>();for(Event event:events)if(event.trigger.equals("empty")&&event.camera.equals(camera))jobs.add(new Job(event,null,now));return List.copyOf(jobs);}
        public List<Job> stageJobs(UUID camera,int stage,long now){if(stage<1||stage>3)return List.of();List<Job> jobs=new ArrayList<>();for(Event event:events)if(event.trigger.equals("stage"+stage)&&event.camera.equals(camera))jobs.add(new Job(event,null,now));return List.copyOf(jobs);}
        /** Compatibility dispatcher for immediate legacy programs. Timed callers use jobs. */
        public void dispatch(UUID camera,Entity entity,Vec3 origin,BiConsumer<UUID,String> action){immediate(entryJobs(camera,entity,origin,0),action);}
        public void dispatchEmpty(UUID camera,Vec3 origin,BiConsumer<UUID,String> action){immediate(emptyJobs(camera,0),action);}
        private void immediate(List<Job> jobs,BiConsumer<UUID,String> action){if(jobs.stream().anyMatch(job->job.scheduled))throw new IllegalStateException("Timed camera programs require entryJobs/emptyJobs");for(Job job:jobs)job.advance(0,action);}
        public CompoundTag save(){CompoundTag root=new CompoundTag();root.putInt("Version",1);ListTag list=new ListTag();for(Event event:events){CompoundTag tag=new CompoundTag();tag.putUUID("Camera",event.camera);if(!event.trigger.equals("entry")){tag.putString("Trigger",event.trigger);if(event.trigger.equals("empty")&&event.delay.explicit)duration(tag,event.delay);}tag.put("Body",statements(event.body));list.add(tag);}root.put("Events",list);return root;}
    }
    private record Event(UUID camera,String trigger,Duration delay,List<Stmt> body) {}
    private interface Stmt {CompoundTag save();}
    private interface Cond {boolean test(Snapshot snapshot);CompoundTag save();default boolean requiresInventory(){return false;}}
    private record Action(UUID target,String action,String text) implements Stmt {
        public void request(ActionOutput consumer){consumer.accept(target,action,text);}
        public CompoundTag save(){CompoundTag tag=op("action");tag.putUUID("Target",target);tag.putString("Action",action);if(action.equals("panel_text"))tag.putString("Text",text);return tag;}
    }
    private record Wait(Duration duration) implements Stmt {public CompoundTag save(){CompoundTag tag=op("wait");CameraProgram.duration(tag,duration);return tag;}}
    private record If(Cond condition,List<Stmt> yes,List<Stmt> no) implements Stmt {
        public CompoundTag save(){CompoundTag tag=op("if");tag.put("Condition",condition.save());tag.put("Then",statements(yes));tag.put("Else",statements(no));return tag;}
    }
    private record BooleanCondition(String operator,Cond left,Cond right) implements Cond {
        public boolean requiresInventory(){return left.requiresInventory()||right!=null&&right.requiresInventory();}
        public boolean test(Snapshot snapshot){return operator.equals("not")?!left.test(snapshot):operator.equals("and")?left.test(snapshot)&&right.test(snapshot):left.test(snapshot)||right.test(snapshot);}
        public CompoundTag save(){CompoundTag tag=op(operator);tag.put(operator.equals("not")?"Value":"Left",left.save());if(right!=null)tag.put("Right",right.save());return tag;}
    }
    private record Compare(String getter,String comparator,String text,double number) implements Cond {
        public boolean requiresInventory(){return getter.equals("inventory_item");}
        public boolean test(Snapshot snapshot){
            if(snapshot==null)return false;
            if(getter.equals("distance")||getter.equals("health")||getter.equals("inventory_item")){
                if(getter.equals("health")&&snapshot.health==null||getter.equals("inventory_item")&&snapshot.inventory==null)return false;
                double value=getter.equals("distance")?snapshot.distance:getter.equals("health")?snapshot.health:snapshot.inventory.getOrDefault(text,0);
                return switch(comparator){case "eq"->value==number;case "ne"->value!=number;case "lt"->value<number;case "le"->value<=number;case "gt"->value>number;default->value>=number;};
            }
            String value=switch(getter){case "type"->snapshot.type;case "category"->snapshot.category;case "name"->snapshot.name;case "uuid"->snapshot.uuid;case "team"->snapshot.team;default->"";};
            return comparator.equals("eq")==value.equals(text);
        }
        public CompoundTag save(){CompoundTag tag=op("compare");tag.putString("Getter",getter);tag.putString("Test",comparator);if(getter.equals("inventory_item")){tag.putString("Item",text);tag.putDouble("Number",number);}else if(getter.equals("distance")||getter.equals("health"))tag.putDouble("Number",number);else tag.putString("Text",text);return tag;}
    }
    private static String category(Entity entity){if(entity instanceof Player)return "player";String name=entity.getType().getCategory().name();if(name.equals("MONSTER"))return "hostile";return Set.of("CREATURE","AMBIENT","WATER_CREATURE","WATER_AMBIENT","UNDERGROUND_WATER_CREATURE","AXOLOTLS").contains(name)?"animal":"other";}
    private static CompoundTag op(String operator){CompoundTag tag=new CompoundTag();tag.putString("Op",operator);return tag;}
    private static void duration(CompoundTag tag,Duration duration){tag.putInt("Ticks",duration.ticks);tag.putString("Unit",duration.unit);}
    private static ListTag statements(List<Stmt> nodes){ListTag list=new ListTag();for(Stmt node:nodes)list.add(node.save());return list;}
    private static final class Parser {
        int nodes;
        final Map<UUID,String> links=new HashMap<>();
        Parser(List<CameraTerminalEntity.LinkView> views){for(var view:views)links.put(view.id(),view.kind());}
        void count(int depth){if(++nodes>MAX_NODES||depth>MAX_DEPTH)throw new IllegalArgumentException("program_limits");}
        UUID id(CompoundTag tag,String field){if(!tag.hasUUID(field))throw new IllegalArgumentException("invalid_program");return tag.getUUID(field);}
        List<Stmt> body(CompoundTag tag,String field,int depth){
            return body(tag,field,depth,false);
        }
        List<Stmt> body(CompoundTag tag,String field,int depth,boolean actionsOnly){
            if(!(tag.get(field) instanceof ListTag raw)||!raw.isEmpty()&&raw.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalArgumentException("invalid_program");
            if(raw.size()>MAX_NODES)throw new IllegalArgumentException("program_limits");List<Stmt> result=new ArrayList<>();for(Tag value:raw){CompoundTag node=(CompoundTag)value;if(actionsOnly&&!Set.of("action","wait").contains(node.getString("Op")))throw new IllegalArgumentException("empty_actions_only");result.add(statement(node,depth));}return List.copyOf(result);
        }
        Duration time(CompoundTag tag,boolean required){
            boolean explicit=tag.contains("Ticks")||tag.contains("Unit");if((required||tag.contains("Ticks"))&&!tag.contains("Ticks",Tag.TAG_INT))throw new IllegalArgumentException("invalid_duration");
            int ticks=tag.contains("Ticks")?tag.getInt("Ticks"):0;if(ticks<(required?1:0)||ticks>MAX_DURATION_TICKS)throw new IllegalArgumentException("invalid_duration");
            if(tag.contains("Unit")&&!tag.contains("Unit",Tag.TAG_STRING))throw new IllegalArgumentException("invalid_duration");String unit=tag.contains("Unit")?tag.getString("Unit"):"seconds";if(!Set.of("seconds","minutes").contains(unit))throw new IllegalArgumentException("invalid_duration");return new Duration(ticks,unit,explicit||required);
        }
        Stmt statement(CompoundTag tag,int depth){
            count(depth);if(tag.getString("Op").equals("if"))return new If(condition(tag.getCompound("Condition"),depth+1),body(tag,"Then",depth+1),body(tag,"Else",depth+1));
            if(tag.getString("Op").equals("wait"))return new Wait(time(tag,true));
            if(!tag.getString("Op").equals("action"))throw new IllegalArgumentException("invalid_program");
            UUID target=id(tag,"Target");String action=tag.getString("Action"),kind=links.get(target);
            if(kind==null||!Set.of("door_open","door_close","alarm_on","alarm_off","emitter_on","emitter_off","panel_text").contains(action)||!action.startsWith(kind+"_"))throw new IllegalArgumentException("invalid_target");String text="";
            if(action.equals("panel_text")){
                text=tag.getString("Text");
                if(!tag.contains("Text",Tag.TAG_STRING)||text.length()>fr.vengelis.industrialfacilities.panel.PanelBlockEntity.MAX_TEXT||!text.equals(fr.vengelis.industrialfacilities.panel.PanelBlockEntity.sanitize(text)))throw new IllegalArgumentException("invalid_panel_text");
            }
            return new Action(target,action,text);
        }
        Cond condition(CompoundTag tag,int depth){
            count(depth);String op=tag.getString("Op");if(op.equals("and")||op.equals("or"))return new BooleanCondition(op,condition(tag.getCompound("Left"),depth+1),condition(tag.getCompound("Right"),depth+1));
            if(op.equals("not"))return new BooleanCondition(op,condition(tag.getCompound("Value"),depth+1),null);
            if(!op.equals("compare"))throw new IllegalArgumentException("invalid_program");String getter=tag.getString("Getter"),test=tag.getString("Test");
            if(!Set.of("type","category","name","uuid","team","distance","health","inventory_item").contains(getter))throw new IllegalArgumentException("invalid_program");
            if(getter.equals("inventory_item")){
                String selected=tag.getString("Item");ResourceLocation item=selected.length()<=MAX_ITEM_ID_LENGTH?ResourceLocation.tryParse(selected):null;
                if(!tag.contains("Item",Tag.TAG_STRING)||item==null||!BuiltInRegistries.ITEM.containsKey(item)||BuiltInRegistries.ITEM.get(item)==Items.AIR)throw new IllegalArgumentException("invalid_inventory_item");
                double value=tag.getDouble("Number");if(!tag.contains("Number",Tag.TAG_ANY_NUMERIC)||!Double.isFinite(value)||value<0||value>MAX_INVENTORY_COUNT||value!=Math.rint(value)||!Set.of("eq","ne","lt","le","gt","ge").contains(test))throw new IllegalArgumentException("invalid_inventory_count");
                return new Compare(getter,test,item.toString(),value);
            }
            if(getter.equals("distance")||getter.equals("health")){
                double value=tag.getDouble("Number");if(!tag.contains("Number",Tag.TAG_ANY_NUMERIC)||!Double.isFinite(value)||Math.abs(value)>1_000_000||!Set.of("eq","ne","lt","le","gt","ge").contains(test))throw new IllegalArgumentException("invalid_program");return new Compare(getter,test,"",value);
            }
            String value=tag.getString("Text");if(!tag.contains("Text",Tag.TAG_STRING)||value.length()>64||value.codePoints().anyMatch(Character::isISOControl)||!Set.of("eq","ne").contains(test))throw new IllegalArgumentException("invalid_program");return new Compare(getter,test,value,0);
        }
        Program parse(CompoundTag root){
            if(root.getInt("Version")!=1||!(root.get("Events") instanceof ListTag raw)||!raw.isEmpty()&&raw.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalArgumentException("invalid_program");if(raw.size()>MAX_EVENTS)throw new IllegalArgumentException("program_limits");List<Event> events=new ArrayList<>();
            for(Tag value:raw){
                count(1);CompoundTag tag=(CompoundTag)value;UUID camera=id(tag,"Camera");if(!"camera".equals(links.get(camera)))throw new IllegalArgumentException("invalid_camera");
                if(tag.contains("Trigger")&&!tag.contains("Trigger",Tag.TAG_STRING))throw new IllegalArgumentException("invalid_trigger");String trigger=tag.contains("Trigger")?tag.getString("Trigger"):"entry";
                if(!Set.of("entry","empty","stage1","stage2","stage3").contains(trigger))throw new IllegalArgumentException("invalid_trigger");
                if(!trigger.equals("empty")&&(tag.contains("Ticks")||tag.contains("Unit")))throw new IllegalArgumentException("invalid_duration");Duration delay=trigger.equals("empty")?time(tag,false):new Duration(0,"seconds",false);
                if(trigger.startsWith("stage")&&tag.get("Body") instanceof ListTag stageBody)for(Tag node:stageBody)if(!(node instanceof CompoundTag statement)||!Set.of("action","wait").contains(statement.getString("Op")))throw new IllegalArgumentException("entity_context_required");
                events.add(new Event(camera,trigger,delay,body(tag,"Body",2,!trigger.equals("entry"))));
            }return new Program(events);
        }
    }
    public static CompoundTag empty(){CompoundTag tag=new CompoundTag();tag.putInt("Version",1);tag.put("Events",new ListTag());return tag;}
    static CompoundTag withoutTargets(CompoundTag program,Set<UUID> removed){
        CompoundTag copy=program.copy();boolean changed=false;
        for(Tag raw:copy.getList("Events",Tag.TAG_COMPOUND))if(raw instanceof CompoundTag event)
            changed|=removeTargets(event.getList("Body",Tag.TAG_COMPOUND),removed,2);
        return changed?copy:program;
    }
    private static boolean removeTargets(ListTag body,Set<UUID> removed,int depth){
        if(depth>MAX_DEPTH)return false;
        boolean changed=false;for(int i=body.size()-1;i>=0;i--){
            CompoundTag node=body.getCompound(i);
            switch(node.getString("Op")){
                case "action"->{if(node.hasUUID("Target")&&removed.contains(node.getUUID("Target"))){body.remove(i);changed=true;}}
                case "if"->{changed|=removeTargets(node.getList("Then",Tag.TAG_COMPOUND),removed,depth+1);changed|=removeTargets(node.getList("Else",Tag.TAG_COMPOUND),removed,depth+1);}
            }
        }return changed;
    }
    public static Validation validate(CompoundTag root,List<CameraTerminalEntity.LinkView> links){if(root==null)return new Validation(false,"gui.industrial_facilities.camera.invalid_program",empty());try{Program program=compile(root,links);return new Validation(true,"",program.save());}catch(IllegalArgumentException e){return new Validation(false,"gui.industrial_facilities.camera."+e.getMessage(),empty());}}
    public static Program compile(CompoundTag root,List<CameraTerminalEntity.LinkView> links){return new Parser(links).parse(root);}
    private CameraProgram(){}
}
