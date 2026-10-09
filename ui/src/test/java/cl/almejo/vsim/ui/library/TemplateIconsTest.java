package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.icons.Icons;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.geom.Point2D;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateIconsTest {

    @TempDir
    Path directory;

    private TemplateFixture fixture;

    @BeforeEach
    void create() {
        fixture = new TemplateFixture(directory.resolve("library"));
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    private static void assertIcon(Action action, String name) {
        assertThat(action.getValue(Action.SMALL_ICON)).as(name).isSameAs(Icons.get(name, Icons.MENU_SIZE));
        assertThat(action.getValue(Action.LARGE_ICON_KEY)).as(name).isSameAs(Icons.get(name, Icons.TOOLBAR_SIZE));
    }

    @Test
    void theTemplateActionsCarryTheirIcons() {
        assertIcon(fixture.features.actions().createComponent(), TemplateIcons.CREATE_COMPONENT);
        assertIcon(fixture.features.actions().updateComponent(), TemplateIcons.UPDATE_COMPONENT);
        assertIcon(fixture.features.importComponent(), TemplateIcons.IMPORT_COMPONENT);

        fixture.openDocument.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
        Component instance = fixture.place(SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), 0, 0, "");
        assertIcon(fixture.features.actions().disassembleAction(instance), TemplateIcons.DISASSEMBLE);
        assertIcon(fixture.features.actions().updateFromLibraryAction(instance), TemplateIcons.UPDATE_FROM_LIBRARY);

        fixture.select(instance);
        JPopupMenu menu = new JPopupMenu();
        fixture.features.actions().contributor().contribute(menu, Optional.of(instance),
                fixture.view.canvas().selection(), new Point2D.Double());
        JMenuItem disassemble = (JMenuItem) menu.getComponent(0);
        assertThat(disassemble.getText()).isEqualTo("Desarmar");
        assertThat(disassemble.getIcon()).isSameAs(Icons.get(TemplateIcons.DISASSEMBLE, Icons.MENU_SIZE));
    }
}
