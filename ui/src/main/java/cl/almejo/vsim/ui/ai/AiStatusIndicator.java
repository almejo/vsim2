package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;

/**
 * The AI's state in every window's status bar (Pulido): a small coloured dot and «IA: apagada / encendiendo… /
 * escuchando / conectada / trabajando / error», in the colour role of the panel's header ({@link AiColors}), read
 * when painted. The tooltip says the state's sentence with the address; a click (primary button) opens Herramientas
 * ▸ «IA (MCP)…». It follows the server live until {@link #dispose()}. Editor chrome only: never in an export. EDT
 * only.
 */
final class AiStatusIndicator extends JLabel {

    /** Smaller than the panel's dot: it sits in a line of text. */
    private static final int DOT_SIZE = 8;
    private static final int DOT_TEXT_GAP = 4;

    private final AiServerService service;
    private final Runnable removeServiceListener;

    /** @param openPanel what a click does: Herramientas ▸ «IA (MCP)…» of this window */
    AiStatusIndicator(AiServerService service, Runnable openPanel) {
        this.service = Objects.requireNonNull(service, "service");
        Objects.requireNonNull(openPanel, "openPanel");
        putClientProperty("html.disable", Boolean.TRUE);
        setIcon(AiStateDot.icon(service::state, DOT_SIZE));
        setIconTextGap(DOT_TEXT_GAP);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event)) {
                    openPanel.run();
                }
            }
        });
        this.removeServiceListener = service.addListener(new AiServerService.Listener() {
            @Override
            public void stateChanged() {
                refresh();
            }
        });
        refresh();
    }

    /** «IA: escuchando»: the panel's word for {@code state}, after «IA:». */
    static String text(AiServerState state) {
        return Messages.text(switch (state) {
            case AiServerState.Off off -> "ai.indicator.off";
            case AiServerState.Starting starting -> "ai.indicator.starting";
            case AiServerState.Listening listening -> "ai.indicator.listening";
            case AiServerState.Connected connected -> "ai.indicator.connected";
            case AiServerState.Working working -> "ai.indicator.working";
            case AiServerState.PortInUse portInUse -> "ai.indicator.error";
            case AiServerState.OtherVsim otherVsim -> "ai.indicator.error";
            case AiServerState.Failed failed -> "ai.indicator.error";
        });
    }

    /** The state's sentence with the address (once: «Escuchando en 127.0.0.1:47017» says it already), then the click. */
    private String tooltip(AiServerState state) {
        String port = Integer.toString(service.port().orElse(service.effectivePort())); // never «47.017»
        String sentence = switch (state) {
            case AiServerState.Off off -> Messages.format("ai.indicator.offAddress", port);
            case AiServerState.Starting starting -> state.text();
            case AiServerState.Listening listening -> state.text();
            default -> Messages.format("ai.indicator.address", state.text(), port);
        };
        return Messages.format("ai.indicator.tip", sentence);
    }

    private void refresh() {
        AiServerState state = service.state();
        setText(text(state));
        setToolTipText(tooltip(state));
        repaint();
    }

    /** Worked out when asked, so a port changed in Preferencias while the AI is off shows too. */
    @Override
    public String getToolTipText() {
        return service == null ? super.getToolTipText() : tooltip(service.state());
    }

    @Override
    public Color getForeground() {
        return service == null ? super.getForeground() : AiColors.of(service.state());
    }

    /** Stops following the server: the window goes. */
    void dispose() {
        removeServiceListener.run();
    }
}
