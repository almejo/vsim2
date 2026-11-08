package cl.almejo.vsim.automation.export;

import cl.almejo.vsim.automation.DocumentId;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** What an export request accepts before it reaches the editor. */
class ExportRequestTest {

    private static ExportRequest request(ExportRequest.Format format, ExportRequest.Scope scope) {
        return new ExportRequest(new DocumentId("circuit-1"), format, scope, Optional.empty(),
                ExportRequest.ImageOptions.DEFAULT, ExportRequest.PdfOptions.DEFAULT);
    }

    @Test
    void theWindowIsExportedAsAPng() {
        assertThat(request(ExportRequest.Format.PNG, new ExportRequest.Scope.Window()).scope())
                .isEqualTo(new ExportRequest.Scope.Window());
    }

    @Test
    void theWindowIsNeverAPdfNorAnSvgAndTheRefusalSaysWhy() {
        assertThatThrownBy(() -> request(ExportRequest.Format.PDF, new ExportRequest.Scope.Window()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The window is exported only as a PNG (format PNG); PDF and SVG take the circuit");
        assertThatThrownBy(() -> request(ExportRequest.Format.SVG, new ExportRequest.Scope.Window()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
