package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The AI server's state line and its switches, the top of Herramientas ▸ «IA (MCP)…» ({@link AiPanel}) and of
 * Preferencias ▸ «IA» ({@link AiPreferencesPanel}): a coloured dot and one word (Apagada / Encendiendo… / Escuchando /
 * Conectado / Trabajando / Error), its sentence, the address with since when it runs and the requests served, then
 * «Encender» / «Apagar» and «Reiniciar». Both turn the one application server on and off; what the buttons have to
 * say («Espera: la IA se está encendiendo») goes to the owner's notice line. Colours are read when painted. EDT only.
 */
final class AiStateHeader extends JPanel {

    private static final DateTimeFormatter CLOCK_TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final AiServerService service;
    private final Consumer<String> notices;
    private final ZoneId zone = ZoneId.systemDefault();
    private final AiStateDot stateDot;
    private final JLabel titleLabel = new StateLabel(true);
    private final JLabel stateLabel = new StateLabel(false);
    private final JLabel detailsLabel = new MutedLabel();
    private final JButton toggleButton = AiIcons.on(new JButton(), AiIcons.POWER);
    private final JButton restartButton = AiIcons.on(new JButton(Messages.text("ai.panel.restart")), AiIcons.RESTART);
    private final Runnable removeServiceListener;

    /**
     * @param notices where the buttons' word goes: a blank one clears the notice, the refused restart says why
     */
    AiStateHeader(AiServerService service, Consumer<String> notices) {
        super(new MigLayout("insets 0, wrap 1", "[grow, fill]"));
        this.service = Objects.requireNonNull(service, "service");
        this.notices = Objects.requireNonNull(notices, "notices");
        this.stateDot = new AiStateDot(service::state);
        setOpaque(false);
        bindButtons();
        add(stateDot, "split 2, growx 0, aligny center");
        add(titleLabel, "growx");
        add(stateLabel, "hidemode 3");
        add(detailsLabel, "hidemode 3");
        add(toggleButton, "split 2, growx 0, gaptop 6");
        add(restartButton, "growx 0, gapright push"); // next to «Encender», however wide the owner is
        this.removeServiceListener = service.addListener(new AiServerService.Listener() {
            @Override
            public void stateChanged() {
                refresh();
            }

            @Override
            public void requestServed() {
                refresh();
            }
        });
        refresh();
    }

    private void bindButtons() {
        toggleButton.addActionListener(event -> {
            AiServerState state = service.state();
            if (state.isListening() || state instanceof AiServerState.Starting) {
                service.deactivate();
            } else {
                notices.accept(" ");
                service.activate();
            }
        });
        restartButton.addActionListener(event ->
                notices.accept(service.restart() ? " " : Messages.text("ai.panel.restartRefused")));
    }

    /** The state, the details and the buttons as the server is now. */
    void refresh() {
        AiServerState state = service.state();
        titleLabel.setText(state.title());
        stateLabel.setText(state.text());
        stateLabel.setVisible(!state.text().equals(state.title())); // «Apagada» says it once
        Optional<String> details = detailsText();
        detailsLabel.setText(details.orElse(" "));
        detailsLabel.setVisible(details.isPresent());
        boolean on = state.isListening() || state instanceof AiServerState.Starting;
        toggleButton.setText(Messages.text(on ? "ai.panel.deactivate" : "ai.panel.activate"));
        restartButton.setEnabled(state.isListening() || state.isError());
        revalidate();
        repaint();
    }

    /** Stops following the server: its window closed. */
    void dispose() {
        removeServiceListener.run();
    }

    /** «127.0.0.1:47017 · desde las 14:03 · 12 pedidos» while it listens. */
    private Optional<String> detailsText() {
        Optional<Instant> since = service.startedAt();
        if (!service.state().isListening() || since.isEmpty() || service.port().isEmpty()) {
            return Optional.empty();
        }
        String time = LocalTime.ofInstant(since.get(), zone).format(CLOCK_TIME);
        return Optional.of(Messages.format("ai.panel.details", Integer.toString(service.port().getAsInt()), time,
                service.requestsServed()));
    }

    /** Coloured by the state at paint time, so a theme change shows at once; the title in bold. */
    private final class StateLabel extends JLabel {

        private final boolean bold;

        StateLabel(boolean bold) {
            this.bold = bold;
            putClientProperty("html.disable", Boolean.TRUE);
        }

        @Override
        public Color getForeground() {
            return service == null ? super.getForeground() : AiColors.of(service.state());
        }

        @Override
        public Font getFont() {
            Font base = UIManager.getFont("Label.font");
            if (!bold || base == null) {
                return super.getFont();
            }
            return base.deriveFont(Font.BOLD, base.getSize2D() + 2);
        }
    }

    /** Grey text read from the theme at paint time. */
    private static final class MutedLabel extends JLabel {
        @Override
        public Color getForeground() {
            return AiColors.muted();
        }
    }

    AiStateDot stateDot() {
        return stateDot;
    }

    JLabel titleLabel() {
        return titleLabel;
    }

    JLabel stateLabel() {
        return stateLabel;
    }

    JLabel detailsLabel() {
        return detailsLabel;
    }

    JButton toggleButton() {
        return toggleButton;
    }

    JButton restartButton() {
        return restartButton;
    }
}
