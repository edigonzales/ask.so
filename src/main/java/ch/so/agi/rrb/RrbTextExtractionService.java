package ch.so.agi.rrb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

@Service
class RrbTextExtractionService {

    private final RrbFetchService fetchService;

    RrbTextExtractionService(RrbFetchService fetchService) {
        this.fetchService = fetchService;
    }

    RrbTextDocument fetchText(int year, int rrbNumber) {
        return extract(fetchService.fetch(year, rrbNumber));
    }

    RrbTextDocument extract(RrbDocument document) {
        try (PDDocument pdfDocument = PDDocument.load(document.pdfBytes())) {
            PDFTextStripper textStripper = new PDFTextStripper();
            List<RrbTextChunk> chunks = new ArrayList<>();

            for (int pageNumber = 1; pageNumber <= pdfDocument.getNumberOfPages(); pageNumber++) {
                textStripper.setStartPage(pageNumber);
                textStripper.setEndPage(pageNumber);
                String extractedText = normalize(textStripper.getText(pdfDocument));
                chunks.add(new RrbTextChunk(pageNumber, extractedText));
            }

            boolean containsExtractableText = chunks.stream()
                    .map(RrbTextChunk::text)
                    .anyMatch(text -> !text.isBlank());

            if (!containsExtractableText) {
                throw error(
                        "NO_EXTRACTABLE_TEXT",
                        "Im PDF konnte kein extrahierbarer Text gefunden werden.",
                        Map.of(
                                "sourcePageUrl", document.sourcePageUrl(),
                                "publicPdfUrl", document.publicPdfUrl()));
            }

            return new RrbTextDocument(
                    document.year(),
                    document.rrbNumber(),
                    document.sourcePageUrl(),
                    document.publicPdfUrl(),
                    document.filename(),
                    pdfDocument.getNumberOfPages(),
                    chunks);
        }
        catch (RrbFetchException exception) {
            throw exception;
        }
        catch (IOException exception) {
            throw error(
                    "TEXT_EXTRACTION_FAILED",
                    "Das PDF konnte nicht als Text extrahiert werden.",
                    Map.of(
                            "sourcePageUrl", document.sourcePageUrl(),
                            "publicPdfUrl", document.publicPdfUrl()));
        }
    }

    private String normalize(String extractedText) {
        return extractedText
                .replace("\u0000", "")
                .replace("\f", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .lines()
                .map(String::stripTrailing)
                .collect(Collectors.joining("\n"))
                .trim();
    }

    private RrbFetchException error(String errorCode, String message, Map<String, Object> details) {
        return new RrbFetchException(errorCode, message, details);
    }
}
