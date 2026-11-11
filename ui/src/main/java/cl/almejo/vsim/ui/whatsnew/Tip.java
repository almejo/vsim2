package cl.almejo.vsim.ui.whatsnew;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One tip of the day, as the tips file lists it.
 *
 * @param id         stable name, remembered once the tip was seen
 * @param kind       how its picture is drawn
 * @param titleKey   its title (both bundles)
 * @param textKey    its text, with keys as {@code {…}} names ({@link NewsText})
 * @param pictures   resources under {@code /tips/}: one circuit, two pictures or one image ({@link TipKind#accepts})
 * @param pictureKey the key VSim draws big in the picture: a catalog id or a fixed key; empty for none
 * @param trial      what «Probarlo» does; empty: no «Probarlo»
 */
record Tip(String id, TipKind kind, String titleKey, String textKey, List<String> pictures,
           Optional<String> pictureKey, Optional<Trial> trial) {

    /**
     * «Probarlo»: open the tip's circuit in a new tab and, when {@code actionId} (a {@code ShortcutCatalog} id) is
     * there, run that action on it (a tip about a dialog opens the real dialog).
     */
    record Trial(Optional<String> actionId) {

        Trial {
            Objects.requireNonNull(actionId, "actionId");
        }
    }

    Tip {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(titleKey, "titleKey");
        Objects.requireNonNull(textKey, "textKey");
        pictures = List.copyOf(pictures);
        Objects.requireNonNull(pictureKey, "pictureKey");
        Objects.requireNonNull(trial, "trial");
    }

    /** The first circuit among the pictures: what «Probarlo» opens. */
    Optional<String> circuit() {
        return pictures.stream().filter(TipKind::isCircuit).findFirst();
    }
}
