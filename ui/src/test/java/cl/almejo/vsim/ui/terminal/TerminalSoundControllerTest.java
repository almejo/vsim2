package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.terminal.TerminalScreen;
import cl.almejo.vsim.core.terminal.TerminalSettings;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.sound.SoundSink;
import cl.almejo.vsim.ui.sound.TerminalSounds;
import cl.almejo.vsim.ui.sound.Tone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/** The bell and the teletype clatter (spec §8.6), from the counters of each published screen; never real sound. */
class TerminalSoundControllerTest {

    private static final long SIXTY_MILLISECONDS = 60_000_000L;

    private OpenDocument document;
    private ComponentType type;
    private final List<Tone> played = new ArrayList<>();
    private final SoundSink sink = played::add;
    private final AtomicLong nanoClock = new AtomicLong(1_000_000_000L);
    private final List<String> flashes = new ArrayList<>();
    private TerminalSoundController controller;
    private TerminalScreen screen;
    private long owner = 1;

    @BeforeEach
    void create() {
        document = OpenDocument.createEmpty(StandardComponentTypes.registry());
        type = document.registry().require(StandardComponentTypes.TERMINAL);
        controller = new TerminalSoundController(document, new TerminalSounds(sink, nanoClock::get), flashes::add);
        screen = new TerminalScreen(TerminalSettings.vt(2, 10));
    }

    @AfterEach
    void close() {
        controller.dispose();
        document.close();
    }

    private Component terminal(String bell, boolean teletype) {
        Component component = type.newComponent(document.document().newComponentId(), new GridPoint(0, 0),
                Rotation.R0, Map.of(TerminalTypes.BELL, bell, TerminalTypes.TELETYPE_SOUND, teletype), "consola");
        document.execute(new AddElementsCommand(List.of(component)));
        return component;
    }

    private void show(Component component) {
        nanoClock.addAndGet(SIXTY_MILLISECONDS);
        controller.poll(new SimulationSnapshot(1, 1, 0, true, Map.of(), Map.of(component.id().value(),
                new VisibleState.TerminalState(owner, screen.version(), screen.frame(), false)), List.of(),
                Optional.empty()));
    }

    private void write(String text) {
        text.chars().forEach(character -> screen.write(character, 1));
    }

    @Test
    void eachBellRingsOnceWithSound() {
        Component terminal = terminal("sound", false);
        show(terminal);

        write("\u0007");
        show(terminal);
        show(terminal);
        assertThat(played).hasSize(1);
        write("x\u0007");
        show(terminal);

        assertThat(played).hasSize(2);
        assertThat(flashes).isEmpty();
    }

    @Test
    void theBellParameterChoosesFlashSoundBothOrNothing() {
        Component flash = terminal("flash", false);
        show(flash);
        write("\u0007");
        show(flash);
        assertThat(played).isEmpty();
        assertThat(flashes).containsExactly(flash.id().value());

        Component both = terminal("both", false);
        show(both);
        write("\u0007");
        show(both);
        assertThat(played).hasSize(1);
        assertThat(flashes).containsExactly(flash.id().value(), both.id().value());

        Component none = terminal("none", false);
        show(none);
        write("\u0007");
        show(none);
        assertThat(played).hasSize(1);
        assertThat(flashes).hasSize(2);
    }

    @Test
    void theFirstLookAndARestartAreSilent() {
        write("\u0007\u0007abc");
        Component terminal = terminal("sound", true);

        show(terminal);
        assertThat(played).as("bells from before the window looked").isEmpty();

        screen = new TerminalScreen(TerminalSettings.vt(2, 10));
        owner = 2;
        show(terminal);
        assertThat(played).as("a restart lowers the counters").isEmpty();
    }

    @Test
    void printedCharactersClickOnlyWithTheTeletypeSound() {
        Component quiet = terminal("none", false);
        show(quiet);
        write("hola");
        show(quiet);
        assertThat(played).isEmpty();

        Component teletype = terminal("none", true);
        show(teletype);
        write("hola");
        show(teletype);
        assertThat(played).hasSize(1);
    }
}
