package ch.so.agi.geo;

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
class GeoMcpToolConfiguration {

    private static final McpJsonMapper MCP_JSON_MAPPER = McpJsonMapper.createDefault();

    private static final String EGRID_TOOL_NAME = "getEgridByNumberAndMunicipality";

    private static final String OEREB_TOOL_NAME = "getOerebExtractById";

    @Bean
    List<McpServerFeatures.SyncToolSpecification> getGeoToolSpecifications(GeoMcpHandler handler) {
        return List.of(
                getEgridToolSpecification(handler),
                getOerebToolSpecification(handler));
    }

    private McpServerFeatures.SyncToolSpecification getEgridToolSpecification(GeoMcpHandler handler) {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name(EGRID_TOOL_NAME)
                .title("Dummy EGRID Lookup")
                .description("Ermittelt den EGRID eines Grundstuecks anhand der sprechenden Grundstuecksnummer und des Gemeindenamens. Dummy-Implementierung fuer Kettentests.")
                .inputSchema(MCP_JSON_MAPPER, inputSchemaJson(egridInputSchema()))
                .outputSchema(egridOutputSchema())
                .annotations(toolAnnotations("Dummy EGRID Lookup"))
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> handler.handleGetEgridByNumberAndMunicipality(request.arguments()))
                .build();
    }

    private McpServerFeatures.SyncToolSpecification getOerebToolSpecification(GeoMcpHandler handler) {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name(OEREB_TOOL_NAME)
                .title("Dummy OEREB PDF Link")
                .description("Erzeugt aus einem EGRID den Dummy-Link auf ein externes OEREB-PDF. Dummy-Implementierung fuer Kettentests.")
                .inputSchema(MCP_JSON_MAPPER, inputSchemaJson(oerebInputSchema()))
                .outputSchema(oerebOutputSchema())
                .annotations(toolAnnotations("Dummy OEREB PDF Link"))
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> handler.handleGetOerebExtractById(request.arguments()))
                .build();
    }

    private String inputSchemaJson(Map<String, Object> schema) {
        try {
            return MCP_JSON_MAPPER.writeValueAsString(schema);
        }
        catch (IOException exception) {
            throw new IllegalStateException("Input-Schema fuer die Geo-Tools konnte nicht serialisiert werden.", exception);
        }
    }

    private Map<String, Object> egridInputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", Map.of(
                "nummer", stringProperty("Sprechende Grundstuecksnummer, z. B. 168."),
                "gemeindename", stringProperty("Name der Gemeinde, z. B. Solothurn.")));
        schema.put("required", List.of("nummer", "gemeindename"));
        return schema;
    }

    private Map<String, Object> oerebInputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", Map.of(
                "egrid", stringProperty("EGRID des Grundstuecks, z. B. CH807306583219.")));
        schema.put("required", List.of("egrid"));
        return schema;
    }

    private Map<String, Object> egridOutputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", Map.of(
                "nummer", stringProperty("Uebergebene Grundstuecksnummer."),
                "gemeindename", stringProperty("Uebergebener Gemeindename."),
                "egrid", stringProperty("Ermittelter Dummy-EGRID."),
                "errorCode", stringProperty("Stabiler Fehlercode bei ungueltigen Eingaben."),
                "message", stringProperty("Menschenlesbare Fehlerbeschreibung.")));
        schema.put("required", List.of("nummer", "gemeindename"));
        return schema;
    }

    private Map<String, Object> oerebOutputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", Map.of(
                "egrid", stringProperty("Uebergebener EGRID."),
                "publicPdfUrl", stringProperty("Deterministisch erzeugter externer Dummy-Link auf das OEREB-PDF."),
                "mimeType", stringProperty("MIME-Type des PDF-Links."),
                "errorCode", stringProperty("Stabiler Fehlercode bei ungueltigen Eingaben."),
                "message", stringProperty("Menschenlesbare Fehlerbeschreibung.")));
        schema.put("required", List.of("egrid"));
        return schema;
    }

    private McpSchema.ToolAnnotations toolAnnotations(String title) {
        return new McpSchema.ToolAnnotations(
                title,
                Boolean.TRUE,
                Boolean.FALSE,
                Boolean.TRUE,
                Boolean.TRUE,
                null);
    }

    private Map<String, Object> stringProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "string");
        property.put("description", description);
        return property;
    }
}
