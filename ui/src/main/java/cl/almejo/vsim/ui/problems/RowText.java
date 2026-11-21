package cl.almejo.vsim.ui.problems;

/** A list row's text: one that has several lines (a «Detalle técnico» line) is shown as HTML with a break each. */
final class RowText {

    private RowText() {
    }

    /** {@code text} itself when it has one line; else escaped HTML with a {@code <br>} for each line end. */
    static String of(String text) {
        if (text.indexOf('\n') < 0) {
            return text;
        }
        StringBuilder html = new StringBuilder("<html>");
        String[] lines = text.split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            html.append(index > 0 ? "<br>" : "")
                    .append(lines[index].replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"));
        }
        return html.append("</html>").toString();
    }
}
