package cl.almejo.vsim.ui.print;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Copies;
import javax.print.attribute.standard.DialogTypeSelection;
import javax.print.attribute.standard.JobName;
import javax.print.attribute.standard.Media;
import javax.print.attribute.standard.MediaPrintableArea;
import javax.print.attribute.standard.MediaSize;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.OrientationRequested;
import javax.print.attribute.standard.PageRanges;
import java.awt.Component;
import java.awt.print.PrinterAbortException;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * The real {@link PrinterAccess}: {@link PrinterJob} and the {@code javax.print} attributes (spec 2026-10-19 §4).
 *
 * <p>The printer dialog is asked for the paper and orientation the VSim dialog shows and one copy. What comes back is
 * the effective setup: the paper (recognised by its size, whatever the printer calls it), the orientation, and a
 * margin of at least 1 cm that grows to the printer's largest hardware margin for that paper (its
 * {@link MediaPrintableArea}). A margin typed in Java's own dialog is not used: VSim lays out its sheets itself.
 *
 * <p>Windows and macOS get their native dialog ({@link DialogTypeSelection#NATIVE}); elsewhere Java shows its own,
 * since asking it for a native one there shows Java's dialog on a fresh attribute set and the choices would not come
 * back. Copies, page range, duplex and colour stay in the attribute set and go to the printer untouched.
 */
public final class SystemPrinterAccess implements PrinterAccess {

    /** Papers whose sizes differ by less than this, in millimetres, are the same paper (8.5 × 13 in is Oficio). */
    static final double PAPER_TOLERANCE_MILLIMETRES = 2;
    private static final double POINTS_PER_MILLIMETRE = 72 / 25.4;

    /** A job accepted in {@link #choose}: the printer job and what the user chose in its dialog. */
    record SystemJob(PrinterJob printerJob, PrintRequestAttributeSet attributes) implements Job {

        SystemJob {
            Objects.requireNonNull(printerJob, "printerJob");
            Objects.requireNonNull(attributes, "attributes");
        }
    }

    private final Executor printThread;
    private final Executor lookupThread;

    /**
     * @param printThread  where the sheets are sent, one job at a time ({@link PrinterJob#print} blocks there)
     * @param lookupThread where the installed printers are looked up (slow with CUPS); never the EDT
     */
    public SystemPrinterAccess(Executor printThread, Executor lookupThread) {
        this.printThread = Objects.requireNonNull(printThread, "printThread");
        this.lookupThread = Objects.requireNonNull(lookupThread, "lookupThread");
    }

    @Override
    public CompletableFuture<Boolean> hasPrinters() {
        return CompletableFuture.supplyAsync(() -> PrintServiceLookup.lookupPrintServices(null, null).length > 0,
                lookupThread);
    }

    @Override
    public PrinterChoice choose(Component parent, PrintSetup requested, String jobName) {
        PrinterJob printerJob = PrinterJob.getPrinterJob();
        printerJob.setJobName(jobName);
        PrintRequestAttributeSet attributes = requestOf(requested,
                dialogTypeFor(System.getProperty("os.name", "")), jobName);
        if (!printerJob.printDialog(attributes)) {
            return new PrinterChoice.Declined();
        }
        PrintService service = printerJob.getPrintService();
        return choiceOf(attributes, requested, media -> printableArea(service, media),
                new SystemJob(printerJob, attributes));
    }

    @Override
    public CompletableFuture<Void> print(Job job, PagePainter pages, PrintSetup setup,
                                         PdfExporter.ProgressListener progress) {
        Objects.requireNonNull(pages, "pages");
        Objects.requireNonNull(setup, "setup");
        Objects.requireNonNull(progress, "progress");
        if (!(job instanceof SystemJob systemJob)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("not a job of this access: " + job));
        }
        CompletableFuture<Void> result = new CompletableFuture<>();
        try {
            printThread.execute(() -> {
                try {
                    send(systemJob, pages, setup, progress);
                    result.complete(null);
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            });
        } catch (RuntimeException rejection) {
            result.completeExceptionally(rejection);
        }
        return result;
    }

    private static void send(SystemJob job, PagePainter pages, PrintSetup setup,
                             PdfExporter.ProgressListener progress) throws PrinterException {
        int total = pages.pages().size();
        if (total == 0 || total > PdfExport.REFUSE_ABOVE_PAGES) {
            throw new IllegalArgumentException("no sheets or too many sheets to print: " + total);
        }
        PrinterJob printerJob = job.printerJob();
        printerJob.setPageable(new PrintPageable(pages, setup, progress, printerJob::cancel,
                printedPages(job.attributes(), total)));
        try {
            printerJob.print(job.attributes());
        } catch (PrinterAbortException aborted) {
            throw cancellation(aborted);
        } catch (PrinterException failure) {
            if (progress.cancelled()) {
                throw cancellation(failure);
            }
            throw failure;
        }
    }

    private static CancellationException cancellation(PrinterException cause) {
        CancellationException cancelled = new CancellationException("print cancelled");
        cancelled.initCause(cause);
        return cancelled;
    }

    /** The largest area {@code service} prints on {@code media}, if it says. */
    private static Optional<MediaPrintableArea> printableArea(PrintService service, MediaSizeName media) {
        if (service == null) {
            return Optional.empty();
        }
        PrintRequestAttributeSet onlyMedia = new HashPrintRequestAttributeSet();
        onlyMedia.add(media);
        Object values = service.getSupportedAttributeValues(MediaPrintableArea.class, null, onlyMedia);
        if (values instanceof MediaPrintableArea[] areas && areas.length > 0) {
            return Optional.of(areas[0]);
        }
        return Optional.empty();
    }

    // --- mapping, pure functions -------------------------------------------------------------------------------

    /** The {@code javax.print} name of {@code paper}; Oficio is 8.5 × 13 in («folio»), the Chilean 216 × 330 mm. */
    static MediaSizeName mediaOf(Paper paper) {
        return switch (paper) {
            case A4 -> MediaSizeName.ISO_A4;
            case LETTER -> MediaSizeName.NA_LETTER;
            case OFICIO -> MediaSizeName.FOLIO;
            case A3 -> MediaSizeName.ISO_A3;
        };
    }

    /** The paper of {@code media} by its registered size, in either orientation; empty for any other size. */
    static Optional<Paper> paperOf(MediaSizeName media) {
        MediaSize size = MediaSize.getMediaSizeForName(media);
        if (size == null) {
            return Optional.empty();
        }
        double width = size.getX(MediaSize.MM);
        double height = size.getY(MediaSize.MM);
        for (Paper paper : Paper.values()) {
            double paperWidth = paper.widthPoints() / POINTS_PER_MILLIMETRE;
            double paperHeight = paper.heightPoints() / POINTS_PER_MILLIMETRE;
            if (close(width, paperWidth) && close(height, paperHeight)
                    || close(width, paperHeight) && close(height, paperWidth)) {
                return Optional.of(paper);
            }
        }
        return Optional.empty();
    }

    private static boolean close(double first, double second) {
        return Math.abs(first - second) <= PAPER_TOLERANCE_MILLIMETRES;
    }

    static OrientationRequested orientationRequestedOf(PageOrientation orientation) {
        return switch (orientation) {
            case PORTRAIT -> OrientationRequested.PORTRAIT;
            case LANDSCAPE -> OrientationRequested.LANDSCAPE;
        };
    }

    /** Turned upside down or not, it is the same orientation on paper. */
    static PageOrientation orientationOf(OrientationRequested requested) {
        return requested == OrientationRequested.LANDSCAPE || requested == OrientationRequested.REVERSE_LANDSCAPE
                ? PageOrientation.LANDSCAPE : PageOrientation.PORTRAIT;
    }

    /** 1 cm, or the widest hardware margin of the four sides when the printer needs more, in points. */
    static double marginPoints(MediaSize media, Optional<MediaPrintableArea> printable) {
        double hardwareMillimetres = printable.map(area -> widestMarginMillimetres(media, area)).orElse(0.0);
        return Math.max(PageGeometry.CENTIMETRE_POINTS, hardwareMillimetres * POINTS_PER_MILLIMETRE);
    }

    private static double widestMarginMillimetres(MediaSize media, MediaPrintableArea area) {
        double width = media.getX(MediaSize.MM);
        double height = media.getY(MediaSize.MM);
        float[] printable = area.getPrintableArea(MediaPrintableArea.MM);
        double left = printable[0];
        double top = printable[1];
        double right = width - printable[0] - printable[2];
        double bottom = height - printable[1] - printable[3];
        return Math.max(0, Math.max(Math.max(left, top), Math.max(right, bottom)));
    }

    /**
     * The sheets the job prints, from 0: every one, or those of the page range chosen in the printer dialog that the
     * document has («2-3» of 4 sheets is sheets 1 and 2).
     */
    static List<Integer> printedPages(PrintRequestAttributeSet chosen, int total) {
        if (!(chosen.get(PageRanges.class) instanceof PageRanges ranges)) {
            return IntStream.range(0, total).boxed().toList();
        }
        return IntStream.range(0, total).filter(pageIndex -> ranges.contains(pageIndex + 1)).boxed().toList();
    }

    /** The native printer dialog on Windows and macOS, Java's own elsewhere (see the class comment). */
    static DialogTypeSelection dialogTypeFor(String osName) {
        String name = osName.toLowerCase(Locale.ROOT);
        return name.startsWith("windows") || name.startsWith("mac") ? DialogTypeSelection.NATIVE
                : DialogTypeSelection.COMMON;
    }

    /**
     * What the printer dialog starts with: the paper and orientation of {@code requested}, one copy, and {@code
     * jobName} as the name the printer's queue shows.
     */
    static PrintRequestAttributeSet requestOf(PrintSetup requested, DialogTypeSelection dialogType, String jobName) {
        PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
        attributes.add(mediaOf(requested.paper()));
        attributes.add(orientationRequestedOf(requested.orientation()));
        attributes.add(new Copies(1));
        attributes.add(dialogType);
        attributes.add(new JobName(jobName, Locale.ROOT));
        return attributes;
    }

    /**
     * The effective setup of what the user accepted in the printer dialog. A paper source (a tray) or no paper at
     * all keeps the requested paper; a paper of another size is {@link PrinterChoice.UnknownPaper}.
     *
     * @param printableArea the printer's largest printable area on a paper, if it says
     */
    static PrinterChoice choiceOf(PrintRequestAttributeSet chosen, PrintSetup requested,
                                  Function<MediaSizeName, Optional<MediaPrintableArea>> printableArea, Job job) {
        MediaSizeName media = chosen.get(Media.class) instanceof MediaSizeName name ? name
                : mediaOf(requested.paper());
        Optional<Paper> paper = paperOf(media);
        if (paper.isEmpty()) {
            return new PrinterChoice.UnknownPaper(describe(media));
        }
        PageOrientation orientation = chosen.get(OrientationRequested.class) instanceof OrientationRequested turned
                ? orientationOf(turned) : requested.orientation();
        MediaSize size = MediaSize.getMediaSizeForName(media);
        return new PrinterChoice.Chosen(new PrintSetup(paper.get(), orientation,
                marginPoints(size, printableArea.apply(media))), job);
    }

    /** «na-legal (216 × 356 mm)», or only the name when no size is registered for it. */
    private static String describe(MediaSizeName media) {
        MediaSize size = MediaSize.getMediaSizeForName(media);
        return size == null ? media.toString() : String.format(Locale.ROOT, "%s (%.0f × %.0f mm)", media,
                size.getX(MediaSize.MM), size.getY(MediaSize.MM));
    }
}
