package ai.docsite.translator.agent.tools;

import ai.docsite.translator.diff.ChangeCategory;
import ai.docsite.translator.diff.DiffMetadata;
import ai.docsite.translator.diff.FileChange;
import dev.langchain4j.agent.tool.Tool;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Tool exposing diff metadata for agent reasoning.
 */
public class DiffTool {

    private final DiffMetadata metadata;

    public DiffTool(DiffMetadata metadata) {
        this.metadata = Objects.requireNonNull(metadata, "metadata");
    }

    @Tool(name = "summarizeDiff", value = "Summarize file changes grouped into categories a/b/c")
    public Map<String, List<String>> summarizeDiff() {
        return metadata.changes().stream()
                .collect(Collectors.groupingBy(change -> change.category().label()))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> entry.getValue().stream().map(FileChange::path).toList()));
    }

    @Tool(name = "countChanges", value = "Count changes by category")
    public Map<ChangeCategory, Long> countChanges() {
        return metadata.changes().stream()
                .collect(Collectors.groupingBy(FileChange::category, Collectors.counting()));
    }
}
