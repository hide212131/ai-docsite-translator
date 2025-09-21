package ai.docsite.translator.translate;

/**
 * Describes a range of lines within the source document to translate.
 */
public record TranslationSegment(int startLine, int endLineExclusive) {

    public TranslationSegment {
        if (startLine < 0 || endLineExclusive < startLine) {
            throw new IllegalArgumentException("Invalid translation segment range");
        }
    }

    public int length() {
        return endLineExclusive - startLine;
    }
}
