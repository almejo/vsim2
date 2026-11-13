package cl.almejo.vsim.ui.netcard;

import cl.almejo.vsim.core.component.standard.ConstantValue;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.canvas.BusValueText;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.List;

/**
 * The texts of the signal card, all from the bundles: the header «Red de 4 bits = 0xB», the value rows (one bit:
 * «valor 1 (alto)»; a bus: bin, hex, dec, con signo, octal, with the bus balloon's rules for Z and X), the section
 * titles, the ends «AND U1 · out» and the problem lines.
 */
final class NetCardText {

    /** One row of the value grid: «con signo» and «-5». */
    record Row(String label, String text) {
    }

    private NetCardText() {
    }

    /** «Red de 1 bit = 1», «Red de 4 bits = 0xB», «Red de 4 bits = sin simular». */
    static String header(NetSummary summary) {
        String value = switch (summary.value()) {
            case NetValue.NotSimulating notSimulating -> Messages.text("netCard.notSimulating");
            case NetValue.Simulated simulated -> value(simulated.value());
        };
        return summary.width() == 1 ? Messages.format("netCard.headerOneBit", value)
                : Messages.format("netCard.header", summary.width(), value);
    }

    /** A value as the pin balloon writes it: «1», «0», «Z» for one bit; «0xB» for a bus; «X» when shorted. */
    static String value(Value value) {
        return value.width() > 1 ? BusValueText.of(value) : BusValueText.binary(value);
    }

    /**
     * The value grid: none while not simulating; «X (cortocircuito)» for a shorted value; one bit with what it means;
     * a bus in binary and hex, and in decimal, signed and octal when every bit is defined.
     */
    static List<Row> valueRows(NetSummary summary) {
        if (!(summary.value() instanceof NetValue.Simulated simulated)) {
            return List.of();
        }
        Value value = simulated.value();
        if (value.conflict()) {
            return List.of(new Row(Messages.text("netCard.row.value"),
                    Messages.format("netCard.bit.conflict", BusValueText.CONFLICT)));
        }
        if (value.width() == 1) {
            return List.of(new Row(Messages.text("netCard.row.value"), oneBit(value)));
        }
        Row binary = new Row(Messages.text("netCard.row.binary"), BusValueText.binary(value));
        Row hex = new Row(Messages.text("netCard.row.hex"), BusValueText.of(value));
        if (!value.isFullyDefined()) {
            return List.of(binary, hex);
        }
        return List.of(binary, hex,
                format("netCard.row.decimal", value, ConstantValue.DECIMAL),
                format("netCard.row.signed", value, ConstantValue.SIGNED),
                format("netCard.row.octal", value, ConstantValue.OCTAL));
    }

    private static Row format(String labelKey, Value value, String format) {
        return new Row(Messages.text(labelKey), ConstantValue.display(value.bits(), value.width(), format));
    }

    private static String oneBit(Value value) {
        String bit = BusValueText.binary(value);
        return switch (value.bit(0)) {
            case ONE -> Messages.format("netCard.bit.high", bit);
            case ZERO -> Messages.format("netCard.bit.low", bit);
            case HIGH_IMPEDANCE -> Messages.format("netCard.bit.highImpedance", bit);
        };
    }

    /** «AND U1 · out», «Interruptor A · out → 1» when the value it imposes is known. */
    static String end(NetEnd end) {
        String pin = end.pinText().isEmpty() ? end.partText()
                : Messages.format("netCard.end", end.partText(), end.pinText());
        return end.valueText().map(value -> Messages.format("netCard.endWithValue", pin, value)).orElse(pin);
    }

    static String driversTitle() {
        return Messages.text("netCard.section.drivers");
    }

    static String readersTitle() {
        return Messages.text("netCard.section.readers");
    }

    static String tunnelsTitle() {
        return Messages.text("netCard.section.tunnels");
    }

    /** Shown under «La maneja» or «Llega a» when the list is empty. */
    static String nobody() {
        return Messages.text("netCard.nobody");
    }

    /** «túnel «LISTO»». */
    static String tunnel(String name) {
        return Messages.format("netCard.tunnel", name);
    }

    /** The problem line: the drivers are listed with their values under «La maneja». */
    static String problem(NetProblem problem) {
        return switch (problem) {
            case NetProblem.ShortCircuit shortCircuit -> Messages.text("netCard.problem.shortCircuit");
            case NetProblem.Undriven undriven -> Messages.text("netCard.problem.undriven");
            case NetProblem.Floating floating -> Messages.text("netCard.problem.floating");
            case NetProblem.WidthMismatch mismatch -> mismatch.text();
        };
    }
}
