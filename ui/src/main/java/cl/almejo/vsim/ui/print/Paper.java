package cl.almejo.vsim.ui.print;

/** The paper sizes «Exportar a PDF» offers, in points (1/72 in), portrait. */
public enum Paper {
    A4(595.28, 841.89, "print.paper.a4"),
    LETTER(612, 792, "print.paper.letter"),
    /** Chilean Oficio, 216 × 330 mm. */
    OFICIO(612.28, 935.43, "print.paper.oficio"),
    A3(841.89, 1190.55, "print.paper.a3");

    private final double widthPoints;
    private final double heightPoints;
    private final String messageKey;

    Paper(double widthPoints, double heightPoints, String messageKey) {
        this.widthPoints = widthPoints;
        this.heightPoints = heightPoints;
        this.messageKey = messageKey;
    }

    public double widthPoints() {
        return widthPoints;
    }

    public double heightPoints() {
        return heightPoints;
    }

    /** The bundle key of the paper's name, e.g. «A4 (210 × 297 mm)». */
    public String messageKey() {
        return messageKey;
    }
}
