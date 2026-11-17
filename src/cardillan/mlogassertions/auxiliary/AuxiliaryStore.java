package cardillan.mlogassertions.auxiliary;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.func.Cons2;
import arc.func.Func;
import arc.func.Prov;
import arc.struct.ObjectMap;
import cardillan.mlogassertions.Constants;
import cardillan.mlogassertions.data.LogMessages;
import cardillan.mlogassertions.data.LogMessages.LoggingData;
import cardillan.mlogassertions.data.SnapshotManager.SnapshotRecord;
import cardillan.mlogassertions.logic.Instrumentation;
import cardillan.mlogassertions.ui.MapIndicators.IndicatorData;
import mindustry.game.EventType;
import mindustry.logic.Senseable;
import mindustry.world.blocks.logic.LogicBlock;

/// This class manages auxiliary data stored with all kinds of objects on the map (processors, memory blocks,
/// any other building or units).
///
/// This allows to centralize the management of the auxiliary data: all auxiliary data are removed when the build
/// is destroyed or when the game is reset.
///
/// The types of data are identified by instances of the `Key` class, providing generic type safety. Keys need to be
/// created within this class, as the class also manages static indexes into the storage array.
public class AuxiliaryStore {
    private static final ObjectMap<Senseable, AuxiliaryStore> auxiliaryStore = new ObjectMap<>();

    public static void init() {
        Events.on(EventType.ResetEvent.class, e -> {
            auxiliaryStore.clear();
        });

        Events.on(EventType.BlockBuildEndEvent.class, e -> {
            if (e.breaking && e.tile.build != null) {
                auxiliaryStore.remove(e.tile.build);
            }
        });

        // For processors: recompilation resets the state
        Events.on(EventType.ConfigEvent.class, e -> {
            if (e.value instanceof byte[]) {
                reset(e.tile);
            }
        });
    }

    public static <T extends AuxiliaryData> void clear(Key<T> key) {
        for (ObjectMap.Values<AuxiliaryStore> values = auxiliaryStore.values(); values.hasNext(); ) {
            AuxiliaryStore store = values.next();
            store.remove(key);
            if (store.isEmpty()) values.remove();
        }
    }

    public static <T extends AuxiliaryData> void each(Key<T> key, Cons2<Senseable, T> action) {
        auxiliaryStore.each((object, store) -> {
            T instance = store.get(key);
            if (instance != null) action.get(object, instance);
        });
    }

    public static void remove(Senseable object, Key<? extends AuxiliaryData> key) {
        AuxiliaryStore store = auxiliaryStore.get(object);
        if (store != null) {
            store.remove(key);
            if (store.isEmpty()) auxiliaryStore.remove(object);
        }
    }

    /// @return the auxiliary data stored with the object; null when no data is found
    public static <T extends AuxiliaryData> T get(Senseable object, Key<T> key) {
        AuxiliaryStore store = auxiliaryStore.get(object);
        return store == null ? null : store.get(key);
    }

    /// @return the auxiliary data stored with the object; creates a new instance when no data is found
    public static <T extends AuxiliaryData> T create(Senseable object, Key<T> key) {
        return auxiliaryStore.get(object, AuxiliaryStore::new).get(key, () -> key.creator.get(object));
    }

    /// Retrieves the given data from the store and passes it to the processor; if no data of the given type is stored,
    /// does nothing.
    public static <T extends AuxiliaryData> T process(Senseable object, Key<T> key, Cons<T> processor) {
        AuxiliaryStore store = auxiliaryStore.get(object);
        if (store == null) return null;

        T instance = store.get(key);
        if (instance != null) processor.get(instance);
        return instance;
    }

    /// Retrieves the given data from the store and passes it to the processor; if no data of the given type is stored,
    /// creates and processes a new instance.
    /// Returns the processed instance.
    public static <T extends AuxiliaryData> T store(Senseable object, Key<T> key, Cons<T> processor) {
        T instance = auxiliaryStore.get(object, AuxiliaryStore::new).get(key, () -> key.creator.get(object));
        processor.get(instance);
        return instance;
    }

    public static void reset(Senseable object) {
        AuxiliaryStore instance = auxiliaryStore.get(object);
        if (instance != null) {
            instance.resetData(object);
            if (object instanceof LogicBlock.LogicBuild build && Core.settings.getBool(Constants.generateSystemLogs, true)) {
                LogMessages.addSystemMessage(build, "Processor recompiled or restarted");
            } else {
                if (instance.isEmpty()) auxiliaryStore.remove(object);
            }
        }
    }

    // Data type keys
    private static int types = 0;
    public static final class Key<T extends AuxiliaryData> {
        private final int type = types++;
        private final Func<Senseable, T> creator;

        private Key(Func<Senseable, T> creator) {
            this.creator = creator;
        }
    }

    public static final Key<IndicatorData> indicatorData = new Key<>(IndicatorData::new);
    public static final Key<Instrumentation> instrumentation = new Key<>(Instrumentation::new);
    public static final Key<LoggingData> loggingData = new Key<>(LoggingData::new);
    public static final Key<SnapshotRecord> snapshotRecord = new Key<>(SnapshotRecord::new);

    // Private instance data

    private final AuxiliaryData[] data = new AuxiliaryData[types];
    private int size = 0;

    /// Called when the object in question has been reset: reconfigured or recompiled
    private void resetData(Senseable object) {
        size = 0;
        for (int i = 0; i < types; i++) {
            if (data[i] != null) {
                data[i] = data[i].reset(object);
                if (data[i] != null) size++;
            }
        }
    }

    private boolean isEmpty() {
        return size == 0;
    }

    private void remove(Key<? extends  AuxiliaryData> key) {
        if (data[key.type] != null) size--;
        data[key.type] = null;
    }

    @SuppressWarnings("unchecked")
    private <T extends AuxiliaryData> T get(Key<T> key) {
        return (T) data[key.type];
    }

    @SuppressWarnings("unchecked")
    private <T extends AuxiliaryData> T get(Key<T> key, Prov<T> creator) {
        T value = (T) data[key.type];
        if (value == null) {
            value = creator.get();
            data[key.type] = value;
            size++;
        }
        return value;
    }
}
