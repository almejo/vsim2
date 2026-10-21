package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentVersion;
import cl.almejo.vsim.automation.read.ComponentNames;
import cl.almejo.vsim.automation.read.ReadSource;
import cl.almejo.vsim.automation.read.RegionPicture;
import cl.almejo.vsim.automation.read.WindowPicture;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.Function;

/**
 * The read side of {@link EditorAutomationHost} (IA.1 Task 2), EDT only: what the readers see of an open circuit and
 * the pictures, painted here and encoded by the host off the EDT. Never changes the circuit, the selection, the focus
 * or the view.
 */
final class ReadHostSupport {

    /** The palette's names in both languages; a user component goes by its definition's name. */
    static final ComponentNames NAMES = new ComponentNames() {
        @Override
        public String spanish(ComponentType type) {
            return nameIn(type, Messages.bundle(Messages.SPANISH));
        }

        @Override
        public String english(ComponentType type) {
            return nameIn(type, Messages.bundle(Locale.ENGLISH));
        }
    };

    private final Function<OpenDocument, List<ShownWindow>> windowsOf;

    /** @param windowsOf the windows showing a circuit, the active one first; called on the EDT */
    ReadHostSupport(Function<OpenDocument, List<ShownWindow>> windowsOf) {
        this.windowsOf = Objects.requireNonNull(windowsOf, "windowsOf");
    }

    /** @param library the user's component library, which the AI may also place from */
    ReadSource source(DocumentId id, OpenDocument document, List<DefinitionBundle> library) {
        return new ReadSource(id, version(document), document.displayName(), document.document(),
                document.registry(), document.netlist(), document.netlistToken(), document.latestSnapshot(), NAMES,
                library, LabelTextMeasure.INSTANCE);
    }

    /** The region picture painted, still to encode; the export one in the scheme of the circuit's first window. */
    AiPictures.Painted paintRegion(OpenDocument document, Optional<Bounds> region, boolean asOnScreen) {
        Optional<ShownWindow> window = windowsOf.apply(document).stream().findFirst();
        if (asOnScreen) {
            ShownWindow shown = window.orElseThrow(() -> new AutomationException(AutomationError.INVALID_REFERENCE,
                    "The circuit has no window on screen; ask for the export picture instead", Map.of()));
            return AiPictures.onScreenRegion(shown.view().canvas(), region);
        }
        ColorScheme scheme = window.map(shown -> shown.view().services().appearance().scheme())
                .orElse(ColorScheme.LIGHT);
        return AiPictures.exportRegion(document, region, scheme);
    }

    AiPictures.Painted paintWindow(OpenDocument document) {
        ShownWindow window = windowsOf.apply(document).stream().findFirst()
                .orElseThrow(() -> new AutomationException(AutomationError.INVALID_REFERENCE,
                        "The circuit has no window on screen", Map.of()));
        return AiPictures.window(window);
    }

    static RegionPicture regionPicture(DocumentId id, DocumentVersion version, boolean asOnScreen,
                                       AiPictures.Painted painted) {
        return new RegionPicture(id, version, painted.region(), asOnScreen, painted.image().getWidth(),
                painted.image().getHeight(), AiPictures.png(painted.image()));
    }

    static WindowPicture windowPicture(DocumentId id, DocumentVersion version, AiPictures.Painted painted) {
        return new WindowPicture(id, version, painted.title(), painted.dialogs(), painted.image().getWidth(),
                painted.image().getHeight(), AiPictures.png(painted.image()));
    }

    static DocumentVersion version(OpenDocument document) {
        return new DocumentVersion(document.changeCount());
    }

    private static String nameIn(ComponentType type, ResourceBundle bundle) {
        return SubcircuitTypes.definitionOf(type).map(ComponentDefinition::name).orElseGet(() -> {
            String key = "component." + type.id();
            return bundle.containsKey(key) ? bundle.getString(key) : type.id();
        });
    }
}
