package cl.almejo.vsim.core.document;

/** What a sticky note says: free text or a checklist. */
public sealed interface StickyContent permits StickyText, StickyChecklist {

    /** All the text, for Ctrl+F. */
    String searchableText();

    /** A short summary line: the first non-empty line of a text; for a list the first pending item, else the first. */
    String firstLine();

    /** This content as a checklist (a checklist returns itself). */
    StickyChecklist toChecklist();

    /** This content as free text (a text returns itself). */
    StickyText toText();
}
