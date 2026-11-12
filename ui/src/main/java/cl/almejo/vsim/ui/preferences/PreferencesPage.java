package cl.almejo.vsim.ui.preferences;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.theme.ThemeColors;
import net.miginfocom.swing.MigLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One page of the preferences: titled sections, each a list of {@link PreferenceRow}s. {@link #filter} shows only
 * the rows that match a search and the sections that still have one. Rows are added in one place, the
 * {@link Builder}, so a later option joins its section with one line.
 */
final class PreferencesPage extends JPanel {

    /** A section title: bold muted text, its colour read from the theme each time it is asked. */
    private static final class SectionTitle extends JLabel {

        SectionTitle(String text) {
            super(text);
        }

        @Override
        public void updateUI() {
            super.updateUI();
            Font font = getFont();
            if (font != null) {
                setFont(font.deriveFont(Font.BOLD));
            }
        }

        @Override
        public Color getForeground() {
            return ThemeColors.ui("Label.disabledForeground", super.getForeground());
        }
    }

    private static final class Section {
        private final String titleKey;
        private final SectionTitle title;
        private final JSeparator separator = new JSeparator();
        private final List<PreferenceRow> rows = new ArrayList<>();

        Section(String titleKey) {
            this.titleKey = titleKey;
            this.title = new SectionTitle(Messages.text(titleKey));
        }

        void setShown(boolean shown) {
            title.setVisible(shown);
            separator.setVisible(shown);
        }
    }

    /** Builds a page: {@code section(...)} then its {@code row(...)}s, again, then {@code build()}. */
    static final class Builder {
        private static final String FIRST_TITLE = "span, split 2";
        private static final String NEXT_TITLE = "span, split 2, gaptop 14";

        private final PreferencesPage page = new PreferencesPage();
        private Section current;

        Builder section(String titleKey) {
            current = new Section(titleKey);
            page.add(current.title, page.sections.isEmpty() ? FIRST_TITLE : NEXT_TITLE);
            page.add(current.separator, "growx");
            page.sections.add(current);
            return this;
        }

        Builder row(PreferenceRow row) {
            current.rows.add(row);
            row.placed().forEach(each -> page.add(each.component(), each.constraints()));
            return this;
        }

        PreferencesPage build() {
            page.fixLabelColumn();
            return page;
        }
    }

    private final List<Section> sections = new ArrayList<>();
    private String query = "";

    private PreferencesPage() {
        super(new MigLayout("insets dialog, wrap 2, hidemode 3", "[right][grow, fill]"));
    }

    /** The label column as wide as the widest label of the page, hidden or not, so rows do not jump while filtering. */
    private void fixLabelColumn() {
        int widest = sections.stream().flatMap(section -> section.rows.stream()).mapToInt(PreferenceRow::labelWidth)
                .max().orElse(0);
        if (getLayout() instanceof MigLayout layout) {
            layout.setColumnConstraints("[right, " + widest + "px!][grow, fill]");
        }
    }

    static Builder builder() {
        return new Builder();
    }

    /**
     * Shows the rows whose words match {@code query} (all of them for a blank one) with the match marked, hides the
     * others and the sections left empty, and returns how many rows are shown.
     */
    int filter(String query) {
        this.query = query;
        int shown = 0;
        for (Section section : sections) {
            int shownInSection = 0;
            for (PreferenceRow row : section.rows) {
                boolean matches = PreferencesSearch.matches(row.words(), query);
                row.setShown(matches);
                row.highlight(matches ? query : "");
                shownInSection += matches ? 1 : 0;
            }
            section.setShown(shownInSection > 0);
            shown += shownInSection;
        }
        revalidate();
        repaint();
        return shown;
    }

    /** The section title keys, each with the ids of its rows, in order. */
    Map<String, List<String>> sectionRows() {
        Map<String, List<String>> rows = new LinkedHashMap<>();
        for (Section section : sections) {
            rows.put(section.titleKey, section.rows.stream().map(PreferenceRow::id).toList());
        }
        return rows;
    }

    List<String> visibleRowIds() {
        return sections.stream().flatMap(section -> section.rows.stream()).filter(PreferenceRow::isShown)
                .map(PreferenceRow::id).toList();
    }

    List<String> visibleSectionKeys() {
        return sections.stream().filter(section -> section.title.isVisible()).map(section -> section.titleKey)
                .toList();
    }

    /** A theme change reinstalls the look and feel: the highlight colours are read again for the open search. */
    @Override
    public void updateUI() {
        super.updateUI();
        if (sections != null && !sections.isEmpty()) {
            fixLabelColumn();
        }
        if (query != null && !query.isBlank()) {
            filter(query);
        }
    }
}
