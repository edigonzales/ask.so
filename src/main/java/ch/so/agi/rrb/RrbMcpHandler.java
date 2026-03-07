package ch.so.agi.rrb;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

@Component
class RrbMcpHandler {

    private final RrbFetchService fetchService;
    private final RrbTextExtractionService textExtractionService;

    RrbMcpHandler(RrbFetchService fetchService, RrbTextExtractionService textExtractionService) {
        this.fetchService = fetchService;
        this.textExtractionService = textExtractionService;
    }

    McpSchema.CallToolResult handleGetRrbPdf(Map<String, Object> arguments) {
        Integer year = null;
        Integer rrbNumber = null;

        try {
            year = parseToolArgument(arguments, "year");
            rrbNumber = parseToolArgument(arguments, "rrbNumber");
            return toCallToolResult(fetchDocument(year, rrbNumber));
        }
        catch (RrbFetchException exception) {
            return toErrorResult(arguments, year, rrbNumber, exception, "RRB konnte nicht aufgeloest werden");
        }
    }

    McpSchema.CallToolResult handleGetRrbText(Map<String, Object> arguments) {
        Integer year = null;
        Integer rrbNumber = null;

        try {
            year = parseToolArgument(arguments, "year");
            rrbNumber = parseToolArgument(arguments, "rrbNumber");
            return toTextCallToolResult(fetchTextDocument(year, rrbNumber));
        }
        catch (RrbFetchException exception) {
            return toErrorResult(arguments, year, rrbNumber, exception, "RRB-Text konnte nicht extrahiert werden");
        }
    }

    @McpResource(
            name = "rrb_pdf",
            title = "RRB PDF",
            uri = "rrb://so.ch/regierungsratsbeschluss/{year}/{rrbNumber}/rrb.pdf",
            description = "Autoritative PDF-Resource des Solothurner Regierungsratsbeschlusses fuer direkte Zusammenfassungen.",
            mimeType = RrbFetchService.PDF_MIME_TYPE)
    public McpSchema.ReadResourceResult readRrbPdf(String year, String rrbNumber) {
        try {
            int parsedYear = fetchService.parsePositiveInt(year, "year");
            int parsedRrbNumber = fetchService.parsePositiveInt(rrbNumber, "rrbNumber");
            RrbDocument document = fetchDocument(parsedYear, parsedRrbNumber);
            return new McpSchema.ReadResourceResult(List.of(toBlobResource(document)));
        }
        catch (RrbFetchException exception) {
            throw toMcpError(exception);
        }
    }

    private RrbDocument fetchDocument(int year, int rrbNumber) {
        return fetchService.fetch(year, rrbNumber);
    }

    private RrbTextDocument fetchTextDocument(int year, int rrbNumber) {
        return textExtractionService.fetchText(year, rrbNumber);
    }

    private McpSchema.CallToolResult toCallToolResult(RrbDocument document) {
        Map<String, Object> structuredContent = structuredContent(document);
        String summary = "RRB %d/%d wurde gefunden; verwende jetzt die PDF-Resource."
                .formatted(document.year(), document.rrbNumber());

        return McpSchema.CallToolResult.builder()
                .structuredContent(structuredContent)
                .addTextContent(summary)
                .addContent(toPublicPdfLink(document))
                .addContent(toPdfResourceLink(document))
                .isError(false)
                .build();
    }

    private McpSchema.CallToolResult toTextCallToolResult(RrbTextDocument document) {
        McpSchema.CallToolResult.Builder result = McpSchema.CallToolResult.builder()
                .structuredContent(structuredTextContent(document))
                .addTextContent("RRB %d/%d wurde als Text extrahiert; %d Seiten bereit."
                        .formatted(document.year(), document.rrbNumber(), document.pageCount()))
                .addTextContent("PDF-Link: %s".formatted(document.publicPdfUrl()))
                .addContent(toPublicPdfLink(document))
                .isError(false);

        for (RrbTextChunk chunk : document.chunks()) {
            result.addTextContent("Seite %d%n%n%s".formatted(chunk.pageNumber(), chunk.text()));
        }

        return result.build();
    }

    private McpSchema.CallToolResult toErrorResult(
            Map<String, Object> arguments,
            Integer year,
            Integer rrbNumber,
            RrbFetchException exception,
            String summaryPrefix) {
        Map<String, Object> structuredContent = new LinkedHashMap<>();
        structuredContent.put("year", valueOrArgument(year, arguments, "year"));
        structuredContent.put("rrbNumber", valueOrArgument(rrbNumber, arguments, "rrbNumber"));
        structuredContent.put("errorCode", exception.getErrorCode());
        structuredContent.put("message", exception.getMessage());
        structuredContent.putAll(exception.getDetails());
        String summary = "%s: %s".formatted(summaryPrefix, exception.getMessage());

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
        structuredContent.put("pdfResourceUri", document.pdfResourceUri());
        structuredContent.put("pdfMimeType", document.pdfMimeType());
        return structuredContent;
    }

    private Map<String, Object> structuredTextContent(RrbTextDocument document) {
        Map<String, Object> structuredContent = new LinkedHashMap<>();
        structuredContent.put("year", document.year());
        structuredContent.put("rrbNumber", document.rrbNumber());
        structuredContent.put("sourcePageUrl", document.sourcePageUrl());
        structuredContent.put("publicPdfUrl", document.publicPdfUrl());
        structuredContent.put("filename", document.filename());
        structuredContent.put("pageCount", document.pageCount());
        structuredContent.put("chunks", document.chunks().stream()
                .map(chunk -> Map.of(
                        "pageNumber", chunk.pageNumber(),
                        "text", chunk.text()))
                .toList());
        return structuredContent;
    }

    private McpSchema.ResourceLink toPublicPdfLink(RrbDocument document) {
        return McpSchema.ResourceLink.builder()
                .name(document.filename())
                .title("RRB %d/%d (oeffentlicher PDF-Link)".formatted(document.year(), document.rrbNumber()))
                .uri(document.publicPdfUrl())
                .description("Direkter oeffentlicher Link zum Original-PDF des Regierungsratsbeschlusses.")
                .mimeType(document.pdfMimeType())
                .size(document.byteLength())
                .build();
    }

    private McpSchema.ResourceLink toPublicPdfLink(RrbTextDocument document) {
        return McpSchema.ResourceLink.builder()
                .name(document.filename())
                .title("RRB %d/%d (oeffentlicher PDF-Link)".formatted(document.year(), document.rrbNumber()))
                .uri(document.publicPdfUrl())
                .description("Verifizierter direkter Link zum Original-PDF des Regierungsratsbeschlusses.")
                .mimeType(RrbFetchService.PDF_MIME_TYPE)
                .build();
    }

    private McpSchema.ResourceLink toPdfResourceLink(RrbDocument document) {
        return McpSchema.ResourceLink.builder()
                .name("rrb_pdf")
                .title("RRB %d/%d PDF-Resource".formatted(document.year(), document.rrbNumber()))
                .uri(document.pdfResourceUri())
                .description("Autoritative MCP-PDF-Resource fuer die inhaltliche Weiterverarbeitung.")
                .mimeType(document.pdfMimeType())
                .size(document.byteLength())
                .build();
    }

    private McpSchema.BlobResourceContents toBlobResource(RrbDocument document) {
        return new McpSchema.BlobResourceContents(
                document.pdfResourceUri(),
                document.pdfMimeType(),
                Base64.getEncoder().encodeToString(document.pdfBytes()),
                Map.of(
                        "year", document.year(),
                        "rrbNumber", document.rrbNumber(),
                        "filename", document.filename()));
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

    private McpError toMcpError(RrbFetchException exception) {
        int jsonRpcErrorCode = switch (exception.getErrorCode()) {
            case "INVALID_INPUT" -> McpSchema.ErrorCodes.INVALID_PARAMS;
            case "NOT_FOUND" -> McpSchema.ErrorCodes.RESOURCE_NOT_FOUND;
            default -> McpSchema.ErrorCodes.INTERNAL_ERROR;
        };

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("errorCode", exception.getErrorCode());
        data.put("message", exception.getMessage());
        data.putAll(exception.getDetails());

        return McpError.builder(jsonRpcErrorCode)
                .message(exception.getMessage())
                .data(data)
                .build();
    }
}
