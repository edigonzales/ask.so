package ch.so.agi.rrb;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.spec.McpSchema;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

@Component
class RrbMcpHandler {

    private final RrbFetchService fetchService;

    RrbMcpHandler(RrbFetchService fetchService) {
        this.fetchService = fetchService;
    }

    McpSchema.CallToolResult handleGetRrbPdf(Map<String, Object> arguments) {
        Integer year = null;
        Integer rrbNumber = null;

        try {
            year = parseToolArgument(arguments, "year");
            rrbNumber = parseToolArgument(arguments, "rrbNumber");
            return getRrbPdf(year, rrbNumber);
        }
        catch (RrbFetchException exception) {
            return toErrorResult(arguments, year, rrbNumber, exception);
        }
    }

    @McpResource(
            name = "rrb_pdf",
            title = "RRB PDF",
            uri = "rrb://so.ch/regierungsratsbeschluss/{year}/{rrbNumber}/rrb.pdf",
            description = "PDF des Solothurner Regierungsratsbeschlusses.",
            mimeType = RrbFetchService.PDF_MIME_TYPE)
    public McpSchema.ReadResourceResult readRrbPdf(String year, String rrbNumber) {
        int parsedYear = fetchService.parsePositiveInt(year, "year");
        int parsedRrbNumber = fetchService.parsePositiveInt(rrbNumber, "rrbNumber");
        RrbDocument document = fetchDocument(parsedYear, parsedRrbNumber);
        return new McpSchema.ReadResourceResult(List.of(toBlobResource(document)));
    }

    private McpSchema.CallToolResult getRrbPdf(int year, int rrbNumber) {
        return toCallToolResult(fetchDocument(year, rrbNumber));
    }

    private RrbDocument fetchDocument(int year, int rrbNumber) {
        return fetchService.fetch(year, rrbNumber);
    }

    private McpSchema.CallToolResult toCallToolResult(RrbDocument document) {
        Map<String, Object> structuredContent = structuredContent(document);
        String summary = "RRB %d/%d wurde als PDF geladen.".formatted(document.year(), document.rrbNumber());

        return McpSchema.CallToolResult.builder()
                .structuredContent(structuredContent)
                .addTextContent(summary)
                .addContent(McpSchema.ResourceLink.builder()
                        .name(document.filename())
                        .title("RRB %d/%d".formatted(document.year(), document.rrbNumber()))
                        .uri(document.publicPdfUrl())
                        .description("Direkter öffentlicher Link zum PDF des Regierungsratsbeschlusses.")
                        .mimeType(document.mimeType())
                        .size(document.byteLength())
                        .build())
                .addContent(new McpSchema.EmbeddedResource(
                        new McpSchema.Annotations(List.of(McpSchema.Role.USER), 1.0),
                        toBlobResource(document)))
                .isError(false)
                .build();
    }

    private McpSchema.CallToolResult toErrorResult(
            Map<String, Object> arguments,
            Integer year,
            Integer rrbNumber,
            RrbFetchException exception) {
        Map<String, Object> structuredContent = new LinkedHashMap<>();
        structuredContent.put("year", valueOrArgument(year, arguments, "year"));
        structuredContent.put("rrbNumber", valueOrArgument(rrbNumber, arguments, "rrbNumber"));
        structuredContent.put("errorCode", exception.getErrorCode());
        structuredContent.put("message", exception.getMessage());
        structuredContent.putAll(exception.getDetails());
        String summary = "RRB konnte nicht geladen werden: %s.".formatted(exception.getMessage());

        return McpSchema.CallToolResult.builder()
                .structuredContent(structuredContent)
                .addTextContent(summary)
                .isError(true)
                .build();
    }

    private Map<String, Object> structuredContent(RrbDocument document) {
        Map<String, Object> structuredContent = new LinkedHashMap<>();
        structuredContent.put("year", document.year());
        structuredContent.put("rrbNumber", document.rrbNumber());
        structuredContent.put("sourcePageUrl", document.sourcePageUrl());
        structuredContent.put("publicPdfUrl", document.publicPdfUrl());
        structuredContent.put("resourceUri", document.resourceUri());
        structuredContent.put("filename", document.filename());
        structuredContent.put("mimeType", document.mimeType());
        structuredContent.put("byteLength", document.byteLength());
        return structuredContent;
    }

    private McpSchema.BlobResourceContents toBlobResource(RrbDocument document) {
        return new McpSchema.BlobResourceContents(
                document.resourceUri(),
                document.mimeType(),
                Base64.getEncoder().encodeToString(document.pdfBytes()));
    }

    private int parseToolArgument(Map<String, Object> arguments, String fieldName) {
        Object value = arguments == null ? null : arguments.get(fieldName);
        return fetchService.parsePositiveInt(value == null ? null : value.toString(), fieldName);
    }

    private Object valueOrArgument(Integer parsedValue, Map<String, Object> arguments, String fieldName) {
        if (parsedValue != null) {
            return parsedValue;
        }
        return arguments == null ? null : arguments.get(fieldName);
    }
}
