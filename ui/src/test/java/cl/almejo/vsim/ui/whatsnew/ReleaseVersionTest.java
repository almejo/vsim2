package cl.almejo.vsim.ui.whatsnew;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The versions VSim compares to know whether the news of a release are due. */
class ReleaseVersionTest {

    @Test
    void versionsCompareNumberByNumberNotAsText() {
        assertThat(ReleaseVersion.parse("0.10").orElseThrow())
                .isGreaterThan(ReleaseVersion.parse("0.9").orElseThrow());
        assertThat(ReleaseVersion.parse("0.8").orElseThrow())
                .isGreaterThan(ReleaseVersion.parse("0.7").orElseThrow());
        assertThat(ReleaseVersion.parse("1.0").orElseThrow())
                .isEqualByComparingTo(ReleaseVersion.parse("1.0").orElseThrow());
    }

    @Test
    void aBuildOutsideGradleHasNoVersionToCompare() {
        assertThat(ReleaseVersion.parse("unknown")).isEmpty();
        assertThat(ReleaseVersion.parse("")).isEmpty();
        assertThat(ReleaseVersion.parse("0.8-beta")).isEmpty();
    }

    @Test
    void theNextReleaseIsOneTenthMore() {
        assertThat(ReleaseVersion.parse("0.7").orElseThrow().next()).hasToString("0.8");
        assertThat(ReleaseVersion.parse("0.9").orElseThrow().next()).hasToString("0.10");
    }
}
