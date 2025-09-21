package ai.docsite.translator.writer;

import java.util.List;

/**
 * Adjusts translated text to mirror the structural layout of the source document.
 */
public interface LineStructureAdjuster {

    List<String> adjust(List<String> originalLines, List<String> translatedLines, LineStructureAnalysis analysis);
}
