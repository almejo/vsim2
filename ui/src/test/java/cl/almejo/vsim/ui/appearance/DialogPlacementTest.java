package cl.almejo.vsim.ui.appearance;

import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DialogPlacementTest {
    private static final Rectangle SCREEN = new Rectangle(0, 0, 1920, 1080);
    private static final Rectangle OWNER = new Rectangle(200, 100, 1400, 900);
    private static final Dimension SIZE = new Dimension(1080, 720);

    @Test
    void withNothingSavedTheDialogIsCentredOnItsOwner() {
        Rectangle placed = DialogPlacement.place(Optional.empty(), OWNER, List.of(SCREEN), SIZE);
        assertThat(placed).isEqualTo(new Rectangle(360, 190, 1080, 720));
    }

    @Test
    void savedBoundsOnAScreenAreKept() {
        Rectangle saved = new Rectangle(50, 60, 900, 700);
        assertThat(DialogPlacement.place(Optional.of(saved), OWNER, List.of(SCREEN), SIZE)).isEqualTo(saved);
    }

    @Test
    void savedBoundsOnAScreenThatIsGoneAreCentredOnTheOwnerKeepingTheirSize() {
        Rectangle saved = new Rectangle(2500, 60, 900, 700);
        assertThat(DialogPlacement.place(Optional.of(saved), OWNER, List.of(SCREEN), SIZE))
                .isEqualTo(new Rectangle(450, 200, 900, 700));
    }

    @Test
    void aDialogLargerThanTheScreenIsShrunkToIt() {
        Rectangle saved = new Rectangle(3000, 0, 2500, 1500);
        assertThat(DialogPlacement.place(Optional.of(saved), OWNER, List.of(SCREEN), SIZE))
                .isEqualTo(new Rectangle(0, 0, 1920, 1080));
    }

    @Test
    void withoutScreensTheSavedBoundsAreUsedAsTheyAre() {
        Rectangle saved = new Rectangle(2500, 60, 900, 700);
        assertThat(DialogPlacement.place(Optional.of(saved), OWNER, List.of(), SIZE)).isEqualTo(saved);
    }
}
