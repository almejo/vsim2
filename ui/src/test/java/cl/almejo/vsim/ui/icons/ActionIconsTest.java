package cl.almejo.vsim.ui.icons;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.actions.EditorActions;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.library.TemplateIcons;
import cl.almejo.vsim.ui.stepping.StepIcons;
import cl.almejo.vsim.ui.waveform.WaveformIcons;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JMenuItem;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

class ActionIconsTest {

    private final WindowCommands window = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }
    };
    private OpenDocument openDocument;
    private EditorView view;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        EditorServices services = EditorServices.of(PainterRegistry.standard(), ColorScheme.LIGHT)
                .withExtensions(List.of(new ActionIcons()));
        view = new EditorView(openDocument, services, window);
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    private static List<AbstractButton> toolbarButtons(EditorView target) {
        return Arrays.stream(target.toolBar().getComponents())
                .filter(AbstractButton.class::isInstance)
                .map(AbstractButton.class::cast)
                .filter(button -> button.getAction() != null)
                .toList();
    }

    private static AbstractButton toolbarButton(EditorView target, Action action) {
        return toolbarButtons(target).stream().filter(button -> button.getAction() == action).findFirst().orElseThrow();
    }

    @Test
    void everyIconFileIsUsedAndEveryUsedNameHasAFile() throws IOException {
        TreeSet<String> used = new TreeSet<>(ActionIcons.iconNames(view).values());
        used.addAll(TemplateIcons.names());
        used.add(EditorActions.SPEED_ICON);
        used.addAll(WaveformIcons.names());
        used.addAll(StepIcons.names());
        assertThat(used).containsExactlyElementsOf(IconsTest.iconNames());
    }

    @Test
    void actionsGetSmallIconsForMenusAndLargeOnesForTheToolbar() {
        for (Map.Entry<Action, String> entry : ActionIcons.iconNames(view).entrySet()) {
            assertThat(entry.getKey().getValue(Action.SMALL_ICON)).as(entry.getValue())
                    .isSameAs(Icons.get(entry.getValue(), Icons.MENU_SIZE));
            assertThat(entry.getKey().getValue(Action.LARGE_ICON_KEY)).as(entry.getValue())
                    .isSameAs(Icons.get(entry.getValue(), Icons.TOOLBAR_SIZE));
        }
        JMenuItem saveItem = new JMenuItem(view.actions().save());
        assertThat(saveItem.getIcon()).isSameAs(Icons.get("save", Icons.MENU_SIZE));
    }

    @Test
    void toolbarButtonsShowOnlyTheirIconAndNameTheActionInTheTooltip() {
        assertThat(toolbarButtons(view)).isNotEmpty();
        for (AbstractButton button : toolbarButtons(view)) {
            String name = String.valueOf(button.getAction().getValue(Action.NAME));
            assertThat(button.getIcon()).as(name).isSameAs(button.getAction().getValue(Action.LARGE_ICON_KEY));
            assertThat(button.getHideActionText()).as(name).isTrue();
            assertThat(button.getText()).as(name).isNullOrEmpty();
            assertThat(button.getToolTipText()).as(name).isNotBlank();
        }
        assertThat(toolbarButton(view, view.actions().save()).getToolTipText())
                .isEqualTo(view.actions().save().getValue(Action.NAME));
    }

    @Test
    void theUndoTooltipFollowsTheNameOfTheStepItWouldUndo() {
        AbstractButton undo = toolbarButton(view, view.actions().undo());
        String before = undo.getToolTipText();
        place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
        assertThat(undo.getToolTipText()).isEqualTo(view.actions().undo().getValue(Action.NAME))
                .isNotEqualTo(before);
    }

    @Test
    void theReturnedRemovalStopsFollowingNames() {
        EditorView plain = new EditorView(openDocument,
                EditorServices.of(PainterRegistry.standard(), ColorScheme.LIGHT), window);
        try {
            Runnable removal = new ActionIcons().install(plain);
            AbstractButton undo = toolbarButton(plain, plain.actions().undo());
            removal.run();
            String before = undo.getToolTipText();
            place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());
            assertThat(plain.actions().undo().getValue(Action.NAME)).isNotEqualTo(before);
            assertThat(undo.getToolTipText()).isEqualTo(before);
        } finally {
            plain.dispose();
        }
    }
}
