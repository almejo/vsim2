package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.tunnel.Tunnels;
import cl.almejo.vsim.ui.i18n.Descriptions;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** A tunnel without direction has its own name and its own description, in both languages. */
class TunnelDirectionTextsTest {

    private static final Map<String, Object> UNDIRECTED = Map.of(Tunnels.NAME, "BUS", Tunnels.DIRECTION,
            Tunnels.UNDIRECTED);

    @Test
    void itsNameIsTunelSinDireccionWhateverItsType() {
        assertThat(Messages.componentName(StandardComponentTypes.TUNNEL_ENTRANCE, UNDIRECTED))
                .isEqualTo("Túnel (sin dirección)");
        assertThat(Messages.componentName(StandardComponentTypes.TUNNEL_EXIT, UNDIRECTED))
                .isEqualTo("Túnel (sin dirección)");
        assertThat(Messages.componentName(StandardComponentTypes.TUNNEL_EXIT, Map.of(Tunnels.NAME, "BUS")))
                .isEqualTo("Túnel (salida)");
    }

    @Test
    void itsDescriptionIsItsOwnAndTheOthersMentionTheDirectionCanBeRemoved() {
        for (Locale locale : new Locale[]{Messages.SPANISH, Locale.ENGLISH}) {
            for (String typeId : new String[]{StandardComponentTypes.TUNNEL_ENTRANCE,
                    StandardComponentTypes.TUNNEL_EXIT}) {
                ComponentType type = StandardComponentTypes.registry().find(typeId).orElseThrow();
                String undirected = Descriptions.of(type, UNDIRECTED, Messages.bundle(locale));
                String directed = Descriptions.of(type, Map.of(Tunnels.NAME, "BUS"), Messages.bundle(locale));
                boolean english = locale.equals(Locale.ENGLISH);

                assertThat(undirected).as("%s %s", locale, typeId)
                        .startsWith(english ? "Tunnel (without direction)" : "Túnel (sin dirección)")
                        .doesNotContain("(entrada)").doesNotContain("(salida)").doesNotContain("(entrance)")
                        .doesNotContain("(exit)").doesNotContain(Descriptions.PARAGRAPH_BREAK + "«Sin dirección»");
                assertThat(directed).as("%s %s directed", locale, typeId)
                        .containsIgnoringCase(english ? "without direction" : "sin dirección");
            }
        }
    }
}
