package cl.almejo.vsim.core.io.logisim;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LogisimPinNamesTest {

    @Test
    void labelsAreTheNames() {
        assertThat(LogisimPinNames.unique(List.of(input(" A ", 0, 10), output("Y", 0, 10))))
                .containsExactly("A", "Y");
    }

    @Test
    void blankPinsAreNumberedByTheirPlaceOnTheirSideAsWhenCreatingAComponent() {
        // inputs top to bottom: a (y 10), the blank at y 20 (second), the blank at y 30 (third); in2 is a label
        List<LogisimInterfacePin> pins = List.of(input("", 0, 30), input("a", 0, 10), input("", 0, 20),
                output("", 0, 10), output("in2", 0, 20));

        assertThat(LogisimPinNames.unique(pins)).containsExactly("in4", "a", "in3", "out1", "in2");
    }

    @Test
    void repeatedLabelsGetANumberIgnoringCase() {
        List<LogisimInterfacePin> pins = List.of(input("data", 0, 10), input("Data", 0, 20), output("data", 0, 10),
                input("data2", 0, 30));

        assertThat(LogisimPinNames.unique(pins)).containsExactly("data", "Data3", "data4", "data2");
    }

    @Test
    void readsTheInterfacePinsOfACircuit() throws Exception {
        LogisimCircuit middle = LogisimTestFiles.read("subcircuits.circ").circuit("middle").orElseThrow();

        assertThat(LogisimInterfacePin.of(middle)).containsExactly(
                new LogisimInterfacePin("a", 1, false, new LogisimPoint(100, 100), "east"),
                new LogisimInterfacePin("y", 4, true, new LogisimPoint(300, 100), "west"));
    }

    private static LogisimInterfacePin input(String label, int x, int y) {
        return new LogisimInterfacePin(label, 1, false, new LogisimPoint(x, y), "east");
    }

    private static LogisimInterfacePin output(String label, int x, int y) {
        return new LogisimInterfacePin(label, 1, true, new LogisimPoint(x, y), "west");
    }
}
