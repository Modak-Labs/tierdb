package io.modak.cdc;

public class CdcException extends RuntimeException {
    public CdcException(String message) {
        super(message);
    }

    public CdcException(String message, Throwable cause) {
        super(message, cause);
    }
}
