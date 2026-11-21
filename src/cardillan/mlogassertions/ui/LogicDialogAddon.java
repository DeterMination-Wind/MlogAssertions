package cardillan.mlogassertions.ui;

import arc.Core;
import arc.Events;
import arc.scene.Element;
import arc.scene.event.ResizeListener;
import arc.scene.event.VisibilityListener;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import cardillan.mlogassertions.MlogDevTools;
import mindustry.Vars;
import mindustry.core.GameState;
import mindustry.game.EventType;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Building;
import mindustry.gen.Icon;
import mindustry.logic.GlobalVarsDialog;
import mindustry.logic.LExecutor;
import mindustry.logic.LogicDialog;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.logic.LogicBlock;

import java.lang.reflect.Field;

import static cardillan.mlogassertions.MlogDevTools.s;
import static mindustry.Vars.net;
import static mindustry.Vars.state;

public class LogicDialogAddon {
    public static int tripleTapSpeed = 500;

    public static Field executorField;
    public static Field wasPausedField;
    public static GlobalVarsDialog globalsDialog;
    static LogicBlock.LogicBuild editedBuild = null;

    // The variables button installed into a dialog that replaced Vars.ui.logic, kept so that a
    // patched bar is not rebuilt every time the dialog is shown or resized. Reset whenever the
    // watched dialog instance changes, because that instance gets a fresh vanilla button.
    static TextButton replacedVariablesButton;

    // The dialog instance currently carrying the shown listener. Another mod (LogicSugar) may
    // replace Vars.ui.logic after ClientLoadEvent, so the addon has to follow the current
    // instance instead of binding once to the vanilla dialog it would discard.
    static LogicDialog watchedDialog;

    // What Dialog.shown(Runnable) installs internally, kept as a field here so the listener can
    // be detached when the watched dialog is replaced instead of outliving its instance.
    static final VisibilityListener shownListener = new VisibilityListener() {
        @Override
        public boolean shown() {
            setupLogicDialog();
            return false;
        }
    };

    // Arc's scene-resize listener on the same instance. This replaces the old global
    // ResizeEvent handler, which ran before the vanilla LogicDialog.setup() cleared the button
    // row and therefore had its work undone on a compact/portrait flip. This listener runs from
    // Dialog.act on the frame after the scene size changed, i.e. after setup() and after another
    // editor (LogicSugar) has re-installed its own controls, so the variables cell is patched last.
    static final ResizeListener resizedListener = new ResizeListener() {
        @Override
        public void resized() {
            if(Vars.ui != null && Vars.ui.logic != null && Vars.ui.logic.isShown()) setupLogicDialog();
        }
    };

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
            MlogDevTools.err("Cannot access LogicDialog fields", e);
            return;
        }

        // Looked up separately: another mod may install a LogicDialog subclass, so the field has
        // to be found through the hierarchy instead of on the concrete class. A missing field may
        // only disable the globals button, never cancel the events registered below.
        try {
            globalsDialog = (GlobalVarsDialog) findField(LogicDialog.class, "globalsDialog").get(Vars.ui.logic);
        } catch (ReflectiveOperationException | RuntimeException e) {
            MlogDevTools.err("Cannot access LogicDialog.globalsDialog", e);
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


        // Follow Vars.ui.logic instead of binding to the instance that exists right now: another
        // mod may install its own LogicDialog subclass one frame after ClientLoadEvent.
        watchDialog();

        // Cheap per-frame identity check (one field read) so a replaced dialog is patched on the
        // next frame, including when the replacement happens while the editor is already open.
        Events.run(Trigger.update, LogicDialogAddon::watchDialog);
    }

    /**
     * Attaches the addon to the current {@link Vars#ui}.logic instance, detaching it from the
     * previous one. Idempotent: the same instance is only ever given one shown listener.
     *
     * <p>When a mod replaces {@code Vars.ui.logic} (LogicSugar does this one frame after
     * ClientLoadEvent), the game keeps opening the replacement, so a one-shot binding left the
     * enhanced variables button missing from its first open. Called from a per-frame trigger and
     * exposed as {@link #refreshEditorAddon()} for third-party editors that swap the dialog
     * themselves.</p>
     */
    static void watchDialog() {
        if (Vars.ui == null) return;

        LogicDialog current = Vars.ui.logic;
        if (current == watchedDialog) return;

        if (watchedDialog != null) {
            watchedDialog.removeListener(shownListener);
            watchedDialog.removeListener(resizedListener);
        }
        watchedDialog = current;
        replacedVariablesButton = null;
        if (current == null) return;

        current.addListener(shownListener);
        current.addListener(resizedListener);

        // The replacement may already be on screen: the swap is not necessarily tied to a show.
        // Patching here is safe because setupLogicDialog() guards every reflective field.
        if (current.isShown()) setupLogicDialog();
    }

    /**
     * Runs the dialog watcher once and patches the current dialog if it is open. Intended for
     * third-party editors that replace Vars.ui.logic without triggering a show, callable
     * reflectively so no compile-time dependency on this mod is required.
     */
    public static void refreshEditorAddon() {
        watchDialog();
        if (Vars.ui != null && Vars.ui.logic != null && Vars.ui.logic.isShown()) {
            setupLogicDialog();
        }
    }

    private static void setupLogicDialog() {
        LogicDialog dialog = Vars.ui == null ? null : Vars.ui.logic;
        if (dialog == null) return;

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
            MlogDevTools.err("Cannot access LogicDialog fields", e);
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

            if (s("variables").equals(old[i].name)) {
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
        Element current = dialog.buttons.find(s("variables"));
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
     * and the element is moved into the bar afterward.
     */
    private static TextButton buildVariablesButton(LogicDialog dialog, LExecutor executor, boolean wasPaused) {
        Cell<TextButton> built = new Table().button("@variables", Icon.menu, () -> {
            //in the editor, it should display the global variables only (the button text is different)
            if (!dialog.shouldShowVariables()) {
                // Resolved at click time: the dialog this button belongs to may not be the one
                // init() cached (another mod can replace Vars.ui.logic at any point).
                GlobalVarsDialog globals = resolveGlobalsDialog();
                // The field is optional: a fork without it loses this button only.
                if (globals != null) globals.show();
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
        }).name(s("variables"));

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

    /**
     * The globals dialog of the editor that is current right now. A one-shot capture in init()
     * would keep pointing at the vanilla dialog after another mod replaced Vars.ui.logic; the
     * cached instance stays as the fallback for forks where the field cannot be read.
     */
    private static GlobalVarsDialog resolveGlobalsDialog() {
        LogicDialog dialog = Vars.ui == null ? null : Vars.ui.logic;
        if (dialog != null) {
            try {
                return (GlobalVarsDialog) findField(LogicDialog.class, "globalsDialog").get(dialog);
            } catch (ReflectiveOperationException | RuntimeException e) {
                //keep the cached instance; the field is optional and must not break the button
            }
        }
        return globalsDialog;
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
