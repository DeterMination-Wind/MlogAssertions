package cardillan.mlogassertions.ui;

import arc.Core;
import arc.func.Cons;
import arc.math.Interp;
import arc.scene.Group;
import arc.scene.actions.Actions;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import arc.util.Log;
import cardillan.mlogassertions.Constants;
import cardillan.mlogassertions.data.SnapshotManager;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.gen.Building;
import mindustry.gen.Icon;
import mindustry.input.InputHandler;
import mindustry.ui.Styles;
import mindustry.ui.fragments.BlockConfigFragment;
import mindustry.world.blocks.logic.LogicBlock.LogicBuild;
import mindustry.world.blocks.logic.MemoryBlock;
import mindustry.world.blocks.logic.MemoryBlock.MemoryBuild;

import java.lang.reflect.Field;

import static mindustry.Vars.player;

public class BuildConfiguration {
    public enum Action {
        none        (null, null),
        snapshot    (Icon.box, SnapshotManager::createUser),
        logScreen   (Icon2.log,LogDialog::show),
        varsScreen  (Icon.menu, VarsDialog::show),
        profiler    (Icon.chartBar, ProfileDialog::show),
        ;

        private final TextureRegionDrawable icon;
        private final Cons<LogicBuild> action;

        Action(TextureRegionDrawable icon, Cons<LogicBuild> action) {
            this.icon = icon;
            this.action = action;
        }

        private void createConfigButton(Table table, LogicBuild build) {
            if (this == none || this == snapshot && SnapshotManager.maxSnapshots == 0) return;
            table.button(icon, Styles.cleari, () -> action.get(build)).size(40);
        }
    }

    public static final Action[] actions = Action.values();

    private static Action getAction(boolean left) {
        int index = Core.settings.getInt(left ? Constants.leftConfigButton : Constants.rightConfigButton);
        return index >= 0 && index < actions.length ? actions[index] : Action.none;
    }

    public static void init() {
        if (Core.settings.getBool(Constants.overrideConfigMenu)) {
            setActive(true);
        }
    }

    private static Field configTable;
    private static Field configSelected;
    private static Field config;
    private static BlockConfigFragment delegate;

    public static void setActive(boolean active) {
        InputHandler input = Vars.control.input;

        // No change
        if (active == (delegate != null)) return;

        try {
            // BlockConfigFragment private fields
            if (configTable == null) {
                configTable = BlockConfigFragment.class.getDeclaredField("table");
                configTable.setAccessible(true);
                configSelected = BlockConfigFragment.class.getDeclaredField("selected");
                configSelected.setAccessible(true);
                config = InputHandler.class.getDeclaredField("config");
                config.setAccessible(true);
            }

            if (active) {
                delegate = input.config;
                config.set(input, new DevToolsConfigFragment((Table) configTable.get(delegate)));
                Vars.content.blocks().each(b -> b instanceof MemoryBlock, b -> b.configurable = true);
            } else {
                config.set(input, delegate);
                delegate = null;
                Vars.content.blocks().each(b -> b instanceof MemoryBlock, b -> b.configurable = false);
            }
        } catch (ReflectiveOperationException e) {
            Log.err("[Mlog Dev Tools] Failed to replace InputHandler.config", e);
        }
    }

    private static class DevToolsConfigFragment extends BlockConfigFragment {
        private final Table table;

        public DevToolsConfigFragment(Table table) {
            this.table = table;
        }

        private Building selected() {
            try {
                return (Building) configSelected.get(delegate);
            } catch (IllegalAccessException e) {
                Log.err("[Mlog Dev Tools] Failed to access BlockConfigFragment private fields", e);
                return null;
            }
        }

        @Override
        public void build(Group parent) {
            delegate.build(parent);
        }

        @Override
        public void forceHide() {
            delegate.forceHide();
        }

        @Override
        public boolean isShown() {
            return delegate.isShown();
        }

        @Override
        public Building getSelected() {
            return delegate.getSelected();
        }

        @Override
        public void showConfig(Building building) {
            if (building instanceof MemoryBuild build) {
                overrideConfig(build, false, table -> {
                    if (SnapshotManager.maxSnapshots > 0) {
                        table.button(Icon.box, Styles.cleari, () -> SnapshotManager.createUser(build)).size(40);
                    }
                    table.button(Icon.menu, Styles.cleari, () -> VarsDialog.show(build)).size(40);
                });
                return;
            } else if (building instanceof LogicBuild build && build.executor.vars.length > 0) {
                overrideConfig(build, false, table -> {
                    Action left = getAction(true);
                    Action right = getAction(false);
                    left.createConfigButton(table, build);
                    table.button(Icon.pencil, Styles.cleari, build::showEditDialog).size(40);
                    if (right != left) right.createConfigButton(table, build);
                });
                return;
            } else if (false) {
                overrideConfig(building, true, table -> {
                    building.buildConfiguration(table);
                    table.button(Icon.zoom, Styles.cleari, () -> VarsDialog.show(building)).size(40);
                });
                return;
            }

            delegate.showConfig(building);
        }

        private <T extends Building> void overrideConfig(T build, boolean append, Cons<Table> create) {
            try {
                if (configSelected.get(delegate) instanceof Building bld) {
                    bld.onConfigureClosed();
                }
                if (build.configTapped()) {
                    configSelected.set(delegate, build);

                    table.visible = true;
                    table.clear();
                    table.background(null);
                    if (append) {
                        build.buildConfiguration(table);
                    }
                    create.get(table);
                    table.pack();
                    table.setTransform(true);
                    table.actions(Actions.scaleTo(0f, 1f), Actions.visible(true),
                            Actions.scaleTo(1f, 1f, 0.07f, Interp.pow3Out));

                    table.update(() -> {
                        Building selected = selected();
                        if (selected != null && selected.shouldHideConfigure(player)) {
                            hideConfig();
                            return;
                        }

                        table.setOrigin(Align.center);
                        if (selected == null || selected.block == Blocks.air || !selected.isValid()) {
                            hideConfig();
                        } else {
                            selected.updateTableAlign(table);
                        }
                    });
                }
            } catch (IllegalAccessException e) {
                Log.err("[Mlog Dev Tools] Failed to access BlockConfigFragment private fields", e);
            }
        }

        @Override
        public boolean hasConfigMouse() {
            return delegate.hasConfigMouse();
        }

        @Override
        public void hideConfig() {
            delegate.hideConfig();
        }
    }
}
