package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.command.ChangeFrameCommand;
import cl.almejo.vsim.core.command.CreateFrameCommand;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.command.SetLabelCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.NoteColor;
import cl.almejo.vsim.core.document.NoteStyle;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.tunnel.Tunnels;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.clipboard.FragmentTransferable;
import cl.almejo.vsim.ui.i18n.FrameTintNames;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import cl.almejo.vsim.ui.wiring.SelectionMoves;
import cl.almejo.vsim.ui.theme.FlatLafInstaller;
import cl.almejo.vsim.ui.theme.ReadableColors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JFormattedTextField;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.LookAndFeel;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.text.JTextComponent;
import java.awt.Color;
import java.awt.datatransfer.StringSelection;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.layOut;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

class PropertiesPanelTest {

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final Selection selection = new Selection();
    private PropertiesPanel panel;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        panel = new PropertiesPanel(openDocument, selection);
    }

    @AfterEach
    void close() {
        panel.dispose();
        openDocument.close();
    }

    private Component gate(int x) {
        return place(openDocument, StandardComponentTypes.NOT, x, 0, Map.of());
    }

    private Component current(Component component) {
        return openDocument.document().component(component.id()).orElseThrow();
    }

    private static void typeInto(JSpinner spinner, String text) {
        ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setText(text);
    }

    @Test
    void nothingSelectedSaysSo() {
        assertThat(panel.heading()).isEqualTo("Sin selección");
        assertThat(panel.textField()).isNull();
    }

    @Test
    void severalElementsShowTheirCount() {
        Component first = gate(0);
        Component second = gate(10);
        selection.set(List.of(first.id(), second.id()));
        assertThat(panel.heading()).isEqualTo("2 elementos");
    }

    @Test
    void aWireShowsItsLength() {
        Wire wire = new Wire(openDocument.document().newWireId(), new GridPoint(0, 0), new GridPoint(6, 0));
        openDocument.execute(new AddWireCommand(wire));
        selection.select(wire.id());
        assertThat(panel.heading()).isEqualTo("Cable · largo 6");
    }

    @Test
    void aSubcircuitInstanceIsHeadedByItsDefinitionName() {
        openDocument.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
        Component instance = place(openDocument, SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), 0, 0,
                Map.of());
        selection.select(instance.id());
        assertThat(panel.heading()).isEqualTo("Inversor");
    }

    @Test
    void aComponentShowsTypeLabelAndParameters() {
        Component gate = gate(0);
        selection.select(gate.id());
        assertThat(panel.heading()).isEqualTo("NOT");
        assertThat(panel.textField().getText()).isEmpty();
        assertThat(((JSpinner) panel.parameterForm().editor("delay")).getValue()).isEqualTo(1);
    }

    @Test
    void enterThenFocusLossAddsOneUndoStep() {
        Component gate = gate(0);
        selection.select(gate.id());
        panel.textField().setText(" U7 ");
        panel.textField().postActionEvent();
        panel.textField().postActionEvent();
        panel.commitEdits();
        assertThat(current(gate).label()).isEqualTo("U7");
        assertThat(openDocument.history().undoDescription()).contains("command.label");
        openDocument.history().undo();
        assertThat(current(gate).label()).isEmpty();
        assertThat(openDocument.history().undoDescription()).contains("command.add");
    }

    @Test
    void aSpinnerStepAppliesTheParameter() {
        Component gate = gate(0);
        selection.select(gate.id());
        ((JSpinner) panel.parameterForm().editor("delay")).setValue(5);
        assertThat(current(gate).parameters()).containsEntry("delay", 5);
        assertThat(openDocument.history().undoDescription()).contains("command.configure");
    }

    @Test
    void choosingATunnelSwatchRecoloursTheEntradaInOneStep() {
        Component entrance = place(openDocument, StandardComponentTypes.TUNNEL_ENTRANCE, 0, 0,
                Map.of(Tunnels.NAME, "CLK"));
        selection.select(entrance.id());
        ColorSwatches<?> swatches = (ColorSwatches<?>) panel.parameterForm().editor(Tunnels.COLOR);
        assertThat(swatches.selectedValue()).isEqualTo(0);

        swatches.swatch(5).doClick();

        assertThat(current(entrance).parameters()).containsEntry(Tunnels.COLOR, 5);
        assertThat(openDocument.history().undoDescription()).contains("command.configure");
        openDocument.history().undo();
        assertThat(current(entrance).parameters()).containsEntry(Tunnels.COLOR, 0);
        assertThat(openDocument.history().undoDescription()).contains("command.add");
    }

    @Test
    void choosingAnLedSwatchRecoloursTheLedInOneStep() {
        Component led = place(openDocument, StandardComponentTypes.LED, 0, 0, Map.of());
        selection.select(led.id());
        ColorSwatches<?> swatches = (ColorSwatches<?>) panel.parameterForm().editor("color");
        assertThat(swatches.selectedValue()).isEqualTo("red");

        swatches.swatch(swatches.values().indexOf("green")).doClick();

        assertThat(current(led).parameters()).containsEntry("color", "green");
        assertThat(openDocument.history().undoDescription()).contains("command.configure");
        openDocument.history().undo();
        assertThat(current(led).parameters()).containsEntry("color", "red");
        assertThat(openDocument.history().undoDescription()).contains("command.add");
    }

    @Test
    void theFrameColourSwatchesOfferEveryTintNamedAndFollowAnUndo() {
        Frame frame = frame(false);
        ColorSwatches<FrameTint> swatches = panel.frameColor();
        assertThat(swatches.values()).containsExactly(FrameTint.values());
        assertThat(swatches.swatch(0).getToolTipText()).isEqualTo(FrameTintNames.of(FrameTint.NONE));

        swatches.swatch(FrameTint.GREEN.ordinal()).doClick();
        openDocument.history().undo();

        assertThat(current(frame).tint()).isEqualTo(FrameTint.BLUE);
        assertThat(panel.frameColor().selectedValue()).isEqualTo(FrameTint.BLUE);
    }

    @Test
    void theFrameSwatchesPaintWithTheColourSchemeTheCanvasUses() {
        panel.dispose();
        panel = new PropertiesPanel(openDocument, selection, SelectionMoves.detached(), () -> ColorSchemes.DARK);
        frame(false);

        Color veil = ColorSchemes.DARK.frameTint(FrameTint.GREEN);
        assertThat(SwatchPixels.centre(panel.frameColor().swatch(FrameTint.GREEN.ordinal())))
                .isEqualTo(new Color(veil.getRed(), veil.getGreen(), veil.getBlue()).getRGB());
    }

    @Test
    void renamingAnEntradaInTheInspectorRenamesItsSalidasInOneStep() {
        Component entrance = place(openDocument, StandardComponentTypes.TUNNEL_ENTRANCE, 0, 0,
                Map.of(Tunnels.NAME, "CLK"));
        Component exit = place(openDocument, StandardComponentTypes.TUNNEL_EXIT, 20, 0, Map.of(Tunnels.NAME, "CLK"));
        selection.select(entrance.id());
        JTextField name = (JTextField) panel.parameterForm().editor(Tunnels.NAME);

        name.setText("RELOJ");
        name.postActionEvent();

        assertThat(current(entrance).parameters()).containsEntry(Tunnels.NAME, "RELOJ");
        assertThat(current(exit).parameters()).containsEntry(Tunnels.NAME, "RELOJ");
        assertThat(openDocument.history().undoDescription()).contains("command.renameTunnel");
        openDocument.history().undo();
        assertThat(current(exit).parameters()).containsEntry(Tunnels.NAME, "CLK");
    }

    @Test
    void renamingAndRecolouringAnEntradaTogetherIsOneStep() {
        Component entrance = place(openDocument, StandardComponentTypes.TUNNEL_ENTRANCE, 0, 0,
                Map.of(Tunnels.NAME, "CLK"));
        Component exit = place(openDocument, StandardComponentTypes.TUNNEL_EXIT, 20, 0, Map.of(Tunnels.NAME, "CLK"));
        selection.select(entrance.id());
        JTextField name = (JTextField) panel.parameterForm().editor(Tunnels.NAME);
        name.setText("RELOJ");
        ColorSwatches<?> swatches = (ColorSwatches<?>) panel.parameterForm().editor(Tunnels.COLOR);

        swatches.swatch(4).doClick();

        assertThat(current(entrance).parameters()).containsEntry(Tunnels.NAME, "RELOJ")
                .containsEntry(Tunnels.COLOR, 4);
        assertThat(current(exit).parameters()).containsEntry(Tunnels.NAME, "RELOJ");
        openDocument.history().undo();
        assertThat(current(entrance).parameters()).containsEntry(Tunnels.NAME, "CLK").containsEntry(Tunnels.COLOR, 0);
        assertThat(current(exit).parameters()).containsEntry(Tunnels.NAME, "CLK");
    }

    @Test
    void aTunnelSalidaHasNoColourToChoose() {
        Component exit = place(openDocument, StandardComponentTypes.TUNNEL_EXIT, 0, 0, Map.of(Tunnels.NAME, "CLK"));
        selection.select(exit.id());
        assertThat(panel.parameterForm().editor(Tunnels.COLOR)).isNull();
    }

    @Test
    void anInvalidValueShowsTheErrorAndAddsNoStep() {
        Component gate = gate(0);
        selection.select(gate.id());
        typeInto((JSpinner) panel.parameterForm().editor("delay"), "0");
        panel.commitEdits();
        assertThat(panel.error()).contains("Retardo");
        assertThat(current(gate).parameters()).containsEntry("delay", 1);
        assertThat(openDocument.history().undoDescription()).contains("command.add");
    }

    @Test
    void selectionChangeCommitsThePendingEditToThePreviousElement() {
        Component first = gate(0);
        Component second = gate(10);
        selection.select(first.id());
        panel.textField().setText("A1");
        selection.select(second.id());
        assertThat(current(first).label()).isEqualTo("A1");
        assertThat(current(second).label()).isEmpty();
        assertThat(panel.textField().getText()).isEmpty();
    }

    @Test
    void undoRefreshesTheFieldsAndAStaleFieldCannotReapply() {
        Component gate = gate(0);
        selection.select(gate.id());
        panel.textField().setText("U1");
        panel.textField().postActionEvent();
        JTextField stale = panel.textField();
        openDocument.history().undo();
        assertThat(panel.textField()).isNotSameAs(stale);
        assertThat(panel.textField().getText()).isEmpty();
        stale.postActionEvent();
        assertThat(current(gate).label()).isEmpty();
        assertThat(openDocument.history().canRedo()).isTrue();
    }

    @Test
    void blankTextInPropertiesShowsAnErrorAndKeepsTheText() {
        TextLabel label = new TextLabel(openDocument.document().newLabelId(), new GridPoint(3, 3), "nota");
        openDocument.execute(new AddElementsCommand(List.of(label)));
        selection.select(label.id());
        assertThat(panel.heading()).isEqualTo("Texto libre");
        panel.noteArea().setText("   ");
        panel.noteArea().confirm();
        assertThat(panel.error()).isEqualTo("El texto no puede quedar vacío");
        assertThat(openDocument.document().label(label.id()).orElseThrow().text()).isEqualTo("nota");
        panel.noteArea().setText("otra");
        panel.noteArea().confirm();
        assertThat(openDocument.document().label(label.id()).orElseThrow().text()).isEqualTo("otra");
        assertThat(panel.error()).isEqualTo(" ");
    }

    /** Controller ruling R8: documents outlive windows (M3-C), so a disposed panel must stop listening. */
    @Test
    void aDisposedPanelNoLongerFollowsTheDocument() {
        Component gate = gate(0);
        selection.select(gate.id());
        JTextField shown = panel.textField();
        panel.dispose();
        openDocument.execute(new SetLabelCommand(gate.id(), "U5"));
        assertThat(panel.textField()).isSameAs(shown);
        assertThat(panel.textField().getText()).isEmpty();
    }

    @Test
    void editorsDoNothingOnceThePanelIsDisposed() {
        Component gate = gate(0);
        selection.select(gate.id());
        JTextField field = panel.textField();
        String before = openDocument.history().undoDescription().toString();
        panel.dispose();
        field.setText("U9");
        field.postActionEvent();
        panel.commitEdits();
        assertThat(current(gate).label()).isEmpty();
        assertThat(openDocument.history().undoDescription().toString()).isEqualTo(before);
    }

    /** Review I1: a drag out released over the label field must not write the fragment JSON into it. */
    @Test
    void editorFieldsRefuseCircuitFragmentsButAcceptPlainText() {
        Component gate = gate(0);
        selection.select(gate.id());
        JFormattedTextField delayField =
                ((JSpinner.DefaultEditor) ((JSpinner) panel.parameterForm().editor("delay")).getEditor()).getTextField();
        for (JTextComponent field : List.of(panel.textField(), delayField)) {
            TransferHandler handler = field.getTransferHandler();
            FragmentTransferable fragment = new FragmentTransferable("{\"formatVersion\":2,\"kind\":\"fragment\"}");
            assertThat(handler.canImport(new TransferHandler.TransferSupport(field, fragment))).isFalse();
            assertThat(handler.importData(new TransferHandler.TransferSupport(field, fragment))).isFalse();
            assertThat(handler.canImport(new TransferHandler.TransferSupport(field, new StringSelection("U7"))))
                    .isTrue();
        }
        panel.commitEdits();
        assertThat(current(gate).label()).isEmpty();
    }

    @Test
    void aLabelAndAParameterChangedTogetherAreOneUndoStep() {
        Component gate = gate(0);
        selection.select(gate.id());
        panel.textField().setText("U7");
        typeInto((JSpinner) panel.parameterForm().editor("delay"), "4");

        panel.commitEdits(); // what a selection change or closing the window does with both edits pending

        assertThat(current(gate).label()).isEqualTo("U7");
        assertThat(current(gate).parameters()).containsEntry("delay", 4);
        assertThat(openDocument.history().undoDescription()).contains("command.configure");
        openDocument.history().undo();
        assertThat(current(gate).label()).isEmpty();
        assertThat(current(gate).parameters()).containsEntry("delay", 1);
        assertThat(openDocument.history().undoDescription()).contains("command.add");
    }

    @Test
    void aSelectionChangeWithBothEditsPendingIsOneUndoStepToo() {
        Component first = gate(0);
        Component second = gate(10);
        selection.select(first.id());
        panel.textField().setText("A1");
        typeInto((JSpinner) panel.parameterForm().editor("delay"), "3");

        selection.select(second.id());

        assertThat(current(first).label()).isEqualTo("A1");
        assertThat(current(first).parameters()).containsEntry("delay", 3);
        openDocument.history().undo();
        assertThat(current(first).label()).isEmpty();
        assertThat(current(first).parameters()).containsEntry("delay", 1);
    }

    // --- styled notes (Pulido T5)

    private TextLabel note(int y, NoteStyle style) {
        TextLabel note = new TextLabel(openDocument.document().newLabelId(), new GridPoint(0, y), "nota " + y, style);
        openDocument.execute(new AddElementsCommand(List.of(note)));
        return note;
    }

    private NoteStyle styleOf(TextLabel note) {
        return openDocument.document().label(note.id()).orElseThrow().style();
    }

    @Test
    void aNoteShowsItsBackgroundTextAndBorderAndEachPickIsOneUndoStep() {
        TextLabel note = note(0, new NoteStyle(NoteColor.NONE, NoteColor.BLUE, NoteColor.NONE));
        selection.select(note.id());

        assertThat(panel.noteStyle(NoteStyle.Role.BACKGROUND).selectedValue()).isEqualTo(NoteColor.NONE);
        assertThat(panel.noteStyle(NoteStyle.Role.TEXT).selectedValue()).isEqualTo(NoteColor.BLUE);
        assertThat(panel.noteStyle(NoteStyle.Role.TEXT).swatch(0).getToolTipText()).isEqualTo("Automático");
        assertThat(panel.noteStyle(NoteStyle.Role.BORDER).swatch(0).getToolTipText()).isEqualTo("Ninguno");

        panel.noteStyle(NoteStyle.Role.BACKGROUND).swatch(NoteColor.YELLOW.ordinal()).doClick();
        panel.noteStyle(NoteStyle.Role.BORDER).swatch(NoteColor.RED.ordinal()).doClick();

        assertThat(styleOf(note)).isEqualTo(new NoteStyle(NoteColor.YELLOW, NoteColor.BLUE, NoteColor.RED));
        assertThat(openDocument.history().undoDescription()).contains("command.noteStyle");
        openDocument.history().undo();
        assertThat(styleOf(note)).isEqualTo(new NoteStyle(NoteColor.YELLOW, NoteColor.BLUE, NoteColor.NONE));
        openDocument.history().undo();
        assertThat(styleOf(note)).isEqualTo(note.style());
        assertThat(panel.noteStyle(NoteStyle.Role.BACKGROUND).selectedValue()).isEqualTo(NoteColor.NONE);
    }

    /** The nine note colours must not widen a narrow Propiedades panel: no wider than a frame's seven tints. */
    @Test
    void theNoteColourRowsAreNoWiderThanTheFrameColourRow() {
        TextLabel note = note(0, NoteStyle.PLAIN);
        selection.select(note.id());
        int frameRow = new ColorSwatches<>(List.of(FrameTint.values()), FrameTint.NONE, Enum::name,
                tint -> Optional.<Color>empty()).getPreferredSize().width;

        for (NoteStyle.Role role : NoteStyle.Role.values()) {
            assertThat(panel.noteStyle(role).getPreferredSize().width).as(role.name())
                    .isLessThanOrEqualTo(frameRow);
        }
    }

    @Test
    void severalNotesShareTheirRowsAndOnePickColoursThemAllAsOneUndoStep() {
        TextLabel plain = note(0, NoteStyle.PLAIN);
        TextLabel yellow = note(4, new NoteStyle(NoteColor.YELLOW, NoteColor.NONE, NoteColor.NONE));
        selection.set(List.of(plain.id(), yellow.id()));

        assertThat(panel.noteStyle(NoteStyle.Role.BACKGROUND).showsNoChoice()).as("the backgrounds differ").isTrue();
        assertThat(panel.noteStyle(NoteStyle.Role.TEXT).selectedValue()).isEqualTo(NoteColor.NONE);

        panel.noteStyle(NoteStyle.Role.BACKGROUND).swatch(NoteColor.YELLOW.ordinal()).doClick();

        assertThat(styleOf(plain).background()).isEqualTo(NoteColor.YELLOW);
        assertThat(styleOf(yellow).background()).isEqualTo(NoteColor.YELLOW);
        openDocument.history().undo();
        assertThat(styleOf(plain)).isEqualTo(NoteStyle.PLAIN);
        assertThat(styleOf(yellow)).isEqualTo(yellow.style());
    }

    @Test
    void aSelectionOfANoteAndAPartHasNoStyleRows() {
        TextLabel note = note(0, NoteStyle.PLAIN);
        Component gate = gate(10);
        selection.set(List.of(note.id(), gate.id()));

        assertThat(panel.noteStyle(NoteStyle.Role.BACKGROUND)).isNull();
    }

    // --- frames (M6.3)

    private Frame frame(boolean locked) {
        Frame frame = new Frame(openDocument.document().newFrameId(), new GridPoint(2, 3), 20, 12, "Sumador",
                FrameTint.BLUE, locked);
        openDocument.execute(new CreateFrameCommand(frame));
        selection.select(frame.id());
        return frame;
    }

    private Frame current(Frame frame) {
        return openDocument.document().frame(frame.id()).orElseThrow();
    }

    @Test
    void aSelectedFrameShowsItsProperties() {
        frame(false);
        assertThat(panel.heading()).isEqualTo("Marco");
        assertThat(panel.textField().getText()).isEqualTo("Sumador");
        assertThat(panel.frameColor().selectedValue()).isEqualTo(FrameTint.BLUE);
        assertThat(Arrays.stream(panel.frameSpinners()).map(JSpinner::getValue).toList())
                .containsExactly(2, 3, 20, 12);
        assertThat(panel.frameSpinners()[2].isEnabled()).isTrue();
    }

    @Test
    void titleAndColourEditsAreOneUndoStepEach() {
        Frame frame = frame(false);
        panel.textField().setText("Lógica");
        panel.commitEdits();
        assertThat(current(frame).title()).isEqualTo("Lógica");
        panel.frameColor().swatch(FrameTint.RED.ordinal()).doClick();
        assertThat(current(frame).tint()).isEqualTo(FrameTint.RED);
        openDocument.history().undo();
        assertThat(current(frame).tint()).isEqualTo(FrameTint.BLUE);
        openDocument.history().undo();
        assertThat(current(frame)).isEqualTo(frame);
    }

    @Test
    void theFrameTitleIsStrippedLikeTheTitleEditorDoes() {
        Frame frame = frame(false);
        panel.textField().setText("  Lógica  ");
        panel.commitEdits();
        assertThat(current(frame).title()).isEqualTo("Lógica");
    }

    @Test
    void sizeAndPositionEditsOfAnUnlockedFrameAreOneUndoStepEach() {
        Frame frame = frame(false);
        panel.frameSpinners()[2].setValue(30);
        assertThat(current(frame).width()).isEqualTo(30);
        panel.frameSpinners()[0].setValue(10);
        assertThat(current(frame).topLeft()).isEqualTo(new GridPoint(10, 3));
        // the same move the canvas drag makes, so the undo item says «mover»
        assertThat(openDocument.history().undoDescription()).contains("command.move");
        openDocument.history().undo();
        assertThat(current(frame).topLeft()).isEqualTo(new GridPoint(2, 3));
        assertThat(current(frame).width()).isEqualTo(30);
        openDocument.history().undo();
        assertThat(current(frame)).isEqualTo(frame);
    }

    @Test
    void movingALockedFrameByItsXStretchesTheWiresCrossingItsBorder() {
        Frame frame = frame(true);
        place(openDocument, StandardComponentTypes.NOT, 6, 6, Map.of());
        Wire inside = new Wire(openDocument.document().newWireId(), new GridPoint(4, 8), new GridPoint(6, 8));
        openDocument.execute(new AddWireCommand(inside));
        Wire crossing = new Wire(openDocument.document().newWireId(), new GridPoint(10, 8), new GridPoint(30, 8));
        openDocument.execute(new AddWireCommand(crossing));
        place(openDocument, StandardComponentTypes.LED, 29, 5, Map.of());
        selection.select(frame.id());

        panel.frameSpinners()[0].setValue(7);

        assertThat(current(frame).topLeft()).isEqualTo(new GridPoint(7, 3));
        assertThat(openDocument.document().wire(inside.id()).orElseThrow()).isEqualTo(inside.translate(5, 0));
        assertThat(openDocument.document().wire(crossing.id()).orElseThrow())
                .isEqualTo(new Wire(crossing.id(), new GridPoint(15, 8), new GridPoint(30, 8)));
        openDocument.history().undo();
        assertThat(openDocument.document().wire(crossing.id()).orElseThrow()).isEqualTo(crossing);
    }

    @Test
    void aSelectedLockedFrameShowsTitleColourPositionAndSizeButNoLockCheckbox() {
        frame(true);
        place(openDocument, StandardComponentTypes.NOT, 6, 6, Map.of());
        assertThat(panel.heading()).isEqualTo("Marco");
        assertThat(panel.textField().getText()).isEqualTo("Sumador");
        assertThat(panel.frameColor().selectedValue()).isEqualTo(FrameTint.BLUE);
        assertThat(Arrays.stream(panel.frameSpinners()).map(JSpinner::getValue).toList())
                .containsExactly(2, 3, 20, 12);
        assertThat(checkBoxesIn(panel)).isEmpty();
    }

    @Test
    void anUnlockedFrameHasNoLockCheckboxEither() {
        frame(false);
        assertThat(checkBoxesIn(panel)).isEmpty();
    }

    @Test
    void lockingTheFrameElsewhereDisablesTheSize() {
        Frame frame = frame(false);
        openDocument.execute(new ChangeFrameCommand(frame, frame.withLocked(true)));
        assertThat(panel.frameSpinners()[2].isEnabled()).isFalse();
        assertThat(panel.frameSpinners()[3].isEnabled()).isFalse();
    }

    private static List<javax.swing.JCheckBox> checkBoxesIn(java.awt.Container container) {
        List<javax.swing.JCheckBox> found = new java.util.ArrayList<>();
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof javax.swing.JCheckBox checkBox) {
                found.add(checkBox);
            }
            if (child instanceof java.awt.Container inner) {
                found.addAll(checkBoxesIn(inner));
            }
        }
        return found;
    }

    @Test
    void aLockedFrameCannotBeResizedFromThePanel() {
        frame(true);
        assertThat(panel.frameSpinners()[2].isEnabled()).isFalse();
        assertThat(panel.frameSpinners()[3].isEnabled()).isFalse();
        assertThat(panel.frameSpinners()[0].isEnabled()).isTrue();
    }

    @Test
    void movingALockedFrameFromThePanelMovesItsContentsInOneUndoStep() {
        Frame frame = frame(true);
        Component inside = place(openDocument, StandardComponentTypes.NOT, 6, 6, Map.of());
        selection.select(frame.id());
        panel.frameSpinners()[1].setValue(8);
        assertThat(current(frame).topLeft()).isEqualTo(new GridPoint(2, 8));
        assertThat(current(inside).position()).isEqualTo(new GridPoint(6, 11));
        openDocument.history().undo();
        assertThat(current(frame)).isEqualTo(frame);
        assertThat(current(inside)).isEqualTo(inside);
    }

    @Test
    void theFieldsFollowTheFrameWhenItChangesElsewhere() {
        Frame frame = frame(false);
        openDocument.execute(new ChangeFrameCommand(frame, frame.withRectangle(new GridPoint(5, 6), 24, 9)));
        assertThat(Arrays.stream(panel.frameSpinners()).map(JSpinner::getValue).toList())
                .containsExactly(5, 6, 24, 9);
        openDocument.history().undo();
        assertThat(Arrays.stream(panel.frameSpinners()).map(JSpinner::getValue).toList())
                .containsExactly(2, 3, 20, 12);
    }

    private Component constant(int width, String value) {
        return place(openDocument, StandardComponentTypes.CONSTANT, 0, 0,
                Map.of("width", width, "value", value));
    }

    private static void typeValue(PropertiesPanel panel, String text) {
        ((JTextField) panel.parameterForm().editor("value")).setText(text);
        panel.commitEdits();
    }

    @Test
    void aValueThatDoesNotFitShowsTheMessageMarksTheFieldAndChangesNothing() {
        Component constant = constant(4, "0b1");
        selection.select(constant.id());
        typeValue(panel, "0x1F");
        assertThat(panel.error()).isEqualTo("No cabe en 4 bits");
        assertThat(((JTextField) panel.parameterForm().editor("value")).getClientProperty("JComponent.outline"))
                .isEqualTo("error");
        assertThat(current(constant).parameters()).containsEntry("value", "0b1");
        assertThat(openDocument.history().undoDescription()).contains("command.add");
    }

    @Test
    void garbageInTheValueIsRefusedWithTheNotationHint() {
        Component constant = constant(8, "1");
        selection.select(constant.id());
        typeValue(panel, "12a");
        assertThat(panel.error()).isEqualTo("Valor no válido (usa 0b…, 0x… o un número)");
        assertThat(current(constant).parameters()).containsEntry("value", "1");
    }

    @Test
    void aFixedValueClearsTheMessageAndTheMark() {
        Component constant = constant(4, "1");
        selection.select(constant.id());
        typeValue(panel, "0x1F");
        typeValue(panel, "0xF");
        assertThat(panel.error()).isEqualTo(" ");
        assertThat(((JTextField) panel.parameterForm().editor("value")).getClientProperty("JComponent.outline"))
                .isNull();
        assertThat(current(constant).parameters()).containsEntry("value", "0xF");
    }

    @Test
    void aValueIsStoredNormalizedInOneUndoStepAndTheFieldShowsIt() {
        Component constant = constant(8, "1");
        selection.select(constant.id());
        typeValue(panel, "0x00ff");
        assertThat(current(constant).parameters()).containsEntry("value", "0xFF");
        assertThat(((JTextField) panel.parameterForm().editor("value")).getText()).isEqualTo("0xFF");
        assertThat(openDocument.history().undoDescription()).contains("command.configure");
        openDocument.history().undo();
        assertThat(current(constant).parameters()).containsEntry("value", "1");
    }

    @Test
    void narrowingTheWidthBelowTheValueIsRefusedNotTruncated() {
        Component constant = constant(8, "0b111");
        selection.select(constant.id());
        ((JSpinner) panel.parameterForm().editor("width")).setValue(2);
        assertThat(panel.error()).isEqualTo("No cabe en 2 bits");
        assertThat(current(constant).parameters()).containsEntry("width", 8).containsEntry("value", "0b111");
        assertThat(openDocument.history().undoDescription()).contains("command.add");
    }

    @Test
    void widthAndValueTogetherAreOneEditWhenTheyFit() {
        Component constant = constant(8, "0xFF");
        selection.select(constant.id());
        ((JTextField) panel.parameterForm().editor("value")).setText("3");
        ((JSpinner) panel.parameterForm().editor("width")).setValue(2);
        assertThat(current(constant).parameters()).containsEntry("width", 2).containsEntry("value", "3");
    }

    // --- one column layout (M6.8)

    /** Every caption with its field, laid out at a sidebar's size, as rectangles in the panel's coordinates. */
    private List<java.awt.Rectangle[]> laidOutRows() {
        panel.setSize(320, 600);
        layOut(panel);
        List<java.awt.Rectangle[]> rows = new java.util.ArrayList<>();
        collectRows(panel, rows);
        return rows;
    }

    private void collectRows(java.awt.Container container, List<java.awt.Rectangle[]> rows) {
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof javax.swing.JLabel label && label.getLabelFor() != null) {
                java.awt.Component field = label.getLabelFor();
                rows.add(new java.awt.Rectangle[]{
                        javax.swing.SwingUtilities.convertRectangle(container, label.getBounds(), panel),
                        javax.swing.SwingUtilities.convertRectangle(field.getParent(), field.getBounds(), panel)});
            }
            if (child instanceof java.awt.Container inner) {
                collectRows(inner, rows);
            }
        }
    }

    /** The left edge of the caption column: the widest caption starts there. */
    private static int captionColumnLeft(List<java.awt.Rectangle[]> rows) {
        return rows.stream().mapToInt(row -> row[0].x).min().orElseThrow();
    }

    private static void assertOneGrid(List<java.awt.Rectangle[]> rows, int expectedRows) {
        assertThat(rows).hasSize(expectedRows);
        assertThat(rows.stream().map(row -> row[0].x + row[0].width).distinct().toList())
                .as("caption right edges").hasSize(1);
        assertThat(rows.stream().map(row -> row[1].x).distinct().toList()).as("field left edges").hasSize(1);
    }

    @Test
    void everyElementLaysItsCaptionsAndFieldsInTheSameColumns() {
        frame(false);
        List<java.awt.Rectangle[]> frameRows = laidOutRows();
        assertOneGrid(frameRows, 6);
        int captionLeft = captionColumnLeft(frameRows);

        Component andGate = place(openDocument, StandardComponentTypes.AND2, 30, 0, Map.of());
        Component constant = place(openDocument, StandardComponentTypes.CONSTANT, 40, 0, Map.of());
        Component toggle = place(openDocument, StandardComponentTypes.SWITCH, 50, 0, Map.of());
        for (Component component : List.of(andGate, constant, toggle)) {
            selection.select(component.id());
            int parameterCount = openDocument.registry().typeOf(component).parameters().size();
            List<java.awt.Rectangle[]> rows = laidOutRows();
            assertOneGrid(rows, 1 + parameterCount);
            assertThat(captionColumnLeft(rows)).as(component.typeId()).isEqualTo(captionLeft);
        }
    }

    @Test
    void theErrorLineFollowsALiveThemeChangeInTheReadableErrorRed() throws Exception {
        LookAndFeel previous = UIManager.getLookAndFeel();
        try {
            UIManager.setLookAndFeel(FlatLafInstaller.lookAndFeel("linux", true));

            assertThat(panel.errorLabel().getForeground()).isEqualTo(ReadableColors.errorText());
        } finally {
            UIManager.setLookAndFeel(previous);
        }
    }

    /** IA.3: a note of several lines keeps them in Propiedades, and Shift+Enter adds one there too. */
    @Test
    void aNoteOfSeveralLinesKeepsThemAndShiftEnterAddsOne() {
        TextLabel label = new TextLabel(openDocument.document().newLabelId(), new GridPoint(3, 3), "uno\ndos");
        openDocument.execute(new AddElementsCommand(List.of(label)));
        String before = openDocument.history().undoDescription().toString();
        selection.select(label.id());
        assertThat(panel.noteArea().getText()).isEqualTo("uno\ndos");
        panel.commitEdits();
        selection.clear();
        assertThat(openDocument.document().label(label.id()).orElseThrow().text()).isEqualTo("uno\ndos");
        assertThat(openDocument.history().undoDescription().toString()).isEqualTo(before);

        selection.select(label.id());
        NoteTextArea area = panel.noteArea();
        area.setCaretPosition(area.getDocument().getLength());
        area.deliver(new java.awt.event.KeyEvent(area, java.awt.event.KeyEvent.KEY_PRESSED, 0,
                java.awt.event.InputEvent.SHIFT_DOWN_MASK, java.awt.event.KeyEvent.VK_ENTER,
                java.awt.event.KeyEvent.CHAR_UNDEFINED));
        area.append("tres");
        assertThat(openDocument.document().label(label.id()).orElseThrow().text()).isEqualTo("uno\ndos");
        area.deliver(new java.awt.event.KeyEvent(area, java.awt.event.KeyEvent.KEY_PRESSED, 0, 0,
                java.awt.event.KeyEvent.VK_ENTER, java.awt.event.KeyEvent.CHAR_UNDEFINED));
        assertThat(openDocument.document().label(label.id()).orElseThrow().text()).isEqualTo("uno\ndos\ntres");
    }
}
