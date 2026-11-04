package cl.almejo.vsim.ui.about;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * The GitHub issue form VSim sends a tester to, with the installation data already filled in. The form of the
 * application's own language is chosen (GitHub shows a form's text as written); the field ids match the forms in
 * {@code .github/ISSUE_TEMPLATE}.
 */
public final class ProblemReportLink {

    static final String NEW_ISSUE = AboutContent.REPOSITORY + "/issues/new";

    private ProblemReportLink() {
    }

    /** The address for {@code information} in {@code locale}: only the version, system and package format go in it. */
    public static String url(AboutInformation information, Locale locale) {
        String template = "es".equals(locale.getLanguage()) ? "problema.yml" : "problem.yml";
        return NEW_ISSUE + "?template=" + template
                + "&version=" + encode(information.version())
                + "&sistema=" + encode(information.operatingSystem())
                + "&formato=" + encode(information.packageFormat());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
