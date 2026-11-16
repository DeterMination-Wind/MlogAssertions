package cardillan.mlogassertions;

import arc.Events;
import cardillan.mlogassertions.data.*;
import cardillan.mlogassertions.logic.AssertLogic;
import cardillan.mlogassertions.logic.InstrumentationEngine;
import cardillan.mlogassertions.auxiliary.AuxiliaryStore;
import cardillan.mlogassertions.ui.MapIndicators;
import cardillan.mlogassertions.ui.BuildConfiguration;
import cardillan.mlogassertions.ui.LogicDialogAddon;
import mindustry.game.EventType;
import mindustry.mod.Mod;

public class MlogAssertions extends Mod {

    public MlogAssertions() {
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
}
