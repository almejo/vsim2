package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Preferencias ▸ «IA» (spec §2): every change is stored at once. */
class AiPreferencesPanelTest {

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<String> copied = new ArrayList<>();

    @BeforeEach
    void spanish() {
        Messages.initialize(Messages.SPANISH);
    }

    @Test
    void itShowsTheRememberedValuesAndTheDataNote() {
        preferences.setAiPort(48000);
        AiPreferencesPanel panel = new AiPreferencesPanel(preferences, copied::add);

        assertThat(panel.startWithVsimBox().getText()).isEqualTo("Iniciar con VSim");
        assertThat(panel.startWithVsimBox().isSelected()).isFalse();
        assertThat(panel.portSpinner().getValue()).isEqualTo(48000);
        assertThat(panel.defaultPortButton().getText()).isEqualTo("Usar el por defecto");
        assertThat(panel.defaultPortButton().isEnabled()).isTrue();
        assertThat(panel.newSecretButton().getText()).isEqualTo("Generar un secreto nuevo");
        assertThat(panel.copyButton().getText()).isEqualTo("Copiar configuración para Claude Code");
        assertThat(panel.dataNote().getText()).isEqualTo("Lo que la IA lee del circuito se envía a su proveedor");
        panel.dispose();
    }

    @Test
    void startingWithVsimAndThePortAreStoredAtOnce() {
        AiPreferencesPanel panel = new AiPreferencesPanel(preferences, copied::add);

        panel.startWithVsimBox().doClick();
        panel.portSpinner().setValue(48123);

        assertThat(preferences.aiStartWithVsim()).isTrue();
        assertThat(preferences.aiPort()).isEqualTo(48123);

        panel.defaultPortButton().doClick();

        assertThat(preferences.aiPort()).isEqualTo(AppPreferences.DEFAULT_AI_PORT);
        assertThat(panel.portSpinner().getValue()).isEqualTo(AppPreferences.DEFAULT_AI_PORT);
        assertThat(panel.defaultPortButton().isEnabled()).isFalse();
        panel.dispose();
    }

    @Test
    void copyingGivesTheExactClaudeCodeLine() {
        preferences.setAiPort(48123);
        preferences.setAiSecret("s3cret-s3cret-s3cret-s3cret");
        AiPreferencesPanel panel = new AiPreferencesPanel(preferences, copied::add);

        panel.copyButton().doClick();

        assertThat(copied).containsExactly("claude mcp add --transport http --scope user vsim "
                + "http://127.0.0.1:48123/mcp --header \"Authorization: Bearer s3cret-s3cret-s3cret-s3cret\"");
        assertThat(panel.feedback().getText()).isEqualTo("Copiado: pégalo en una terminal y ejecútalo una vez");
        panel.dispose();
    }

    @Test
    void aNewSecretReplacesTheRememberedOne() {
        preferences.setAiSecret("s3cret-s3cret-s3cret-s3cret");
        AiPreferencesPanel panel = new AiPreferencesPanel(preferences, copied::add);

        panel.newSecretButton().doClick();

        assertThat(preferences.aiSecret()).isPresent().get().isNotEqualTo("s3cret-s3cret-s3cret-s3cret");
        assertThat(panel.feedback().getText()).startsWith("Secreto nuevo");
        panel.dispose();
    }

    @Test
    void itFollowsChangesMadeElsewhereUntilDisposed() {
        int before = preferences.listenerCount();
        AiPreferencesPanel panel = new AiPreferencesPanel(preferences, copied::add);

        preferences.setAiPort(49001); // «Usar el siguiente libre» in the panel

        assertThat(panel.portSpinner().getValue()).isEqualTo(49001);
        panel.dispose();
        assertThat(preferences.listenerCount()).isEqualTo(before);
    }
}
