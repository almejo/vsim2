package cl.almejo.vsim.ui.groups;

import cl.almejo.vsim.core.DocumentFixtures;
import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.theme.WrappedErrorText;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** «Bits por pin», «Configuración avanzada…» and the summary in the «Propiedades» tab of a bus splitter or joiner. */
class GroupsRowsTest {

    private final OpenDocument document = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final List<ElementId> launched = new ArrayList<>();
    private final GroupsWindowLauncher launcher = new GroupsWindowLauncher() {
        @Override
        public void open(EditorView view, ElementId componentId) {
            launched.add(componentId);
        }
    };
    private EditorView view;

    @AfterEach
    void close() {
        if (view != null) {
            view.dispose();
        }
        document.close();
    }

    private Container inspectorShowing(Component component) {
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                AppPreferences.inMemory(), new ComponentLibrary(), List.of(new GroupsExtension(launcher)));
        view = new EditorView(document, services, NO_WINDOW);
        view.selection().select(component.id());
        return view.labelFeatures().properties();
    }

    private static <T extends java.awt.Component> T named(Container root, String name, Class<T> kind) {
        for (java.awt.Component child : root.getComponents()) {
            if (kind.isInstance(child) && name.equals(child.getName())) {
                return kind.cast(child);
            }
            if (child instanceof Container container) {
                T nested = named(container, name, kind);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static List<String> labelTexts(Container root) {
        List<String> texts = new ArrayList<>();
        for (java.awt.Component child : root.getComponents()) {
            if (child instanceof WrappedErrorText error) {
                texts.add(error.getText());
            } else if (child instanceof JLabel label) {
                texts.add(label.getText());
            } else if (child instanceof Container container) {
                texts.addAll(labelTexts(container));
            }
        }
        return texts;
    }

    private String groups(Component component) {
        return (String) document.document().component(component.id()).orElseThrow().parameters()
                .getOrDefault(StandardComponentTypes.GROUPS, "");
    }

    @Test
    void aSplitterWithEqualPinsShowsItsBitsPerPinAndOneSummaryLine() {
        Component splitter = place(document, StandardComponentTypes.BUS_SPLITTER, 0, 0,
                Map.of(StandardComponentTypes.WIDTH, 8, StandardComponentTypes.GROUPS, "0-3|4-7"));
        Container inspector = inspectorShowing(splitter);

        assertThat(named(inspector, "groups.bitsPerPin", JTextField.class).getText()).isEqualTo("4");
        assertThat(named(inspector, "groups.custom", JLabel.class).getText()).isEmpty();
        assertThat(named(inspector, "groups.advanced", AbstractButton.class).getText())
                .isEqualTo("Configuración avanzada…");
        assertThat(labelTexts(named(inspector, "groups.summary", Container.class)))
                .containsExactly("2 pines de 4 bits");
    }

    @Test
    void equalPinsWithASmallerLastOneSayItNextToBitsPorPin() {
        Component splitter = place(document, StandardComponentTypes.BUS_SPLITTER, 0, 0,
                Map.of(StandardComponentTypes.WIDTH, 8, StandardComponentTypes.GROUPS, "0-6|7"));
        Container inspector = inspectorShowing(splitter);

        assertThat(named(inspector, "groups.bitsPerPin", JTextField.class).getText()).isEqualTo("7");
        assertThat(named(inspector, "groups.custom", JLabel.class).getText()).isEqualTo("(el último de 1)");
    }

    @Test
    void aSplitterWithCustomGroupsSaysPersonalizadoAndListsItsPins() {
        Component splitter = place(document, StandardComponentTypes.BUS_SPLITTER, 0, 0,
                Map.of(StandardComponentTypes.WIDTH, 32, StandardComponentTypes.GROUPS, "7-11,25-31|15-19"));
        Container inspector = inspectorShowing(splitter);

        assertThat(named(inspector, "groups.bitsPerPin", JTextField.class).getText()).isEmpty();
        assertThat(named(inspector, "groups.custom", JLabel.class).getText()).isEqualTo("Personalizado");
        assertThat(labelTexts(named(inspector, "groups.summary", Container.class))).containsExactly(
                "Personalizado, 2 pines:", "1: 7-11,25-31 (12 bits)", "2: 15-19 (5 bits)",
                "Sin usar: 0-6,12-14,20-24");
    }

    @Test
    void theRawGroupsTextIsNotAmongTheParameterRows() {
        Component joiner = place(document, StandardComponentTypes.BUS_JOINER, 0, 0, Map.of());
        Container inspector = inspectorShowing(joiner);

        assertThat(named(inspector, StandardComponentTypes.GROUPS, java.awt.Component.class)).isNull();
        assertThat(named(inspector, StandardComponentTypes.WIDTH, JSpinner.class)).isNotNull();
        assertThat(named(inspector, "groups.bitsPerPin", JTextField.class).getText()).isEqualTo("1");
    }

    @Test
    void otherPartsHaveNoGroupRows() {
        Component gate = place(document, StandardComponentTypes.AND, 0, 0, Map.of());
        Container inspector = inspectorShowing(gate);

        assertThat(named(inspector, "groups.bitsPerPin", JTextField.class)).isNull();
    }

    @Test
    void typingBitsPerPinRegroupsTheBusInOneUndoStep() {
        Component splitter = place(document, StandardComponentTypes.BUS_SPLITTER, 0, 0, Map.of());
        // a wire on b0 (row 1, right side): its pin disappears, the wire stays where it was
        Wire wire = new Wire(document.document().newWireId(), new GridPoint(2, 1), new GridPoint(6, 1));
        document.execute(new AddElementsCommand(List.of(wire)));
        Container inspector = inspectorShowing(splitter);
        JTextField bitsPerPin = named(inspector, "groups.bitsPerPin", JTextField.class);

        bitsPerPin.setText("4");
        bitsPerPin.postActionEvent();

        assertThat(groups(splitter)).isEqualTo("0-3|4-7");
        assertThat(document.document().wires()).containsExactly(wire);
        assertThat(document.history().undoDescription()).contains("command.busGroups");
        document.history().undo();
        assertThat(groups(splitter)).isEmpty();
        assertThat(document.document().wires()).containsExactly(wire);
        assertThat(document.history().undoDescription()).contains("command.add");
    }

    @Test
    void aBitsPerPinOutsideTheBusChangesNothing() {
        Component splitter = place(document, StandardComponentTypes.BUS_SPLITTER, 0, 0, Map.of());
        Container inspector = inspectorShowing(splitter);
        JTextField bitsPerPin = named(inspector, "groups.bitsPerPin", JTextField.class);

        bitsPerPin.setText("9");
        bitsPerPin.postActionEvent();

        assertThat(groups(splitter)).isEmpty();
        assertThat(named(inspector, "groups.bitsPerPin", JTextField.class).getText()).isEqualTo("1");
    }

    @Test
    void theAdvancedButtonOpensTheGroupsWindowOfThePart() {
        Component splitter = place(document, StandardComponentTypes.BUS_SPLITTER, 0, 0, Map.of());
        Container inspector = inspectorShowing(splitter);

        named(inspector, "groups.advanced", AbstractButton.class).doClick();

        assertThat(launched).containsExactly(splitter.id());
    }

    @Test
    void changingTheWidthKeepsEqualPinsEqual() {
        Component splitter = place(document, StandardComponentTypes.BUS_SPLITTER, 0, 0,
                Map.of(StandardComponentTypes.WIDTH, 8, StandardComponentTypes.GROUPS, "0-3|4-7"));
        Container inspector = inspectorShowing(splitter);
        JSpinner width = named(inspector, StandardComponentTypes.WIDTH, JSpinner.class);

        width.setValue(16);

        assertThat(groups(splitter)).isEqualTo("0-3|4-7|8-11|12-15");
        document.history().undo();
        assertThat(groups(splitter)).isEqualTo("0-3|4-7");
    }

    @Test
    void narrowingTheBusDropsTheCustomBitsBeyondIt() {
        Component splitter = place(document, StandardComponentTypes.BUS_SPLITTER, 0, 0,
                Map.of(StandardComponentTypes.WIDTH, 32, StandardComponentTypes.GROUPS, "7-11,25-31|15-19"));
        Container inspector = inspectorShowing(splitter);
        JSpinner width = named(inspector, StandardComponentTypes.WIDTH, JSpinner.class);

        width.setValue(16);

        assertThat(groups(splitter)).isEqualTo("7-11|15");
    }

    /** Fix round 3: the summary and «Bits por pin» said «2 pines de 4 bits» without «(el último de 2)». */
    @Test
    void narrowingEqualPinsSaysTheSmallerLastPin() {
        Component splitter = place(document, StandardComponentTypes.BUS_SPLITTER, 0, 0,
                Map.of(StandardComponentTypes.WIDTH, 8, StandardComponentTypes.GROUPS, "0-3|4-7"));
        Container inspector = inspectorShowing(splitter);

        named(inspector, StandardComponentTypes.WIDTH, JSpinner.class).setValue(6);

        assertThat(groups(splitter)).isEqualTo("0-3|4-5");
        assertThat(named(inspector, "groups.bitsPerPin", JTextField.class).getText()).isEqualTo("4");
        assertThat(named(inspector, "groups.custom", JLabel.class).getText()).isEqualTo("(el último de 2)");
        assertThat(labelTexts(named(inspector, "groups.summary", Container.class)))
                .containsExactly("2 pines de 4 bits (el último de 2)");
    }

    @Test
    void bitsPerPinTypedAfterAWidthChangeUsesTheNewWidth() {
        Component splitter = place(document, StandardComponentTypes.BUS_SPLITTER, 0, 0,
                Map.of(StandardComponentTypes.WIDTH, 8, StandardComponentTypes.GROUPS, "0-3|4-7"));
        Container inspector = inspectorShowing(splitter);
        named(inspector, StandardComponentTypes.WIDTH, JSpinner.class).setValue(6);
        JTextField bitsPerPin = named(inspector, "groups.bitsPerPin", JTextField.class);

        bitsPerPin.setText("3");
        bitsPerPin.postActionEvent();

        assertThat(width(splitter)).isEqualTo(6);
        assertThat(groups(splitter)).isEqualTo("0-2|3-5");
    }

    private int width(Component component) {
        return (Integer) document.document().component(component.id()).orElseThrow().parameters()
                .get(StandardComponentTypes.WIDTH);
    }

    /** Where pin {@code pinIndex} of a splitter at the origin would be with {@code groups} on 8 bits. */
    private GridPoint futurePin(String groups, int pinIndex) {
        ComponentType type = document.registry().require(StandardComponentTypes.BUS_SPLITTER);
        Component future = type.newComponent(new ElementId("future"), new GridPoint(0, 0), Rotation.R0,
                Map.of(StandardComponentTypes.WIDTH, 8, StandardComponentTypes.GROUPS, groups), "");
        return type.pinLocation(future, pinIndex);
    }

    @Test
    void bitsPerPinThatWouldJoinAPinToAWireOfItsWidthIsRefusedAndSaid() {
        Component splitter = place(document, StandardComponentTypes.BUS_SPLITTER, 0, 0,
                Map.of(StandardComponentTypes.WIDTH, 8, StandardComponentTypes.GROUPS, "0-3|4-7"));
        GridPoint future = futurePin("0-1|2-3|4-5|6-7", 3);
        // the 2-bit bus pin of another splitter, wired to where b4-5 would appear
        place(document, StandardComponentTypes.BUS_SPLITTER, 30, future.y(), Map.of(StandardComponentTypes.WIDTH, 2));
        DocumentFixtures.wire(document.document(), future.x(), future.y(), 30, future.y());
        Container inspector = inspectorShowing(splitter);
        JTextField bitsPerPin = named(inspector, "groups.bitsPerPin", JTextField.class);

        bitsPerPin.setText("2");
        bitsPerPin.postActionEvent();

        assertThat(groups(splitter)).isEqualTo("0-3|4-7");
        assertThat(labelTexts(view.statusBar())).anyMatch(line -> line.startsWith("El pin b4-5 quedaría unido"));
    }

    @Test
    void narrowingABusWhosePinsKeepTheirWiresIsAllowed() {
        Component splitter = place(document, StandardComponentTypes.BUS_SPLITTER, 0, 0,
                Map.of(StandardComponentTypes.WIDTH, 8, StandardComponentTypes.GROUPS, "0-3|4-7"));
        GridPoint first = futurePin("0-3|4-7", 1);
        GridPoint second = futurePin("0-3|4-7", 2);
        DocumentFixtures.wire(document.document(), first.x(), first.y(), first.x() + 6, first.y());
        DocumentFixtures.wire(document.document(), second.x(), second.y(), second.x() + 6, second.y());
        Container inspector = inspectorShowing(splitter);

        named(inspector, StandardComponentTypes.WIDTH, JSpinner.class).setValue(6);

        assertThat(groups(splitter)).isEqualTo("0-3|4-5");
    }

    @Test
    void aWiderBusWhoseNewPinWouldJoinAOneBitReaderIsRefusedNamingThePin() {
        Component splitter = place(document, StandardComponentTypes.BUS_SPLITTER, 0, 0,
                Map.of(StandardComponentTypes.WIDTH, 4));
        DocumentFixtures.gate(document.registry(), document.document(), 20, 3);
        DocumentFixtures.wire(document.document(), 2, 5, 20, 5);
        Container inspector = inspectorShowing(splitter);

        named(inspector, StandardComponentTypes.WIDTH, JSpinner.class).setValue(8);

        assertThat(width(splitter)).isEqualTo(4);
        assertThat(labelTexts(inspector)).anyMatch(line -> line.startsWith("El pin b4 quedaría unido"));
    }
}
