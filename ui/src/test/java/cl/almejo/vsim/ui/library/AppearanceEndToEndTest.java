package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.component.appearance.SymbolDraft;
import cl.almejo.vsim.core.component.appearance.SymbolTemplates;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.ui.appearance.AppearanceExtension;
import cl.almejo.vsim.ui.appearance.AppearancePrompts;
import cl.almejo.vsim.ui.appearance.ShapeEditRequest;
import cl.almejo.vsim.ui.appearance.ShapeEditResult;
import cl.almejo.vsim.ui.appearance.ShapeEditScope;
import cl.almejo.vsim.ui.appearance.ShapeEditor;
import cl.almejo.vsim.ui.i18n.Messages;
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
    void returningFromTheLibraryWarningKeepsTheDraftAndAcceptingStillAppliesIt() {
        try (TemplateFixture fixture = component()) {
            Component instance = fixture.single("subcircuit:negador");
            List<ShapeEditRequest> requests = new ArrayList<>();
            ShapeEditor editor = (owner, request) -> {
                requests.add(request);
                return Optional.of(shaped(request, ShapeEditScope.CIRCUIT_DEFINITION));
            };
            Prompts prompts = new Prompts(); prompts.libraryBackOnce = true;
            Runnable remove = install(fixture, editor, prompts);
            try {
                changeShape(fixture, instance);
                assertThat(requests).hasSize(2);
                assertThat(requests.get(1).appearance().drawing()).isPresent();
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
        JPopupMenu menu = menu(fixture, instance);
        for (java.awt.Component component : menu.getComponents()) {
            if (component instanceof JMenuItem item && item.getText().equals(Messages.text("shape.change"))) {
                item.doClick(); return;
            }
        }
        throw new AssertionError("Missing shape action");
    }

    private static final class Prompts implements AppearancePrompts {
        boolean libraryBackOnce;
        @Override public boolean confirmConnections(java.awt.Component owner, List<String> disconnected,
                                                     List<String> kept, boolean blockedInstance) { return !blockedInstance; }
        @Override public boolean confirmLibraryChange(java.awt.Component owner) {
            if (libraryBackOnce) { libraryBackOnce = false; return false; }
            return true;
        }
        @Override public boolean retryLibraryWrite(java.awt.Component owner, boolean applied) { return false; }
        @Override public boolean reopenStale(java.awt.Component owner) { return false; }
    }
}
