package cardillan.mlogassertions;

import arc.Core;
import arc.func.Intc;
import arc.graphics.Color;
import arc.scene.event.Touchable;
import arc.scene.ui.Label;
import arc.scene.ui.Slider;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.util.Log;
import cardillan.mlogassertions.data.LogMessages;
import cardillan.mlogassertions.data.SnapshotManager;
import cardillan.mlogassertions.ui.BuildConfiguration;
import cardillan.mlogassertions.ui.LogicDialogAddon;
import cardillan.mlogassertions.ui.MapIndicators;
import cardillan.mlogassertions.ui.VarsDialog;
import mindustry.Vars;
import mindustry.gen.Icon;
import mindustry.logic.LExecutor;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable;
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable.Setting;
import mindustry.ui.dialogs.SettingsMenuDialog.StringProcessor;
import org.jetbrains.annotations.NonNls;

import java.lang.reflect.Modifier;

import static arc.Core.settings;

public class Settings {
    public static final String SETTING = "setting.";

    public static void init() {
        Vars.ui.settings.addCategory(Core.bundle.get("mlogdevtools.mod.name"), Icon.wrench, t -> {
            category(t, "category-user-interface");

            t.sliderPref(Constants.tripleTapSpeed, 500, 0, 3000, 50,
                    i -> i == 0 ? bundle(Constants.tripleTapSpeed, "disabled") :
                            format(Constants.tripleTapSpeed, "delay", i),
                    i -> LogicDialogAddon.tripleTapSpeed = i);

            t.checkPref(Constants.overrideConfigMenu, true, BuildConfiguration::setActive);

            final String[] commands = bundle(Constants.configButtonTexts).split(";");

            t.sliderPref(Constants.leftConfigButton, 2, 0, commands.length - 1, 1, i -> commands[i]);
            t.sliderPref(Constants.rightConfigButton, 3, 0, commands.length - 1, 1, i -> commands[i]);

            category(t, "category-map-indicators");

            t.sliderPref(Constants.minWaitTimeUpdate, 1000, 0, 10000, 500,
                    i -> i == 0 ? bundle(Constants.minWaitTimeUpdate, "none") : Double.toString(i / 1000.0),
                    i -> MapIndicators.minWaitTimeUpdate = i);

            steppedPref(t,Constants.processorUpdatesPerTick, 50, new int[]{1, 5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000},
                    Integer::toString,
                    i -> MapIndicators.processorUpdatesPerTick = i);

            t.sliderPref(Constants.warnEffectFrequency, 0, -5, 60, 5,
                    i -> i < 0 ? bundle(Constants.warnEffectFrequency, "never") :
                            i == 0 ? bundle(Constants.warnEffectFrequency, "once") :
                                    format(Constants.warnEffectFrequency, "every", i),
                    i -> MapIndicators.warnEffectFrequency = i);

            category(t, "category-vars-screen");

            t.sliderPref(Constants.variableUpdateFrequency, 15, 0, 60, 5,
                    i -> i == 0 ? bundle(Constants.variableUpdateFrequency, "everyframe")
                            : format(Constants.variableUpdateFrequency, "ticks", i),
                    i -> VarsDialog.updateFrequency = i);

            t.sliderPref(Constants.varsSignificantDigits, 7, 3, 15, 1,
                    Integer::toString,
                    i -> VarsDialog.significantDigits = i);

            category(t, "category-debugging");

            if (canSetInstructions()) {
                t.sliderPref(Constants.maxInstructions, 1000, 1000, 2000, 100,
                        Integer::toString,
                        i -> LExecutor.maxInstructions = i);
            }

            t.checkPref(Constants.disableBreakpoints, false);
            t.checkPref(Constants.assertsAreBreakpoints, false);
            t.checkPref(Constants.freeCameraOnBreakpoint, true);

            category(t, "category-profiling");

            t.checkPref(Constants.startProfilerImmediately, false);

            category(t, "category-logging");

            steppedPref(t, Constants.logMessageLimit, 100, new int[]{0, 20, 50, 100, 200, 500, 1000, 2000, 5000},
                    i -> i == 0 ? bundle(Constants.logMessageLimit, "disabled") : Integer.toString(i),
                    i -> LogMessages.maxMessages = i);

            t.sliderPref(Constants.logLevelLimit, 0, 0, 3, 1,
                    i -> Log.LogLevel.values()[i].name(),
                    i -> LogMessages.maxLevel = i);

            t.checkPref(Constants.clearLogOnRestart, true);
            t.checkPref(Constants.generateSystemLogs, true);

            category(t, "category-snapshots");

            steppedPref(t, Constants.snapshotLimit, 20, new int[]{0, 5, 10, 20, 50, 100, 200, 500, 1000},
                    i -> i == 0 ? bundle(Constants.snapshotLimit, "disabled") : Integer.toString(i),
                    i -> SnapshotManager.maxSnapshots = i);

            t.checkPref(Constants.snapshotOnBreakpoint, false);
            t.checkPref(Constants.snapshotOnAssertion, false);
        });

        Vars.ui.settings.hidden(() -> {
            SnapshotManager.updateLimit();
            LogMessages.updateLimit();
        });

        if (canSetInstructions()) {
            LExecutor.maxInstructions = Core.settings.getInt(Constants.maxInstructions);
        }
        MapIndicators.minWaitTimeUpdate = Core.settings.getInt(Constants.minWaitTimeUpdate);
        MapIndicators.processorUpdatesPerTick = Core.settings.getInt(Constants.processorUpdatesPerTick, 50);
        MapIndicators.warnEffectFrequency = Core.settings.getInt(Constants.warnEffectFrequency);
        SnapshotManager.maxSnapshots = Core.settings.getInt(Constants.snapshotLimit);

        VarsDialog.updateFrequency = Core.settings.getInt(Constants.variableUpdateFrequency);
        VarsDialog.significantDigits = Core.settings.getInt(Constants.varsSignificantDigits);

        LogMessages.maxMessages = Core.settings.getInt(Constants.logMessageLimit);
    }

    private static String bundle(@NonNls String key) {
        return Core.bundle.get(SETTING + key);
    }

    private static String bundle(@NonNls String key, @NonNls String suffix) {
        return Core.bundle.get(SETTING + key + "." + suffix);
    }

    private static String format(@NonNls String key, @NonNls String suffix, Object... args) {
        return Core.bundle.format(SETTING + key + "." + suffix, args);
    }

    @NonNls
    private static void category(SettingsTable t, String name) {
        t.pref(new SettingsCategory(name));
    }

    private static SteppedSliderSetting steppedPref(SettingsTable t, String name, int def, int[] steps, StringProcessor s, Intc changed) {
        settings.defaults(name, def);
        SteppedSliderSetting res;
        t.pref(res = new SteppedSliderSetting(name, def, steps, s, changed));
        t.rebuild();
        return res;
    }

    public static class SettingsCategory extends Setting {
        public SettingsCategory(String name) {
            super(name);
        }

        @Override
        public void add(SettingsTable table) {
            Cell<Label> cell = table.add(title);
            cell.left().color(Color.lightGray).pad(30, 10, 4, 0).row();
            table.image().color(Color.lightGray).fillX().height(3).pad(0, 0, 5, 0).row();
        }
    }

    public static class SteppedSliderSetting extends Setting {
        int def;
        int[] steps;
        StringProcessor sp;
        Intc changed;

        public SteppedSliderSetting(String name, int def, int[] steps, StringProcessor s, Intc changed) {
            super(name);
            this.def = def;
            this.steps = steps;
            this.sp = s;
            this.changed = changed;
        }

        @Override
        public void add(SettingsTable table) {
            Slider slider = new Slider(0, steps.length - 1, 1, false);

            int val = settings.getInt(name);
            int pos = 0, minDif = Integer.MAX_VALUE;
            for (int i = 0; i < steps.length; i++) {
                if (steps[i] == val) {
                    pos = i;
                    break;
                }
                int dif = Math.abs(steps[i] - val);
                if (dif < minDif) {
                    minDif = dif;
                    pos = i;
                }
            }
            slider.setValue(pos);

            Label value = new Label("", Styles.outlineLabel);
            Table content = new Table();
            content.add(title, Styles.outlineLabel).left().growX().wrap();
            content.add(value).padLeft(10f).right();
            content.margin(3f, 33f, 3f, 33f);
            content.touchable = Touchable.disabled;

            slider.changed(() -> {
                int v = steps[(int) slider.getValue()];
                settings.put(name, v);
                value.setText(sp.get(v));
                if (changed != null) changed.get(v);
            });

            slider.change();

            addDesc(table.stack(slider, content).width(Math.min(Core.graphics.getWidth() / 1.2f / Scl.scl(1f), 500f)).left().padTop(4f).get());
            table.row();
        }
    }

    public static boolean disableBreakpoints() {
        return Core.settings.getBool(Constants.disableBreakpoints, false);
    }

    public static boolean assertsAreBreakpoints() {
        return Core.settings.getBool(Constants.assertsAreBreakpoints, false);
    }

    public static boolean detachCameraOnBreakpoint() {
        return Core.settings.getBool(Constants.freeCameraOnBreakpoint, true);
    }

    public static boolean snapshotOnBreakpoint() {
        return Core.settings.getBool(Constants.snapshotOnBreakpoint, false);
    }

    public static boolean snapshotOnAssertion() {
        return Core.settings.getBool(Constants.snapshotOnAssertion, false);
    }

    public static boolean canSetInstructions() {
        try {
            return !Modifier.isFinal(LExecutor.class.getField("maxInstructions").getModifiers());
        } catch (NoSuchFieldException e) {
            return false;
        }
    }
}
