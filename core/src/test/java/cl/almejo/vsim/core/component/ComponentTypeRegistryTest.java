package cl.almejo.vsim.core.component;

import cl.almejo.vsim.core.geometry.GridSize;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComponentTypeRegistryTest {

    private static ComponentType type(String id) {
        return new ComponentType(id, ComponentCategory.BASIC, new GridSize(2, 2), List.of(), List.of(),
                parameters -> (context, pinIndex) -> {
                });
    }

    @Test
    void resolversSupplyTypesThatAreNotListed() {
        ComponentTypeRegistry registry = new ComponentTypeRegistry();
        registry.register(type("and2"));
        ComponentType dynamic = type("subcircuit:adder");
        registry.addResolver(typeId -> typeId.equals("subcircuit:adder") ? Optional.of(dynamic) : Optional.empty());
        assertThat(registry.find("subcircuit:adder")).containsSame(dynamic);
        assertThat(registry.require("subcircuit:adder")).isSameAs(dynamic);
        assertThat(registry.all()).extracting(ComponentType::id).containsExactly("and2");
        assertThatThrownBy(() -> registry.require("subcircuit:other")).isInstanceOf(UnknownComponentTypeException.class);
    }

    @Test
    void registeredTypesWinOverResolvers() {
        ComponentTypeRegistry registry = new ComponentTypeRegistry();
        ComponentType registered = type("and2");
        registry.register(registered);
        registry.addResolver(typeId -> Optional.of(type(typeId)));
        assertThat(registry.require("and2")).isSameAs(registered);
    }

    @Test
    void copyKeepsTypesButNotResolversAndExtendedWithKeepsBoth() {
        ComponentTypeRegistry registry = new ComponentTypeRegistry();
        registry.register(type("and2"));
        ComponentType first = type("subcircuit:first");
        ComponentType second = type("subcircuit:second");
        registry.addResolver(typeId -> typeId.equals(first.id()) ? Optional.of(first) : Optional.empty());

        ComponentTypeRegistry copy = registry.copy();
        assertThat(copy.find("and2")).isPresent();
        assertThat(copy.find(first.id())).isEmpty();

        ComponentTypeRegistry extended = registry.extendedWith(
                typeId -> typeId.equals(second.id()) ? Optional.of(second) : Optional.empty());
        assertThat(extended.find(first.id())).containsSame(first);
        assertThat(extended.find(second.id())).containsSame(second);
        assertThat(registry.find(second.id())).isEmpty();
    }

    @Test
    void concurrentRegistrationLosesNothing() throws Exception {
        ComponentTypeRegistry registry = new ComponentTypeRegistry();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int worker = 0; worker < 8; worker++) {
                int first = worker * 200;
                futures.add(executor.submit(() -> {
                    for (int index = first; index < first + 200; index++) {
                        registry.register(type("t" + index));
                        registry.all();
                        registry.find("t" + index);
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
        assertThat(registry.all()).hasSize(1600);
    }
}
