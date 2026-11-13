package cl.almejo.vsim.ui.netcard;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.NetId;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Container;
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

        @Override
        public void dragged(java.awt.Point location) {
            heard.add("dragged " + location.x + "," + location.y);
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

    private static NetSummary andOutputWith(Value value) {
        NetSummary one = andOutput();
        return new NetSummary(one.netId(), one.width(), one.wires(), one.points(), one.drivers(), one.readers(),
                one.tunnelNames(), new NetValue.Simulated(value), Optional.empty());
    }

    private static NetSummary busWith(Value value) {
        return new NetSummary(new NetId("n2"), 4, Set.of(new ElementId("w2")), Set.of(),
                List.of(end("c1", "Constante K", "out")), List.of(end("c2", "Sonda S", "in")), List.of(),
                new NetValue.Simulated(value), Optional.empty());
    }

    @Test
    void aNewValueChangesTheTextsInPlaceSoItsButtonsStayClickable() {
        showAndLayOut(andOutputWith(Value.ONE), false);
        AbstractButton pin = card.pinButton();
        AbstractButton led = card.endButton("LED L1 · in").orElseThrow();

        showAndLayOut(andOutputWith(Value.ZERO), false);

        assertThat(card.pinButton()).isSameAs(pin);
        assertThat(card.endButton("LED L1 · in")).containsSame(led);
        assertThat(card.texts()).contains("Red de 1 bit = 0", "0 (bajo)").doesNotContain("Red de 1 bit = 1");
    }

    @Test
    void aCountingBusKeepsItsButtonsWhileItsValueChanges() {
        showAndLayOut(busWith(Value.of(4, 0xB)), false);
        AbstractButton probe = card.endButton("Sonda S · in").orElseThrow();

        showAndLayOut(busWith(Value.of(4, 0xC)), false);

        assertThat(card.endButton("Sonda S · in")).containsSame(probe);
        assertThat(card.texts()).contains("Red de 4 bits = 0xC", "1100", "12");
    }

    @Test
    void theSecondaryTextsReadTheirColourWhenPainted() {
        showAndLayOut(andOutput(), false);
        Color original = UIManager.getColor("Label.disabledForeground");
        try {
            Color changed = new Color(1, 2, 3);
            UIManager.put("Label.disabledForeground", changed);

            assertThat(labelWithText(card, "La maneja").getForeground()).isEqualTo(changed);
        } finally {
            UIManager.put("Label.disabledForeground", original);
        }
    }

    private static JLabel labelWithText(Container container, String text) {
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof JLabel label && text.equals(label.getText())) {
                return label;
            }
            if (child instanceof Container nested) {
                JLabel found = labelWithText(nested, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
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
