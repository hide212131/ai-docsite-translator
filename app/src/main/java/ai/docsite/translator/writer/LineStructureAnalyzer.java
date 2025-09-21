package ai.docsite.translator.writer;

import java.util.List;

/**
 * Analyzes line-oriented documents to extract structural metadata used for alignment.
 */
public interface LineStructureAnalyzer {

    LineStructureAnalysis analyze(List<String> lines);
}
