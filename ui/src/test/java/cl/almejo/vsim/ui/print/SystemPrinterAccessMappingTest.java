package cl.almejo.vsim.ui.print;

import org.junit.jupiter.api.Test;

import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Copies;
import javax.print.attribute.standard.JobName;
import javax.print.attribute.standard.DialogTypeSelection;
import javax.print.attribute.standard.MediaPrintableArea;
import javax.print.attribute.standard.MediaSize;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.MediaTray;
import javax.print.attribute.standard.OrientationRequested;
import javax.print.attribute.standard.PageRanges;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** The attributes of {@code javax.print} and VSim's papers, orientations and margins, both ways; no printer needed. */
class SystemPrinterAccessMappingTest {

    private static final PrinterAccess.Job JOB = new PrinterAccess.Job() { };
    private static final double POINTS_PER_MILLIMETRE = 72 / 25.4;
    private static final PrintSetup REQUESTED = new PrintSetup(Paper.A4, PageOrientation.PORTRAIT,
            PageGeometry.CENTIMETRE_POINTS);

    /** A printable area of the A4 sheet that leaves {@code left}, {@code top}, {@code right}, {@code bottom} mm. */
    private static MediaPrintableArea a4Leaving(double left, double top, double right, double bottom) {
        return new MediaPrintableArea((float) left, (float) top, (float) (210 - left - right),
                (float) (297 - top - bottom), MediaPrintableArea.MM);
    }

    private static PrintRequestAttributeSet chosen(MediaSizeName media, OrientationRequested orientation) {
        PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
        attributes.add(media);
        attributes.add(orientation);
        return attributes;
    }

    @Test
    void everyPaperGoesToAMediaAndBack() {
        assertThat(SystemPrinterAccess.mediaOf(Paper.A4)).isEqualTo(MediaSizeName.ISO_A4);
        assertThat(SystemPrinterAccess.mediaOf(Paper.LETTER)).isEqualTo(MediaSizeName.NA_LETTER);
        assertThat(SystemPrinterAccess.mediaOf(Paper.A3)).isEqualTo(MediaSizeName.ISO_A3);
        assertThat(SystemPrinterAccess.mediaOf(Paper.OFICIO)).as("8.5 × 13 in, the Chilean 216 × 330 mm")
                .isEqualTo(MediaSizeName.FOLIO);
        for (Paper paper : Paper.values()) {
            assertThat(SystemPrinterAccess.paperOf(SystemPrinterAccess.mediaOf(paper))).contains(paper);
        }
    }

    @Test
    void aMediaIsKnownByItsSizeWhateverItsName() {
        // like the media CUPS lists under its own names: a name of its own, with its size registered
        MediaSizeName customA4 = new MediaSizeName(0) {
            @Override
            public String toString() {
                return "iso_a4_210x297mm";
            }
        };
        assertThat(SystemPrinterAccess.paperOf(customA4)).as("no size registered for it yet").isEmpty();
        new MediaSize(210, 297, MediaSize.MM, customA4);
        assertThat(SystemPrinterAccess.paperOf(customA4)).contains(Paper.A4);
        assertThat(SystemPrinterAccess.paperOf(MediaSizeName.NA_LEGAL)).isEmpty();
        assertThat(SystemPrinterAccess.paperOf(MediaSizeName.ISO_A5)).isEmpty();
    }

    @Test
    void orientationsGoBothWays() {
        assertThat(SystemPrinterAccess.orientationRequestedOf(PageOrientation.PORTRAIT))
                .isEqualTo(OrientationRequested.PORTRAIT);
        assertThat(SystemPrinterAccess.orientationRequestedOf(PageOrientation.LANDSCAPE))
                .isEqualTo(OrientationRequested.LANDSCAPE);
        assertThat(SystemPrinterAccess.orientationOf(OrientationRequested.PORTRAIT))
                .isEqualTo(PageOrientation.PORTRAIT);
        assertThat(SystemPrinterAccess.orientationOf(OrientationRequested.REVERSE_PORTRAIT))
                .isEqualTo(PageOrientation.PORTRAIT);
        assertThat(SystemPrinterAccess.orientationOf(OrientationRequested.LANDSCAPE))
                .isEqualTo(PageOrientation.LANDSCAPE);
        assertThat(SystemPrinterAccess.orientationOf(OrientationRequested.REVERSE_LANDSCAPE))
                .isEqualTo(PageOrientation.LANDSCAPE);
    }

    @Test
    void theMarginIsACentimetreUnlessThePrinterNeedsMore() {
        MediaSize a4 = MediaSize.ISO.A4;
        assertThat(SystemPrinterAccess.marginPoints(a4, Optional.empty()))
                .isCloseTo(PageGeometry.CENTIMETRE_POINTS, within(1e-6));
        assertThat(SystemPrinterAccess.marginPoints(a4, Optional.of(a4Leaving(6, 6, 6, 6))))
                .as("0.6 cm on every side fits in the centimetre").isCloseTo(PageGeometry.CENTIMETRE_POINTS,
                        within(1e-6));
        assertThat(SystemPrinterAccess.marginPoints(a4, Optional.of(a4Leaving(3, 3, 3, 15))))
                .as("1.5 cm at the bottom grows every side").isCloseTo(15 * POINTS_PER_MILLIMETRE, within(0.01));
        assertThat(SystemPrinterAccess.marginPoints(a4, Optional.of(a4Leaving(0, 12, 0, 0))))
                .isCloseTo(12 * POINTS_PER_MILLIMETRE, within(0.01));
    }

    @Test
    void theRequestCarriesThePaperTheOrientationOneCopyTheJobNameAndTheNativeDialog() {
        PrintRequestAttributeSet request = SystemPrinterAccess.requestOf(new PrintSetup(Paper.LETTER,
                PageOrientation.LANDSCAPE, PageGeometry.CENTIMETRE_POINTS), DialogTypeSelection.NATIVE, "sumador");

        assertThat(request.get(javax.print.attribute.standard.Media.class)).isEqualTo(MediaSizeName.NA_LETTER);
        assertThat(request.get(OrientationRequested.class)).isEqualTo(OrientationRequested.LANDSCAPE);
        assertThat(request.get(Copies.class)).isEqualTo(new Copies(1));
        assertThat(request.get(DialogTypeSelection.class)).isEqualTo(DialogTypeSelection.NATIVE);
        assertThat(((JobName) request.get(JobName.class)).getValue()).as("the name in the printer's queue")
                .isEqualTo("sumador");
    }

    @Test
    void withoutAPageRangeEverySheetIsPrinted() {
        assertThat(SystemPrinterAccess.printedPages(new HashPrintRequestAttributeSet(), 4))
                .containsExactly(0, 1, 2, 3);
    }

    @Test
    void aPageRangeKeepsOnlyItsSheetsInsideTheDocument() {
        PrintRequestAttributeSet chosen = new HashPrintRequestAttributeSet();
        chosen.add(new PageRanges("2-3, 6-9"));

        assertThat(SystemPrinterAccess.printedPages(chosen, 7)).containsExactly(1, 2, 5, 6);
    }

    @Test
    void windowsAndMacGetTheirNativeDialogAndLinuxGetsJavas() {
        assertThat(SystemPrinterAccess.dialogTypeFor("Windows 11")).isEqualTo(DialogTypeSelection.NATIVE);
        assertThat(SystemPrinterAccess.dialogTypeFor("Mac OS X")).isEqualTo(DialogTypeSelection.NATIVE);
        assertThat(SystemPrinterAccess.dialogTypeFor("Linux")).isEqualTo(DialogTypeSelection.COMMON);
    }

    @Test
    void whatThePrinterChoseBecomesTheEffectiveSetup() {
        PrinterChoice choice = SystemPrinterAccess.choiceOf(chosen(MediaSizeName.NA_LETTER,
                OrientationRequested.LANDSCAPE), REQUESTED, media -> Optional.empty(), JOB);

        assertThat(choice).isInstanceOf(PrinterChoice.Chosen.class);
        PrinterChoice.Chosen chosen = (PrinterChoice.Chosen) choice;
        assertThat(chosen.job()).isSameAs(JOB);
        assertThat(chosen.effective().paper()).isEqualTo(Paper.LETTER);
        assertThat(chosen.effective().orientation()).isEqualTo(PageOrientation.LANDSCAPE);
        assertThat(chosen.effective().marginPoints()).isCloseTo(PageGeometry.CENTIMETRE_POINTS, within(1e-6));
    }

    @Test
    void theHardwareMarginOfTheChosenPaperGrowsTheMargin() {
        PrinterChoice choice = SystemPrinterAccess.choiceOf(chosen(MediaSizeName.ISO_A4,
                OrientationRequested.PORTRAIT), REQUESTED, media -> media == MediaSizeName.ISO_A4
                ? Optional.of(a4Leaving(4, 4, 4, 14)) : Optional.empty(), JOB);

        assertThat(((PrinterChoice.Chosen) choice).effective().marginPoints())
                .isCloseTo(14 * POINTS_PER_MILLIMETRE, within(0.01));
    }

    @Test
    void aPaperVsimDoesNotKnowIsNamedNotPrinted() {
        PrinterChoice choice = SystemPrinterAccess.choiceOf(chosen(MediaSizeName.NA_LEGAL,
                OrientationRequested.PORTRAIT), REQUESTED, media -> Optional.empty(), JOB);

        assertThat(choice).isInstanceOf(PrinterChoice.UnknownPaper.class);
        assertThat(((PrinterChoice.UnknownPaper) choice).name()).contains("na-legal").contains("216 × 356 mm");
    }

    @Test
    void withoutAPaperOrOrientationOrWithATrayTheRequestedOnesStay() {
        PrintSetup letterLandscape = new PrintSetup(Paper.LETTER, PageOrientation.LANDSCAPE,
                PageGeometry.CENTIMETRE_POINTS);
        PrinterChoice nothing = SystemPrinterAccess.choiceOf(new HashPrintRequestAttributeSet(), letterLandscape,
                media -> Optional.empty(), JOB);
        PrintRequestAttributeSet tray = new HashPrintRequestAttributeSet();
        tray.add(MediaTray.MANUAL);
        PrinterChoice fromATray = SystemPrinterAccess.choiceOf(tray, letterLandscape, media -> Optional.empty(), JOB);

        assertThat(((PrinterChoice.Chosen) nothing).effective()).isEqualTo(letterLandscape);
        assertThat(((PrinterChoice.Chosen) fromATray).effective()).isEqualTo(letterLandscape);
    }

    @Test
    void aPrintersDefaultMediaIsAPaperOnlyWhenItIsANamedSizeWeKnow() {
        assertThat(SystemPrinterAccess.paperOfDefault(MediaSizeName.NA_LETTER)).contains(Paper.LETTER);
        assertThat(SystemPrinterAccess.paperOfDefault(MediaSizeName.NA_LEGAL)).isEmpty();
        assertThat(SystemPrinterAccess.paperOfDefault(MediaTray.MAIN)).isEmpty();
        assertThat(SystemPrinterAccess.paperOfDefault(null)).isEmpty();
        assertThat(SystemPrinterAccess.defaultPaperOf(null)).isEmpty();
    }
}
