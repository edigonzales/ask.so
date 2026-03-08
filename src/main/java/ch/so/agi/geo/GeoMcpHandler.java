package ch.so.agi.geo;

import java.util.LinkedHashMap;
import java.util.Map;

import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Component;

@Component
class GeoMcpHandler {

    private final GeoDummyService geoDummyService;

    GeoMcpHandler(GeoDummyService geoDummyService) {
        this.geoDummyService = geoDummyService;
    }

    McpSchema.CallToolResult handleGetEgridByNumberAndMunicipality(Map<String, Object> arguments) {
        String nummer = null;
        String gemeindename = null;

        try {
            nummer = parseToolArgument(arguments, "nummer");
            gemeindename = parseToolArgument(arguments, "gemeindename");
            return toEgridResult(geoDummyService.getEgridByNumberAndMunicipality(nummer, gemeindename));
        }
        catch (GeoToolException exception) {
            return toErrorResult(
                    arguments,
                    parsedValues("nummer", nummer, "gemeindename", gemeindename),
                    exception,
                    "EGRID konnte nicht ermittelt werden");
        }
    }

    McpSchema.CallToolResult handleGetOerebExtractById(Map<String, Object> arguments) {
        String egrid = null;

        try {
            egrid = parseToolArgument(arguments, "egrid");
            return toOerebResult(geoDummyService.getOerebExtractById(egrid));
        }
        catch (GeoToolException exception) {
            return toErrorResult(
                    arguments,
                    parsedValues("egrid", egrid),
                    exception,
                    "OEREB-PDF-Link konnte nicht erzeugt werden");
        }
    }

    private McpSchema.CallToolResult toEgridResult(GeoEgridResult result) {
        Map<String, Object> structuredContent = new LinkedHashMap<>();
        structuredContent.put("nummer", result.nummer());
        structuredContent.put("gemeindename", result.gemeindename());
        structuredContent.put("egrid", result.egrid());

        return McpSchema.CallToolResult.builder()
                .structuredContent(structuredContent)
                .addTextContent("EGRID: %s".formatted(result.egrid()))
                .isError(false)
                .build();
    }

    private McpSchema.CallToolResult toOerebResult(GeoOerebExtractResult result) {
        Map<String, Object> structuredContent = new LinkedHashMap<>();
        structuredContent.put("egrid", result.egrid());
        structuredContent.put("publicPdfUrl", result.publicPdfUrl());
        structuredContent.put("mimeType", result.mimeType());

        return McpSchema.CallToolResult.builder()
                .structuredContent(structuredContent)
                .addTextContent("PDF-Link: %s".formatted(result.publicPdfUrl()))
                .addContent(McpSchema.ResourceLink.builder()
                        .name("oereb_extract_pdf")
                        .title("OEREB-Auszug PDF")
                        .uri(result.publicPdfUrl())
                        .description("Direkter externer Dummy-Link auf das PDF des OEREB-Auszugs.")
                        .mimeType(result.mimeType())
                        .build())
                .isError(false)
                .build();
    }

    private McpSchema.CallToolResult toErrorResult(
            Map<String, Object> arguments,
            Map<String, String> parsedValues,
            GeoToolException exception,
            String summaryPrefix) {
        Map<String, Object> structuredContent = new LinkedHashMap<>();
        parsedValues.forEach((fieldName, value) -> structuredContent.put(
                fieldName,
                value != null ? value : valueOrArgument(arguments, fieldName)));
        structuredContent.put("errorCode", exception.getErrorCode());
        structuredContent.put("message", exception.getMessage());
        structuredContent.putAll(exception.getDetails());

        return McpSchema.CallToolResult.builder()
                .structuredContent(structuredContent)
                .addTextContent("%s: %s".formatted(summaryPrefix, exception.getMessage()))
                .isError(true)
                .build();
    }

    private String parseToolArgument(Map<String, Object> arguments, String fieldName) {
        Object value = arguments == null ? null : arguments.get(fieldName);
        return geoDummyService.parseRequiredString(value == null ? null : value.toString(), fieldName);
    }

    private Object valueOrArgument(Map<String, Object> arguments, String fieldName) {
        return arguments == null ? null : arguments.get(fieldName);
    }

    private Map<String, String> parsedValues(String firstField, String firstValue) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(firstField, firstValue);
        return values;
    }

    private Map<String, String> parsedValues(String firstField, String firstValue, String secondField, String secondValue) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(firstField, firstValue);
        values.put(secondField, secondValue);
        return values;
    }
}
