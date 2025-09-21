package ai.docsite.translator.diff;

/**
 * Classification for file changes after analyzing upstream and origin differences.
 */
public enum ChangeCategory {
    DOCUMENT_NEW("a"),
    DOCUMENT_UPDATED("b"),
    NON_DOCUMENT("c");

    private final String label;

    ChangeCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
