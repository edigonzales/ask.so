package ch.so.agi.rrb;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

@Service
class RrbFetchService {

    static final String PDF_MIME_TYPE = "application/pdf";

    private static final String USER_AGENT = "ask-so-mcp/0.1";

    private static final String DETAIL_TABLE_SELECTOR = "table.tx-rrbpublications.table.detailview";

    private static final String DOCUMENTS_LABEL = "Dokumente";

    private static final String MAIN_RRB_LINK_TEXT = "RRB";

    private static final Pattern FILENAME_PATTERN = Pattern.compile("filename\\*?=(?:UTF-8''|\\\")?([^\\\";]+)");

    private final HttpClient httpClient;

    private final RrbProperties properties;

    RrbFetchService(HttpClient httpClient, RrbProperties properties) {
        this.httpClient = httpClient;
        this.properties = properties;
    }

    RrbDocument fetch(int year, int rrbNumber) {
        validatePositive(year, "year");
        validatePositive(rrbNumber, "rrbNumber");

        URI detailPageUri = detailPageUri(year, rrbNumber);
        String detailHtml = fetchDetailPage(detailPageUri);
        String publicPdfUrl = extractPdfUrl(detailPageUri, detailHtml);
        PdfDownload pdfDownload = downloadPdf(publicPdfUrl, year, rrbNumber, detailPageUri.toString());

        return new RrbDocument(
                year,
                rrbNumber,
                detailPageUri.toString(),
                publicPdfUrl,
                resourceUri(year, rrbNumber),
                pdfDownload.filename(),
                pdfDownload.mimeType(),
                pdfDownload.bytes());
    }

    int parsePositiveInt(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw error("INVALID_INPUT", "%s ist erforderlich.".formatted(fieldName), Map.of("field", fieldName));
        }

        try {
            int parsed = Integer.parseInt(value.trim());
            validatePositive(parsed, fieldName);
            return parsed;
        }
        catch (NumberFormatException exception) {
            throw error(
                    "INVALID_INPUT",
                    "%s muss eine Ganzzahl sein.".formatted(fieldName),
                    Map.of("field", fieldName, "value", value));
        }
    }

    String resourceUri(int year, int rrbNumber) {
        return "rrb://so.ch/regierungsratsbeschluss/%d/%d/rrb.pdf".formatted(year, rrbNumber);
    }

    private URI detailPageUri(int year, int rrbNumber) {
        return properties.getBaseUri().resolve("/beschlussnummer/%d_%d/".formatted(year, rrbNumber));
    }

    private String fetchDetailPage(URI detailPageUri) {
        HttpResponse<String> response = send(detailPageUri, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8),
                "text/html");

        if (response.statusCode() == 404) {
            throw error("NOT_FOUND", "RRB-Seite wurde nicht gefunden.",
                    Map.of("sourcePageUrl", detailPageUri.toString(), "status", response.statusCode()));
        }

        if (response.statusCode() != 200) {
            throw error("UPSTREAM_UNAVAILABLE", "RRB-Seite konnte nicht geladen werden.",
                    Map.of("sourcePageUrl", detailPageUri.toString(), "status", response.statusCode()));
        }

        return response.body();
    }

    private String extractPdfUrl(URI detailPageUri, String detailHtml) {
        Document document = Jsoup.parse(detailHtml, detailPageUri.toString());
        Element detailTable = document.selectFirst(DETAIL_TABLE_SELECTOR);
        if (detailTable == null) {
            throw error("DOCUMENTS_ROW_MISSING", "Die Detailtabelle des RRB konnte nicht gefunden werden.",
                    Map.of("sourcePageUrl", detailPageUri.toString()));
        }

        Elements rows = detailTable.select("tr");
        for (Element row : rows) {
            Elements cells = row.select("> td");
            if (cells.size() < 2) {
                continue;
            }

            if (!normalize(cells.getFirst().text()).equals(DOCUMENTS_LABEL)) {
                continue;
            }

            Optional<Element> pdfLink = cells.get(1).select("a").stream()
                    .filter(link -> normalize(link.text()).equals(MAIN_RRB_LINK_TEXT))
                    .findFirst();

            if (pdfLink.isEmpty()) {
                throw error("RRB_LINK_MISSING", "Der Hauptlink 'RRB' wurde in der Dokumentenliste nicht gefunden.",
                        Map.of("sourcePageUrl", detailPageUri.toString()));
            }

            String resolvedUrl = pdfLink.get().absUrl("href");
            if (resolvedUrl == null || resolvedUrl.isBlank()) {
                throw error("RRB_LINK_MISSING", "Der gefundene 'RRB'-Link enthielt keine nutzbare URL.",
                        Map.of("sourcePageUrl", detailPageUri.toString()));
            }

            return resolvedUrl;
        }

        throw error("DOCUMENTS_ROW_MISSING", "Die Zeile 'Dokumente' wurde nicht gefunden.",
                Map.of("sourcePageUrl", detailPageUri.toString()));
    }

    private PdfDownload downloadPdf(String publicPdfUrl, int year, int rrbNumber, String sourcePageUrl) {
        URI pdfUri = URI.create(publicPdfUrl);
        HttpResponse<byte[]> response = send(pdfUri, HttpResponse.BodyHandlers.ofByteArray(), PDF_MIME_TYPE);

        if (response.statusCode() != 200) {
            throw error("PDF_FETCH_FAILED", "Das PDF konnte nicht geladen werden.",
                    Map.of(
                            "sourcePageUrl", sourcePageUrl,
                            "publicPdfUrl", publicPdfUrl,
                            "status", response.statusCode()));
        }

        byte[] bytes = response.body();
        String mimeType = response.headers().firstValue("content-type")
                .map(value -> value.split(";")[0].trim().toLowerCase(Locale.ROOT))
                .orElse("");

        if (!PDF_MIME_TYPE.equals(mimeType) && !hasPdfSignature(bytes)) {
            throw error("PDF_FETCH_FAILED", "Die heruntergeladene Datei ist kein gültiges PDF.",
                    Map.of(
                            "sourcePageUrl", sourcePageUrl,
                            "publicPdfUrl", publicPdfUrl,
                            "contentType", mimeType));
        }

        String filename = extractFilename(response.headers())
                .orElse("RRB__%d-%d.pdf".formatted(year, rrbNumber));

        return new PdfDownload(filename, PDF_MIME_TYPE, bytes);
    }

    private <T> HttpResponse<T> send(URI uri, HttpResponse.BodyHandler<T> bodyHandler, String accept) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(requestTimeout())
                .header("Accept", accept)
                .header("User-Agent", USER_AGENT)
                .build();

        try {
            return httpClient.send(request, bodyHandler);
        }
        catch (HttpTimeoutException exception) {
            throw error("UPSTREAM_UNAVAILABLE", "Zeitüberschreitung beim Abruf der RRB-Quelle.",
                    Map.of("uri", uri.toString()));
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw error("UPSTREAM_UNAVAILABLE", "Abruf der RRB-Quelle wurde unterbrochen.",
                    Map.of("uri", uri.toString()));
        }
        catch (IOException exception) {
            throw error("UPSTREAM_UNAVAILABLE", "Abruf der RRB-Quelle ist fehlgeschlagen.",
                    Map.of("uri", uri.toString()));
        }
    }

    private Duration requestTimeout() {
        return properties.getRequestTimeout();
    }

    private void validatePositive(int value, String fieldName) {
        if (value <= 0) {
            throw error("INVALID_INPUT", "%s muss grösser als 0 sein.".formatted(fieldName),
                    Map.of("field", fieldName, "value", value));
        }
    }

    private Optional<String> extractFilename(HttpHeaders headers) {
        return headers.firstValue("content-disposition")
                .flatMap(value -> {
                    Matcher matcher = FILENAME_PATTERN.matcher(value);
                    if (!matcher.find()) {
                        return Optional.empty();
                    }

                    String rawFilename = matcher.group(1).replace("\"", "");
                    return Optional.of(java.net.URLDecoder.decode(rawFilename, StandardCharsets.UTF_8));
                });
    }

    private boolean hasPdfSignature(byte[] bytes) {
        return bytes.length >= 4
                && bytes[0] == '%'
                && bytes[1] == 'P'
                && bytes[2] == 'D'
                && bytes[3] == 'F';
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private RrbFetchException error(String errorCode, String message, Map<String, Object> details) {
        return new RrbFetchException(errorCode, message, new LinkedHashMap<>(details));
    }

    private record PdfDownload(String filename, String mimeType, byte[] bytes) {
    }
}
