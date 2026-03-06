package ch.so.agi.rrb;

import java.util.Map;

final class RrbFetchException extends RuntimeException {

    private final String errorCode;

    private final Map<String, Object> details;

    RrbFetchException(String errorCode, String message, Map<String, Object> details) {
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
