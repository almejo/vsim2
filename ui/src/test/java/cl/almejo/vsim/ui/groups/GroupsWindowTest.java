package cl.almejo.vsim.ui.groups;

import cl.almejo.vsim.core.DocumentFixtures;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.theme.WrappedErrorText;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.layOut;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** The «Grupos del separador» window, built without showing it: its table, bit strip, text, problems and buttons. */
class GroupsWindowTest {

    private final OpenDocument document = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private int closed;

    @AfterEach
    void close() {
        document.close();
    }

    private GroupsWindow windowFor(Component component) {
        GroupsWindow window = new GroupsWindow(document, component.id(), PainterRegistry.standard(),
                () -> ColorScheme.LIGHT);
        window.onClose(() -> closed++);
        return window;
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
            if (child instanceof JLabel label && label.isVisible()) {
                texts.add(label.getText());
            } else if (child instanceof WrappedErrorText error) {
                texts.add(error.getText());
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

    private Component splitter(int width, String groups) {
        return place(document, StandardComponentTypes.BUS_SPLITTER, 0, 0,
                Map.of(StandardComponentTypes.WIDTH, width, StandardComponentTypes.GROUPS, groups));
    }

    /** Types {@code value} in the «Como texto» field and leaves it (focus lost): the text is read, nothing more. */
    private static void leave(JTextField text, String value) {
        text.setText(value);
        FocusEvent lost = new FocusEvent(text, FocusEvent.FOCUS_LOST);
        for (FocusListener listener : text.getFocusListeners()) {
            listener.focusLost(lost);
        }
    }

    /** Types {@code value} in the «Como texto» field and presses Enter. */
    private static void enter(JTextField text, String value) {
        text.setText(value);
        text.postActionEvent();
    }

    private static AbstractButton accept(GroupsWindow window) {
        return named(window.content(), "groups.accept", AbstractButton.class);
    }

    @Test
    void theWindowShowsOneRowPerPinWithItsRanges() {
        GroupsWindow window = windowFor(splitter(32, "7-11,25-31|15-19"));
        Container content = window.content();

        assertThat((Integer) named(content, "groups.from.0.0", JSpinner.class).getValue()).isEqualTo(7);
        assertThat((Integer) named(content, "groups.to.0.1", JSpinner.class).getValue()).isEqualTo(31);
        assertThat((Integer) named(content, "groups.from.1.0", JSpinner.class).getValue()).isEqualTo(15);
        assertThat(named(content, "groups.width.0", JLabel.class).getText()).isEqualTo("12");
        assertThat(named(content, "groups.text", JTextField.class).getText()).isEqualTo("7-11,25-31|15-19");
        assertThat(window.title()).isEqualTo("Grupos del separador");
        assertThat(accept(window).isEnabled()).isTrue();
    }

    @Test
    void theJoinersWindowSaysSoAndItsUnusedBitsAreZ() {
        Component joiner = place(document, StandardComponentTypes.BUS_JOINER, 0, 0,
                Map.of(StandardComponentTypes.WIDTH, 8, StandardComponentTypes.GROUPS, "0-3"));
        GroupsWindow window = windowFor(joiner);

        assertThat(window.title()).isEqualTo("Grupos de la unión");
        assertThat(labelTexts(named(window.content(), "groups.problems", Container.class)))
                .containsExactly("Sin usar: 4-7 (en la Unión salen en Z).");
    }

    @Test
    void addingAPinAndARangeAndRemovingThemRebuildsTheTable() {
        GroupsWindow window = windowFor(splitter(8, "0-3"));

        named(window.content(), "groups.addPin", AbstractButton.class).doClick();
        assertThat(named(window.content(), "groups.text", JTextField.class).getText()).isEqualTo("0-3|4");
        named(window.content(), "groups.addRange.1", AbstractButton.class).doClick();
        named(window.content(), "groups.to.1.1", JSpinner.class).setValue(7);
        named(window.content(), "groups.from.1.1", JSpinner.class).setValue(6);
        assertThat(named(window.content(), "groups.text", JTextField.class).getText()).isEqualTo("0-3|4,6-7");
        assertThat(named(window.content(), "groups.width.1", JLabel.class).getText()).isEqualTo("3");

        named(window.content(), "groups.removeRange.1.0", AbstractButton.class).doClick();
        assertThat(named(window.content(), "groups.text", JTextField.class).getText()).isEqualTo("0-3|6-7");
        named(window.content(), "groups.removePin.0", AbstractButton.class).doClick();
        assertThat(named(window.content(), "groups.text", JTextField.class).getText()).isEqualTo("6-7");
        assertThat(named(window.content(), "groups.removePin.0", AbstractButton.class)).isNull();
    }

    @Test
    void aClickOnTheStripMovesTheBitToTheSelectedPin() {
        GroupsWindow window = windowFor(splitter(8, "0-3|4-7"));
        named(window.content(), "groups.pin.0", AbstractButton.class).doClick();
        BitStrip strip = named(window.content(), "groups.strip", BitStrip.class);
        strip.setSize(new Dimension(8 * 30, 30));

        java.awt.Rectangle bitFive = strip.cellOf(5);
        strip.dispatchEvent(new MouseEvent(strip, MouseEvent.MOUSE_PRESSED, 0, 0, bitFive.x + 2, bitFive.y + 2, 1,
                false, MouseEvent.BUTTON1));

        assertThat(named(window.content(), "groups.text", JTextField.class).getText()).isEqualTo("0-3,5|4,6-7");
        assertThat(strip.getToolTipText(new MouseEvent(strip, MouseEvent.MOUSE_MOVED, 0, 0, bitFive.x + 2,
                bitFive.y + 2, 0, false))).isEqualTo("bit 5 → pin 1");
    }

    @Test
    void theStripsHighestBitIsOnTheLeft() {
        GroupsWindow window = windowFor(splitter(8, ""));
        BitStrip strip = named(window.content(), "groups.strip", BitStrip.class);
        strip.setSize(new Dimension(8 * 30, 30));

        assertThat(strip.cellOf(7).x).isLessThan(strip.cellOf(0).x);
    }

    @Test
    void aProblemDisablesAceptarAndIsShown() {
        GroupsWindow window = windowFor(splitter(8, "0-3|4-7"));

        named(window.content(), "groups.from.1.0", JSpinner.class).setValue(3);

        assertThat(accept(window).isEnabled()).isFalse();
        assertThat(labelTexts(named(window.content(), "groups.problems", Container.class)))
                .containsExactly("El bit 3 está en dos pines");
        window.accept();
        assertThat(closed).isZero();
        assertThat(groups(document.document().components().get(0))).isEqualTo("0-3|4-7");
    }

    @Test
    void unreadableTextIsAProblemUntilItIsFixed() {
        GroupsWindow window = windowFor(splitter(8, "0-3|4-7"));
        JTextField text = named(window.content(), "groups.text", JTextField.class);

        leave(text, "0-3|x");
        assertThat(accept(window).isEnabled()).isFalse();
        assertThat(labelTexts(named(window.content(), "groups.problems", Container.class)).get(0))
                .startsWith("No se entiende el texto");

        leave(text, "0-1|2-7");
        assertThat(accept(window).isEnabled()).isTrue();
        assertThat((Integer) named(window.content(), "groups.to.1.0", JSpinner.class).getValue()).isEqualTo(7);
    }

    @Test
    void aceptarAppliesTheGroupsAsOneUndoStepAndCloses() {
        Component splitter = splitter(32, "");
        GroupsWindow window = windowFor(splitter);
        JTextField text = named(window.content(), "groups.text", JTextField.class);
        leave(text, "7-11,25-31|15-19|20-24");

        window.accept();

        assertThat(closed).isEqualTo(1);
        assertThat(groups(splitter)).isEqualTo("7-11,25-31|15-19|20-24");
        assertThat(document.history().undoDescription()).contains("command.busGroups");
        document.history().undo();
        assertThat(groups(splitter)).isEmpty();
    }

    @Test
    void aceptarWithOneBitPerPinStoresTheEmptyText() {
        Component splitter = splitter(4, "0-3");
        GroupsWindow window = windowFor(splitter);
        JTextField text = named(window.content(), "groups.text", JTextField.class);
        leave(text, "0|1|2|3");

        window.accept();

        assertThat(document.document().component(splitter.id()).orElseThrow().parameters())
                .doesNotContainEntry(StandardComponentTypes.GROUPS, "0|1|2|3");
        assertThat(groups(splitter)).isEmpty();
    }

    @Test
    void cancelarLeavesTheDocumentUntouched() {
        Component splitter = splitter(8, "0-3|4-7");
        GroupsWindow window = windowFor(splitter);
        named(window.content(), "groups.addPin", AbstractButton.class).doClick();
        named(window.content(), "groups.from.0.0", JSpinner.class).setValue(1);

        named(window.content(), "groups.cancel", AbstractButton.class).doClick();

        assertThat(closed).isEqualTo(1);
        assertThat(groups(splitter)).isEqualTo("0-3|4-7");
        assertThat(document.history().undoDescription()).contains("command.add");
    }

    @Test
    void aceptarWithNothingChangedAddsNoUndoStep() {
        Component splitter = splitter(8, "0-3|4-7");
        GroupsWindow window = windowFor(splitter);

        window.accept();

        assertThat(closed).isEqualTo(1);
        assertThat(document.history().undoDescription()).contains("command.add");
    }

    @Test
    void thePreviewPaintsThePartAsItWouldBe() {
        GroupsWindow window = windowFor(splitter(8, "0-3|4-7"));
        GroupsPreview preview = named(window.content(), "groups.preview", GroupsPreview.class);
        Dimension size = preview.getPreferredSize();
        BufferedImage image = new BufferedImage(Math.max(1, size.width), Math.max(1, size.height),
                BufferedImage.TYPE_INT_ARGB);
        preview.setSize(size);
        java.awt.Graphics2D graphics = image.createGraphics();
        preview.paint(graphics);
        graphics.dispose();

        assertThat(preview.shownPins()).containsExactly("bus", "b0-3", "b4-7");
        named(window.content(), "groups.addPin", AbstractButton.class).doClick();
        assertThat(preview.shownPins()).containsExactly("bus", "b0-3", "b4-7");
    }

    @Test
    void theWindowFitsItsContentInsteadOfFillingTheScreen() {
        GroupsWindow small = windowFor(splitter(4, "0-1|2-3"));
        GroupsWindow big = windowFor(splitter(32, "0-7|8-15|16-23|24-31"));
        Dimension smallSize = small.preferredDialogSize(new Dimension(1920, 1080));
        Dimension bigSize = big.preferredDialogSize(new Dimension(1920, 1080));

        assertThat(smallSize.height).isLessThan(bigSize.height);
        assertThat(smallSize.height).isLessThanOrEqualTo(small.content().getPreferredSize().height + 40);
        assertThat(bigSize.height).isLessThanOrEqualTo((int) (1080 * 0.9));
        assertThat(small.preferredDialogSize(new Dimension(800, 600)).height).isLessThanOrEqualTo(540);
    }

    @Test
    void thePreviewIsAsTallAsTheSmallPartAndNoMore() {
        GroupsPreview small = named(windowFor(splitter(4, "0-1|2-3")).content(), "groups.preview",
                GroupsPreview.class);
        GroupsPreview big = named(windowFor(splitter(32, "0-7|8-15|16-23|24-31")).content(), "groups.preview",
                GroupsPreview.class);

        assertThat(small.getPreferredSize().height).isLessThan(200).isLessThan(big.getPreferredSize().height);
        assertThat(big.getPreferredSize().height).isLessThanOrEqualTo(360);
    }

    /** An 8-bit splitter with a wire from each bit to its own gate, the review's GroupsProbe layout. */
    private Component splitterWiredBitByBit() {
        Component splitter = place(document, StandardComponentTypes.BUS_SPLITTER, 20, 10,
                Map.of(StandardComponentTypes.WIDTH, 8));
        for (int bit = 0; bit < 8; bit++) {
            DocumentFixtures.gate(document.registry(), document.document(), 40 + 8 * bit, 9 + bit);
            DocumentFixtures.wire(document.document(), 22, 11 + bit, 40 + 8 * bit, 11 + bit);
        }
        return splitter;
    }

    @Test
    void aceptarThatWouldPutAPinOnAnotherBitsWireIsRefusedAndTheWindowSaysWhy() {
        Component splitter = splitterWiredBitByBit();
        GroupsWindow window = windowFor(splitter);
        JTextField text = named(window.content(), "groups.text", JTextField.class);
        leave(text, "0-3|4|5|6|7");

        window.accept();

        assertThat(closed).isZero();
        assertThat(groups(splitter)).isEmpty();
        assertThat(document.history().undoDescription()).contains("command.add");
        assertThat(labelTexts(named(window.content(), "groups.problems", Container.class)))
                .anyMatch(line -> line.startsWith("El pin b4 quedaría unido"));
        leave(text, "0-3|4-7");
        assertThat(labelTexts(named(window.content(), "groups.problems", Container.class)))
                .noneMatch(line -> line.startsWith("El pin b4"));
    }

    /** Fix round 3: the refusal was one line cut at the window's edge; it wraps within the window's width. */
    @Test
    void aLongRefusalWrapsWithinTheWindowInsteadOfWideningIt() {
        GroupsWindow window = windowFor(splitterWiredBitByBit());
        Container content = window.content();
        JTextField text = named(content, "groups.text", JTextField.class);
        leave(text, "0-3|4|5|6|7");
        int widthBeforeTheRefusal = content.getPreferredSize().width;

        window.accept();

        content.invalidate();
        assertThat(content.getPreferredSize().width).isLessThanOrEqualTo(widthBeforeTheRefusal);
        int width = widthBeforeTheRefusal;
        content.setSize(width, 2000);
        layOut(content);
        WrappedErrorText refusal = refusalText(content);
        // on screen the text's view asks for a new layout once it knows its width (a revalidate); here, by hand
        for (Container ancestor = refusal.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            ancestor.invalidate();
        }
        layOut(content);
        java.awt.Rectangle inContent = javax.swing.SwingUtilities.convertRectangle(refusal.getParent(),
                refusal.getBounds(), content);
        int lineHeight = refusal.getFontMetrics(refusal.getFont()).getHeight();
        assertThat(inContent.x + inContent.width).isLessThanOrEqualTo(width);
        assertThat(refusal.getHeight()).isGreaterThanOrEqualTo(2 * lineHeight);
    }

    private static WrappedErrorText refusalText(Container content) {
        for (java.awt.Component child : named(content, "groups.problems", Container.class).getComponents()) {
            if (child instanceof WrappedErrorText error && error.getText().startsWith("El pin b4")) {
                return error;
            }
        }
        throw new AssertionError("no refusal among the problems");
    }

    /** Fix round 3: Enter in «Como texto» reads the text and, with no problem, is Aceptar (one undo step, closes). */
    @Test
    void enterInTheTextFieldReadsItAndAcceptsAsOneUndoStep() {
        Component splitter = splitter(8, "");
        GroupsWindow window = windowFor(splitter);

        enter(named(window.content(), "groups.text", JTextField.class), "0-1|2-7");

        assertThat(closed).isEqualTo(1);
        assertThat(groups(splitter)).isEqualTo("0-1|2-7");
        assertThat(document.history().undoDescription()).contains("command.busGroups");
        document.history().undo();
        assertThat(groups(splitter)).isEmpty();
    }

    @Test
    void enterWithAProblemShowsItAndStaysOpen() {
        Component splitter = splitter(8, "0-3|4-7");
        GroupsWindow window = windowFor(splitter);
        JTextField text = named(window.content(), "groups.text", JTextField.class);

        enter(text, "0-3|x");
        assertThat(closed).isZero();
        assertThat(labelTexts(named(window.content(), "groups.problems", Container.class)).get(0))
                .startsWith("No se entiende el texto");

        enter(text, "0-3|3-7");
        assertThat(closed).isZero();
        assertThat(labelTexts(named(window.content(), "groups.problems", Container.class)))
                .contains("El bit 3 está en dos pines");
        assertThat(groups(splitter)).isEqualTo("0-3|4-7");
    }

    @Test
    void enterWithGroupsTheDocumentRefusesSaysWhyAndStaysOpen() {
        Component splitter = splitterWiredBitByBit();
        GroupsWindow window = windowFor(splitter);

        enter(named(window.content(), "groups.text", JTextField.class), "0-3|4|5|6|7");

        assertThat(closed).isZero();
        assertThat(groups(splitter)).isEmpty();
        assertThat(labelTexts(named(window.content(), "groups.problems", Container.class)))
                .anyMatch(line -> line.startsWith("El pin b4 quedaría unido"));
    }

    /** Fix round 3: the table showed «3 – 0» after reading {@code 11-7|3-0}; it shows each range low – high. */
    @Test
    void theTableShowsRangesTypedFromTheHighEndLowToHigh() {
        GroupsWindow window = windowFor(splitter(16, ""));
        Container content = window.content();

        leave(named(content, "groups.text", JTextField.class), "11-7|3-0");

        assertThat((Integer) named(content, "groups.from.0.0", JSpinner.class).getValue()).isEqualTo(7);
        assertThat((Integer) named(content, "groups.to.0.0", JSpinner.class).getValue()).isEqualTo(11);
        assertThat((Integer) named(content, "groups.from.1.0", JSpinner.class).getValue()).isZero();
        assertThat((Integer) named(content, "groups.to.1.0", JSpinner.class).getValue()).isEqualTo(3);
    }

    /** Fix round 4: a click on Aceptar right after typing (no focus lost yet) used to apply the old groups. */
    @Test
    void aceptarReadsTheTextTypedButNotYetLeft() {
        Component splitter = splitter(8, "");
        GroupsWindow window = windowFor(splitter);
        named(window.content(), "groups.text", JTextField.class).setText("0-3|4-7");

        accept(window).doClick();

        assertThat(closed).isEqualTo(1);
        assertThat(groups(splitter)).isEqualTo("0-3|4-7");
        assertThat(document.history().undoDescription()).contains("command.busGroups");
    }

    @Test
    void aceptarWithTypedTextTheDocumentRefusesSaysWhyAndStaysOpen() {
        Component splitter = splitterWiredBitByBit();
        GroupsWindow window = windowFor(splitter);
        named(window.content(), "groups.text", JTextField.class).setText("0-3|4|5|6|7");

        accept(window).doClick();

        assertThat(closed).isZero();
        assertThat(groups(splitter)).isEmpty();
        assertThat(labelTexts(named(window.content(), "groups.problems", Container.class)))
                .anyMatch(line -> line.startsWith("El pin b4 quedaría unido"));
    }

    /** Fix round 4: a pending text whose current draft has a problem still lets Aceptar read it. */
    @Test
    void typingTextEnablesAceptarEvenWhileTheTableHasAProblem() {
        Component splitter = splitter(8, "0-3|4-7");
        GroupsWindow window = windowFor(splitter);
        named(window.content(), "groups.from.1.0", JSpinner.class).setValue(3);
        assertThat(accept(window).isEnabled()).isFalse();

        named(window.content(), "groups.text", JTextField.class).setText("0-1|2-7");
        assertThat(accept(window).isEnabled()).isTrue();
        accept(window).doClick();

        assertThat(closed).isEqualTo(1);
        assertThat(groups(splitter)).isEqualTo("0-1|2-7");
    }

    /**
     * Fix round 4: pressing the mouse on Aceptar read the text (focus lost), the table went from 2 to 5 rows and the
     * button moved from under the mouse, so the click was lost. The buttons stay put whatever the content does.
     */
    @Test
    void theButtonsDoNotMoveWhenTheTableGrows() {
        GroupsWindow window = windowFor(splitter(8, "0-3|4-7"));
        Container content = window.content();
        content.setSize(700, 900);
        layOut(content);
        java.awt.Point before = javax.swing.SwingUtilities.convertPoint(accept(window).getParent(),
                accept(window).getLocation(), content);

        leave(named(content, "groups.text", JTextField.class), "0-3|4|5|6|7");
        assertThat(named(content, "groups.from.4.0", JSpinner.class)).isNotNull();
        content.invalidate();
        layOut(content);
        java.awt.Point after = javax.swing.SwingUtilities.convertPoint(accept(window).getParent(),
                accept(window).getLocation(), content);

        assertThat(after).isEqualTo(before);
    }

    /** Fix round 4: Aceptar is the dialog's default button (painted as the primary one; Enter anywhere accepts). */
    @Test
    void aceptarIsTheDefaultButtonAndTheBodyIsTheContentPane() {
        GroupsWindow window = windowFor(splitter(8, "0-3|4-7"));
        javax.swing.JRootPane rootPane = new javax.swing.JRootPane();

        window.installOn(rootPane);

        assertThat(rootPane.getDefaultButton()).isSameAs(accept(window));
        assertThat(rootPane.getContentPane()).isSameAs(window.content());
        assertThat(rootPane.getActionForKeyStroke(javax.swing.KeyStroke.getKeyStroke(
                java.awt.event.KeyEvent.VK_ESCAPE, 0))).isNotNull();
    }

    /** Fix round 4: with 5 pins the window took ~970 px of a 1080 screen, mostly preview and unwrapped hints. */
    @Test
    void aFivePinSplittersWindowIsCompact() {
        GroupsWindow window = windowFor(splitter(8, "0-3|4|5|6|7"));

        assertThat(window.preferredDialogSize(new Dimension(1920, 1080)).height).isLessThan(700);
    }

    @Test
    void thePreviewIsCompactEvenForATallPart() {
        GroupsWindow window = windowFor(splitter(32, "0|1|2|3|4|5|6|7|8|9|10|11|12|13|14|15"));
        GroupsPreview preview = named(window.content(), "groups.preview", GroupsPreview.class);

        assertThat(preview.getPreferredScrollableViewportSize().height).isLessThanOrEqualTo(160);
        assertThat(preview.getParent().getParent().getPreferredSize().height).isLessThanOrEqualTo(170);
    }

    /** Fix round 4: the dialog follows its content when the pins change, taller with more of them, shorter with less. */
    @Test
    void theDialogsHeightFollowsThePins() {
        GroupsWindow window = windowFor(splitter(8, "0-3|4-7"));
        List<Dimension> sizes = new ArrayList<>();
        window.onResize(() -> new Dimension(1920, 1080), sizes::add);
        JTextField text = named(window.content(), "groups.text", JTextField.class);

        leave(text, "0-3|4|5|6|7");
        leave(text, "0-3|4-7");

        assertThat(sizes).hasSize(2);
        assertThat(sizes.get(0).height).isGreaterThan(sizes.get(1).height);
        assertThat(sizes.get(1)).isEqualTo(window.preferredDialogSize(new Dimension(1920, 1080)));
    }

    /**
     * Fix round 5: laid out at the size it asks for, nothing in the window is cut: the table shows all its rows (up
     * to its cap), the bit strip its whole cells and the preview the whole part (up to its cap), with no scrollbar.
     * The last one is a store word's fields.
     */
    @Test
    void nothingIsCutAtTheSizeTheWindowAsksFor() {
        for (Component component : List.of(splitter(8, "0-3|4-7"), splitter(8, "0-3|4|5|6|7"),
                splitter(32, "0-6|7-11|12-14|15-19|20-24|25-31"))) {
            GroupsWindow window = windowFor(component);
            Dimension size = window.preferredDialogSize(new Dimension(1920, 1080));
            Container body = window.content();
            body.setSize(size);
            layOut(body);

            assertWhole(scrollPaneOf(named(body, "groups.from.0.0", JSpinner.class)), 200);
            assertWhole(scrollPaneOf(named(body, "groups.strip", BitStrip.class)), Integer.MAX_VALUE);
            assertWhole(scrollPaneOf(named(body, "groups.preview", GroupsPreview.class)), GroupsPreview.TALLEST);
        }
    }

    private static javax.swing.JScrollPane scrollPaneOf(java.awt.Component inside) {
        return (javax.swing.JScrollPane) javax.swing.SwingUtilities.getAncestorOfClass(javax.swing.JScrollPane.class,
                inside);
    }

    /** The pane shows its view's whole height (up to {@code cap}) and no scrollbar cuts into it. */
    private static void assertWhole(javax.swing.JScrollPane pane, int cap) {
        java.awt.Component view = pane.getViewport().getView();
        int wanted = Math.min(view.getPreferredSize().height, cap);
        String name = String.valueOf(view.getName());
        assertThat(pane.getViewport().getHeight()).as(name + " shown height").isGreaterThanOrEqualTo(wanted);
        if (wanted == view.getPreferredSize().height) {
            assertThat(pane.getVerticalScrollBar().isVisible()).as(name + " scrolls").isFalse();
        }
    }

    /** Fix round 5: the table, the strip and the preview never give up height they need to the others. */
    @Test
    void theScrollPanesAskForTheirWholeViewAsTheirLeast() {
        for (Component component : List.of(splitter(8, "0-3|4-7"), splitter(8, "0-3|4|5|6|7"),
                splitter(32, "0|1|2|3|4|5|6|7|8|9|10|11|12|13|14|15"))) {
            Container body = windowFor(component).content();
            javax.swing.JScrollPane table = scrollPaneOf(named(body, "groups.from.0.0", JSpinner.class));
            javax.swing.JScrollPane strip = scrollPaneOf(named(body, "groups.strip", BitStrip.class));
            javax.swing.JScrollPane preview = scrollPaneOf(named(body, "groups.preview", GroupsPreview.class));

            assertThat(table.getMinimumSize().height).isGreaterThanOrEqualTo(
                    Math.min(table.getViewport().getView().getPreferredSize().height, 200));
            assertThat(strip.getMinimumSize().height).isGreaterThanOrEqualTo(
                    strip.getViewport().getView().getPreferredSize().height);
            assertThat(preview.getMinimumSize().height).isGreaterThanOrEqualTo(
                    Math.min(preview.getViewport().getView().getPreferredSize().height, GroupsPreview.TALLEST));
        }
    }

    /** Fix round 5: a window narrower than a 32-bit strip scrolls it sideways and still shows its whole cells. */
    @Test
    void aNarrowWindowStillShowsTheStripsWholeCells() {
        Container body = windowFor(splitter(32, "0-6|7-11|12-14|15-19|20-24|25-31")).content();
        javax.swing.JScrollPane strip = scrollPaneOf(named(body, "groups.strip", BitStrip.class));
        body.setSize(520, 2000);
        layOut(body);
        layOut(body);

        assertThat(strip.getHorizontalScrollBar().isVisible()).isTrue();
        assertThat(strip.getViewport().getHeight())
                .isGreaterThanOrEqualTo(strip.getViewport().getView().getPreferredSize().height);
    }

    /** Fix round 5: the dialog's size is its content's plus the title bar and borders (setSize counts them). */
    @Test
    void theDialogsSizeAddsItsDecorations() {
        GroupsWindow window = windowFor(splitter(8, "0-3|4-7"));
        Dimension screen = new Dimension(1920, 1080);
        Dimension content = window.preferredDialogSize(screen);

        Dimension decorated = window.preferredDialogSize(screen, new java.awt.Insets(32, 4, 6, 5));

        assertThat(decorated).isEqualTo(new Dimension(content.width + 9, content.height + 38));
        assertThat(window.preferredDialogSize(new Dimension(800, 600), new java.awt.Insets(32, 4, 6, 5)).height)
                .isLessThanOrEqualTo(540);
    }

    /** Pressing the mouse on Aceptar takes the focus from the text: nothing moves then; Aceptar reads the text. */
    @Test
    void leavingTheTextForAButtonDoesNotRebuildTheTable() {
        GroupsWindow window = windowFor(splitter(8, "0-3|4-7"));
        List<Dimension> sizes = new ArrayList<>();
        window.onResize(() -> new Dimension(1920, 1080), sizes::add);
        JTextField text = named(window.content(), "groups.text", JTextField.class);
        text.setText("0-3|4|5|6|7");

        FocusEvent lost = new FocusEvent(text, FocusEvent.FOCUS_LOST, false, accept(window));
        for (FocusListener listener : text.getFocusListeners()) {
            listener.focusLost(lost);
        }

        assertThat(named(window.content(), "groups.from.4.0", JSpinner.class)).isNull();
        assertThat(sizes).isEmpty();
        accept(window).doClick();
        assertThat(closed).isEqualTo(1);
    }
}
