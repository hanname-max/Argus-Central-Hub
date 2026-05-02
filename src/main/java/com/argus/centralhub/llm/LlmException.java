package com.argus.centralhub.llm;

public class LlmException extends RuntimeException {

    private final LlmErrorType errorType;
    private final int httpStatus;
    private final String responseBody;

    public LlmException(LlmErrorType errorType, String message) {
        super(message);
        this.errorType = errorType;
        this.httpStatus = 0;
        this.responseBody = null;
    }

    public LlmException(LlmErrorType errorType, String message, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.httpStatus = 0;
        this.responseBody = null;
    }

    public LlmException(LlmErrorType errorType, int httpStatus, String message, String responseBody) {
        super(message);
        this.errorType = errorType;
        this.httpStatus = httpStatus;
        this.responseBody = responseBody;
    }

    public LlmException(LlmErrorType errorType, int httpStatus, String message, String responseBody, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.httpStatus = httpStatus;
        this.responseBody = responseBody;
    }

    public LlmErrorType getErrorType() {
        return errorType;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public boolean isRetryable() {
        return errorType.isRetryable();
    }

    public enum LlmErrorType {
        CONFIGURATION_ERROR(false),
        NETWORK_TIMEOUT(true),
        NETWORK_ERROR(true),
        RATE_LIMITED(true),
        INSUFFICIENT_QUOTA(false),
        INVALID_API_KEY(false),
        INVALID_REQUEST(false),
        CONTEXT_LENGTH_EXCEEDED(false),
        SERVICE_UNAVAILABLE(true),
        INTERNAL_SERVER_ERROR(true),
        PARSE_ERROR(false),
        UNKNOWN_ERROR(false);

        private final boolean retryable;

        LlmErrorType(boolean retryable) {
            this.retryable = retryable;
        }

        public boolean isRetryable() {
            return retryable;
        }

        public static LlmErrorType fromHttpStatus(int statusCode) {
            return switch (statusCode) {
                case 400 -> INVALID_REQUEST;
                case 401 -> INVALID_API_KEY;
                case 403 -> INSUFFICIENT_QUOTA;
                case 429 -> RATE_LIMITED;
                case 500, 502, 503, 504 -> SERVICE_UNAVAILABLE;
                default -> UNKNOWN_ERROR;
            };
        }
    }
}
