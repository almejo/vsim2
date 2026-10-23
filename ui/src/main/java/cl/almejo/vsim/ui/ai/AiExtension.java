package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationApi;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JDialog;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The AI in every editor view (IA.1): Herramientas ▸ «IA (MCP)…» opens this window's panel ({@link AiPanel}) and the
 * status line shows the AI's tasks on its circuit ({@link AiStatusFeatures}) and, always, the server's state
 * ({@link AiStatusIndicator}, a click opens the panel). The server itself is one per application
 * ({@link AiServerService}); disposing takes back the menu item, the shortcut, the status strip, the indicator, the
 * glow and the panel.
 */
public final class AiExtension implements EditorExtension {

    static final String SHORTCUT_ID = "tools.aiPanel";

    private final AiServerService service;
    private final Function<OpenDocument, DocumentId> ids;
    private final PreferenceProposals proposals;
    /** The override of the window the AI works in for a circuit (its first window); empty for none. */
    private final Function<OpenDocument, Optional<SettingsOverride>> circuitOverrides;

    /**
     * Each window's panel shows its own «Ajustes que tocó la IA».
     *
     * @param ids the id the AI knows a circuit by ({@link EditorAutomationHost#documentId})
     */
    public AiExtension(AiServerService service, Function<OpenDocument, DocumentId> ids,
                       PreferenceProposals proposals) {
        this(service, ids, proposals, null);
    }

    /**
     * @param ids              the id the AI knows a circuit by ({@link EditorAutomationHost#documentId})
     * @param circuitOverrides the override of the window the AI works in for a circuit
     *                         ({@link EditorWindows#settingsOverride}): a second window of the circuit shows it too
     */
    public AiExtension(AiServerService service, Function<OpenDocument, DocumentId> ids,
                       PreferenceProposals proposals,
                       Function<OpenDocument, Optional<SettingsOverride>> circuitOverrides) {
        this.service = Objects.requireNonNull(service, "service");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.proposals = Objects.requireNonNull(proposals, "proposals");
        this.circuitOverrides = circuitOverrides;
    }

    /** An extension over a server of its own that nobody turns on (tests that install every extension). */
    public static AiExtension inactive() {
        AppPreferences preferences = AppPreferences.inMemory();
        EditorAutomationHost host = new EditorAutomationHost(List::of);
        return new AiExtension(new AiServerService(preferences, new AutomationApi(host)), host::documentId,
                new PreferenceProposals(preferences));
    }

    /** The application's server this extension shows (Preferencias ▸ «IA» shows its session port too). */
    public AiServerService server() {
        return service;
    }

    @Override
    public Runnable install(EditorView view) {
        AiStatusFeatures status = new AiStatusFeatures(view, service, ids, proposals, System::nanoTime);
        PanelWindow panelWindow = new PanelWindow(view);
        Action open = new AbstractAction(Messages.text("action.aiPanel")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                panelWindow.show();
            }
        };
        JMenu tools = view.menu(MenuId.TOOLS);
        JSeparator separator = new JPopupMenu.Separator();
        JMenuItem item = new JMenuItem(open);
        tools.add(separator);
        tools.add(item);
        Runnable removeShortcut = view.registerShortcutAction(SHORTCUT_ID, open);
        AiStatusIndicator indicator = new AiStatusIndicator(service, panelWindow::show);
        Runnable removeIndicator = view.statusBar().addIndicator(indicator);
        return () -> {
            removeIndicator.run();
            indicator.dispose();
            removeShortcut.run();
            tools.remove(item);
            tools.remove(separator);
            panelWindow.dispose();
            status.dispose();
        };
    }

    /** The panel of one window, made the first time it is asked for and kept until the view goes. */
    private final class PanelWindow {

        private final EditorView view;
        private JDialog dialog;
        private AiPanel panel;

        PanelWindow(EditorView view) {
            this.view = view;
        }

        void show() {
            if (dialog == null) {
                Window owner = SwingUtilities.getWindowAncestor(view);
                dialog = new JDialog(owner, Messages.text("ai.panel.title"));
                AppPreferences windowPreferences = view.services().preferences();
                Supplier<Optional<SettingsOverride>> override = circuitOverrides == null
                        ? () -> SettingsOverride.of(windowPreferences)
                        : () -> circuitOverrides.apply(view.openDocument());
                panel = new AiPanel(service, windowPreferences, override, proposals,
                        AiPreferencesPanel.systemClipboard());
                dialog.setContentPane(panel);
                dialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
                dialog.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent event) {
                        dispose();
                    }
                });
                dialog.pack();
                dialog.setLocationRelativeTo(owner);
            } else {
                panel.refresh();
            }
            dialog.setVisible(true);
            dialog.toFront();
        }

        void dispose() {
            if (panel != null) {
                panel.dispose();
                panel = null;
            }
            if (dialog != null) {
                JDialog closing = dialog;
                dialog = null;
                closing.dispose();
            }
        }
    }
}
