package cl.almejo.vsim.ui.ai;

import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ChangedSettingsSectionTest {

    private final AtomicInteger toggles = new AtomicInteger();
    private final ChangedSettingsSection section = new ChangedSettingsSection(
            "insets 0, wrap 1", "insets 0 16 0 0, wrap 1, gap 2", "", JLabel::new, toggles::incrementAndGet);

    @Test
    void isHiddenWithoutChangesAndShowsTheCountInItsHeader() {
        section.show(List.of());
        assertThat(section.isVisible()).isFalse();

        section.show(List.of("Cuadrícula: líneas", "Mostrar guías: no"));

        assertThat(section.isVisible()).isTrue();
        assertThat(section.header().getText()).contains("2");
        assertThat(section.texts()).containsExactly("Cuadrícula: líneas", "Mostrar guías: no");
        assertThat(section.changesList().getComponentCount()).isEqualTo(2);
    }

    @Test
    void showReplacesTheListInsteadOfAppendingToIt() {
        section.show(List.of("uno", "dos"));
        section.show(List.of("tres"));

        assertThat(section.texts()).containsExactly("tres");
        assertThat(section.changesList().getComponentCount()).isEqualTo(1);
    }

    @Test
    void theListStartsFoldedAndTheHeaderTogglesItAndTellsTheOwner() {
        section.show(List.of("uno"));
        assertThat(section.changesList().isVisible()).isFalse();

        section.header().doClick();

        assertThat(section.changesList().isVisible()).isTrue();
        assertThat(toggles.get()).isEqualTo(1);

        section.header().doClick();

        assertThat(section.changesList().isVisible()).isFalse();
        assertThat(toggles.get()).isEqualTo(2);
    }
}
