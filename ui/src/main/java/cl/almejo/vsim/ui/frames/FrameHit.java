package cl.almejo.vsim.ui.frames;

import cl.almejo.vsim.core.document.Frame;

/** A frame and the part of it under the pointer. */
public record FrameHit(Frame frame, FramePart part) {
}
