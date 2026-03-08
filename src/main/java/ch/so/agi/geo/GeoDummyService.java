package ch.so.agi.geo;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
class GeoDummyService {

    static final String DUMMY_EGRID = "CH807306583219";

    static final String PDF_MIME_TYPE = "application/pdf";

    private static final String OEREB_PDF_BASE_URL = "https://geo.so.ch/api/oereb/extract/pdf/?EGRID=";

    GeoEgridResult getEgridByNumberAndMunicipality(String nummer, String gemeindename) {
        String normalizedNummer = parseRequiredString(nummer, "nummer");
        String normalizedGemeindename = parseRequiredString(gemeindename, "gemeindename");
        return new GeoEgridResult(normalizedNummer, normalizedGemeindename, DUMMY_EGRID);
    }

    GeoOerebExtractResult getOerebExtractById(String egrid) {
        String normalizedEgrid = parseRequiredString(egrid, "egrid");
        String publicPdfUrl = OEREB_PDF_BASE_URL + URLEncoder.encode(normalizedEgrid, StandardCharsets.UTF_8);
        return new GeoOerebExtractResult(normalizedEgrid, publicPdfUrl, PDF_MIME_TYPE);
    }

    String parseRequiredString(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw error("INVALID_INPUT", "%s ist erforderlich.".formatted(fieldName), Map.of("field", fieldName));
        }
        return value.trim();
    }

    GeoToolException error(String errorCode, String message, Map<String, Object> details) {
        return new GeoToolException(errorCode, message, new LinkedHashMap<>(details));
    }
}
