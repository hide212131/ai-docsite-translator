package ai.docsite.translator.writer;

/**
 * Placeholder adjuster used by the agent to align translated documents.
 */
public interface LineStructureAdjuster {

    default String adjust(String original, String translated) {
        return translated;
    }
}
