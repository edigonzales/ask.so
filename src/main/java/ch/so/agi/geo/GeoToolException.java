package ch.so.agi.geo;

import java.util.Map;

final class GeoToolException extends RuntimeException {

    private final String errorCode;

    private final Map<String, Object> details;

    GeoToolException(String errorCode, String message, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = Map.copyOf(details);
    }

    String getErrorCode() {
        return errorCode;
    }

    Map<String, Object> getDetails() {
        return details;
    }
}
