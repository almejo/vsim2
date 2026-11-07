package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.ui.window.tabs.TabMark;
import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.util.Objects;

/**
 * The AI's robot on a tab and in the tab lists (fix round D, item 5n; mockup 2026-11-06 option A): {@code ai-robot.svg}
 * painted in the AI's green while it works there (the colour of the status bar's «IA: conectada» dot) and in the
 * muted grey once it finished. The colour is read from the theme at every paint, never kept.
 */
public final class AiRobotIcon implements Icon {

    private final FlatSVGIcon robot;

    public AiRobotIcon(TabMark.Phase phase, int size) {
        Objects.requireNonNull(phase, "phase");
        robot = new FlatSVGIcon("icons/" + AiIcons.ROBOT + ".svg", size, size, AiRobotIcon.class.getClassLoader());
        robot.setColorFilter(new FlatSVGIcon.ColorFilter(drawn -> colorOf(phase)));
    }

    /** The robot's colour now: the AI's green while working, grey after. */
    static Color colorOf(TabMark.Phase phase) {
        return switch (phase) {
            case WORKING -> AiColors.success();
            case FINISHED -> AiColors.muted();
        };
    }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        robot.paintIcon(component, graphics, x, y);
    }

    @Override
    public int getIconWidth() {
        return robot.getIconWidth();
    }

    @Override
    public int getIconHeight() {
        return robot.getIconHeight();
    }
}
