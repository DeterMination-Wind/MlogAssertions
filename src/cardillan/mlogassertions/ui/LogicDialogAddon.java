package cardillan.mlogassertions.ui;

import arc.Core;
import arc.Events;
import arc.scene.Element;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.util.Log;
import mindustry.Vars;
import mindustry.core.GameState;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.gen.Icon;
import mindustry.logic.GlobalVarsDialog;
import mindustry.logic.LExecutor;
import mindustry.logic.LogicDialog;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.logic.LogicBlock;

import java.lang.reflect.Field;

import static mindustry.Vars.net;
import static mindustry.Vars.state;

public class LogicDialogAddon {
    public static int tripleTapSpeed = 500;

    public static Field executorField;
    public static Field wasPausedField;
    public static GlobalVarsDialog globalsDialog;
    static LogicBlock.LogicBuild editedBuild = null;

    // The variables button installed into a dialog that replaced Vars.ui.logic, kept so that a
    // patched bar is not rebuilt every time the dialog is shown or resized.
    static TextButton replacedVariablesButton;

    static int tapCount = 0;
    static long timeTapped1, timeTapped2;
    static Building lastTappedBuild;

    public static void init() {
        try {
            executorField = LogicDialog.class.getDeclaredField("executor");
            executorField.setAccessible(true);

            wasPausedField = BaseDialog.class.getDeclaredField("wasPaused");
            wasPausedField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            Log.err("[Mlog Dev Tools] Cannot access LogicDialog fields", e);
            return;
        }

        // Looked up separately: another mod may install a LogicDialog subclass, so the field has
        // to be found through the hierarchy instead of on the concrete class. A missing field may
        // only disable the globals button, never cancel the events registered below.
        try {
            globalsDialog = (GlobalVarsDialog) findField(LogicDialog.class, "globalsDialog").get(Vars.ui.logic);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.err("[Mlog Dev Tools] Cannot access LogicDialog.globalsDialog", e);
        }

        Events.on(EventType.TapEvent.class, e -> {
            if (tripleTapSpeed <= 0 || e.tile.build != null  && !e.tile.build.displayable()) return;

            if (lastTappedBuild != e.tile.build ||  e.tile.build == null) {
                lastTappedBuild = e.tile.build;
                timeTapped1 = timeTapped2 = System.currentTimeMillis();
                tapCount = 1;
            } else if (timeTapped1 >= System.currentTimeMillis() - tripleTapSpeed) {
                if (tapCount >= 2) {
                    Building build = lastTappedBuild;
                    Core.app.post(() -> VarsDialog.show(build));
                    lastTappedBuild = null;
                } else {
                    tapCount++;
                    timeTapped1 = timeTapped2;
                    timeTapped2 = System.currentTimeMillis();
                }
            } else if (timeTapped2 >= System.currentTimeMillis() - 1000) {
                // The previous tap is still valid
                timeTapped1 = timeTapped2;
                timeTapped2 = System.currentTimeMillis();
                tapCount = 2;
            } else {
                timeTapped1 = timeTapped2 = System.currentTimeMillis();
                tapCount = 1;
            }
        });


        Vars.ui.logic.shown(LogicDialogAddon::setupLogicDialog);

        Events.on(EventType.ResizeEvent.class, event -> {
            if (Vars.ui.logic.isShown() && Core.scene.getDialog() == Vars.ui.logic) {
                setupLogicDialog();
            }
        });
    }

    private static void setupLogicDialog() {
        LogicDialog dialog = Vars.ui.logic;

        // Only the unmodified dialog gets its button row rebuilt. Any subclass may build that row
        // out of Stack/Table cells: rebuilding it would flatten the other mod's layout, and
        // assuming the cells hold TextButtons would throw.
        boolean vanilla = dialog.getClass() == LogicDialog.class;

        LExecutor executor;
        boolean wasPaused;
        try {
            // A subclass may shadow the vanilla executor field, so non-vanilla dialogs are read
            // through their own declaration.
            executor = vanilla ? (LExecutor) executorField.get(dialog) : readExecutor(dialog);
            wasPaused = (boolean) wasPausedField.get(dialog);
        } catch (ReflectiveOperationException e) {
            Log.err("[Mlog Dev Tools] Cannot access LogicDialog fields", e);
            return;
        }

        // Sessions without a processor (e.g. a library file opened for editing) have no variables.
        if (executor == null) return;
        editedBuild = executor.build;

        if (!vanilla) {
            replaceVariablesButton(dialog, executor, wasPaused);
            return;
        }

        Table buttons = dialog.buttons;

        Element[] old = new Element[buttons.getCells().size];
        for (int i = 0; i < old.length; i++) {
            old[i] = buttons.getCells().get(i).get();
        }
        for (Element element : old) buttons.removeChild(element);
        buttons.clearChildren();

        for (int i = 0; i < old.length; i++) {
            if (i == 2 && Core.graphics.isPortrait()) buttons.row();

            if ("variables".equals(old[i].name)) {
                buttons.add(buildVariablesButton(dialog, executor, wasPaused));
            } else {
                buttons.add(old[i]);
            }
        }
    }

    /**
     * Replaces the variables element of a dialog row that another mod built, keeping the cell that
     * holds it so size, padding and row structure stay exactly as that mod laid them out.
     */
    @SuppressWarnings("unchecked")
    private static void replaceVariablesButton(LogicDialog dialog, LExecutor executor, boolean wasPaused) {
        Element current = dialog.buttons.find("variables");
        // Nothing to replace, or an earlier show already patched this row.
        if (current == null || current == replacedVariablesButton) return;

        // Only a plain cell can be swapped for another element. Unknown nesting (a Stack, a nested
        // table with its own layout) is left alone, so the risk is a missing button, not a broken bar.
        if (!(current.parent instanceof Table parent)) return;

        Cell<Element> cell = parent.getCell(current);
        if (cell == null) return;

        TextButton button = buildVariablesButton(dialog, executor, wasPaused);
        cell.setElement(button);
        replacedVariablesButton = button;
        parent.invalidateHierarchy();
    }

    /**
     * Builds the variables button the vanilla rebuild path adds: same text, icon and style.
     * Table.button() is the only place that assembles all three, so it is built in a scratch table
     * and the element is moved into the bar afterwards.
     */
    private static TextButton buildVariablesButton(LogicDialog dialog, LExecutor executor, boolean wasPaused) {
        Cell<TextButton> built = new Table().button("@variables", Icon.menu, () -> {
            //in the editor, it should display the global variables only (the button text is different)
            if (!dialog.shouldShowVariables()) {
                // The field is optional: a fork without it loses this button only.
                if (globalsDialog != null) globalsDialog.show();
                return;
            }

            VarsDialog varsDialog = new VarsDialog(executor.build);

            varsDialog.hidden(() -> {
                if (!wasPaused && !net.active() && !state.isMenu()) {
                    state.set(GameState.State.paused);
                }
            });

            varsDialog.shown(() -> {
                if (!wasPaused && !net.active() && !state.isMenu()) {
                    state.set(GameState.State.playing);
                }
            });

            varsDialog.show();
        }).name("variables");

        built.update(b -> {
            if (dialog.shouldShowVariables()) {
                b.setText("@variables");
            } else {
                b.setText("@logic.globals");
            }
        });

        return built.get();
    }

    /** Reads the executor a dialog instance actually uses, shadowing fields included. */
    private static LExecutor readExecutor(LogicDialog dialog) throws ReflectiveOperationException {
        for (Class<?> type = dialog.getClass(); type != null; type = type.getSuperclass()) {
            Field field;
            try {
                field = type.getDeclaredField("executor");
            } catch (NoSuchFieldException e) {
                continue; //declared by a superclass
            }

            // A shadowing field of another type is not the executor; keep walking instead of failing.
            if (LExecutor.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                return (LExecutor) field.get(dialog);
            }
        }

        throw new NoSuchFieldException("executor");
    }

    /** Finds a field declared on the class or any of its superclasses and makes it accessible. */
    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                //keep looking: the field may be declared by a superclass
            }
        }

        throw new NoSuchFieldException(type.getName() + "." + name);
    }
}
