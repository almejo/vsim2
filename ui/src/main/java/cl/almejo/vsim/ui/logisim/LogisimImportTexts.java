package cl.almejo.vsim.ui.logisim;

import java.util.List;

/** The bundle keys of the Logisim import's own screens (the notes' keys are {@code LogisimNotes}'). */
public final class LogisimImportTexts {

    static final String MENU_ITEM = "logisimImport.menu";
    static final String CHOOSE_TITLE = "logisimImport.choose.title";
    static final String CHOOSE_FILTER = "logisimImport.choose.filter";
    static final String PROGRESS_TITLE = "logisimImport.progress.title";
    static final String PROGRESS_TEXT = "logisimImport.progress.text";
    static final String PROGRESS_CANCEL = "logisimImport.progress.cancel";
    static final String REPORT_TITLE = "logisimImport.report.title";
    static final String REPORT_SUMMARY = "logisimImport.report.summary";
    static final String KIND_DIFFERENT = "logisimImport.report.kind.different";
    static final String KIND_APPROXIMATED = "logisimImport.report.kind.approximated";
    static final String KIND_UNSUPPORTED = "logisimImport.report.kind.unsupported";
    static final String KIND_INVALID = "logisimImport.report.kind.invalid";
    static final String CIRCUIT_PROJECT = "logisimImport.report.circuit.project";
    static final String CIRCUIT_NAMED = "logisimImport.report.circuit.named";
    static final String REPEATED = "logisimImport.report.repeated";
    static final String PLACE_LOGISIM_POINT = "logisimImport.report.place.logisimPoint";
    static final String PLACE_TOOLTIP = "logisimImport.report.place.tooltip";
    static final String PART = "logisimImport.report.part";
    static final String PART_LABELED = "logisimImport.report.part.labeled";
    static final String PART_INSIDE = "logisimImport.report.part.inside";
    static final String PART_LABELED_INSIDE = "logisimImport.report.part.labeledInside";
    static final String PART_TEXT = "logisimImport.report.part.text";
    static final String CLOSE = "logisimImport.report.close";
    static final String PROBLEM_TITLE = "logisimImport.problem.title";
    static final String PROBLEM_NOT_XML = "logisimImport.problem.notXml";
    static final String PROBLEM_NOT_LOGISIM = "logisimImport.problem.notLogisim";
    static final String PROBLEM_UNREADABLE = "logisimImport.problem.unreadable";
    static final String PROBLEM_UNEXPECTED = "logisimImport.problem.unexpected";

    /** Every key of this class, for the bundle check. */
    public static final List<String> MESSAGE_KEYS = List.of(MENU_ITEM, CHOOSE_TITLE, CHOOSE_FILTER, PROGRESS_TITLE,
            PROGRESS_TEXT, PROGRESS_CANCEL, REPORT_TITLE, REPORT_SUMMARY, KIND_DIFFERENT, KIND_APPROXIMATED,
            KIND_UNSUPPORTED, KIND_INVALID, CIRCUIT_PROJECT, CIRCUIT_NAMED, REPEATED, PLACE_LOGISIM_POINT, PLACE_TOOLTIP,
            PART, PART_LABELED, PART_INSIDE, PART_LABELED_INSIDE, PART_TEXT, CLOSE, PROBLEM_TITLE,
            PROBLEM_NOT_XML, PROBLEM_NOT_LOGISIM, PROBLEM_UNREADABLE, PROBLEM_UNEXPECTED);

    private LogisimImportTexts() {
    }
}
