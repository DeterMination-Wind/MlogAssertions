package cardillan.mlogassertions.data;

import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Queue;
import arc.struct.Seq;
import cardillan.mlogassertions.logic.SnapshotType;
import cardillan.mlogassertions.auxiliary.AuxiliaryData;
import cardillan.mlogassertions.auxiliary.AuxiliaryStore;
import mindustry.ai.types.LogicAI;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.logic.LVar;
import mindustry.logic.Senseable;
import mindustry.world.blocks.logic.LogicBlock.LogicBuild;
import mindustry.world.blocks.logic.MemoryBlock;
import mindustry.world.blocks.logic.MemoryBlock.MemoryBuild;

public class SnapshotManager {
    public static int maxSnapshots = 20;
    private static int id = 0;

    // Snapshots
    private static final ObjectMap<Senseable, SnapshotRecord> snapshots = new ObjectMap<>();

    public static void updateLimit() {
        if (maxSnapshots == 0) {
            AuxiliaryStore.clear(AuxiliaryStore.snapshotRecord);
        } else {
            AuxiliaryStore.each(AuxiliaryStore.snapshotRecord, (o, r) -> r.adjustSize(maxSnapshots));
        }
    }

    public static VariableValues liveView(Senseable entity) {
        return entity instanceof MemoryBuild build ? new MemoryVars(build) :
                entity instanceof LogicBuild build && build.executor.counter != null ? new ProcessorVars(build) :
                        new SensorVars(entity);
    }

    public static boolean hasSnapshots(Senseable entity) {
        SnapshotRecord record = AuxiliaryStore.get(entity, AuxiliaryStore.snapshotRecord);
        return record != null && record.queue.size > 0;
    }

    private static SnapshotRecord getExisting(Senseable object) {
        return AuxiliaryStore.create(object, AuxiliaryStore.snapshotRecord);
    }

    public static Queue<Snapshot> get(Senseable entity) {
        return getExisting(entity).queue;
    }

    public static Snapshot create(Senseable entity, String name) {
        return create(entity, SnapshotType.connected, name);
    }

    public static Snapshot create(Senseable entity, SnapshotType type, String name) {
        return create(entity, type, name, null);
    }

    public static Snapshot create(Senseable entity, SnapshotType type, String name, LVar[] vars) {
        if (maxSnapshots == 0) return null;

        VariableValues content = liveView(entity);
        if (!content.valid()) return null;

        id++;
        Seq<Senseable> entities = new Seq<>();
        switch (type) {
            case isolated:
                entities.add(entity);
                break;

            case recording:
                if (vars != null) {
                    ObjectSet<Object> set = new ObjectSet<>();
                    set.add(entity);
                    entities.add(entity);
                    for (LVar v : vars) {
                        if (v.obj() instanceof Senseable e && set.add(e)) entities.add(e);
                    }
                    break;
                }
                // fall through

            case connected:
                ObjectSet<Object> set = new ObjectSet<>();
                set.add(entity);
                entities.add(entity);

                content.eachObject(o -> {
                    if (o instanceof Building build && set.add(o)) entities.add(build);
                    if (o instanceof Unit unit && set.add(o)) entities.add(unit);
                });
                if (entity instanceof LogicBuild) {
                    Groups.unit.each(u -> {
                        if (u.controller() instanceof LogicAI ai && ai.controller == entity && set.add(u)) {
                            entities.add(u);
                        }
                    });
                }
                break;

            case global:
                entities.addAll(MapIndex.processors);
                entities.addAll(MapIndex.memories);
                break;
        }

        Seq<Snapshot> group = type == SnapshotType.isolated ? null : new Seq<>();
        Snapshot result = null;
        boolean first = true;

        for (Senseable e : entities) {
            Snapshot snapshot = create(e, type, group, name);
            if (snapshot == null) return null;

            if (first) {
                result = snapshot;
                if (vars != null) snapshot.setDefaultFilter(vars);
                first = false;
            }

            if (vars == null) {
                AuxiliaryStore.store(snapshot.entity(), AuxiliaryStore.snapshotRecord, r -> r.add(snapshot));
            }
        }

        return result;
    }

    public static void register(Snapshot snapshot) {
        getExisting(snapshot.entity()).register();
    }

    public static void delete(Snapshot snapshot) {
        getExisting(snapshot.entity()).removed(snapshot);
    }

    public static void deleteEntity(Senseable entity) {
        getExisting(entity).clear();
    }

    private static Snapshot create(Senseable entity, SnapshotType type, Seq<Snapshot> group, String name) {
        Snapshot snapshot =
                entity instanceof MemoryBlock.MemoryBuild build ? MemorySnapshot.create(build, type, id, group, name) :
                entity instanceof LogicBuild build ? ProcessorSnapshot.create(build, type, id, group, name) :
                SensorSnapshot.create(entity, type, id, group, name);

        if (group != null && snapshot != null) group.add(snapshot);
        return snapshot;
    }

    public static class SnapshotRecord implements AuxiliaryData {
        final Queue<Snapshot> queue = new Queue<>();
        int size = 0;

        public SnapshotRecord(Senseable object) {
        }

        @Override
        public AuxiliaryData reset(Senseable object) {
            return this;
        }

        void add(Snapshot snapshot) {
            adjustSize(maxSnapshots);
            queue.addFirst(snapshot);
            size += snapshot.recording() != null ? snapshot.recording().size : 1;
        }

        void register() {
            size++;
        }

        void clear() {
            queue.clear();
            size = 0;
        }

        void adjustSize(int limit) {
            while (size > limit) {
                Snapshot snapshot = queue.removeLast();
                size -= snapshot.recording() != null ? snapshot.recording().size : 1;
            }
        }

        void removed(Snapshot snapshot) {
            size -= snapshot.recording() != null ? snapshot.recording().size : 1;
        }
    }
}
