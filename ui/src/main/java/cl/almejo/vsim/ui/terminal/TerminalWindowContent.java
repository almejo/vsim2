package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.KeyboardTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * What the Terminal window shows (spec §9, mockup §5): a bar «nombre · filas×columnas · modo» with «Copiar texto»,
 * «Guardar .txt» and «Limpiar», and the tabs «Pantalla», «Monitor de bytes» and «Salida esperada». It reads the
 * Terminal's frames through {@link #update}; typing on the screen goes to the first Teclado whose «Terminal enlazado»
 * is this Terminal's name, encoded with that Teclado's parameters, as a captured key would ({@link CapturedKeys}).
 * «Limpiar» and typing are simulation requests, never edits. EDT only.
 */
final class TerminalWindowContent extends JPanel {

    private static final Logger LOGGER = LoggerFactory.getLogger(TerminalWindowContent.class);
    private static final String TEXT_EXTENSION = ".txt";

    private final OpenDocument document;
    private final String componentId;
    private final TerminalWindowServices services;
    private final Consumer<String> notices;
    private final JLabel bar = new JLabel();
    private final JLabel typingHint = new JLabel();
    private final JTabbedPane tabs = new JTabbedPane();
    private final TerminalScreenPanel screen;
    private final ByteMonitorPanel monitor = new ByteMonitorPanel();
    private final ExpectedOutputPanel expected;
    private final KeyboardCapture typingCapture;
    private final CapturedKeys typing;
    private Map<String, Object> resolved = Map.of();

    TerminalWindowContent(OpenDocument document, String componentId, TerminalWindowServices services,
                          TerminalWindowOwner owner) {
        super(new BorderLayout());
        this.document = Objects.requireNonNull(document, "document");
        this.componentId = Objects.requireNonNull(componentId, "componentId");
        this.services = Objects.requireNonNull(services, "services");
        this.notices = owner.notices();
        screen = new TerminalScreenPanel(services.renderer(), services.nanoClock(), owner.scheme());
        expected = new ExpectedOutputPanel(services.files(), services.edt());
        typingCapture = new KeyboardCapture(document.document());
        typing = new CapturedKeys(typingCapture, document, new WindowKeys(), notices);
        typing.useClipboard(services.clipboardText());
        add(bar(), BorderLayout.NORTH);
        JPanel screenTab = new JPanel(new BorderLayout());
        screenTab.add(screen, BorderLayout.CENTER);
        screenTab.add(typingHint, BorderLayout.SOUTH);
        tabs.addTab(Messages.text("terminal.window.screen"), screenTab);
        tabs.addTab(Messages.text("terminal.window.monitor"), monitor);
        tabs.addTab(Messages.text("terminal.window.expected"), expected);
        add(tabs, BorderLayout.CENTER);
        screen.typingTarget().addKeyListener(new TypingKeys());
        parametersChanged();
    }

    /** A new frame of the Terminal. */
    void update(TerminalUpdate update) {
        screen.show(update.frame(), resolved);
        monitor.accept(update.newBytes(), update.startedOver(), update.lostBytes());
        expected.showTranscript(update.frame().transcript());
    }

    /** The part's parameters or name changed (or the circuit around it): bar, look and expected text follow. */
    void parametersChanged() {
        Optional<Component> component = component();
        if (component.isEmpty()) {
            return;
        }
        ComponentType type = document.registry().typeOf(component.get());
        resolved = type.resolveParameters(component.get().parameters());
        bar.setText(Messages.format("terminal.window.bar", title(), resolved.get(TerminalTypes.ROWS),
                resolved.get(TerminalTypes.COLUMNS),
                Messages.choiceName(TerminalTypes.MODE, String.valueOf(resolved.get(TerminalTypes.MODE)))));
        screen.parametersChanged(resolved);
        expected.parametersChanged(resolved, document.path());
        Optional<String> hint = typingHint();
        typingHint.setText(hint.orElse(""));
        typingHint.setVisible(hint.isPresent());
    }

    /** The window's title: the part's name, else its type's name. */
    String title() {
        return component().map(found -> found.label().isBlank()
                ? Messages.componentName(found.typeId(), found.parameters()) : found.label()).orElse(componentId);
    }

    /** How many history rows the window wants: all the part keeps. */
    int historyLines() {
        Object scrollback = resolved.get(TerminalTypes.SCROLLBACK);
        return scrollback instanceof Integer lines ? lines : 0;
    }

    /** «Copiar texto»: the history and the screen. */
    void copyText() {
        services.clipboard().copy(screen.allText());
    }

    /** «Guardar .txt»: the history and the screen, ending with a line feed, written off the EDT. */
    void saveText() {
        Optional<Path> chosen = services.saveChooser().chooseFile(this, title() + TEXT_EXTENSION);
        if (chosen.isEmpty()) {
            return;
        }
        String text = screen.allText() + "\n";
        Path file = chosen.get();
        services.files().execute(() -> {
            try {
                Files.writeString(file, text, StandardCharsets.UTF_8);
            } catch (IOException exception) {
                LOGGER.warn("Could not save the Terminal text to {}", file, exception);
                services.edt().execute(() -> notices.accept(Messages.format("terminal.window.saveFailed",
                        file.getFileName())));
            }
        });
    }

    /** «Limpiar»: screen and history empty, as CLR; a simulation request, not an edit. */
    void clear() {
        document.clearTerminal(new ElementId(componentId));
    }

    /** The bell's «Destello». */
    void flash() {
        screen.flash();
    }

    /** Once per frame. */
    void tick() {
        screen.tick();
    }

    /** The window closes: typing stops watching the document. */
    void dispose() {
        typingCapture.dispose();
    }

    String barText() {
        return bar.getText();
    }

    List<String> tabTitles() {
        List<String> titles = new ArrayList<>();
        for (int index = 0; index < tabs.getTabCount(); index++) {
            titles.add(tabs.getTitleAt(index));
        }
        return titles;
    }

    TerminalScreenPanel screen() {
        return screen;
    }

    ByteMonitorPanel monitor() {
        return monitor;
    }

    ExpectedOutputPanel expected() {
        return expected;
    }

    /** What the «Pantalla» tab says when no Teclado is linked to this Terminal; empty when one is. */
    Optional<String> typingHint() {
        return linkedKeyboard().isPresent() ? Optional.empty()
                : Optional.of(Messages.text("terminal.window.noKeyboard"));
    }

    private Optional<Component> component() {
        return document.document().component(new ElementId(componentId));
    }

    /**
     * Where typing on the screen goes: a UART 16550's own keyboard, else the first Teclado on the circuit whose
     * «Terminal enlazado» is this Terminal's name.
     */
    private Optional<Component> linkedKeyboard() {
        Optional<Component> uart = component().filter(found -> TerminalParts.isIntegratedUart(document, found));
        if (uart.isPresent()) {
            return uart;
        }
        String label = component().map(Component::label).orElse("");
        if (label.isBlank()) {
            return Optional.empty();
        }
        for (Component candidate : List.copyOf(document.document().components())) {
            if (candidate.typeId().equals(StandardComponentTypes.KEYBOARD) && label.equals(String.valueOf(
                    document.registry().typeOf(candidate).resolveParameters(candidate.parameters())
                            .get(KeyboardTypes.LINKED_TERMINAL)))) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private JPanel bar() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(bar, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING));
        buttons.add(button("terminal.window.copy", this::copyText));
        buttons.add(button("terminal.window.save", this::saveText));
        buttons.add(button("terminal.window.clear", this::clear));
        panel.add(buttons, BorderLayout.EAST);
        return panel;
    }

    private static JButton button(String key, Runnable action) {
        JButton button = new JButton(Messages.text(key));
        button.setName(key);
        button.setFocusable(false);
        button.addActionListener(event -> action.run());
        return button;
    }

    /** Keys on the screen go to the linked Teclado; without one they do nothing. */
    private final class TypingKeys extends KeyAdapter {

        @Override
        public void keyPressed(KeyEvent event) {
            if (aimAtLinkedKeyboard() && typing.keyPressed(event)) {
                event.consume();
            }
        }

        @Override
        public void keyTyped(KeyEvent event) {
            if (aimAtLinkedKeyboard() && typing.keyTyped(event)) {
                event.consume();
            }
        }

        private boolean aimAtLinkedKeyboard() {
            Optional<Component> keyboard = linkedKeyboard();
            if (keyboard.isEmpty()) {
                typingCapture.release();
                return false;
            }
            typingCapture.capture(keyboard.get().id().value());
            return true;
        }
    }

    /** The window has no menus or bindings of its own: only the paste shortcut is special. */
    private static final class WindowKeys implements CapturedKeys.Shortcuts {

        @Override
        public boolean isBound(KeyStroke stroke, java.awt.Component focused) {
            return false;
        }

        @Override
        public boolean isPaste(KeyStroke stroke) {
            return stroke.equals(KeyStroke.getKeyStroke(KeyEvent.VK_V, ShortcutCatalog.menuMask()));
        }
    }
}
