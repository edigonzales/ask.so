package ch.so.agi.rrb;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RrbMcpToolConfiguration {

    private static final McpJsonMapper MCP_JSON_MAPPER = McpJsonMapper.createDefault();

    private static final String PDF_TOOL_NAME = "get_rrb_pdf";

    private static final String PDF_TOOL_TITLE = "RRB (Regierungsratsbeschluss) PDF";

    private static final String PDF_TOOL_DESCRIPTION = "Loest den Solothurner Hauptbeschluss anhand von Jahr und RRB-Nummer auf und liefert das Original-PDF fuer Download und Referenz.";

    private static final String TEXT_TOOL_NAME = "get_rrb_text";

    private static final String TEXT_TOOL_TITLE = "RRB (Regierungsratsbeschluss) Text";

    private static final String TEXT_TOOL_DESCRIPTION = "Extrahiert den Text des Solothurner Hauptbeschlusses seitenweise aus dem Original-PDF und liefert zusaetzlich den verifizierten Link auf das Originaldokument.";

    private static final String YEAR_DESCRIPTION = "Vierstelliges Jahr des Regierungsratsbeschlusses.";

    private static final String RRB_NUMBER_DESCRIPTION = "RRB-Nummer innerhalb des angegebenen Jahres.";

    @Bean
    List<McpServerFeatures.SyncToolSpecification> getRrbToolSpecifications(RrbMcpHandler handler) {
        return List.of(
                getRrbPdfToolSpecification(handler),
                getRrbTextToolSpecification(handler));
    }

    private McpServerFeatures.SyncToolSpecification getRrbPdfToolSpecification(RrbMcpHandler handler) {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name(PDF_TOOL_NAME)
                .title(PDF_TOOL_TITLE)
                .description(PDF_TOOL_DESCRIPTION)
                .inputSchema(MCP_JSON_MAPPER, inputSchemaJson())
                .outputSchema(pdfOutputSchema())
                .annotations(new McpSchema.ToolAnnotations(
                        PDF_TOOL_TITLE,
                        Boolean.TRUE,
                        Boolean.FALSE,
                        Boolean.TRUE,
                        Boolean.TRUE,
                        null))
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> handler.handleGetRrbPdf(request.arguments()))
                .build();
    }

    private McpServerFeatures.SyncToolSpecification getRrbTextToolSpecification(RrbMcpHandler handler) {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name(TEXT_TOOL_NAME)
                .title(TEXT_TOOL_TITLE)
                .description(TEXT_TOOL_DESCRIPTION)
                .inputSchema(MCP_JSON_MAPPER, inputSchemaJson())
                .outputSchema(textOutputSchema())
                .annotations(new McpSchema.ToolAnnotations(
                        TEXT_TOOL_TITLE,
                        Boolean.TRUE,
                        Boolean.FALSE,
                        Boolean.TRUE,
                        Boolean.TRUE,
                        null))
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> handler.handleGetRrbText(request.arguments()))
                .build();
    }

    private String inputSchemaJson() {
        try {
            return MCP_JSON_MAPPER.writeValueAsString(inputSchema());
        }
        catch (IOException exception) {
            throw new IllegalStateException("Input-Schema fuer die RRB-Tools konnte nicht serialisiert werden.", exception);
        }
    }

    private Map<String, Object> inputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("year", integerProperty(YEAR_DESCRIPTION));
        properties.put("rrbNumber", integerProperty(RRB_NUMBER_DESCRIPTION));

        schema.put("properties", properties);
        schema.put("required", List.of("year", "rrbNumber"));
        return schema;
    }

    private Map<String, Object> pdfOutputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("year", integerProperty(YEAR_DESCRIPTION));
        properties.put("rrbNumber", integerProperty(RRB_NUMBER_DESCRIPTION));
        properties.put("sourcePageUrl", stringProperty("Öffentliche Detailseite des Beschlusses."));
        properties.put("publicPdfUrl", stringProperty("Direkter öffentlicher Download-Link des Hauptdokuments."));
        properties.put("pdfResourceUri", stringProperty("MCP-Resource-URI für das PDF."));
        properties.put("pdfMimeType", stringProperty("MIME-Type des PDF-Dokuments."));
        properties.put("errorCode", stringProperty("Stabiler Fehlercode bei fehlgeschlagenem Abruf."));
        properties.put("message", stringProperty("Menschenlesbare Fehlerbeschreibung."));

        schema.put("properties", properties);
        schema.put("required", List.of("year", "rrbNumber"));
        return schema;
    }

    private Map<String, Object> textOutputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("year", integerProperty(YEAR_DESCRIPTION));
        properties.put("rrbNumber", integerProperty(RRB_NUMBER_DESCRIPTION));
        properties.put("sourcePageUrl", stringProperty("Öffentliche Detailseite des Beschlusses."));
        properties.put("publicPdfUrl", stringProperty("Direkter öffentlicher Download-Link des Hauptdokuments."));
        properties.put("filename", stringProperty("Dateiname des Hauptdokuments."));
        properties.put("pageCount", integerProperty("Anzahl extrahierter PDF-Seiten."));
        properties.put("chunks", chunksProperty());
        properties.put("errorCode", stringProperty("Stabiler Fehlercode bei fehlgeschlagener Extraktion."));
        properties.put("message", stringProperty("Menschenlesbare Fehlerbeschreibung."));

        schema.put("properties", properties);
        schema.put("required", List.of("year", "rrbNumber"));
        return schema;
    }

    private Map<String, Object> integerProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "integer");
        property.put("description", description);
        return property;
    }

    private Map<String, Object> stringProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "string");
        property.put("description", description);
        return property;
    }

    private Map<String, Object> chunksProperty() {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "array");
        property.put("description", "Seitenweise extrahierte Text-Chunks des Hauptdokuments.");
        property.put("items", chunkItemSchema());
        return property;
    }

    private Map<String, Object> chunkItemSchema() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "object");
        item.put("additionalProperties", false);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("pageNumber", integerProperty("1-basierte Seitennummer im PDF."));
        properties.put("text", stringProperty("Extrahierter Text dieser PDF-Seite."));

        item.put("properties", properties);
        item.put("required", List.of("pageNumber", "text"));
        return item;
    }
}
