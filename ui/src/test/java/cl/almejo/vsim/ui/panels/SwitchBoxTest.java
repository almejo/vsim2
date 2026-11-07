package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.theme.FlatLafInstaller;
import org.junit.jupiter.api.Test;

import javax.swing.JCheckBox;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SwitchBoxTest {

    @Test
    void aBooleanParameterIsEditedWithASwitchThatIsStillACheckBox() {
        ParameterForm gate = new ParameterForm(StandardComponentTypes.registry().require(StandardComponentTypes.AND),
                Map.of());
        assertThat(gate.editor(StandardComponentTypes.NEGATE)).isInstanceOf(SwitchBox.class).isInstanceOf(JCheckBox.class);
    }

    @Test
    void togglingFiresOneCommitAndTheValueFollows() {
        ParameterForm gate = new ParameterForm(StandardComponentTypes.registry().require(StandardComponentTypes.AND),
                Map.of());
        AtomicInteger commits = new AtomicInteger();
        gate.addCommitListener(commits::incrementAndGet);
        SwitchBox negate = (SwitchBox) gate.editor(StandardComponentTypes.NEGATE);
        assertThat(negate.isSelected()).isFalse();

        negate.doClick();

        assertThat(commits.get()).isEqualTo(1);
        assertThat(negate.isSelected()).isTrue();
    }

    @Test
    void theTextSaysSiOrNoAndFollowsTheState() {
        SwitchBox box = new SwitchBox(false);
        assertThat(box.getText()).isEqualTo("No");
        box.setSelected(true);
        assertThat(box.getText()).isEqualTo("Sí");
    }

    @Test
    void spaceTogglesItLikeACheckBox() {
        SwitchBox box = new SwitchBox(false);
        AtomicInteger actions = new AtomicInteger();
        box.addActionListener(event -> actions.incrementAndGet());
        box.getModel().setArmed(true);
        box.getModel().setPressed(true);
        box.getModel().setPressed(false);
        assertThat(box.isSelected()).isTrue();
        assertThat(actions.get()).isEqualTo(1);
    }

    @Test
    void paintingInLightAndDarkOnAndOffDoesNotThrowAndChangesThePixels() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            for (boolean dark : new boolean[]{false, true}) {
                UIManager.setLookAndFeel(FlatLafInstaller.lookAndFeel("linux", dark));
                BufferedImage off = paint(new SwitchBox(false));
                BufferedImage on = paint(new SwitchBox(true));
                assertThat(differs(off, on)).as("dark=%s", dark).isTrue();
            }
        } finally {
            UIManager.setLookAndFeel(original);
        }
    }

    @Test
    void theSwitchAndTheSectionHeaderGrowWithTheUiScale() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            UIManager.setLookAndFeel(FlatLafInstaller.lookAndFeel("linux", false));
            int normalBox = new SwitchBox(true).getPreferredSize().height;
            int normalHeader = new SectionHeader("General").getPreferredSize().height;
            // a big default font makes FlatLaf scale every pixel size (what a 200 % screen does)
            System.setProperty("flatlaf.uiScale", "2");
            UIManager.setLookAndFeel(FlatLafInstaller.lookAndFeel("linux", false));
            SwitchBox box = new SwitchBox(true);
            SectionHeader header = new SectionHeader("General");
            assertThat(com.formdev.flatlaf.util.UIScale.scale(20)).as("the scale is on").isGreaterThan(20);
            assertThat(box.getPreferredSize().height).isGreaterThanOrEqualTo(com.formdev.flatlaf.util.UIScale.scale(20));
            assertThat(box.getPreferredSize().height).isGreaterThan(normalBox);
            java.awt.FontMetrics metrics = box.getFontMetrics(UIManager.getFont("CheckBox.font"));
            int widest = Math.max(metrics.stringWidth("Sí"), metrics.stringWidth("No"));
            assertThat(box.getPreferredSize().width)
                    .isGreaterThanOrEqualTo(com.formdev.flatlaf.util.UIScale.scale(30 + 8) + widest);
            assertThat(header.getPreferredSize().height).isGreaterThan(normalHeader);
        } finally {
            System.clearProperty("flatlaf.uiScale");
            UIManager.setLookAndFeel(original);
        }
    }

    private static BufferedImage paint(SwitchBox box) {
        box.setSize(box.getPreferredSize());
        BufferedImage image = new BufferedImage(box.getWidth(), box.getHeight(), BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        try {
            box.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static boolean differs(BufferedImage first, BufferedImage second) {
        for (int row = 0; row < Math.min(first.getHeight(), second.getHeight()); row++) {
            for (int column = 0; column < Math.min(first.getWidth(), second.getWidth()); column++) {
                if (first.getRGB(column, row) != second.getRGB(column, row)) {
                    return true;
                }
            }
        }
        return false;
    }
}
