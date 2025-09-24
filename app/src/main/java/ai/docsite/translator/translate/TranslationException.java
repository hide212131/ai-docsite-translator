package ai.docsite.translator.translate;

/**
 * Runtime exception used to propagate translation failures.
 */
public class TranslationException extends RuntimeException {

    public TranslationException(String message, Throwable cause) {
        super(message, cause);
    }
}
