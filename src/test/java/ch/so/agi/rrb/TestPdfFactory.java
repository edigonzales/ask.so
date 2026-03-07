package ch.so.agi.rrb;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

final class TestPdfFactory {

    private TestPdfFactory() {
    }

    static byte[] createPdf(String... pageTexts) {
        String[] pages = pageTexts.length == 0 ? new String[] {""} : pageTexts;

        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            for (String pageText : pages) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);

                if (pageText == null || pageText.isBlank()) {
                    continue;
                }

                try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                    contentStream.beginText();
                    contentStream.setFont(PDType1Font.HELVETICA, 12);
                    contentStream.newLineAtOffset(72, 780);

                    for (String line : pageText.split("\\R", -1)) {
                        contentStream.showText(line);
                        contentStream.newLineAtOffset(0, -16);
                    }

                    contentStream.endText();
                }
            }

            document.save(outputStream);
            return outputStream.toByteArray();
        }
        catch (IOException exception) {
            throw new UncheckedIOException("Test-PDF konnte nicht erzeugt werden.", exception);
        }
    }
}
