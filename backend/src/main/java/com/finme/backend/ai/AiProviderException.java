package com.finme.backend.ai;

/**
 * Thrown by an individual AiProvider on any failure (HTTP error, timeout, unparseable
 * response). Caught by FallbackAiProviderChain, which moves on to the next provider - not
 * meant to escape to callers directly.
 */
public class AiProviderException extends RuntimeException {

    public AiProviderException(String message) {
        super(message);
    }

    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
