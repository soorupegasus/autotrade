package com.example.fyersapi;

public class FyersApiException extends RuntimeException {
    public FyersApiException(String message) {
        super(message);
    }

    public FyersApiException(String message, Throwable cause) {
        super(message, cause);
    }
}