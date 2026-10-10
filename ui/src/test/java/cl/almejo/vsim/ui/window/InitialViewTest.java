package cl.almejo.vsim.ui.window;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class InitialViewTest {

    private final AtomicInteger applied = new AtomicInteger();
    private InitialView initialView;

    private void create() {
        initialView = new InitialView(() -> {
            applied.incrementAndGet();
            initialView.viewChanged(); // the canvas reports its own change while the view is applied
        });
    }

    @Test
    void aNormalWindowAppliesItsViewOnceWhenItOpens() {
        create();
        initialView.opened(false);
        initialView.canvasResized(false);
        initialView.canvasResized(true);
        assertThat(applied).hasValue(1);
    }

    @Test
    void aMaximizedWindowAppliesItsViewAgainAfterTheMaximizedLayout() {
        create();
        initialView.opened(true);
        assertThat(applied).hasValue(1);
        initialView.canvasResized(true);
        assertThat(applied).hasValue(2);
        initialView.canvasResized(true);
        assertThat(applied).hasValue(2);
    }

    @Test
    void aViewTheUserChangedFirstIsNotOverwritten() {
        create();
        initialView.opened(true);
        initialView.viewChanged();
        initialView.canvasResized(true);
        assertThat(applied).hasValue(1);
    }

    @Test
    void aWindowNoLongerMaximizedAtItsFirstResizeKeepsItsView() {
        create();
        initialView.opened(true);
        initialView.canvasResized(false);
        initialView.canvasResized(true);
        assertThat(applied).hasValue(1);
    }
}
