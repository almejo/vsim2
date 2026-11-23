package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.theme.ReadableColors;
import cl.almejo.vsim.core.command.InsideEditRefusedException;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.InstancePaths;
import cl.almejo.vsim.core.library.LibraryVersions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.tabs.GroupColors;
import com.formdev.flatlaf.util.UIScale;
import net.miginfocom.swing.MigLayout;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Font;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The bar under the tab strip of a component tab (spec §5.1), tinted with its group's colour: the route of the
 * context as clickable crumbs, «Valores de» (every instance path that reaches the definition, plus «Sin valores»),
 * how many places use it, «Ver la pieza», a machine's «Probar…», the
 * library button, and the explanation of an edit refused inside, which
 * has no other place. One row that never wraps nor holds the side panels back: what follows the route leaves from the
 * right into a «»» menu, in order, as the toolbar's ({@link OverflowToolBarLayout}); the route is cut last, with «…».
 * It follows its place's changes and the library's; {@link #dispose()}
 * takes both listeners away. Colours are read when it paints or its look changes, never kept. EDT only.
 */
public final class PlaceBar extends JPanel {

    /** What the bar asks of the window and the library (the window and the palette implement it). */
    public interface Callbacks {
        /** Activates the document's main tab. */
        void showMain();

        /** Enters {@code definitionId} of the document with {@code contextPath} as the values to show. */
        void enter(String definitionId, Optional<String> contextPath);

        /** Activates or opens the place that contains this place's context instance, selects it and travels to it. */
        void seePiece();

        /** Writes the bundle over the library's entry of the same id. */
        void saveToLibrary(DefinitionBundle bundle);

        /** Adds the bundle to the library (the palette's «Agregar a Mis componentes»). */
        void addToLibrary(DefinitionBundle bundle);
    }

    /** How long a refused edit's explanation stays in the bar. */
    private static final int REFUSAL_MILLISECONDS = 4000;
    private static final float TINT = 0.10f;

    /** The name of the line that explains a refused edit (what tests look for). */
    public static final String REFUSAL_NAME = "placeBar.refusal";

    private final OpenDocument place;
    private final ComponentLibrary library;
    private final Callbacks callbacks;
    private final Runnable refresher = this::refresh;
    /** The name of the «»» that opens what does not fit the bar (what tests look for). */
    public static final String OVERFLOW_NAME = "placeBar.overflow";
    /** The space between the bar's controls, at 100 %. */
    private static final int GAP = 14;

    private final JPanel crumbPanel = new JPanel(new MigLayout("insets 0, gapx 2"));
    /** The chevron of the button that opens what does not fit the bar, as the toolbar's. */
    private final JButton overflowButton = new JButton("»");
    private final OverflowToolBarLayout actionsLayout = new OverflowToolBarLayout(overflowButton,
            () -> UIScale.scale(GAP));
    /** Everything after the route, one row; what does not fit goes behind {@link #overflowButton}, in order. */
    private final JPanel actions = new JPanel(actionsLayout) {
        @Override
        public Dimension getMinimumSize() {
            Insets insets = getInsets();
            Dimension button = overflowButton.getPreferredSize();
            return new Dimension(button.width + insets.left + insets.right,
                    Math.max(button.height, getPreferredSize().height));
        }
    };
    /** The current place's name: not a control (it goes nowhere), so it reads as plain bold text. */
    private final JLabel currentCrumb = new CutLabel();
    private final JLabel valuesLabel = new JLabel(Messages.text("place.valuesOf"));
    private final JComboBox<String> valuesCombo = new JComboBox<>();
    private final JLabel useChip = new JLabel();
    private final JButton seePiece = new JButton(Messages.text("place.seePiece") + " ↗");
    private final JButton libraryButton = new JButton();
    /** «Probar…»: a machine's tab shows it ({@link #showMachineActions}). */
    private final JButton machineTestButton = new JButton(Messages.text("machine.palette.test"));
    /** Why the library button is disabled now (a machine's test mode); empty while it works. */
    private Optional<String> libraryHold = Optional.empty();
    private final JLabel refusalLabel = new JLabel();
    private final Timer refusalTimer = new Timer(REFUSAL_MILLISECONDS, action -> clearRefusal());
    private final List<String> crumbTexts = new ArrayList<>();
    private final List<JButton> crumbButtons = new ArrayList<>();
    private List<InstancePaths.InstanceUse> uses = List.of();
    private Optional<DefinitionBundle> libraryBundle = Optional.empty();
    private boolean updating;
    private boolean libraryOffersAdd;
    private final Runnable removeRefusalListener;

    public PlaceBar(OpenDocument place, ComponentLibrary library, Callbacks callbacks) {
        // the route shrinks last: what follows it goes behind «»» first (fix round 4)
        super(new MigLayout("insets 4 12 4 12, gapx 14, fillx, hidemode 3",
                "[shrinkprio 50][grow, fill, shrinkprio 200]"));
        this.place = place;
        this.library = library;
        this.callbacks = callbacks;
        refusalTimer.setRepeats(false);
        refusalLabel.setName(REFUSAL_NAME);
        refusalLabel.setVisible(false);
        crumbPanel.setOpaque(false);
        actions.setOpaque(false);
        overflowButton.setName(OVERFLOW_NAME);
        overflowButton.setToolTipText(Messages.text("place.more"));
        overflowButton.getAccessibleContext().setAccessibleName(Messages.text("place.more"));
        overflowButton.putClientProperty("JButton.buttonType", "toolBarButton");
        overflowButton.addActionListener(action -> actionsLayout.showMenu());
        valuesLabel.setLabelFor(valuesCombo);
        useChip.putClientProperty(ToolbarOverflowMenu.SHOWN_IN_MENU, true);
        add(crumbPanel, "wmin 0");
        add(actions);
        actions.add(valuesLabel);
        actions.add(valuesCombo);
        actions.add(useChip);
        actions.add(seePiece);
        actions.add(machineTestButton);
        actions.add(libraryButton);
        actions.add(overflowButton); // always the last child
        machineTestButton.setVisible(false);
        machineTestButton.setToolTipText(Messages.text("machine.palette.testHint"));
        add(refusalLabel, "newline, span, wmin 0");
        valuesCombo.addActionListener(action -> {
            if (!updating && valuesCombo.getSelectedIndex() >= 0) {
                chooseValuesItem(valuesCombo.getSelectedIndex());
            }
        });
        seePiece.addActionListener(action -> callbacks.seePiece());
        libraryButton.addActionListener(action -> libraryAction());
        place.addChangeListener(refresher);
        library.addListener(refresher);
        // an edit refused inside is explained here; the status line explains it too (EditorView)
        removeRefusalListener = place.addRefusalListener(refusal -> showRefusal(explanation(refusal)));
        refresh();
    }

    /** The sentence that explains {@code refusal}, the same in the place bar and the status line. */
    public static String explanation(InsideEditRefusedException refusal) {
        return Messages.format(refusal.messageKey(), refusal.arguments().toArray());
    }

    /** Takes the listeners and the timer away; the bar is not used afterwards. */
    public void dispose() {
        place.removeChangeListener(refresher);
        library.removeListener(refresher);
        removeRefusalListener.run();
        refusalTimer.stop();
    }

    /**
     * Shows {@code text} (an edit refused before anything changed) for four seconds, the place's explanation, stressed.
     */
    public void showRefusal(String text) {
        refusalLabel.setText(text);
        updateExplanationLines();
        refusalTimer.restart();
        revalidate();
        repaint();
    }

    private void clearRefusal() {
        refusalLabel.setText("");
        updateExplanationLines();
        revalidate();
        repaint();
    }

    private void updateExplanationLines() {
        refusalLabel.setVisible(!refusalLabel.getText().isEmpty());
    }

    @Override
    public void updateUI() {
        super.updateUI();
        applyColors();
    }

    /** The group's colour at 10 % over the chrome's background, the amber chip; read now, on every theme change. */
    private void applyColors() {
        if (place == null) {
            return; // called by the superclass constructor
        }
        Color panel = UIManager.getColor("Panel.background");
        Color group = GroupColors.colorOf(place.parent().orElse(place));
        if (panel != null) {
            setBackground(mix(panel, group, TINT));
        }
        // checked against the tinted bar itself, not the panel: the grey of «Se usa en 1 lugar» was 3.85:1 in dark;
        // aiming at the comfortable contrast, as a colour fitted to exactly 4.5:1 reads under it once anti-aliased
        Color bar = getBackground();
        useChip.setForeground(ReadableColors.readableOn(uses.size() > 1 ? ReadableColors.warningText()
                : ReadableColors.mutedText(), bar, ReadableColors.COMFORTABLE_TEXT_CONTRAST));
        refusalLabel.setForeground(ReadableColors.readableOn(ReadableColors.warningText(), bar,
                ReadableColors.COMFORTABLE_TEXT_CONTRAST));
        refusalLabel.setFont(useChip.getFont().deriveFont(Font.BOLD));
        Color text = UIManager.getColor("Label.foreground");
        if (text != null) {
            currentCrumb.setForeground(ReadableColors.readableOn(text, bar, ReadableColors.COMFORTABLE_TEXT_CONTRAST));
        }
    }

    private static Color mix(Color base, Color tint, float amount) {
        return new Color(Math.round(base.getRed() * (1 - amount) + tint.getRed() * amount),
                Math.round(base.getGreen() * (1 - amount) + tint.getGreen() * amount),
                Math.round(base.getBlue() * (1 - amount) + tint.getBlue() * amount));
    }

    // --- what the bar shows, re-read from the place and the library ---

    private void refresh() {
        OpenDocument parent = place.parent().orElseThrow();
        String definitionId = place.definitionId().orElseThrow();
        uses = place.instancePaths(definitionId);
        refreshCrumbs(parent);
        refreshValues(parent);
        refreshUses();
        seePiece.setEnabled(place.contextPath().isPresent());
        refreshLibrary(parent, definitionId);
        applyColors();
        revalidate();
        repaint();
    }

    private void refreshCrumbs(OpenDocument parent) {
        crumbTexts.clear();
        crumbButtons.clear();
        crumbPanel.removeAll();
        crumbTexts.add(parent.displayName());
        addCrumb("▤ " + parent.displayName(), 0);
        CircuitDocument document = parent.document();
        List<String> steps = place.contextPath().map(path -> InstanceNames.route(path, document))
                .orElse(List.of());
        if (steps.isEmpty()) {
            steps = List.of(place.definitionName());
        }
        for (int index = 0; index < steps.size() - 1; index++) {
            crumbTexts.add(steps.get(index));
            addCrumb("⬚ " + steps.get(index), crumbTexts.size() - 1);
        }
        crumbTexts.add(steps.getLast());
        crumbPanel.add(new JLabel("›"));
        currentCrumb.setText("⬚ " + steps.getLast());
        currentCrumb.setFont(UIManager.getFont("Label.font").deriveFont(Font.BOLD));
        // the earlier crumbs are cut first, the current place last; «›» never
        StringBuilder columns = new StringBuilder();
        for (int index = 0; index < crumbTexts.size(); index++) {
            columns.append(index > 0 ? "[shrinkprio 0]" : "")
                    .append(index < crumbTexts.size() - 1 ? "[shrinkprio 200]" : "[shrinkprio 100]");
        }
        ((MigLayout) crumbPanel.getLayout()).setColumnConstraints(columns.toString());
        crumbPanel.add(currentCrumb, "wmin 0, gapleft 6lp");
    }

    private void addCrumb(String text, int index) {
        if (index > 0) {
            crumbPanel.add(new JLabel("›"));
        }
        JButton crumb = new CutButton(text);
        crumb.putClientProperty("JButton.buttonType", "borderless");
        crumb.addActionListener(action -> clickCrumb(index));
        crumbButtons.add(crumb);
        crumbPanel.add(crumb, "wmin 0");
    }

    /** A label whose tooltip is its whole text while the bar cuts it with «…». */
    private static final class CutLabel extends JLabel {
        CutLabel() {
            setToolTipText(""); // registers it: the text comes from getToolTipText()
        }

        @Override
        public String getToolTipText() {
            return getWidth() < getPreferredSize().width ? getText() : null;
        }
    }

    /** A crumb whose tooltip is its whole text while the bar cuts it with «…». */
    private static final class CutButton extends JButton {
        CutButton(String text) {
            super(text);
            setToolTipText("");
        }

        @Override
        public String getToolTipText() {
            return getWidth() < getPreferredSize().width ? getText() : null;
        }
    }

    private void refreshValues(OpenDocument parent) {
        CircuitDocument document = parent.document();
        List<String> items = new ArrayList<>();
        for (InstancePaths.InstanceUse use : uses) {
            items.add(valuesItem(use, parent, document));
        }
        items.add(Messages.text("place.noValues"));
        updating = true;
        try {
            valuesCombo.removeAllItems();
            items.forEach(valuesCombo::addItem);
            int selected = items.size() - 1;
            for (int index = 0; index < uses.size(); index++) {
                if (place.contextPath().filter(uses.get(index).path()::equals).isPresent()) {
                    selected = index;
                }
            }
            valuesCombo.setSelectedIndex(selected);
        } finally {
            updating = false;
        }
    }

    /**
     * «Inversor 2 en Doble inversor», the instance and the piece that holds it named as the tree names them; a use
     * placed on the circuit itself: «Inversor en {circuit name}».
     */
    private static String valuesItem(InstancePaths.InstanceUse use, OpenDocument parent, CircuitDocument document) {
        List<String> route = InstanceNames.route(use.path(), document);
        if (route.isEmpty()) {
            return use.path();
        }
        String container = route.size() == 1 ? parent.displayName() : route.get(route.size() - 2);
        return Messages.format("place.valuesItem", route.getLast(), container);
    }

    private void refreshUses() {
        int count = uses.size();
        useChip.setText(switch (count) {
            case 0 -> Messages.text("place.unused");
            case 1 -> Messages.text("place.usedInOne");
            default -> Messages.format("place.usedIn", count);
        });
    }

    private void refreshLibrary(OpenDocument parent, String definitionId) {
        CircuitDocument document = parent.document();
        libraryBundle = Optional.empty();
        libraryOffersAdd = false;
        Optional<ComponentDefinition> definition = document.definition(definitionId);
        if (definition.isPresent()) {
            boolean inLibrary = library.find(definitionId).isPresent();
            boolean differs = !LibraryVersions.differences(List.of(definition.get()), library).isEmpty();
            if (!inLibrary || differs) {
                try {
                    libraryBundle = Optional.of(DefinitionBundle.of(definition.get(), document::definition));
                    libraryOffersAdd = !inLibrary;
                } catch (IllegalArgumentException exception) {
                    libraryBundle = Optional.empty(); // a definition whose dependencies are missing is not offered
                }
            }
        }
        libraryButton.setVisible(libraryBundle.isPresent());
        libraryButton.setText(Messages.text(libraryOffersAdd ? "place.addToLibrary" : "place.saveToLibrary"));
        libraryButton.setEnabled(libraryHold.isEmpty());
        libraryButton.setToolTipText(libraryHold.orElse(null));
    }

    /** The place bar shown over {@code view}'s canvas, when it has one (a component's tab). */
    public static Optional<PlaceBar> of(EditorView view) {
        return view.getClientProperty(PlaceBar.class) instanceof PlaceBar bar ? Optional.of(bar) : Optional.empty();
    }

    /** Puts this bar over {@code view}'s canvas for {@link #of}; the returned runnable takes it back. */
    public Runnable shownIn(EditorView view) {
        view.putClientProperty(PlaceBar.class, this);
        return () -> {
            if (view.getClientProperty(PlaceBar.class) == this) {
                view.putClientProperty(PlaceBar.class, null);
            }
        };
    }

    /**
     * Disables «Agregar a Mis componentes» / «Guardar en Mis componentes» with {@code reason} as its tooltip (a
     * machine's test mode), until the returned runnable runs.
     */
    public Runnable holdLibraryButton(String reason) {
        Optional<String> hold = Optional.of(reason);
        libraryHold = hold;
        libraryButton.setEnabled(false);
        libraryButton.setToolTipText(reason);
        return () -> {
            if (libraryHold == hold) {
                libraryHold = Optional.empty();
                libraryButton.setEnabled(true);
                libraryButton.setToolTipText(null);
            }
        };
    }

    /**
     * A machine's tab: shows «Probar…», which calls {@code startTest}, before the library button. The returned runnable
     * hides it again.
     */
    public Runnable showMachineActions(Runnable startTest) {
        ActionListener start = action -> startTest.run();
        machineTestButton.addActionListener(start);
        machineTestButton.setVisible(true);
        revalidate();
        repaint();
        return () -> {
            machineTestButton.removeActionListener(start);
            machineTestButton.setVisible(false);
            revalidate();
            repaint();
        };
    }

    /** «Probar…» is disabled with «Ya estás probando» while the machine's tab tests, enabled again when it ends. */
    public void machineTesting(boolean testing) {
        machineTestButton.setEnabled(!testing);
        machineTestButton.setToolTipText(Messages.text(testing ? "machine.test.alreadyTesting"
                : "machine.palette.testHint"));
    }

    /** Why the library button is disabled now; empty while it works (tests). */
    Optional<String> libraryButtonHold() {
        return libraryButton.isEnabled() ? Optional.empty() : Optional.ofNullable(libraryButton.getToolTipText());
    }

    // --- actions (also what the tests press) ---

    /** Chooses entry {@code index} of «Valores de»: an instance path, or the last, «Sin valores». */
    void chooseValuesItem(int index) {
        place.setContextPath(index < uses.size() ? Optional.of(uses.get(index).path()) : Optional.empty());
        refresh();
    }

    void clickCrumb(int index) {
        if (index == crumbTexts.size() - 1) {
            return; // the current place
        }
        if (index == 0) {
            callbacks.showMain();
            return;
        }
        Optional<List<Component>> chain = place.contextPath()
                .flatMap(path -> InstanceNames.chain(path, place.parent().orElseThrow().document()));
        if (chain.isEmpty() || index > chain.get().size()) {
            return;
        }
        Component step = chain.get().get(index - 1);
        Optional<String> definitionId = SubcircuitTypes.definitionIdOf(step.typeId());
        if (definitionId.isPresent()) {
            callbacks.enter(definitionId.get(), Optional.of(String.join(InstancePath.SEPARATOR,
                    chain.get().subList(0, index).stream().map(component -> component.id().value()).toList())));
        }
    }

    void clickSeePiece() {
        seePiece.doClick(0);
    }

    void clickLibraryButton() {
        libraryButton.doClick(0);
    }

    private void libraryAction() {
        libraryBundle.ifPresent(bundle -> {
            if (libraryOffersAdd) {
                callbacks.addToLibrary(bundle);
            } else {
                callbacks.saveToLibrary(bundle);
            }
        });
        refresh();
    }

    // --- what the tests read ---

    List<String> valuesItems() {
        List<String> items = new ArrayList<>();
        for (int index = 0; index < valuesCombo.getItemCount(); index++) {
            items.add(valuesCombo.getItemAt(index));
        }
        return items;
    }

    int selectedValuesItem() {
        return valuesCombo.getSelectedIndex();
    }

    String useChipText() {
        return useChip.getText();
    }

    boolean useChipIsWarning() {
        return uses.size() > 1;
    }

    List<String> crumbs() {
        return List.copyOf(crumbTexts);
    }

    int currentCrumb() {
        return crumbTexts.size() - 1;
    }

    Optional<String> libraryButtonText() {
        return libraryButton.isVisible() ? Optional.of(libraryButton.getText()) : Optional.empty();
    }

    Optional<String> refusalText() {
        return refusalLabel.isVisible() ? Optional.of(refusalLabel.getText()) : Optional.empty();
    }

    /** Whether the «»» shows (something did not fit). */
    boolean overflowShown() {
        return overflowButton.isVisible() && overflowButton.getWidth() > 0;
    }

    /** The items the «»» opens now, in order (a submenu for «Valores de»). */
    List<JMenuItem> overflowItems() {
        List<JMenuItem> items = new ArrayList<>();
        for (java.awt.Component child : actionsLayout.overflowMenu().getComponents()) {
            if (child instanceof JMenuItem item) {
                items.add(item);
            }
        }
        return items;
    }

    List<String> overflowTexts() {
        return overflowItems().stream().map(JMenuItem::getText).toList();
    }

    /** The texts of the controls after the route that are on the bar now (not behind «»»). */
    List<String> shownActionTexts() {
        List<String> texts = new ArrayList<>();
        for (java.awt.Component component : actions.getComponents()) {
            if (component != overflowButton && component.isVisible() && component.getWidth() > 0
                    && component.getX() + component.getWidth() <= actions.getWidth()) {
                if (component instanceof AbstractButton button) {
                    texts.add(button.getText());
                } else if (component instanceof JLabel label) {
                    texts.add(label.getText());
                }
            }
        }
        return texts;
    }

    /** Whether the route is on the bar, even cut. */
    boolean crumbsShown() {
        return crumbPanel.getWidth() > 0 && currentCrumb.getWidth() > 0;
    }

    Color useChipForeground() {
        return useChip.getForeground();
    }

    java.awt.Component currentCrumbComponent() {
        return currentCrumb;
    }
}
