package ch.so.agi.rrb;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.ai.mcp.server.stdio=false",
                "spring.main.web-application-type=servlet",
                "spring.ai.mcp.server.streamable-http.mcp-endpoint=/mcp"
        })
class RrbMcpHttpIntegrationTests {

    private static final byte[] PDF_BYTES = TestPdfFactory.createPdf(
            "Beschluss 2026/292 Seite 1\nMassnahme A",
            "Beschluss 2026/292 Seite 2\nMassnahme B");

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().findAndAddModules().build();

    private static HttpServer upstreamServer;

    @LocalServerPort
    private int port;

    @Value("${rrb.base-uri}")
    private String baseUri;

    @BeforeAll
    static void startUpstream() {
        ensureUpstreamStarted();
    }

    @AfterAll
    static void stopUpstream() {
        upstreamServer.stop(0);
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        ensureUpstreamStarted();
        registry.add("rrb.base-uri", () -> "http://localhost:%d".formatted(upstreamServer.getAddress().getPort()));
    }

    @Test
    void streamableHttpToolAndResourceEndpointsReturnResolvedPdf() throws Exception {
        String sessionId = initializeSession();
        sendNotification(sessionId, """
                {"jsonrpc":"2.0","method":"notifications/initialized"}
                """);

        JsonNode toolsList = sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
                """);
        JsonNode pdfTool = findByName(toolsList.at("/result/tools"), "get_rrb_pdf");
        assertThat(pdfTool.path("name").asText()).isEqualTo("get_rrb_pdf");
        assertThat(pdfTool.at("/inputSchema/properties/year/type").asText()).isEqualTo("integer");
        assertThat(pdfTool.at("/inputSchema/properties/rrbNumber/type").asText()).isEqualTo("integer");
        assertThat(pdfTool.at("/outputSchema/properties/pdfResourceUri/type").asText()).isEqualTo("string");
        assertThat(pdfTool.at("/outputSchema/properties/textResourceUri").isMissingNode()).isTrue();
        assertThat(pdfTool.at("/outputSchema/properties/pageTextUriTemplate").isMissingNode()).isTrue();
        assertThat(pdfTool.at("/outputSchema/properties/pageCount").isMissingNode()).isTrue();
        assertThat(pdfTool.at("/annotations/readOnlyHint").asBoolean()).isTrue();

        JsonNode textTool = findByName(toolsList.at("/result/tools"), "get_rrb_text");
        assertThat(textTool.path("name").asText()).isEqualTo("get_rrb_text");
        assertThat(textTool.at("/inputSchema/properties/year/type").asText()).isEqualTo("integer");
        assertThat(textTool.at("/inputSchema/properties/rrbNumber/type").asText()).isEqualTo("integer");
        assertThat(textTool.at("/outputSchema/properties/pageCount/type").asText()).isEqualTo("integer");
        assertThat(textTool.at("/outputSchema/properties/chunks/type").asText()).isEqualTo("array");
        assertThat(textTool.at("/outputSchema/properties/chunks/items/properties/pageNumber/type").asText())
                .isEqualTo("integer");
        assertThat(textTool.at("/annotations/readOnlyHint").asBoolean()).isTrue();

        JsonNode toolCall = sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"get_rrb_pdf","arguments":{"year":2026,"rrbNumber":292}}}
                """);
        assertThat(toolCall.at("/result/content/0/type").asText()).isEqualTo("text");
        assertThat(toolCall.at("/result/content/0/text").asText())
                .isEqualTo("RRB 2026/292 wurde gefunden; verwende jetzt die PDF-Resource.");
        assertThat(toolCall.at("/result/structuredContent/sourcePageUrl").asText())
                .isEqualTo("%s/beschlussnummer/2026_292/".formatted(baseUri));
        assertThat(toolCall.at("/result/structuredContent/publicPdfUrl").asText())
                .isEqualTo("%s/beschlussnummer/2026_292/download/main-link/".formatted(baseUri));
        assertThat(toolCall.at("/result/structuredContent/pdfResourceUri").asText())
                .isEqualTo("rrb://so.ch/regierungsratsbeschluss/2026/292/rrb.pdf");
        assertThat(toolCall.at("/result/structuredContent/pdfMimeType").asText()).isEqualTo("application/pdf");
        assertThat(toolCall.at("/result/structuredContent/textResourceUri").isMissingNode()).isTrue();
        assertThat(toolCall.at("/result/structuredContent/pageTextUriTemplate").isMissingNode()).isTrue();
        assertThat(toolCall.at("/result/content/1/type").asText()).isEqualTo("resource_link");
        assertThat(toolCall.at("/result/content/1/uri").asText())
                .isEqualTo("%s/beschlussnummer/2026_292/download/main-link/".formatted(baseUri));
        assertThat(toolCall.at("/result/content/2/type").asText()).isEqualTo("resource_link");
        assertThat(toolCall.at("/result/content/2/uri").asText())
                .isEqualTo("rrb://so.ch/regierungsratsbeschluss/2026/292/rrb.pdf");
        assertThat(toolCall.at("/result/content/3").isMissingNode()).isTrue();

        JsonNode textToolCall = sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":33,"method":"tools/call","params":{"name":"get_rrb_text","arguments":{"year":2026,"rrbNumber":292}}}
                """);
        assertThat(textToolCall.at("/result/content/0/type").asText()).isEqualTo("text");
        assertThat(textToolCall.at("/result/content/0/text").asText())
                .isEqualTo("RRB 2026/292 wurde als Text extrahiert; 2 Seiten bereit.");
        assertThat(textToolCall.at("/result/content/1/type").asText()).isEqualTo("text");
        assertThat(textToolCall.at("/result/content/1/text").asText())
                .isEqualTo("PDF-Link: %s/beschlussnummer/2026_292/download/main-link/".formatted(baseUri));
        assertThat(textToolCall.at("/result/content/2/type").asText()).isEqualTo("resource_link");
        assertThat(textToolCall.at("/result/content/2/uri").asText())
                .isEqualTo("%s/beschlussnummer/2026_292/download/main-link/".formatted(baseUri));
        assertThat(textToolCall.at("/result/content/3/type").asText()).isEqualTo("text");
        assertThat(textToolCall.at("/result/content/3/text").asText())
                .contains("Seite 1", "Beschluss 2026/292 Seite 1", "Massnahme A");
        assertThat(textToolCall.at("/result/content/4/type").asText()).isEqualTo("text");
        assertThat(textToolCall.at("/result/content/4/text").asText())
                .contains("Seite 2", "Beschluss 2026/292 Seite 2", "Massnahme B");
        assertThat(textToolCall.at("/result/structuredContent/sourcePageUrl").asText())
                .isEqualTo("%s/beschlussnummer/2026_292/".formatted(baseUri));
        assertThat(textToolCall.at("/result/structuredContent/publicPdfUrl").asText())
                .isEqualTo("%s/beschlussnummer/2026_292/download/main-link/".formatted(baseUri));
        assertThat(textToolCall.at("/result/structuredContent/filename").asText()).isEqualTo("RRB__2026-292.pdf");
        assertThat(textToolCall.at("/result/structuredContent/pageCount").asInt()).isEqualTo(2);
        assertThat(textToolCall.at("/result/structuredContent/chunks/0/pageNumber").asInt()).isEqualTo(1);
        assertThat(textToolCall.at("/result/structuredContent/chunks/0/text").asText())
                .contains("Beschluss 2026/292 Seite 1", "Massnahme A");
        assertThat(textToolCall.at("/result/structuredContent/chunks/1/pageNumber").asInt()).isEqualTo(2);
        assertThat(textToolCall.at("/result/structuredContent/chunks/1/text").asText())
                .contains("Beschluss 2026/292 Seite 2", "Massnahme B");

        JsonNode resourcesList = sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":4,"method":"resources/list","params":{}}
                """);
        assertThat(resourcesList.at("/result/resources").isArray()).isTrue();
        assertThat(resourcesList.at("/result/resources")).isEmpty();

        JsonNode resourceTemplatesList = sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":5,"method":"resources/templates/list","params":{}}
                """);
        assertThat(resourceTemplatesList.at("/result/resourceTemplates").size()).isEqualTo(1);
        JsonNode pdfTemplate = findByUriTemplate(
                resourceTemplatesList.at("/result/resourceTemplates"),
                "rrb://so.ch/regierungsratsbeschluss/{year}/{rrbNumber}/rrb.pdf");
        assertThat(pdfTemplate.at("/mimeType").asText()).isEqualTo("application/pdf");

        JsonNode pdfResourceRead = sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":6,"method":"resources/read","params":{"uri":"rrb://so.ch/regierungsratsbeschluss/2026/292/rrb.pdf"}}
                """);
        assertThat(pdfResourceRead.at("/result/contents/0/uri").asText())
                .isEqualTo("rrb://so.ch/regierungsratsbeschluss/2026/292/rrb.pdf");
        assertThat(pdfResourceRead.at("/result/contents/0/mimeType").asText()).isEqualTo("application/pdf");
        assertThat(Base64.getDecoder().decode(pdfResourceRead.at("/result/contents/0/blob").asText()))
                .isEqualTo(PDF_BYTES);
    }

    @Test
    void streamableHttpToolReturnsStructuredErrorMetadataForMissingRrb() throws Exception {
        String sessionId = initializeSession();
        sendNotification(sessionId, """
                {"jsonrpc":"2.0","method":"notifications/initialized"}
                """);

        JsonNode toolCall = sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"get_rrb_pdf","arguments":{"year":2026,"rrbNumber":999}}}
                """);

        assertThat(toolCall.at("/result/isError").asBoolean()).isTrue();
        assertThat(toolCall.at("/result/structuredContent/year").asInt()).isEqualTo(2026);
        assertThat(toolCall.at("/result/structuredContent/rrbNumber").asInt()).isEqualTo(999);
        assertThat(toolCall.at("/result/structuredContent/errorCode").asText()).isEqualTo("NOT_FOUND");
        assertThat(toolCall.at("/result/structuredContent/message").asText()).isNotBlank();
        assertThat(toolCall.at("/result/content/0/type").asText()).isEqualTo("text");
        assertThat(toolCall.at("/result/content/0/text").asText()).contains("RRB konnte nicht aufgeloest werden");

        JsonNode textToolCall = sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":17,"method":"tools/call","params":{"name":"get_rrb_text","arguments":{"year":2026,"rrbNumber":999}}}
                """);
        assertThat(textToolCall.at("/result/isError").asBoolean()).isTrue();
        assertThat(textToolCall.at("/result/structuredContent/year").asInt()).isEqualTo(2026);
        assertThat(textToolCall.at("/result/structuredContent/rrbNumber").asInt()).isEqualTo(999);
        assertThat(textToolCall.at("/result/structuredContent/errorCode").asText()).isEqualTo("NOT_FOUND");
        assertThat(textToolCall.at("/result/content/0/text").asText()).contains("RRB-Text konnte nicht extrahiert werden");
        assertThat(textToolCall.at("/result/content/1").isMissingNode()).isTrue();
    }

    @Test
    void removedTextResourcesReturnStandardResourceNotFound() throws Exception {
        String sessionId = initializeSession();
        sendNotification(sessionId, """
                {"jsonrpc":"2.0","method":"notifications/initialized"}
                """);

        JsonNode missingTextResource = sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":8,"method":"resources/read","params":{"uri":"rrb://so.ch/regierungsratsbeschluss/2026/292/rrb.txt"}}
                """);
        assertThat(missingTextResource.at("/error/code").asInt()).isEqualTo(McpSchema.ErrorCodes.RESOURCE_NOT_FOUND);
        assertThat(missingTextResource.at("/error/message").asText()).isEqualTo("Resource not found");

        JsonNode missingPageResource = sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":9,"method":"resources/read","params":{"uri":"rrb://so.ch/regierungsratsbeschluss/2026/292/pages/2.txt"}}
                """);
        assertThat(missingPageResource.at("/error/code").asInt()).isEqualTo(McpSchema.ErrorCodes.RESOURCE_NOT_FOUND);
        assertThat(missingPageResource.at("/error/message").asText()).isEqualTo("Resource not found");
    }

    private String initializeSession() throws Exception {
        HttpResponse<String> response = rawPost(null, """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test-client","version":"1.0"}}}
                """);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JSON_MAPPER.readTree(response.body()).at("/result/protocolVersion").asText()).isEqualTo("2025-06-18");

        return response.headers()
                .firstValue("Mcp-Session-Id")
                .orElseThrow(() -> new IllegalStateException("MCP-Session-ID fehlt."));
    }

    private void sendNotification(String sessionId, String payload) throws Exception {
        HttpResponse<String> response = rawPost(sessionId, payload);
        assertThat(response.statusCode()).isEqualTo(202);
    }

    private JsonNode sendRequest(String sessionId, String payload) throws Exception {
        HttpResponse<String> response = rawPost(sessionId, payload);
        assertThat(response.statusCode()).isEqualTo(200);
        return JSON_MAPPER.readTree(sseData(response.body()));
    }

    private HttpResponse<String> rawPost(String sessionId, String payload) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:%d/mcp".formatted(port)))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream");

        if (sessionId != null) {
            builder.header("Mcp-Session-Id", sessionId);
        }

        return HttpClient.newHttpClient().send(
                builder.POST(HttpRequest.BodyPublishers.ofString(payload)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String sseData(String body) {
        return body.lines()
                .filter(line -> line.startsWith("data:"))
                .map(line -> line.substring("data:".length()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Keine SSE-Daten im Response-Body gefunden."));
    }

    private JsonNode findByName(JsonNode items, String name) {
        for (JsonNode item : items) {
            if (name.equals(item.path("name").asText())) {
                return item;
            }
        }
        throw new IllegalStateException("Eintrag mit name=%s wurde nicht gefunden.".formatted(name));
    }

    private JsonNode findByUriTemplate(JsonNode items, String uriTemplate) {
        for (JsonNode item : items) {
            if (uriTemplate.equals(item.path("uriTemplate").asText())) {
                return item;
            }
        }
        throw new IllegalStateException("Template mit uriTemplate=%s wurde nicht gefunden.".formatted(uriTemplate));
    }

    private static HttpServer createUpstreamServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/beschlussnummer/2026_292/", exchange -> writeResponse(
                    exchange,
                    200,
                    "text/html; charset=UTF-8",
                    """
                    <!DOCTYPE html>
                    <html>
                    <body>
                      <table class="tx-rrbpublications table detailview">
                        <tr>
                          <td>Dokumente</td>
                          <td>
                            <ul>
                              <li><a href="/beschlussnummer/2026_292/download/main-link/">RRB</a></li>
                              <li><a href="/beschlussnummer/2026_292/download/beilage-01/">RRB-Beilage: 01</a></li>
                            </ul>
                          </td>
                        </tr>
                      </table>
                    </body>
                    </html>
                    """.getBytes(StandardCharsets.UTF_8)));
            server.createContext("/beschlussnummer/2026_292/download/main-link/", exchange -> writeResponse(
                    exchange,
                    200,
                    "application/pdf",
                    PDF_BYTES,
                    "Content-Disposition", "attachment; filename=\"RRB__2026-292.pdf\""));
            server.createContext("/beschlussnummer/2026_292/download/beilage-01/", exchange -> writeResponse(
                    exchange,
                    200,
                    "application/pdf",
                    PDF_BYTES,
                    "Content-Disposition", "attachment; filename=\"RRB-Beilage__2026-292-01.pdf\""));
            server.createContext("/beschlussnummer/2026_999/", exchange -> writeResponse(
                    exchange,
                    404,
                    "text/plain",
                    new byte[0]));
            return server;
        }
        catch (IOException exception) {
            throw new IllegalStateException("Stub-Upstream konnte nicht gestartet werden.", exception);
        }
    }

    private static void ensureUpstreamStarted() {
        if (upstreamServer != null) {
            return;
        }
        upstreamServer = createUpstreamServer();
        upstreamServer.start();
    }

    private static void writeResponse(HttpExchange exchange, int status, String contentType, byte[] body,
            String... extraHeaders) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        for (int index = 0; index + 1 < extraHeaders.length; index += 2) {
            exchange.getResponseHeaders().set(extraHeaders[index], extraHeaders[index + 1]);
        }
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }
}
