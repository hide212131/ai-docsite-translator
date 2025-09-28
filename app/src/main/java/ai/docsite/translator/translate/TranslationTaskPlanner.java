package ai.docsite.translator.translate;

import ai.docsite.translator.diff.ChangeCategory;
import ai.docsite.translator.diff.DiffMetadata;
import ai.docsite.translator.diff.FileChange;
import ai.docsite.translator.git.GitWorkflowResult;
import ai.docsite.translator.translate.conflict.ConflictDetector;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds {@link TranslationTask} instances by comparing the upstream source at the
 * target commit with the existing translation at the origin base commit.
 */
public class TranslationTaskPlanner {

    private static final Logger LOGGER = LoggerFactory.getLogger(TranslationTaskPlanner.class);
    private static final int MAX_SEGMENT_LINES = 120;

    public TranslationTaskPlanner() {
    }

    public PlanResult planWithDiagnostics(GitWorkflowResult workflowResult, int maxFilesPerRun) {
        Objects.requireNonNull(workflowResult, "workflowResult");
        DiffMetadata metadata = workflowResult.diffMetadata();
        if (metadata.changes().isEmpty()) {
            return new PlanResult(List.of(), List.of(), List.of());
        }

        List<TranslationTask> tasks = new ArrayList<>();
        List<String> conflictFiles = new ArrayList<>();
        List<String> upstreamReadFailures = new ArrayList<>();
        int limit = maxFilesPerRun <= 0 ? Integer.MAX_VALUE : maxFilesPerRun;
        ConflictDetector conflictDetector = new ConflictDetector();

        for (FileChange change : metadata.changes()) {
            if (tasks.size() >= limit) {
                break;
            }
            if (change.category() == ChangeCategory.NON_DOCUMENT) {
                continue;
            }

            List<String> upstreamLines;
            try {
                upstreamLines = readLinesAtCommit(workflowResult.upstreamDirectory(), workflowResult.targetCommitSha(), change.path());
            } catch (IOException ex) {
                LOGGER.warn("Skipping {} due to upstream read failure: {}", change.path(), ex.getMessage());
                upstreamReadFailures.add(change.path());
                continue;
            }
            if (upstreamLines.isEmpty()) {
                // File removed or not present in upstream commit, nothing to translate.
                continue;
            }

            if (conflictDetector.detect(upstreamLines).isPresent()) {
                LOGGER.warn("Detected unresolved merge conflict markers in {}; skipping automatic translation", change.path());
                conflictFiles.add(change.path());
                continue;
            }

            List<String> baseSourceLines;
            try {
                baseSourceLines = readLinesAtCommit(workflowResult.upstreamDirectory(), workflowResult.baseUpstreamCommitSha(), change.path());
            } catch (IOException ex) {
                LOGGER.warn("Failed to read base upstream content for {}: {}", change.path(), ex.getMessage());
                baseSourceLines = List.of();
            }

            List<String> existingTranslationLines;
            try {
                existingTranslationLines = readLinesAtCommit(workflowResult.originDirectory(), workflowResult.originBaseCommitSha(), change.path());
            } catch (IOException ex) {
                LOGGER.warn("Failed to read existing translation for {}: {}", change.path(), ex.getMessage());
                existingTranslationLines = List.of();
            }

            TranslationTask task = planFromDiff(change.path(), baseSourceLines, existingTranslationLines, upstreamLines);
            if (task != null) {
                tasks.add(task);
            }
        }
        return new PlanResult(List.copyOf(tasks), List.copyOf(conflictFiles), List.copyOf(upstreamReadFailures));
    }

    public List<TranslationTask> plan(GitWorkflowResult workflowResult, int maxFilesPerRun) {
        return planWithDiagnostics(workflowResult, maxFilesPerRun).tasks();
    }

    private TranslationTask planFromDiff(String filePath,
                                         List<String> baseSourceLines,
                                         List<String> existingTranslationLines,
                                         List<String> newSourceLines) {
        EditList edits = computeEdits(baseSourceLines, newSourceLines);
        List<TranslationSegment> segments = segmentsFromEdits(edits);
        if (segments.isEmpty()) {
            return null;
        }
        List<String> existingLines = alignExistingLines(existingTranslationLines, newSourceLines, edits);
        List<TranslationSegment> normalized = normalizeSegments(segments);
        return new TranslationTask(filePath, newSourceLines, existingLines, normalized);
    }

    private List<String> readLinesAtCommit(Path repositoryDir, String commitSha, String filePath) throws IOException {
        if (commitSha == null || commitSha.isBlank()) {
            return List.of();
        }
        Path gitDir = repositoryDir.resolve(".git");
        if (!Files.exists(gitDir)) {
            return List.of();
        }
        FileRepositoryBuilder builder = new FileRepositoryBuilder()
                .setGitDir(gitDir.toFile())
                .setMustExist(true);
        try (Repository repository = builder.build()) {
            ObjectId commitId = repository.resolve(commitSha);
            if (commitId == null) {
                return List.of();
            }
            try (RevWalk walk = new RevWalk(repository)) {
                RevCommit commit = walk.parseCommit(commitId);
                try (TreeWalk treeWalk = TreeWalk.forPath(repository, filePath, commit.getTree())) {
                    if (treeWalk == null) {
                        return List.of();
                    }
                    ObjectLoader loader = repository.open(treeWalk.getObjectId(0));
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(loader.openStream(), StandardCharsets.UTF_8))) {
                        return reader.lines().toList();
                    }
                }
            }
        }
    }

    private EditList computeEdits(List<String> baseLines, List<String> newLines) {
        RawText baseText = new RawText(toByteArray(baseLines));
        RawText newText = new RawText(toByteArray(newLines));
        DiffAlgorithm algorithm = DiffAlgorithm.getAlgorithm(DiffAlgorithm.SupportedAlgorithm.HISTOGRAM);
        return algorithm.diff(RawTextComparator.DEFAULT, baseText, newText);
    }

    private byte[] toByteArray(List<String> lines) {
        if (lines.isEmpty()) {
            return new byte[0];
        }
        String joined = String.join("\n", lines);
        return joined.getBytes(StandardCharsets.UTF_8);
    }

    private List<TranslationSegment> segmentsFromEdits(EditList edits) {
        List<TranslationSegment> segments = new ArrayList<>();
        for (Edit edit : edits) {
            if (edit.getType() == Edit.Type.DELETE) {
                continue;
            }
            int start = edit.getBeginB();
            int end = edit.getEndB();
            if (start == end) {
                continue;
            }
            segments.add(new TranslationSegment(start, end));
        }
        return segments;
    }

    private List<TranslationSegment> normalizeSegments(List<TranslationSegment> segments) {
        if (segments.isEmpty()) {
            return segments;
        }
        segments.sort(Comparator.comparingInt(TranslationSegment::startLine));
        List<TranslationSegment> merged = new ArrayList<>();
        for (TranslationSegment segment : segments) {
            if (merged.isEmpty()) {
                merged.add(segment);
                continue;
            }
            TranslationSegment last = merged.get(merged.size() - 1);
            if (segment.startLine() <= last.endLineExclusive()) {
                merged.set(merged.size() - 1,
                        new TranslationSegment(last.startLine(), Math.max(last.endLineExclusive(), segment.endLineExclusive())));
            } else {
                merged.add(segment);
            }
        }

        List<TranslationSegment> result = new ArrayList<>();
        for (TranslationSegment segment : merged) {
            int start = segment.startLine();
            int end = segment.endLineExclusive();
            for (int current = start; current < end; current += MAX_SEGMENT_LINES) {
                int chunkEnd = Math.min(end, current + MAX_SEGMENT_LINES);
                result.add(new TranslationSegment(current, chunkEnd));
            }
        }
        return result;
    }

    private List<String> alignExistingLines(List<String> existingTranslationLines, List<String> newLines, EditList edits) {
        if (existingTranslationLines.isEmpty()) {
            List<String> blanks = new ArrayList<>(newLines.size());
            for (int i = 0; i < newLines.size(); i++) {
                blanks.add("");
            }
            return blanks;
        }
        List<String> existing = new ArrayList<>();
        int lastA = 0;
        int lastB = 0;
        for (Edit edit : edits) {
            int unchanged = edit.getBeginB() - lastB;
            for (int i = 0; i < unchanged; i++) {
                existing.add(lastA < existingTranslationLines.size() ? existingTranslationLines.get(lastA) : "");
                lastA++;
                lastB++;
            }
            switch (edit.getType()) {
                case INSERT -> {
                    int len = edit.getLengthB();
                    for (int i = 0; i < len; i++) {
                        existing.add("");
                    }
                    lastB += len;
                }
                case REPLACE -> {
                    int lenB = edit.getLengthB();
                    for (int i = 0; i < lenB; i++) {
                        int baseIdx = edit.getBeginA() + i;
                        String line = baseIdx < edit.getEndA() && baseIdx < existingTranslationLines.size()
                                ? existingTranslationLines.get(baseIdx)
                                : "";
                        existing.add(line);
                    }
                    lastA = edit.getEndA();
                    lastB = edit.getEndB();
                }
                case DELETE -> lastA = edit.getEndA();
                default -> { }
            }
        }

        while (lastB < newLines.size()) {
            String line = lastA < existingTranslationLines.size() ? existingTranslationLines.get(lastA) : "";
            existing.add(line);
            lastA++;
            lastB++;
        }

        while (existing.size() < newLines.size()) {
            existing.add("");
        }
        if (existing.size() > newLines.size()) {
            return new ArrayList<>(existing.subList(0, newLines.size()));
        }
        return existing;
    }

    public record PlanResult(List<TranslationTask> tasks,
                             List<String> conflictFiles,
                             List<String> upstreamReadFailures) {

        public PlanResult {
            tasks = List.copyOf(tasks);
            conflictFiles = List.copyOf(conflictFiles);
            upstreamReadFailures = List.copyOf(upstreamReadFailures);
        }

        public List<String> plannedFilePaths() {
            if (tasks.isEmpty()) {
                return List.of();
            }
            List<String> files = new ArrayList<>(tasks.size());
            for (TranslationTask task : tasks) {
                files.add(task.filePath());
            }
            return Collections.unmodifiableList(files);
        }
    }
}
