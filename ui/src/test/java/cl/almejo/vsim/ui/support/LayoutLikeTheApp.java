package cl.almejo.vsim.ui.support;

import cl.almejo.vsim.ui.theme.MigLayoutDefaults;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Registered globally (junit-platform.properties + META-INF/services): every ui test lays out with MigLayout set as
 * {@code Main} sets it at startup, so a layout test on macOS or Windows measures what the app shows there.
 */
public final class LayoutLikeTheApp implements BeforeEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        MigLayoutDefaults.install();
    }
}
