package ch.so.agi.rrb;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RrbTextExtractionServiceTests {

    private final RrbTextExtractionService textExtractionService = new RrbTextExtractionService(null);

    @Test
    void extractReturnsChunksPerPdfPage() {
        RrbTextDocument document = textExtractionService.extract(documentWithPdf(
                TestPdfFactory.createPdf(
                        "Beschluss Seite 1\nMassnahme A",
                        "Beschluss Seite 2\nMassnahme B")));

        assertThat(document.year()).isEqualTo(2026);
        assertThat(document.rrbNumber()).isEqualTo(292);
        assertThat(document.pageCount()).isEqualTo(2);
        assertThat(document.filename()).isEqualTo("RRB__2026-292.pdf");
        assertThat(document.chunks()).hasSize(2);
        assertThat(document.chunks().get(0).pageNumber()).isEqualTo(1);
        assertThat(document.chunks().get(0).text()).contains("Beschluss Seite 1", "Massnahme A");
        assertThat(document.chunks().get(1).pageNumber()).isEqualTo(2);
        assertThat(document.chunks().get(1).text()).contains("Beschluss Seite 2", "Massnahme B");
    }

    @Test
    void extractThrowsWhenNoTextCanBeFound() {
        assertThatThrownBy(() -> textExtractionService.extract(documentWithPdf(TestPdfFactory.createPdf("", ""))))
                .isInstanceOf(RrbFetchException.class)
                .extracting(throwable -> ((RrbFetchException) throwable).getErrorCode())
                .isEqualTo("NO_EXTRACTABLE_TEXT");
    }

    @Test
    void extractThrowsWhenPdfCannotBeRead() {
        byte[] invalidPdf = "%PDF-broken".getBytes();

        assertThatThrownBy(() -> textExtractionService.extract(documentWithPdf(invalidPdf)))
                .isInstanceOf(RrbFetchException.class)
                .extracting(throwable -> ((RrbFetchException) throwable).getErrorCode())
                .isEqualTo("TEXT_EXTRACTION_FAILED");
    }

    private RrbDocument documentWithPdf(byte[] pdfBytes) {
        return new RrbDocument(
                2026,
                292,
                "https://rrb.so.ch/beschlussnummer/2026_292/",
                "https://rrb.so.ch/beschlussnummer/2026_292/download/main-link/",
                "rrb://so.ch/regierungsratsbeschluss/2026/292/rrb.pdf",
                "RRB__2026-292.pdf",
                RrbFetchService.PDF_MIME_TYPE,
                pdfBytes);
    }
}
