package cl.almejo.vsim.ui.netcard;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.NetId;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static cl.almejo.vsim.ui.UiTestSupport.layOut;
import static org.assertj.core.api.Assertions.assertThat;

class SignalCardTest {

    private final List<String> heard = new ArrayList<>();
    private final SignalCard card = new SignalCard(new SignalCard.Listener() {
        @Override
        public void pinPressed() {
            heard.add("pin");
        }

        @Override
        public void closePressed() {
            heard.add("close");
        }

        @Override
        public void endPressed(NetEnd end) {
            heard.add("go " + end.partText());
        }

        @Override
        public void pointerEntered() {
            heard.add("entered");
        }

        @Override
        public void pointerLeft() {
            heard.add("left");
        }
    });

    private static NetEnd end(String id, String part, String pin) {
        return new NetEnd(new ElementId(id), 0, part, pin, Optional.empty(), new GridPoint(0, 0));
    }

    private static NetSummary andOutput() {
        return new NetSummary(new NetId("n1"), 1, Set.of(new ElementId("w1")), Set.of(),
                List.of(end("c1", "AND U1", "out")), List.of(end("c2", "LED L1", "in"), end("c3", "NOT U2", "in")),
                List.of("LISTO"), new NetValue.Simulated(Value.ONE), Optional.empty());
    }

    private void showAndLayOut(NetSummary summary, boolean pinned) {
        card.show(summary, pinned);
        card.setSize(card.getPreferredSize());
        layOut(card);
    }

    @Test
    void theCardSaysTheValueWhoDrivesTheNetWhereItGoesAndItsTunnels() {
        showAndLayOut(andOutput(), false);

        assertThat(card.texts()).containsSubsequence("Red de 1 bit = 1", "valor", "1 (alto)", "La maneja",
                "AND U1 · out", "Llega a", "LED L1 · in", "NOT U2 · in", "Pasa por", "túnel «LISTO»");
        assertThat(card.getPreferredSize().width).isPositive();
    }

    @Test
    void aProblemIsShownAboveTheEnds() {
        NetSummary undriven = new NetSummary(new NetId("n1"), 1, Set.of(), Set.of(), List.of(),
                List.of(end("c2", "LED L1", "in")), List.of(), new NetValue.NotSimulating(),
                Optional.of(new NetProblem.Undriven()));

        showAndLayOut(undriven, false);

        assertThat(card.texts()).containsSubsequence("Red de 1 bit = sin simular", "⚠ Nadie la maneja: vale Z",
                "La maneja", "nadie", "Llega a", "LED L1 · in");
    }

    @Test
    void clickingAPartAskedToGoThere() {
        showAndLayOut(andOutput(), false);

        card.endButton("NOT U2 · in").orElseThrow().doClick();

        assertThat(heard).containsExactly("go NOT U2");
    }

    @Test
    void thePinButtonPinsAndPinnedItCloses() {
        showAndLayOut(andOutput(), false);
        card.pinButton().doClick();
        showAndLayOut(andOutput(), true);
        card.pinButton().doClick();

        assertThat(heard).containsExactly("pin", "close");
        assertThat(card.isPinned()).isTrue();
    }

    @Test
    void movingFromTheCardOntoOneOfItsItemsIsNotLeavingIt() {
        showAndLayOut(andOutput(), false);
        java.awt.Component item = card.endButton("LED L1 · in").orElseThrow();

        dispatch(card, MouseEvent.MOUSE_ENTERED, 2, 2);
        dispatch(card, MouseEvent.MOUSE_EXITED, item.getX() + 2, item.getY() + 2);
        dispatch(item, MouseEvent.MOUSE_ENTERED, 1, 1);
        dispatch(item, MouseEvent.MOUSE_EXITED, -500, -500);

        assertThat(heard).containsExactly("entered", "left");
    }

    @Test
    void itFadesByItsOpacity() {
        card.setOpacity(0.25f);

        assertThat(card.opacity()).isEqualTo(0.25f);
    }

    private static void dispatch(java.awt.Component target, int id, int x, int y) {
        MouseEvent event = new MouseEvent(target, id, 0, 0, x, y, 0, false);
        for (MouseListener listener : target.getMouseListeners()) {
            if (id == MouseEvent.MOUSE_ENTERED) {
                listener.mouseEntered(event);
            } else {
                listener.mouseExited(event);
            }
        }
    }
}
