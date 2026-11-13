package cl.almejo.vsim.ui.netcard;

import cl.almejo.vsim.core.component.standard.ConstantValue;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.canvas.BusValueText;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.ArrayList;
import java.util.List;

/**
 * The texts of the signal card, all from the bundles: the header «Red de 4 bits = 0xB», the value rows (one bit:
 * «valor 1 (alto)»; a bus: every format of the bus balloon, with its rules for Z and X), the section titles, the
 * ends «AND U1 · out» and the problem lines.
 */
final class NetCardText {

    /** One row of the value grid: «con signo» and «-5». */
    record Row(String label, String text) {
    }

    /** A 7-bit bus reads as an ASCII character too, as an 8-bit one does. */
    private static final int CHARACTER_WIDTH = 7;

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
     * a bus in binary and hex, and when every bit is defined also in decimal, signed and octal, as a character with 7
     * or 8 bits, as a float with 16, 32 or 64 and in fixed point when the part that drives it shows it so: the same
     * formats as the bus balloon ({@link BusValueText#otherFormats(Value, String)}).
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
        List<Row> rows = new ArrayList<>(List.of(binary, hex,
                format("netCard.row.decimal", value, ConstantValue.DECIMAL),
                format("netCard.row.signed", value, ConstantValue.SIGNED),
                format("netCard.row.octal", value, ConstantValue.OCTAL)));
        if (value.width() == CHARACTER_WIDTH || value.width() == Byte.SIZE) {
            rows.add(format("netCard.row.character", value, ConstantValue.ASCII));
        }
        if (value.width() == Short.SIZE || value.width() == Integer.SIZE || value.width() == Long.SIZE) {
            rows.add(format("netCard.row.float", value, ConstantValue.FLOAT));
        }
        summary.partFormat()
                .filter(partFormat -> ConstantValue.FIXED.equals(Probe.valueFormatOf(partFormat)))
                .ifPresent(partFormat -> rows.add(fixedPoint(value, Probe.fractionBitsOf(partFormat))));
        return List.copyOf(rows);
    }

    private static Row fixedPoint(Value value, int fractionBits) {
        String text = ConstantValue.display(value.bits(), value.width(), ConstantValue.FIXED, fractionBits);
        return new Row(Messages.text("netCard.row.fixed"), Messages.format("netCard.fixedValue", text, fractionBits));
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

    /**
     * «La maneja»: the net's drivers, then the drivers of its short circuit that are not among them (a part inside a
     * component's body, by its route), each with the value it imposes, so no driver that fights is hidden.
     */
    static List<NetEnd> drivers(NetSummary summary) {
        if (!(summary.problem().orElse(null) instanceof NetProblem.ShortCircuit shortCircuit)) {
            return summary.drivers();
        }
        List<NetEnd> drivers = new ArrayList<>(summary.drivers());
        shortCircuit.drivers().stream().filter(driver -> !drivers.contains(driver)).forEach(drivers::add);
        return List.copyOf(drivers);
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
