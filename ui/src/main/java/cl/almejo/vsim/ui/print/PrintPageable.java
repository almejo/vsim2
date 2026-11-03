package cl.almejo.vsim.ui.print;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.print.PageFormat;
import java.awt.print.Pageable;
import java.awt.print.Printable;
import java.awt.print.PrinterAbortException;
import java.awt.print.PrinterException;
import java.util.List;
import java.util.Objects;

/**
 * The sheets of a {@link PagePainter} as a printer job asks for them (spec 2026-10-19 §4): as many pages as the
 * painter has, each on the {@link PrintSetup}'s paper and orientation with the margin as its imageable area, painted
 * by the same {@link PagePainter#paint} as the PDF and the preview.
 *
 * <p>Java hands {@link #print} a graphics already in points with the origin at the sheet's top-left corner and, for a
 * landscape {@link PageFormat}, already turned; the painter only composes transforms on it, so nothing is turned
 * twice. That graphics also carries the printer's resolution, the landscape turn and, on a page painted in raster
 * bands, a mirror, which the painters would read as a turned part: they get it through {@link SheetGraphics}, which
 * answers like the PDF's graphics, so the paper shows the PDF's sheet. A printer may ask for the same page several times (banding): painting is idempotent, and the progress counts
 * the highest page asked so far. «Cancelar» is checked each time a page is asked: the job is cancelled and a
 * {@link PrinterAbortException} ends it.
 */
final class PrintPageable implements Pageable, Printable {

    private final PagePainter painter;
    private final List<PagePainter.Page> pages;
    private final PageFormat format;
    private final PdfExporter.ProgressListener progress;
    private final Runnable cancelJob;
    /** The highest page asked so far; -1 before the first. Only the printing thread touches it. */
    private int highestPageAsked = -1;

    /**
     * @param cancelJob tells the printer job to stop, when {@code progress} says «Cancelar» was pressed
     */
    PrintPageable(PagePainter painter, PrintSetup setup, PdfExporter.ProgressListener progress, Runnable cancelJob) {
        this.painter = Objects.requireNonNull(painter, "painter");
        this.pages = painter.pages();
        this.format = pageFormat(Objects.requireNonNull(setup, "setup"));
        this.progress = Objects.requireNonNull(progress, "progress");
        this.cancelJob = Objects.requireNonNull(cancelJob, "cancelJob");
    }

    /** The paper of {@code setup} (described portrait, as Java wants it), turned by its orientation. */
    static PageFormat pageFormat(PrintSetup setup) {
        java.awt.print.Paper paper = new java.awt.print.Paper();
        double width = setup.paper().widthPoints();
        double height = setup.paper().heightPoints();
        double margin = setup.marginPoints();
        paper.setSize(width, height);
        paper.setImageableArea(margin, margin, width - 2 * margin, height - 2 * margin);
        PageFormat result = new PageFormat();
        result.setPaper(paper);
        result.setOrientation(setup.orientation() == PageOrientation.LANDSCAPE
                ? PageFormat.LANDSCAPE : PageFormat.PORTRAIT);
        return result;
    }

    @Override
    public int getNumberOfPages() {
        return pages.size();
    }

    @Override
    public PageFormat getPageFormat(int pageIndex) {
        Objects.checkIndex(pageIndex, pages.size());
        return (PageFormat) format.clone();
    }

    @Override
    public Printable getPrintable(int pageIndex) {
        Objects.checkIndex(pageIndex, pages.size());
        return this;
    }

    @Override
    public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {
        if (pageIndex < 0 || pageIndex >= pages.size()) {
            return NO_SUCH_PAGE;
        }
        if (progress.cancelled()) {
            cancelJob.run();
            throw new PrinterAbortException("print cancelled");
        }
        if (pageIndex > highestPageAsked) {
            highestPageAsked = pageIndex;
            progress.pageDone(pageIndex + 1, pages.size());
        }
        Graphics2D sheet = SheetGraphics.over((Graphics2D) graphics);
        painter.paint(sheet, pages.get(pageIndex).layoutIndex());
        return PAGE_EXISTS;
    }
}
