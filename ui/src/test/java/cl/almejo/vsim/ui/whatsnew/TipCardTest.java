package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The card takes the size of what it shows, also when ‹ › go back to a shorter tip. */
class TipCardTest {

    private static final String SHORT_TITLE = "Buscar";
    private static final String LONG_TITLE = "Doble clic: un tramo; triple: la red, y algo más para dos líneas";

    private static int heightShowing(TipCard card, String title) {
        card.show(new TipCard.Content(title, new FlowText(List.of(new FlowText.Words("Un texto corto."))),
                TipPicture.Content.EMPTY, true, true));
        UiTestSupport.layOut(card);
        return card.getHeight();
    }

    @Test
    void aLongTitleWrapsAndTheCardShrinksBackForAShortOne() {
        Messages.initialize(Messages.SPANISH);
        TipCard card = new TipCard(PainterRegistry.standard(), () -> ColorScheme.LIGHT,
                new TipCard.Handlers(() -> { }, () -> { }, () -> { }, () -> { }, show -> { }));

        int short1 = heightShowing(card, SHORT_TITLE);
        int longer = heightShowing(card, LONG_TITLE);
        int short2 = heightShowing(card, SHORT_TITLE);

        assertThat(longer).as("a title of two lines makes the card taller").isGreaterThan(short1);
        assertThat(short2).as("back to a short title").isEqualTo(short1);
    }
}
