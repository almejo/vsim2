package cl.almejo.vsim.ui.print;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Reads the PDFs the print tests write, through PDFBox (a test-only dependency). Page indices start at 0. */
final class PdfReading {

    /** Path construction and painting operators (PDF 32000 §8.5.2, §8.5.3). */
    private static final Set<String> PATH_OPERATORS = Set.of(
            "m", "l", "c", "v", "y", "h", "re", "S", "s", "f", "F", "f*", "B", "B*", "b", "b*", "n");

    private PdfReading() {
    }

    static int pageCount(Path file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            return document.getNumberOfPages();
        }
    }

    static PdfPages.PageSize pageSize(Path file, int pageIndex) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            PDRectangle mediaBox = document.getPage(pageIndex).getMediaBox();
            return new PdfPages.PageSize(mediaBox.getWidth(), mediaBox.getHeight());
        }
    }

    static boolean hasImages(Path file, int pageIndex) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            PDResources resources = document.getPage(pageIndex).getResources();
            if (resources == null) {
                return false;
            }
            for (COSName name : resources.getXObjectNames()) {
                PDXObject object = resources.getXObject(name);
                if (object instanceof PDImageXObject) {
                    return true;
                }
            }
            return false;
        }
    }

    /** Every operator of the page's content stream, in order (e.g. {@code re}, {@code f}, {@code d}). */
    static List<String> operators(Path file, int pageIndex) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            PDPage page = document.getPage(pageIndex);
            List<String> result = new ArrayList<>();
            for (Object token : new PDFStreamParser(page).parse()) {
                if (token instanceof Operator operator) {
                    result.add(operator.getName());
                }
            }
            return result;
        }
    }

    static long pathOperatorCount(Path file, int pageIndex) throws IOException {
        return operators(file, pageIndex).stream().filter(PATH_OPERATORS::contains).count();
    }

    /** The fill alphas ({@code ca}) of the page's extended graphics states; empty when everything is opaque. */
    static List<Float> fillAlphas(Path file, int pageIndex) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            PDResources resources = document.getPage(pageIndex).getResources();
            List<Float> result = new ArrayList<>();
            if (resources != null) {
                for (COSName name : resources.getExtGStateNames()) {
                    PDExtendedGraphicsState state = resources.getExtGState(name);
                    if (state != null && state.getNonStrokingAlphaConstant() != null) {
                        result.add(state.getNonStrokingAlphaConstant());
                    }
                }
            }
            return result;
        }
    }

    /** The page's extractable text; empty when the text was drawn as shapes. */
    static String text(Path file, int pageIndex) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(pageIndex + 1);
            stripper.setEndPage(pageIndex + 1);
            return stripper.getText(document).strip();
        }
    }

    /** The page rasterised at {@code pixelsPerPoint} (1 = 72 dpi), for checking where things landed. */
    static BufferedImage render(Path file, int pageIndex, float pixelsPerPoint) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            return new PDFRenderer(document).renderImage(pageIndex, pixelsPerPoint);
        }
    }
}
