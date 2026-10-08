package cl.almejo.vsim.core.document;

public sealed interface DocumentElement permits Component, Wire, TextLabel {
    ElementId id();

    DocumentElement translate(int deltaX, int deltaY);
}
