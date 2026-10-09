package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.PinOrder;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ComponentFormTest {

    private final ElementId first = new ElementId("c1");
    private final ElementId second = new ElementId("c2");
    private final ElementId output = new ElementId("c3");
    private final PinOrder order = new PinOrder(List.of(first, second), List.of(output));
    private final Map<ElementId, String> names = Map.of(first, "a", second, "b", output, "y");

    @BeforeEach
    void spanish() {
        Messages.initialize(Messages.SPANISH);
    }

    @Test
    void okNeedsANameThatNoOtherComponentUses() {
        ComponentForm form = ComponentForm.create(order, names, List.of("Inversor"), Set.of("inversor"));
        assertThat(form.canAccept()).isFalse();
        assertThat(form.problem()).contains("Escribe un nombre para el componente.");
        form.setName("   ");
        assertThat(form.canAccept()).isFalse();
        form.setName("  INVERSOR ");
        assertThat(form.problem()).contains("Ya existe un componente llamado «INVERSOR».");
        form.setName(" Negador ");
        assertThat(form.canAccept()).isTrue();
        assertThat(form.result()).isEqualTo(new ComponentForm.Result("negador", "Negador", order, true));
    }

    @Test
    void theIdGetsASuffixWhenTheSlugIsTaken() {
        ComponentForm form = ComponentForm.create(order, names, List.of(), Set.of("sumador", "sumador-2"));
        form.setName("Sumador");
        assertThat(form.result().definitionId()).isEqualTo("sumador-3");
    }

    @Test
    void pinsMoveWithinTheirSideAndListenersHear() {
        ComponentForm form = ComponentForm.create(order, names, List.of(), Set.of());
        AtomicInteger changes = new AtomicInteger();
        form.addListener(changes::incrementAndGet);
        form.moveInput(0, 1);
        form.moveInput(1, 1);
        form.moveOutput(0, -1);
        assertThat(form.inputNames()).containsExactly("b", "a");
        assertThat(form.outputNames()).containsExactly("y");
        assertThat(changes).hasValue(1);
        form.setName("Par");
        assertThat(form.result().pinOrder()).isEqualTo(new PinOrder(List.of(second, first), List.of(output)));
    }

    @Test
    void updatingIgnoresTheTargetsOwnNameAndKeepsItsId() {
        ComponentDefinition inverter = ExampleDefinitions.inverter();
        ComponentDefinition doubleInverter = ExampleDefinitions.doubleInverter();
        ComponentForm form = ComponentForm.update(order, names, List.of("Inversor", "Doble inversor"),
                List.of(inverter, doubleInverter));
        assertThat(form.mode()).isEqualTo(ComponentForm.Mode.UPDATE);
        assertThat(form.name()).isEqualTo("Inversor");
        assertThat(form.canAccept()).isTrue();
        form.setName("doble INVERSOR");
        assertThat(form.canAccept()).isFalse();

        form.setTarget(doubleInverter);

        assertThat(form.name()).isEqualTo("Doble inversor");
        assertThat(form.result()).isEqualTo(new ComponentForm.Result(ExampleDefinitions.DOUBLE_INVERTER,
                "Doble inversor", order, false));
    }
}
