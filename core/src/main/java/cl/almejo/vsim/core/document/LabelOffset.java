package cl.almejo.vsim.core.document;

/**
 * Where a component's label centre sits, in grid units, relative to the component's position and in the component's
 * own unrotated frame: the drawn centre is {@code position + rotation.apply(offset)}, so the label turns with the body.
 */
public record LabelOffset(int x, int y) {
}
