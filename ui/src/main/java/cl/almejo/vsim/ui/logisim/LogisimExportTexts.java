package cl.almejo.vsim.ui.logisim;

import java.util.List;

/** The bundle keys of the Logisim export's own screens (the notes' keys are {@code LogisimNotes}'). */
public final class LogisimExportTexts {

    static final String ACTION = "action.exportLogisim";
    static final String MENU_ITEM = "menu.file.export.logisim";
    static final String CHOOSE_FILTER = "logisimExport.choose.filter";
    static final String REPORT_TITLE = "logisimExport.report.title";
    static final String REPORT_SUMMARY = "logisimExport.report.summary";
    static final String PROBLEM_TITLE = "logisimExport.problem.title";
    static final String PROBLEM_UNWRITABLE = "logisimExport.problem.unwritable";
    /** A part in a note: its kind and its label ({@code ALU «U1»}). */
    static final String PART_WITH_LABEL = "logisimExport.partWithLabel";

    /** Every key of this class, for the bundle check. */
    public static final List<String> MESSAGE_KEYS = List.of(ACTION, MENU_ITEM, CHOOSE_FILTER, REPORT_TITLE, REPORT_SUMMARY,
            PROBLEM_TITLE, PROBLEM_UNWRITABLE, PART_WITH_LABEL);

    private LogisimExportTexts() {
    }
}
