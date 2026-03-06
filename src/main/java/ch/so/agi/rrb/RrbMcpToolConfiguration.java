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

    private static final String TOOL_NAME = "get_rrb_pdf";

    private static final String TOOL_TITLE = "RRB (Regierungsratsbeschluss) PDF";

    private static final String TOOL_DESCRIPTION = "Lädt den Hauptbeschluss als PDF anhand von Jahr und RRB-Nummer.";

    private static final String YEAR_DESCRIPTION = "Vierstelliges Jahr des Regierungsratsbeschlusses.";

    private static final String RRB_NUMBER_DESCRIPTION = "RRB-Nummer innerhalb des angegebenen Jahres.";

    @Bean
    List<McpServerFeatures.SyncToolSpecification> getRrbPdfToolSpecifications(RrbMcpHandler handler) {
        return List.of(getRrbPdfToolSpecification(handler));
    }

    private McpServerFeatures.SyncToolSpecification getRrbPdfToolSpecification(RrbMcpHandler handler) {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name(TOOL_NAME)
                .title(TOOL_TITLE)
                .description(TOOL_DESCRIPTION)
                .inputSchema(MCP_JSON_MAPPER, inputSchemaJson())
                .outputSchema(outputSchema())
                .annotations(new McpSchema.ToolAnnotations(
                        TOOL_TITLE,
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

    private String inputSchemaJson() {
        try {
            return MCP_JSON_MAPPER.writeValueAsString(inputSchema());
        }
        catch (IOException exception) {
            throw new IllegalStateException("Input-Schema für get_rrb_pdf konnte nicht serialisiert werden.", exception);
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

    private Map<String, Object> outputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("year", integerProperty(YEAR_DESCRIPTION));
        properties.put("rrbNumber", integerProperty(RRB_NUMBER_DESCRIPTION));
        properties.put("sourcePageUrl", stringProperty("Öffentliche Detailseite des Beschlusses."));
        properties.put("publicPdfUrl", stringProperty("Direkter öffentlicher Download-Link des Hauptdokuments."));
        properties.put("resourceUri", stringProperty("MCP-Resource-URI für das PDF."));
        properties.put("filename", stringProperty("Dateiname des PDF-Dokuments."));
        properties.put("mimeType", stringProperty("MIME-Type des eingebetteten Dokuments."));
        properties.put("byteLength", integerProperty("Grösse des PDF in Bytes."));
        properties.put("errorCode", stringProperty("Stabiler Fehlercode bei fehlgeschlagenem Abruf."));
        properties.put("message", stringProperty("Menschenlesbare Fehlerbeschreibung."));

        schema.put("properties", properties);
        schema.put("required", List.of(
                "year",
                "rrbNumber",
                "sourcePageUrl",
                "publicPdfUrl",
                "resourceUri",
                "filename",
                "mimeType",
                "byteLength"));
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
}
