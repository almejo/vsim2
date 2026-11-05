package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.DefinitionUpdateSafety;
import cl.almejo.vsim.core.library.InstancePaths;
import cl.almejo.vsim.core.library.LibraryVersions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.tabs.GroupColors;
import net.miginfocom.swing.MigLayout;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.UIManager;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The bar under the tab strip of a component tab (spec §5.1), tinted with its group's colour: the route of the
 * context as clickable crumbs, «Valores de» (every instance path that reaches the definition, plus «Sin valores»),
 * how many places use it, «Ver la pieza», the library button, and the explanations that have no other place (an
 * interface locked by nesting; a refused edit). It follows its place's changes and the library's; {@link #dispose()}
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

        /** Adds the bundle to the library (the palette's «Agregar a mi librería»). */
        void addToLibrary(DefinitionBundle bundle);
    }

    /** How long a refused edit's explanation stays in the bar. */
    private static final int REFUSAL_MILLISECONDS = 4000;
    private static final float TINT = 0.10f;

    private final OpenDocument place;
    private final ComponentLibrary library;
    private final Callbacks callbacks;
    private final Runnable refresher = this::refresh;
    private final JPanel crumbPanel = new JPanel(new MigLayout("insets 0, gapx 2"));
    private final JLabel valuesLabel = new JLabel(Messages.text("place.valuesOf"));
    private final JComboBox<String> valuesCombo = new JComboBox<>();
    private final JLabel useChip = new JLabel();
    private final JButton seePiece = new JButton(Messages.text("place.seePiece") + " ↗");
    private final JButton libraryButton = new JButton();
    private final JLabel lockedLabel = new JLabel();
    private final JLabel refusalLabel = new JLabel();
    private final Timer refusalTimer = new Timer(REFUSAL_MILLISECONDS, action -> clearRefusal());
    private final List<String> crumbTexts = new ArrayList<>();
    private final List<JButton> crumbButtons = new ArrayList<>();
    private List<InstancePaths.InstanceUse> uses = List.of();
    private Optional<DefinitionBundle> libraryBundle = Optional.empty();
    private boolean updating;
    private boolean libraryOffersAdd;

    public PlaceBar(OpenDocument place, ComponentLibrary library, Callbacks callbacks) {
        super(new MigLayout("insets 4 12 4 12, gapx 14, fillx", "[][][][][]push[][]"));
        this.place = place;
        this.library = library;
        this.callbacks = callbacks;
        refusalTimer.setRepeats(false);
        add(crumbPanel);
        add(valuesLabel, "split 2, gapx 14 4");
        add(valuesCombo);
        add(useChip);
        add(seePiece);
        add(libraryButton);
        add(lockedLabel, "newline, span");
        add(refusalLabel, "newline, span");
        valuesCombo.addActionListener(action -> {
            if (!updating && valuesCombo.getSelectedIndex() >= 0) {
                chooseValuesItem(valuesCombo.getSelectedIndex());
            }
        });
        seePiece.addActionListener(action -> callbacks.seePiece());
        libraryButton.addActionListener(action -> libraryAction());
        place.addChangeListener(refresher);
        library.addListener(refresher);
        refresh();
    }

    /** Takes the listeners and the timer away; the bar is not used afterwards. */
    public void dispose() {
        place.removeChangeListener(refresher);
        library.removeListener(refresher);
        refusalTimer.stop();
    }

    /** Shows {@code text} (an edit refused before anything changed) for four seconds, the place's explanation. */
    public void showRefusal(String text) {
        refusalLabel.setText(text);
        refusalLabel.setVisible(true);
        refusalTimer.restart();
        revalidate();
        repaint();
    }

    private void clearRefusal() {
        refusalLabel.setText("");
        refusalLabel.setVisible(false);
        revalidate();
        repaint();
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
            crumbPanel.setBackground(getBackground());
        }
        boolean dark = GroupColors.chromeIsDark();
        useChip.setForeground(uses.size() > 1 ? new Color(dark ? 0xF5C16C : 0x8A5300) : mutedColor());
    }

    private static Color mutedColor() {
        Color muted = UIManager.getColor("Label.disabledForeground");
        return muted == null ? Color.GRAY : muted;
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
        refreshLocked(parent, definitionId);
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
        Optional<List<Component>> chain = place.contextPath().flatMap(path -> InstanceNames.chain(path, document));
        List<String> steps = chain.map(components -> components.stream()
                .map(component -> InstanceNames.step(component, document)).toList()).orElse(List.of());
        if (steps.isEmpty()) {
            steps = List.of(place.definitionName());
        }
        for (String step : steps) {
            crumbTexts.add(step);
            addCrumb("⬚ " + step, crumbTexts.size() - 1);
        }
        JButton current = crumbButtons.getLast();
        current.setEnabled(false);
        current.setFont(current.getFont().deriveFont(java.awt.Font.BOLD));
    }

    private void addCrumb(String text, int index) {
        if (index > 0) {
            crumbPanel.add(new JLabel("›"));
        }
        JButton crumb = new JButton(text);
        crumb.putClientProperty("JButton.buttonType", "borderless");
        crumb.addActionListener(action -> clickCrumb(index));
        crumbButtons.add(crumb);
        crumbPanel.add(crumb);
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

    /** «c3 en Doble inversor (c2)»; a use placed on the circuit itself: «c1 en {circuit name}». */
    private static String valuesItem(InstancePaths.InstanceUse use, OpenDocument parent, CircuitDocument document) {
        List<Component> chain = InstanceNames.chain(use.path(), document).orElse(List.of());
        if (chain.isEmpty()) {
            return use.path();
        }
        String reference = InstanceNames.reference(chain.getLast());
        if (chain.size() == 1) {
            return Messages.format("place.valuesItemTop", reference, parent.displayName());
        }
        Component container = chain.get(chain.size() - 2);
        return Messages.format("place.valuesItem", reference, InstanceNames.typeName(container, document),
                InstanceNames.reference(container));
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
    }

    private void refreshLocked(OpenDocument parent, String definitionId) {
        if (place.interfaceLocked()) {
            lockedLabel.setText(Messages.format("inside.interfaceLocked",
                    String.join(", ", DefinitionUpdateSafety.containingNames(parent.document(), definitionId))));
            lockedLabel.setVisible(true);
        } else {
            lockedLabel.setText("");
            lockedLabel.setVisible(false);
        }
        refusalLabel.setVisible(!refusalLabel.getText().isEmpty());
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

    Optional<String> lockedText() {
        return lockedLabel.isVisible() ? Optional.of(lockedLabel.getText()) : Optional.empty();
    }

    Optional<String> refusalText() {
        return refusalLabel.isVisible() ? Optional.of(refusalLabel.getText()) : Optional.empty();
    }
}
