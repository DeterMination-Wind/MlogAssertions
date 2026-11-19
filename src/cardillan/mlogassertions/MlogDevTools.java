package cardillan.mlogassertions;

import arc.Events;
import arc.util.Log;
import cardillan.mlogassertions.auxiliary.AuxiliaryStore;
import cardillan.mlogassertions.data.CustomGameRules;
import cardillan.mlogassertions.data.MapIndex;
import cardillan.mlogassertions.data.MemoryVars;
import cardillan.mlogassertions.logic.AssertLogic;
import cardillan.mlogassertions.logic.InstrumentationEngine;
import cardillan.mlogassertions.ui.BuildConfiguration;
import cardillan.mlogassertions.ui.LogicDialogAddon;
import cardillan.mlogassertions.ui.MapIndicators;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.mod.Mod;
import org.jetbrains.annotations.NonNls;

public class MlogDevTools extends Mod {
    public static final String MLOG_DEV_TOOLS = "[Mlog Dev Tools] ";

    public MlogDevTools() {
        Events.on(EventType.ClientLoadEvent.class, e -> {
            Settings.init();

            // Basic data
            MapIndex.init();
            MemoryVars.init();
            AuxiliaryStore.init();

            // Logic
            AssertLogic.init();
            InstrumentationEngine.init();

            // Game rules
            CustomGameRules.init();

            // UI
            MapIndicators.init();
            LogicDialogAddon.init();
            BuildConfiguration.init();
        });
    }

    public static boolean canPauseGame() {
        return !Vars.net.active() && !Vars.state.isMenu();
    }

    public static String s(@NonNls String str) {
        return str;
    }

    public static void err(@NonNls String message, Throwable err) {
        Log.err(MLOG_DEV_TOOLS + message, err);
    }

    public static void err(@NonNls String message, Object... arguments) {
        Log.err(MLOG_DEV_TOOLS + message, arguments);
    }

    public static void warn(@NonNls String message, Object... arguments) {
        Log.warn(MLOG_DEV_TOOLS + message, arguments);
    }

    public static void info(@NonNls String message, Object... arguments) {
        Log.info(MLOG_DEV_TOOLS + message, arguments);
    }
}
