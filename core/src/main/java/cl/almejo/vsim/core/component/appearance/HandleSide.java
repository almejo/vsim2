package cl.almejo.vsim.core.component.appearance;

/** Which of a node's two handles: the one of the segment arriving at the node or the one of the segment leaving it. */
public enum HandleSide {
    INCOMING, OUTGOING;

    public HandleSide opposite() {
        return this == INCOMING ? OUTGOING : INCOMING;
    }
}
