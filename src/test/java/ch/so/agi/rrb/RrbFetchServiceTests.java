package ch.so.agi.rrb;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RrbFetchServiceTests {

    private static final byte[] PDF_BYTES = "%PDF-1.7\nstub\n".getBytes(StandardCharsets.ISO_8859_1);

    private HttpServer upstreamServer;

    private RrbFetchService fetchService;

    @BeforeEach
    void setUp() throws IOException {
        upstreamServer = HttpServer.create(new InetSocketAddress(0), 0);
        upstreamServer.start();

        RrbProperties properties = new RrbProperties();
        properties.setBaseUri(URI.create("http://localhost:%d".formatted(upstreamServer.getAddress().getPort())));
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setRequestTimeout(Duration.ofSeconds(2));

        fetchService = new RrbFetchService(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                properties);
    }

    @AfterEach
    void tearDown() {
        upstreamServer.stop(0);
    }

    @Test
    void fetchReturnsResolvedMainPdf() {
        registerSingleDocument(2026, 292, "main-link");

        RrbDocument document = fetchService.fetch(2026, 292);

        assertThat(document.year()).isEqualTo(2026);
        assertThat(document.rrbNumber()).isEqualTo(292);
        assertThat(document.sourcePageUrl()).isEqualTo(sourceUrl(2026, 292));
        assertThat(document.publicPdfUrl()).isEqualTo(pdfUrl(2026, 292, "main-link"));
        assertThat(document.pdfResourceUri()).isEqualTo("rrb://so.ch/regierungsratsbeschluss/2026/292/rrb.pdf");
        assertThat(document.filename()).isEqualTo("RRB__2026-292.pdf");
        assertThat(document.pdfMimeType()).isEqualTo("application/pdf");
        assertThat(document.pdfBytes()).isEqualTo(PDF_BYTES);
    }

    @Test
    void fetchPrefersExactRrbLinkWhenMultipleDocumentsExist() {
        registerHtml(
                "/beschlussnummer/2023_904/",
                detailPageWithDocuments(
                        """
                        <li><a href="/beschlussnummer/2023_904/download/main-rrb/">RRB</a></li>
                        <li><a href="/beschlussnummer/2023_904/download/beilage-01/">RRB-Beilage: 01</a></li>
                        <li><a href="/beschlussnummer/2023_904/download/beilage-02/">RRB-Beilage: 02</a></li>
                        """));
        registerPdf("/beschlussnummer/2023_904/download/main-rrb/", "RRB__2023-904.pdf");
        registerPdf("/beschlussnummer/2023_904/download/beilage-01/", "RRB-Beilage__2023-904-01.pdf");
        registerPdf("/beschlussnummer/2023_904/download/beilage-02/", "RRB-Beilage__2023-904-02.pdf");

        RrbDocument document = fetchService.fetch(2023, 904);

        assertThat(document.publicPdfUrl()).isEqualTo(pdfUrl(2023, 904, "main-rrb"));
        assertThat(document.filename()).isEqualTo("RRB__2023-904.pdf");
    }

    @Test
    void fetchThrowsNotFoundForMissingDetailPage() {
        registerEmpty("/beschlussnummer/2026_999999/", 404);

        assertThatThrownBy(() -> fetchService.fetch(2026, 999999))
                .isInstanceOf(RrbFetchException.class)
                .extracting(throwable -> ((RrbFetchException) throwable).getErrorCode())
                .isEqualTo("NOT_FOUND");
    }

    @Test
    void fetchThrowsWhenMainRrbLinkIsMissing() {
        registerHtml(
                "/beschlussnummer/2024_12/",
                detailPageWithDocuments("<li><a href=\"/beschlussnummer/2024_12/download/beilage-01/\">RRB-Beilage: 01</a></li>"));

        assertThatThrownBy(() -> fetchService.fetch(2024, 12))
                .isInstanceOf(RrbFetchException.class)
                .extracting(throwable -> ((RrbFetchException) throwable).getErrorCode())
                .isEqualTo("RRB_LINK_MISSING");
    }

    @Test
    void fetchRejectsInvalidPdfBody() {
        registerHtml(
                "/beschlussnummer/2024_50/",
                detailPageWithDocuments("<li><a href=\"/beschlussnummer/2024_50/download/main/\">RRB</a></li>"));
        register("/beschlussnummer/2024_50/download/main/", 200, "application/octet-stream",
                "not-a-pdf".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> fetchService.fetch(2024, 50))
                .isInstanceOf(RrbFetchException.class)
                .extracting(throwable -> ((RrbFetchException) throwable).getErrorCode())
                .isEqualTo("PDF_FETCH_FAILED");
    }

    private void registerSingleDocument(int year, int rrbNumber, String token) {
        registerHtml(
                "/beschlussnummer/%d_%d/".formatted(year, rrbNumber),
                detailPageWithDocuments("<li><a href=\"/beschlussnummer/%d_%d/download/%s/\">RRB</a></li>"
                        .formatted(year, rrbNumber, token)));
        registerPdf("/beschlussnummer/%d_%d/download/%s/".formatted(year, rrbNumber, token),
                "RRB__%d-%d.pdf".formatted(year, rrbNumber));
    }

    private String detailPageWithDocuments(String listItems) {
        return """
                <!DOCTYPE html>
                <html>
                <body>
                  <table class="tx-rrbpublications table detailview">
                    <tr>
                      <td>Dokumente</td>
                      <td><ul>%s</ul></td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(listItems);
    }

    private void registerHtml(String path, String html) {
        register(path, 200, "text/html; charset=UTF-8", html.getBytes(StandardCharsets.UTF_8));
    }

    private void registerPdf(String path, String filename) {
        register(path, 200, "application/pdf", PDF_BYTES, "Content-Disposition", "attachment; filename=\"%s\"".formatted(filename));
    }

    private void registerEmpty(String path, int status) {
        register(path, status, "text/plain", new byte[0]);
    }

    private void register(String path, int status, String contentType, byte[] body, String... extraHeaders) {
        upstreamServer.createContext(path, exchange -> writeResponse(exchange, status, contentType, body, extraHeaders));
    }

    private void writeResponse(HttpExchange exchange, int status, String contentType, byte[] body, String... extraHeaders)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        for (int index = 0; index + 1 < extraHeaders.length; index += 2) {
            exchange.getResponseHeaders().set(extraHeaders[index], extraHeaders[index + 1]);
        }
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    private String sourceUrl(int year, int rrbNumber) {
        return "http://localhost:%d/beschlussnummer/%d_%d/"
                .formatted(upstreamServer.getAddress().getPort(), year, rrbNumber);
    }

    private String pdfUrl(int year, int rrbNumber, String token) {
        return "http://localhost:%d/beschlussnummer/%d_%d/download/%s/"
                .formatted(upstreamServer.getAddress().getPort(), year, rrbNumber, token);
    }
}
