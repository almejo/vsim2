package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.simulation.DriverRef;
import cl.almejo.vsim.core.simulation.ShortCircuit;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.theme.FlatLafInstaller;
import cl.almejo.vsim.ui.theme.ReadableColors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JLabel;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class StatusBarTest {

    private final StatusBar statusBar = new StatusBar();

    @BeforeEach
    void spanish() {
        Messages.initialize(Messages.SPANISH);
    }

    private static SimulationSnapshot snapshot(boolean running, int shorts, Optional<String> failure) {
        List<ShortCircuit> problems = java.util.stream.IntStream.range(0, shorts)
                .mapToObj(index -> new ShortCircuit("n" + index, List.of(new DriverRef("c1", 0, Value.ONE)), 0))
                .toList();
        return new SimulationSnapshot(1, 1, 1234, running, Map.of(), Map.of(), List.copyOf(problems), failure);
    }

    private static ZoomControl installZoomControl(StatusBar bar) {
        Action nothing = new AbstractAction("—") {
            @Override
            public void actionPerformed(ActionEvent event) {
            }
        };
        ZoomControl control = new ZoomControl(new ZoomControl.Target() {
            @Override public double zoom() { return 1.0; }
            @Override public void zoomAroundCenter(double zoom) { }
            @Override public void zoomTo(double zoom) { }
            @Override public void returnFocus() { }
        }, new ZoomControl.Actions(nothing, nothing, nothing, nothing, nothing));
        bar.setZoomControl(control);
        return control;
    }

    @Test
    void showsStateTimeAndZoom() {
        installZoomControl(statusBar);
        statusBar.showSnapshot(snapshot(true, 0, Optional.empty()));
        statusBar.showZoom(1.25);
        assertThat(statusBar.text()).isEqualTo("▶ corriendo · t = 1.234 · 125 %");
        statusBar.showSnapshot(snapshot(false, 0, Optional.empty()));
        assertThat(statusBar.text()).startsWith("⏸ en pausa");
    }

    @Test
    void showsShortCircuitsOnlyWhenThereAreSome() {
        statusBar.showZoom(1);
        statusBar.showSnapshot(snapshot(true, 2, Optional.empty()));
        assertThat(statusBar.text()).doesNotContain("⚠"); // the Problems controller feeds the count
        statusBar.showShortCircuits(2);
        assertThat(statusBar.text()).contains("⚠ 2 cortocircuitos");
        statusBar.showShortCircuits(0);
        assertThat(statusBar.text()).doesNotContain("⚠");
    }

    @Test
    void showsTheSimulationFailure() {
        statusBar.showZoom(1);
        statusBar.showSnapshot(snapshot(false, 0, Optional.of("IllegalStateException: boom")));
        assertThat(statusBar.text()).contains("Simulación detenida: IllegalStateException: boom");
    }

    @Test
    void showsHowManyElementsAreSelectedOnlyWhenThereAreSome() {
        statusBar.showZoom(1);
        statusBar.showSelection(0);
        assertThat(statusBar.text()).doesNotContain("seleccionado");
        statusBar.showSelection(1);
        assertThat(statusBar.text()).isEqualTo("1 seleccionado");
        statusBar.showSelection(3);
        assertThat(statusBar.text()).endsWith("3 seleccionados");
        statusBar.showSelection(0);
        assertThat(statusBar.text()).doesNotContain("seleccionado");
    }

    @Test
    void clickingTheShortCircuitWarningRunsTheCallback() {
        AtomicInteger clicks = new AtomicInteger();
        statusBar.onShortCircuitsClicked(clicks::incrementAndGet);
        statusBar.showShortCircuits(1);
        JLabel warning = statusBar.shortCircuitsLabel();
        MouseEvent click = new MouseEvent(warning, MouseEvent.MOUSE_CLICKED, 0, 0, 2, 2, 1, false, MouseEvent.BUTTON1);
        for (MouseListener listener : warning.getMouseListeners()) {
            listener.mouseClicked(click);
        }
        assertThat(clicks).hasValue(1);
        assertThat(warning.getCursor().getType()).isEqualTo(Cursor.HAND_CURSOR);
        assertThat(warning.getToolTipText()).isEqualTo("Clic para ir al siguiente problema (F8)");
    }

    @Test
    void showsWidthMismatchesWithOrWithoutShortCircuits() {
        statusBar.showZoom(1);
        statusBar.showProblems(0, 1);
        assertThat(statusBar.text()).contains("⚠ 1 ancho incompatible");
        statusBar.showProblems(2, 3);
        assertThat(statusBar.text()).contains("⚠ 2 cortocircuitos · 3 anchos incompatibles");
        statusBar.showProblems(1, 0);
        assertThat(statusBar.text()).contains("⚠ 1 cortocircuito").doesNotContain("ancho");
        statusBar.showProblems(0, 0);
        assertThat(statusBar.text()).doesNotContain("⚠");
    }

    @Test
    void countsTunnelErrorsAfterTheOtherProblems() {
        statusBar.showProblems(0, 0, 2);
        assertThat(statusBar.text()).contains("⚠ 2 errores de túneles");
        statusBar.showProblems(1, 0, 1);
        assertThat(statusBar.text()).contains("⚠ 1 cortocircuito · 1 error de túnel");
        statusBar.showProblems(0, 0, 0);
        assertThat(statusBar.text()).doesNotContain("⚠");
    }

    @Test
    void theWarningTooltipStaysTheGenericOneWhenOnlyMismatchesAreCounted() {
        statusBar.onShortCircuitsClicked(() -> { });
        JLabel warning = statusBar.shortCircuitsLabel();
        statusBar.showProblems(1, 0);
        assertThat(warning.getToolTipText()).isEqualTo("Clic para ir al siguiente problema (F8)");
        statusBar.showProblems(0, 1);
        assertThat(warning.getToolTipText()).isEqualTo("Clic para ir al siguiente problema (F8)");
        statusBar.showProblems(2, 1);
        assertThat(warning.getToolTipText()).isEqualTo("Clic para ir al siguiente problema (F8)");
        statusBar.showProblems(1, 0);
        assertThat(warning.getToolTipText()).isEqualTo("Clic para ir al siguiente problema (F8)");
    }

    @Test
    void theZoomControlSitsAtTheRightEndAfterTheIndicatorsAndReportsThePercentage() {
        ZoomControl control = installZoomControl(statusBar);
        JLabel indicator = new JLabel("IA: apagada");
        JLabel activity = new JLabel("IA trabajando");
        statusBar.addIndicator(indicator);
        statusBar.addActivity(activity);
        statusBar.showZoom(1.25);

        assertThat(statusBar.getComponentZOrder(control)).isEqualTo(statusBar.getComponentCount() - 1);
        assertThat(statusBar.getComponentZOrder(indicator)).isLessThan(statusBar.getComponentZOrder(control));
        assertThat(statusBar.text()).isEqualTo("125 %");
        for (java.awt.Component child : statusBar.getComponents()) {
            if (child instanceof JLabel label) {
                assertThat(label.getText()).as("the old «zoom N %» label is gone").doesNotStartWith("zoom");
            }
        }
    }

    @Test
    void aNarrowBarShrinksTheSliderFirstAndKeepsThePercentageAndTheIndicator() {
        ZoomControl control = installZoomControl(statusBar);
        JLabel indicator = new JLabel("IA: escuchando");
        statusBar.addIndicator(indicator);
        statusBar.showSnapshot(snapshot(true, 0, Optional.empty()));
        statusBar.showShortCircuits(2);
        statusBar.showZoom(1.5);
        int roomy = statusBar.getPreferredSize().width;
        statusBar.showNotice("Guardado en /home/alumno/circuitos/" + "muy-largo/".repeat(40) + "sumador.vsim");
        statusBar.setSize(roomy - 40, 28);

        statusBar.doLayout();
        control.doLayout();

        int sliderWidth = control.slider().getWidth();
        assertThat(sliderWidth).as("the slider gave way").isLessThan(control.slider().getPreferredSize().width)
                .isGreaterThanOrEqualTo(ZoomControl.SLIDER_MINIMUM_WIDTH);
        assertThat(control.field().getWidth()).as("the percentage keeps its width")
                .isEqualTo(control.field().getPreferredSize().width);
        assertThat(control.getX() + control.getWidth()).isLessThanOrEqualTo(statusBar.getWidth());
        assertThat(indicator.getWidth()).isEqualTo(indicator.getPreferredSize().width);
        assertThat(indicator.getX() + indicator.getWidth()).isLessThanOrEqualTo(control.getX());
        assertThat(statusBar.shortCircuitsLabel().getWidth())
                .isEqualTo(statusBar.shortCircuitsLabel().getPreferredSize().width);
    }

    @Test
    void theTimeSaysWhatItCounts() {
        statusBar.showSnapshot(snapshot(true, 0, Optional.empty()));
        assertThat(statusBar.timeLabel().getToolTipText()).isEqualTo("Tiempo de simulación (unidades)");
    }

    @Test
    void clickingTheRunStatePausesWhileRunningAndStartsWhilePaused() {
        List<String> clicks = new java.util.ArrayList<>();
        statusBar.showSnapshot(snapshot(true, 0, Optional.empty()));
        statusBar.onRunStateClicked(() -> clicks.add("pause"), () -> clicks.add("start"));
        JLabel runState = statusBar.runStateLabel();
        assertThat(runState.getCursor().getType()).isEqualTo(Cursor.HAND_CURSOR);
        assertThat(runState.getToolTipText()).isEqualTo("Clic: pausar");
        MouseEvent click = new MouseEvent(runState, MouseEvent.MOUSE_CLICKED, 0, 0, 2, 2, 1, false, MouseEvent.BUTTON1);
        MouseEvent rightClick = new MouseEvent(runState, MouseEvent.MOUSE_CLICKED, 0, 0, 2, 2, 1, false,
                MouseEvent.BUTTON3);
        for (MouseListener listener : runState.getMouseListeners()) {
            listener.mouseClicked(click);
            listener.mouseClicked(rightClick);
        }
        statusBar.showSnapshot(snapshot(false, 0, Optional.empty()));
        assertThat(runState.getToolTipText()).isEqualTo("Clic: iniciar");
        for (MouseListener listener : runState.getMouseListeners()) {
            listener.mouseClicked(click);
        }
        assertThat(clicks).containsExactly("pause", "start");
        assertThat(statusBar.text()).startsWith("⏸ en pausa");
    }

    @Test
    void aNoticeIsShownReplacedAndHiddenWhenItsTimerFires() {
        List<Runnable> expirations = new java.util.ArrayList<>();
        StatusBar bar = new StatusBar(expire -> {
            expirations.add(expire);
            return new javax.swing.Timer(Integer.MAX_VALUE, event -> expire.run());
        });
        bar.showNotice("uno");
        assertThat(bar.text()).isEqualTo("uno");
        bar.showNotice("dos");
        assertThat(bar.text()).isEqualTo("dos");
        expirations.get(0).run();
        assertThat(bar.text()).isEmpty();
        bar.dispose();
    }

    @Test
    void aNoticeWithAButtonRunsItsActionOnceClickedAndAPlainNoticeHidesTheButton() {
        List<javax.swing.Timer> timers = new java.util.ArrayList<>();
        StatusBar bar = new StatusBar(expire -> {
            javax.swing.Timer timer = new javax.swing.Timer(Integer.MAX_VALUE, event -> expire.run());
            timers.add(timer);
            return timer;
        });
        List<String> clicked = new java.util.ArrayList<>();

        bar.showNotice("Guardado", "Abrir carpeta", () -> clicked.add("open"));

        assertThat(bar.text()).isEqualTo("Guardado");
        assertThat(bar.noticeAction().isVisible()).isTrue();
        assertThat(bar.noticeAction().getText()).isEqualTo("Abrir carpeta");
        assertThat(timers.getFirst().getInitialDelay()).isEqualTo(StatusBar.ACTION_NOTICE_MILLISECONDS);
        bar.noticeAction().doClick();
        assertThat(clicked).containsExactly("open");
        assertThat(bar.noticeAction().isVisible()).isFalse();
        assertThat(bar.text()).isEmpty();

        bar.showNotice("Otro", "Abrir carpeta", () -> clicked.add("again"));
        bar.showNotice("Simple");
        assertThat(bar.noticeAction().isVisible()).isFalse();
        assertThat(timers.getFirst().getInitialDelay()).isEqualTo(StatusBar.NOTICE_MILLISECONDS);
        bar.dispose();
    }

    @Test
    void aLongNoticeNeverPushesTheProblemsOrAnIndicatorOutOfTheBar() {
        JLabel indicator = new JLabel("IA: escuchando");
        statusBar.addIndicator(indicator);
        statusBar.showSnapshot(snapshot(true, 0, Optional.empty()));
        statusBar.showZoom(1);
        statusBar.showShortCircuits(2);
        String longNotice = "Guardado en /home/alumno/circuitos/" + "muy-largo/".repeat(40) + "sumador.vsim";
        statusBar.showNotice(longNotice);
        statusBar.setSize(640, 24);

        statusBar.doLayout();

        assertThat(indicator.getWidth()).as("its whole text").isEqualTo(indicator.getPreferredSize().width);
        assertThat(indicator.getX() + indicator.getWidth()).isLessThanOrEqualTo(statusBar.getWidth());
        JLabel problems = statusBar.shortCircuitsLabel();
        assertThat(problems.getWidth()).isEqualTo(problems.getPreferredSize().width);
        assertThat(problems.getX() + problems.getWidth()).isLessThanOrEqualTo(indicator.getX());
        assertThat(statusBar.runStateLabel().getWidth())
                .isEqualTo(statusBar.runStateLabel().getPreferredSize().width);
        assertThat(statusBar.noticeLabel().getToolTipText()).as("the whole notice stays readable")
                .isEqualTo(longNotice);
    }

    @Test
    void indicatorsStayAtTheRightEndAfterTheActivitiesAndGoWhenRemoved() {
        JLabel indicator = new JLabel("IA: apagada");
        JLabel activity = new JLabel("IA trabajando: Flip-flop D…");
        int before = statusBar.getComponentCount();

        Runnable removeIndicator = statusBar.addIndicator(indicator);
        Runnable removeActivity = statusBar.addActivity(activity);

        assertThat(statusBar.getComponentCount()).isEqualTo(before + 2);
        assertThat(statusBar.getComponentZOrder(activity)).isLessThan(statusBar.getComponentZOrder(indicator));
        assertThat(statusBar.getComponentZOrder(statusBar.failureLabel()))
                .isLessThan(statusBar.getComponentZOrder(activity));

        removeActivity.run();
        removeIndicator.run();

        assertThat(statusBar.getComponentCount()).isEqualTo(before);
    }

    @Test
    void theRealNoticeTimerLastsFourSeconds() {
        assertThat(StatusBar.NOTICE_MILLISECONDS).isEqualTo(4000);
    }

    @Test
    void theWarningsAreInTheReadableErrorRedOfTheThemeShownNow() throws Exception {
        LookAndFeel previous = UIManager.getLookAndFeel();
        try {
            UIManager.setLookAndFeel(FlatLafInstaller.lookAndFeel("linux", true));
            statusBar.showShortCircuits(2);
            assertThat(statusBar.shortCircuitsLabel().getForeground()).isEqualTo(ReadableColors.errorText());
            assertThat(statusBar.failureLabel().getForeground()).isEqualTo(ReadableColors.errorText());
        } finally {
            UIManager.setLookAndFeel(previous);
        }
    }
}
