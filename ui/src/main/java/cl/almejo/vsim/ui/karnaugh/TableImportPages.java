package cl.almejo.vsim.ui.karnaugh;

import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Builds the two pages of the table-import dialog (the widgets stay in {@link TableImportPanel}). */
final class TableImportPages {

    private TableImportPages() {
    }

    /** Page «Datos»: where the table comes from, the format help, the inputs, the problems and the preview. */
    static JPanel dataPage(TableImportPanel panel) {
        JPanel data = new JPanel(new MigLayout("insets 12 16 8 16, fill, wrap 1, hidemode 3", "[grow, fill]",
                "[][]8[][][][][grow]"));
        JPanel sourceRow = new JPanel(new MigLayout("insets 0", "[][]12[grow]"));
        sourceRow.add(panel.openFile);
        sourceRow.add(panel.paste);
        sourceRow.add(panel.sourceLabel);
        data.add(sourceRow);
        // The format help in a soft box, so it reads as instructions and not as part of the form.
        JPanel helpBox = new JPanel(new MigLayout("insets 8 12 8 12, fill", "[grow, fill]")) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D graphics2D = (Graphics2D) graphics.create();
                try {
                    graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    graphics2D.setColor(WizardColors.sidePanel());
                    graphics2D.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                    graphics2D.setColor(WizardColors.sidePanelEdge());
                    graphics2D.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                } finally {
                    graphics2D.dispose();
                }
            }
        };
        helpBox.setOpaque(false);
        helpBox.add(panel.help, "wmin 100");
        data.add(helpBox, "wmin 100");
        JPanel inputsRow = new JPanel(new MigLayout("insets 0", "[][]12[grow]"));
        inputsRow.add(new JLabel(Messages.text("tableImport.inputCount")));
        inputsRow.add(panel.inputCount);
        inputsRow.add(panel.columns);
        data.add(inputsRow);
        data.add(panel.problems, "wmin 100");
        data.add(panel.notice, "wmin 100");
        panel.wizardHint.setOpaque(false);
        // A drawn bulb: the fonts Java finds rarely have the emoji 💡, which would show as an empty box or nothing.
        panel.wizardHint.add(new JLabel(Icons.get(TableImportPanel.HINT_ICON, Icons.MENU_SIZE)));
        panel.wizardHint.add(panel.hintText, "wmin 100");
        panel.wizardHint.add(panel.hintButton);
        data.add(panel.wizardHint, "wmin 100");
        data.add(panel.preview, "grow, push, align left top");
        return data;
    }

    /** Page «Crear»: the title, the options, the summary and the creation controls. */
    static JPanel createPage(TableImportPanel panel) {
        JPanel content = new JPanel(new MigLayout("insets 16, fillx, wrap 2", "[][grow, fill]",
                "[][]12[][][]12[]12[]"));
        content.add(new JLabel(Messages.text("karnaugh.table.titleLabel")));
        content.add(panel.title, "growx 0, wmin 200");
        content.add(panel.titleProblem, "skip 1, growx, wmin 100, hidemode 3");
        content.add(new JLabel(Messages.text("karnaugh.expression.gates")));
        content.add(panel.optionControls.gatesPanel());
        content.add(new JLabel(Messages.text("karnaugh.expression.form")));
        content.add(panel.optionControls.formsPanel());
        content.add(panel.optionControls.shareBox(), "span 2");
        panel.summary.setFont(panel.summary.getFont().deriveFont(Font.BOLD));
        content.add(panel.summary, "span 2");
        content.add(panel.gateLimit, "span 2, growx, wmin 100, hidemode 3");
        content.add(panel.controls, "span 2, growx");
        return content;
    }
}
