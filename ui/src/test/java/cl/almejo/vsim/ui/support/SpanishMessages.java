package cl.almejo.vsim.ui.support;

import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Registered globally (junit-platform.properties + META-INF/services): every ui test starts in Spanish, whatever the
 * previous test left. Runs before the test's own {@code @BeforeEach}, so a test that wants English switches there.
 */
public final class SpanishMessages implements BeforeEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        Messages.initialize(Messages.SPANISH);
    }
}
