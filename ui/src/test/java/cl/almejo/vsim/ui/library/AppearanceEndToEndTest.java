package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.appearance.SymbolDraft;
import cl.almejo.vsim.core.component.appearance.SymbolTemplates;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.ui.appearance.AppearanceExtension;
import cl.almejo.vsim.ui.appearance.AppearancePrompts;
import cl.almejo.vsim.ui.appearance.ShapeEditRequest;
import cl.almejo.vsim.ui.appearance.ShapeEditResult;
import cl.almejo.vsim.ui.appearance.ShapeEditScope;
import cl.almejo.vsim.ui.appearance.ShapeEditor;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.PalettePopupTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.geom.Point2D;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static cl.almejo.vsim.ui.library.TemplateFixture.perform;
import static org.assertj.core.api.Assertions.assertThat;

class AppearanceEndToEndTest {
    @TempDir Path directory;

    @Test
    void changingOnePieceIsOneUndoStepAndLeavesItsDefinitionAndLibraryUnchanged() {
        try (TemplateFixture fixture = component()) {
            Component instance = fixture.single("subcircuit:negador");
            ComponentDefinition before = fixture.openDocument.document().definition("negador").orElseThrow();
            ShapeEditor editor = (owner, request) -> Optional.of(shaped(request, ShapeEditScope.INSTANCE));
            Runnable remove = install(fixture, editor, new Prompts());
            try {
                changeShape(fixture, instance);
                assertThat(fixture.openDocument.document().component(instance.id()).orElseThrow().appearance()).isPresent();
                assertThat(fixture.openDocument.document().definition("negador")).contains(before);
                assertThat(fixture.library.find("negador").orElseThrow().root()).isEqualTo(before);
                fixture.openDocument.history().undo();
                assertThat(fixture.openDocument.document().component(instance.id())).contains(instance);
                fixture.openDocument.history().redo();
                assertThat(fixture.openDocument.document().component(instance.id()).orElseThrow().appearance()).isPresent();
            } finally { remove.run(); }
        }
    }

    @Test
    void changingTheCircuitCopyWritesTheLibraryWithoutAnExtraConfirmation() {
        try (TemplateFixture fixture = component()) {
            Component instance = fixture.single("subcircuit:negador");
            List<ShapeEditRequest> requests = new ArrayList<>();
            ShapeEditor editor = (owner, request) -> {
                requests.add(request);
                return Optional.of(shaped(request, ShapeEditScope.CIRCUIT_DEFINITION));
            };
            Runnable remove = install(fixture, editor, new Prompts());
            try {
                changeShape(fixture, instance);
                assertThat(requests).hasSize(1);
                ComponentDefinition changed = fixture.openDocument.document().definition("negador").orElseThrow();
                assertThat(changed.appearance()).isPresent();
                assertThat(changed.revision()).isEqualTo(2);
                assertThat(fixture.library.find("negador").orElseThrow().root()).isEqualTo(changed);
                fixture.openDocument.history().undo();
                assertThat(fixture.openDocument.document().definition("negador").orElseThrow().revision()).isEqualTo(1);
            } finally { remove.run(); }
        }
    }

    @Test
    void cancellationDoesNotTouchTheDocumentAndDisposalRemovesEveryRegistration() {
        try (TemplateFixture fixture = component()) {
            Component instance = fixture.single("subcircuit:negador");
            long revision = fixture.openDocument.document().revision();
            int libraryListeners = fixture.library.listenerCount();
            int documentListeners = fixture.openDocument.changeListenerCount();
            AtomicInteger opens = new AtomicInteger();
            Runnable remove = install(fixture, (owner, request) -> { opens.incrementAndGet(); return Optional.empty(); }, new Prompts());
            changeShape(fixture, instance);
            assertThat(opens).hasValue(1);
            assertThat(fixture.openDocument.document().revision()).isEqualTo(revision);
            remove.run();
            assertThat(fixture.library.listenerCount()).isEqualTo(libraryListeners);
            assertThat(fixture.openDocument.changeListenerCount()).isEqualTo(documentListeners);
            assertThat(menu(fixture, instance).getComponents()).noneMatch(component -> component instanceof JMenuItem item
                    && item.getText().equals(Messages.text("shape.change")));
        }
    }

    @Test
    void aPieceMenuEndsWithChangeShapeAndOffersNoVersionReview() {
        try (TemplateFixture fixture = component()) {
            Component instance = fixture.single("subcircuit:negador");
            Runnable remove = install(fixture, (owner, request) -> Optional.empty(), new Prompts());
            try {
                List<String> texts = new ArrayList<>();
                for (java.awt.Component component : menu(fixture, instance).getComponents()) {
                    if (component instanceof JMenuItem item) {
                        texts.add(item.getText());
                    }
                }
                assertThat(texts).last().isEqualTo(Messages.text("shape.change"));
            } finally { remove.run(); }
        }
    }

    @Test
    void aLibraryUpdateThatWouldTouchAnotherWireAsksWithTheConnectionsSheet() throws Exception {
        try (TemplateFixture fixture = component()) {
            Component instance = fixture.single("subcircuit:negador");
            ComponentDefinition before = fixture.openDocument.document().definition("negador").orElseThrow();
            DefinitionPin output = before.pinout().getLast();
            GridPoint movedOutput = new GridPoint(output.spec().position().x() + 2, output.spec().position().y());
            ComponentDefinition wider = new ComponentDefinition(before.id(), before.name(), "",
                    new GridSize(before.size().width() + 2, before.size().height()), List.of(before.pinout().getFirst(),
                    new DefinitionPin(PinSpec.output(output.spec().name(), movedOutput.x(), movedOutput.y()),
                            output.source())), before.body(), Optional.empty(), before.revision() + 1);
            fixture.service.save(new DefinitionBundle(wider, List.of()));
            GridPoint crossing = instance.position().plus(movedOutput);
            fixture.openDocument.execute(new AddWireCommand(new Wire(fixture.openDocument.document().newWireId(),
                    crossing.translate(0, -5), crossing.translate(0, 5))));
            Prompts prompts = new Prompts();
            Runnable remove = install(fixture, (owner, request) -> Optional.empty(), prompts);
            try {
                fixture.features.actions().updateFromLibrary(instance);

                assertThat(prompts.keptLists).hasSize(1);
                assertThat(prompts.keptLists.getFirst()).hasSize(1);
                assertThat(fixture.openDocument.document().definition("negador")).contains(wider);
            } finally { remove.run(); }
        }
    }

    @Test
    void aComponentOfThisCircuitInThePaletteOffersChangeShapeOnTheCircuitCopy() {
        try (TemplateFixture fixture = new TemplateFixture(directory.resolve("library"))) {
            fixture.openDocument.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter())));
            fixture.place("subcircuit:inverter", 30, 30, ""); // only used definitions are listed
            assertThat(fixture.view.palette().visibleSections()).endsWith(Messages.text("palette.section.document"));
            List<ShapeEditRequest> requests = new ArrayList<>();
            Runnable remove = install(fixture, (owner, request) -> {
                requests.add(request);
                return Optional.empty();
            }, new Prompts());
            try {
                JPopupMenu menu = PalettePopupTestSupport.popupFor(fixture.view.palette(), "subcircuit:inverter")
                        .orElseThrow();
                clickItem(menu, Messages.text("shape.change"));

                assertThat(requests).extracting(ShapeEditRequest::initialScope)
                        .containsExactly(ShapeEditScope.CIRCUIT_DEFINITION);
            } finally { remove.run(); }
        }
    }

    private TemplateFixture component() {
        TemplateFixture fixture = new TemplateFixture(directory.resolve("library"));
        fixture.drawInverter(0, 0);
        fixture.dialogs.acceptForm(form -> form.setName("Negador"));
        perform(fixture.features.actions().createComponent());
        return fixture;
    }

    private static Runnable install(TemplateFixture fixture, ShapeEditor editor, AppearancePrompts prompts) {
        return new AppearanceExtension(fixture.service, fixture.dialogs, Optional.of(editor), prompts).install(fixture.view);
    }

    private static ShapeEditResult shaped(ShapeEditRequest request, ShapeEditScope scope) {
        return new ShapeEditResult(scope, SymbolDraft.of(request.appearance(), request.definition().pinout(), false)
                .applyTemplate(SymbolTemplates.Template.ALU).appearance());
    }

    private static JPopupMenu menu(TemplateFixture fixture, Component instance) {
        fixture.select(instance);
        return fixture.view.contextMenu(Optional.of(instance), new Point2D.Double());
    }

    private static void changeShape(TemplateFixture fixture, Component instance) {
        clickItem(menu(fixture, instance), Messages.text("shape.change"));
    }

    private static void clickItem(JPopupMenu menu, String text) {
        for (java.awt.Component component : menu.getComponents()) {
            if (component instanceof JMenuItem item && item.getText().equals(text)) {
                item.doClick(); return;
            }
        }
        throw new AssertionError("Missing menu item " + text);
    }

    private static final class Prompts implements AppearancePrompts {
        private final List<List<String>> keptLists = new ArrayList<>();

        @Override public boolean confirmConnections(java.awt.Component owner, List<String> disconnected,
                                                     List<String> kept, boolean blockedInstance) {
            keptLists.add(kept);
            return !blockedInstance;
        }
        @Override public boolean retryLibraryWrite(java.awt.Component owner, boolean applied) { return false; }
        @Override public boolean reopenStale(java.awt.Component owner) { return false; }
    }
}
