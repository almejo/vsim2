package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.MultiBitLayout;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

/** A click on a bit of a multi-bit Switch toggles that bit: an input event, never an edit. */
class MultiBitSwitchClickTest {

    private final TestToolContext context = TestToolContext.withNewDocument();
    private final SelectTool tool = new SelectTool();

    @AfterEach
    void close() {
        context.close();
    }

    private Component fourBitSwitch() {
        Component lever = place(context.openDocument(), StandardComponentTypes.SWITCH, 0, 0,
                Map.of("width", 4, "value", "0b1010"));
        waitUntil(() -> context.openDocument().latestSnapshot().componentState(lever.id().value())
                .equals(new VisibleState.SwitchState(Value.of(4, 0b1010))));
        return lever;
    }

    private void clickBit(int bit) {
        clickBit(context, bit);
    }

    private void clickBit(TestToolContext clicking, int bit) {
        MultiBitLayout.BitCell cell = MultiBitLayout.cells(4).stream().filter(candidate -> candidate.bit() == bit)
                .findFirst().orElseThrow();
        double pixelsPerUnit = GridPoint.PIXELS_PER_UNIT;
        clicking.click(tool, (cell.x() + cell.width() / 2) / pixelsPerUnit,
                (cell.y() + cell.height() / 2) / pixelsPerUnit);
    }

    private void expectValue(Component lever, int bits) {
        waitUntil(() -> context.openDocument().latestSnapshot().componentState(lever.id().value())
                .equals(new VisibleState.SwitchState(Value.of(4, bits))));
    }

    @Test
    void clickingBitThreeOfTenGivesTwo() {
        Component lever = fourBitSwitch();

        clickBit(3);

        expectValue(lever, 0b0010);
    }

    @Test
    void clickingTwoBitsFlipsEachOneOnly() {
        Component lever = fourBitSwitch();

        clickBit(0);
        expectValue(lever, 0b1011);
        clickBit(2);
        expectValue(lever, 0b1111);
    }

    @Test
    void aBitClickIsNotAnEditAndLeavesTheHistoryAlone() {
        Component lever = fourBitSwitch();
        String undoBefore = context.openDocument().history().undoDescription().toString();
        boolean modifiedBefore = context.openDocument().isModified();

        clickBit(1);
        expectValue(lever, 0b1000);

        assertThat(context.openDocument().history().undoDescription().toString()).isEqualTo(undoBefore);
        assertThat(context.openDocument().isModified()).isEqualTo(modifiedBefore);
    }

    @Test
    void aBitClickedWhilePausedPublishesAStepWithTheNetThatChanged() {
        Component lever = fourBitSwitch();
        context.openDocument().pause();
        waitUntil(() -> !context.openDocument().latestSnapshot().running());

        clickBit(2);

        expectValue(lever, 0b1110);
        waitUntil(() -> context.openDocument().latestStep().isPresent());
        assertThat(context.openDocument().latestStep().orElseThrow().result().changedNets()).isNotEmpty();
    }

    @Test
    void twoWindowsOnTheSameDocumentSeeEachOthersBitClicks() {
        Component lever = fourBitSwitch();
        TestToolContext otherWindow = new TestToolContext(context.openDocument());

        clickBit(3);
        expectValue(lever, 0b0010);
        clickBit(otherWindow, 0);

        expectValue(lever, 0b0011);
        assertThat(otherWindow.openDocument().latestSnapshot().componentState(lever.id().value()))
                .isEqualTo(new VisibleState.SwitchState(Value.of(4, 0b0011)));
    }

    @Test
    void aClickOnTheBodyBetweenCellsSelectsTheSwitchButTogglesNothing() {
        Component lever = fourBitSwitch();

        context.click(tool, 0.1, 0.1);

        assertThat(context.selection.ids()).containsExactly(lever.id());
        assertThat(context.openDocument().latestSnapshot().componentState(lever.id().value()))
                .isEqualTo(new VisibleState.SwitchState(Value.of(4, 0b1010)));
    }

    @Test
    void aOneBitSwitchStillTogglesFromAnywhereOnItsBody() {
        Component lever = place(context.openDocument(), StandardComponentTypes.SWITCH, 0, 0, Map.of("value", "1"));
        waitUntil(() -> context.openDocument().latestSnapshot().componentState(lever.id().value())
                .equals(new VisibleState.SwitchState(Value.ONE)));

        context.click(tool, 1, 1);

        waitUntil(() -> context.openDocument().latestSnapshot().componentState(lever.id().value())
                .equals(new VisibleState.SwitchState(Value.ZERO)));
    }
}
