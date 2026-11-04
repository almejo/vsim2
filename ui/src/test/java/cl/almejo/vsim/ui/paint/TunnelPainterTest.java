package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.tunnel.TunnelKind;
import cl.almejo.vsim.core.tunnel.Tunnels;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** A tunnel without direction wears a two-way mark where the others have their arrow. */
class TunnelPainterTest {

    private static BufferedImage render(String typeId, Map<String, Object> parameters, ColorScheme scheme) {
        ComponentType type = StandardComponentTypes.registry().find(typeId).orElseThrow();
        BufferedImage image = new BufferedImage(160, 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            new TunnelPainter().paint(graphics, type, parameters, VisibleState.NONE, scheme);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    /** Whether the two pixels beside the middle of the arrow's slot, 2.5 px above the middle line, are the tag's colour. */
    private static boolean markIsWide(BufferedImage image, TunnelShape shape, ColorScheme scheme) {
        double middleX = (shape.arrowTip().getX() + shape.arrowBack().getX()) / 2;
        int column = (int) Math.floor(middleX);
        int row = (int) Math.floor(shape.arrowTip().getY() - 2.5);
        int color = scheme.tunnelColor(0).getRGB();
        return image.getRGB(column - 1, row) == color && image.getRGB(column, row) == color;
    }

    @Test
    void aTunnelWithoutDirectionDrawsTwoTrianglesBackToBackAndOneWithDirectionAnArrow() {
        for (ColorScheme scheme : new ColorScheme[]{ColorSchemes.LIGHT, ColorSchemes.DARK}) {
            for (TunnelKind kind : TunnelKind.values()) {
                String typeId = kind == TunnelKind.ENTRANCE ? StandardComponentTypes.TUNNEL_ENTRANCE
                        : StandardComponentTypes.TUNNEL_EXIT;
                TunnelShape shape = TunnelShape.of(kind, "A");
                BufferedImage withoutDirection = render(typeId,
                        Map.of(Tunnels.NAME, "A", Tunnels.DIRECTION, Tunnels.UNDIRECTED), scheme);
                BufferedImage withDirection = render(typeId, Map.of(Tunnels.NAME, "A"), scheme);

                assertThat(markIsWide(withoutDirection, shape, scheme)).as("%s %s without", kind, scheme).isTrue();
                assertThat(markIsWide(withDirection, shape, scheme)).as("%s %s with", kind, scheme).isFalse();
            }
        }
    }
}
