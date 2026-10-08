package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;

public final class SetLabelCommand implements Command {

    private final ElementId id;
    private final String text;
    private String previousText;

    public SetLabelCommand(ElementId id, String text) {
        this.id = id;
        this.text = text == null ? "" : text;
    }

    @Override
    public void apply(CircuitDocument document) {
        DocumentElement element = document.element(id).orElseThrow();
        previousText = currentText(element);
        document.replace(withText(element, text));
    }

    @Override
    public void revert(CircuitDocument document) {
        document.replace(withText(document.element(id).orElseThrow(), previousText));
    }

    private static String currentText(DocumentElement element) {
        return switch (element) {
            case Component component -> component.label();
            case TextLabel label -> label.text();
            case Wire wire -> throw new IllegalArgumentException("wires have no label: " + wire.id());
        };
    }

    private static DocumentElement withText(DocumentElement element, String newText) {
        return switch (element) {
            case Component component -> component.withLabel(newText);
            case TextLabel label -> label.withText(newText);
            case Wire wire -> throw new IllegalArgumentException("wires have no label: " + wire.id());
        };
    }

    @Override
    public String description() {
        return "command.label";
    }
}
