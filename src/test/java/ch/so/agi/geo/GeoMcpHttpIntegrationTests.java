package ch.so.agi.geo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.ai.mcp.server.stdio=false",
                "spring.main.web-application-type=servlet",
                "spring.ai.mcp.server.streamable-http.mcp-endpoint=/mcp"
        })
class GeoMcpHttpIntegrationTests {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().findAndAddModules().build();

    @LocalServerPort
    private int port;

    @Test
    void streamableHttpToolsExposeGeoChainableDummyResults() throws Exception {
        String sessionId = initializeSession();
        sendNotification(sessionId, """
                {"jsonrpc":"2.0","method":"notifications/initialized"}
                """);

        JsonNode toolsList = sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
                """);
        JsonNode egridTool = findByName(toolsList.at("/result/tools"), "getEgridByNumberAndMunicipality");
        assertThat(egridTool.at("/inputSchema/properties/nummer/type").asText()).isEqualTo("string");
        assertThat(egridTool.at("/inputSchema/properties/gemeindename/type").asText()).isEqualTo("string");
        assertThat(egridTool.at("/annotations/readOnlyHint").asBoolean()).isTrue();

        JsonNode oerebTool = findByName(toolsList.at("/result/tools"), "getOerebExtractById");
        assertThat(oerebTool.at("/inputSchema/properties/egrid/type").asText()).isEqualTo("string");
        assertThat(oerebTool.at("/outputSchema/properties/publicPdfUrl/type").asText()).isEqualTo("string");
        assertThat(oerebTool.at("/annotations/readOnlyHint").asBoolean()).isTrue();

        JsonNode egridCall = sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"getEgridByNumberAndMunicipality","arguments":{"nummer":"168","gemeindename":"Solothurn"}}}
                """);
        assertThat(egridCall.at("/result/isError").asBoolean()).isFalse();
        assertThat(egridCall.at("/result/structuredContent/nummer").asText()).isEqualTo("168");
        assertThat(egridCall.at("/result/structuredContent/gemeindename").asText()).isEqualTo("Solothurn");
        assertThat(egridCall.at("/result/structuredContent/egrid").asText()).isEqualTo(GeoDummyService.DUMMY_EGRID);
        assertThat(egridCall.at("/result/content/0/type").asText()).isEqualTo("text");
        assertThat(egridCall.at("/result/content/0/text").asText())
                .isEqualTo("EGRID: %s".formatted(GeoDummyService.DUMMY_EGRID));

        String egrid = egridCall.at("/result/structuredContent/egrid").asText();
        JsonNode oerebCall = sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"getOerebExtractById","arguments":{"egrid":"%s"}}}
                """.formatted(egrid));
        assertThat(oerebCall.at("/result/isError").asBoolean()).isFalse();
        assertThat(oerebCall.at("/result/structuredContent/egrid").asText()).isEqualTo(GeoDummyService.DUMMY_EGRID);
        assertThat(oerebCall.at("/result/structuredContent/publicPdfUrl").asText())
                .isEqualTo("https://geo.so.ch/api/oereb/extract/pdf/?EGRID=%s".formatted(GeoDummyService.DUMMY_EGRID));
        assertThat(oerebCall.at("/result/structuredContent/mimeType").asText()).isEqualTo("application/pdf");
        assertThat(oerebCall.at("/result/content/0/type").asText()).isEqualTo("text");
        assertThat(oerebCall.at("/result/content/0/text").asText())
                .isEqualTo("PDF-Link: https://geo.so.ch/api/oereb/extract/pdf/?EGRID=%s".formatted(GeoDummyService.DUMMY_EGRID));
        assertThat(oerebCall.at("/result/content/1/type").asText()).isEqualTo("resource_link");
        assertThat(oerebCall.at("/result/content/1/uri").asText())
                .isEqualTo("https://geo.so.ch/api/oereb/extract/pdf/?EGRID=%s".formatted(GeoDummyService.DUMMY_EGRID));
        assertThat(oerebCall.at("/result/content/2").isMissingNode()).isTrue();
    }

    @Test
    void streamableHttpToolsReturnStructuredInvalidInputErrors() throws Exception {
        String sessionId = initializeSession();
        sendNotification(sessionId, """
                {"jsonrpc":"2.0","method":"notifications/initialized"}
                """);

        JsonNode missingNummer = sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"getEgridByNumberAndMunicipality","arguments":{"nummer":"","gemeindename":"Solothurn"}}}
                """);
        assertThat(missingNummer.at("/result/isError").asBoolean()).isTrue();
        assertThat(missingNummer.at("/result/structuredContent/errorCode").asText()).isEqualTo("INVALID_INPUT");
        assertThat(missingNummer.at("/result/structuredContent/gemeindename").asText()).isEqualTo("Solothurn");
        assertThat(missingNummer.at("/result/content/0/text").asText()).contains("EGRID konnte nicht ermittelt werden");

        JsonNode missingGemeindename = sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":6,"method":"tools/call","params":{"name":"getEgridByNumberAndMunicipality","arguments":{"nummer":"168","gemeindename":""}}}
                """);
        assertThat(missingGemeindename.at("/result/isError").asBoolean()).isTrue();
        assertThat(missingGemeindename.at("/result/structuredContent/errorCode").asText()).isEqualTo("INVALID_INPUT");
        assertThat(missingGemeindename.at("/result/structuredContent/nummer").asText()).isEqualTo("168");

        JsonNode missingEgrid = sendRequest(sessionId, """
                {"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"getOerebExtractById","arguments":{"egrid":""}}}
                """);
        assertThat(missingEgrid.at("/result/isError").asBoolean()).isTrue();
        assertThat(missingEgrid.at("/result/structuredContent/errorCode").asText()).isEqualTo("INVALID_INPUT");
        assertThat(missingEgrid.at("/result/content/0/text").asText()).contains("OEREB-PDF-Link konnte nicht erzeugt werden");
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
}
