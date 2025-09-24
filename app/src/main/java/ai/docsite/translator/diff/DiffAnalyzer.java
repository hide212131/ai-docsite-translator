package ai.docsite.translator.diff;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;

/**
 * Performs diff analysis between two commits and classifies file changes for downstream processing.
 */
public class DiffAnalyzer {

    private static final Set<String> DEFAULT_DOCUMENT_EXTENSIONS = Set.of("md", "mdx", "txt", "html");

    private final Set<String> documentExtensions;

    public DiffAnalyzer() {
        this(DEFAULT_DOCUMENT_EXTENSIONS);
    }

    public DiffAnalyzer(Set<String> documentExtensions) {
        this.documentExtensions = Objects.requireNonNull(documentExtensions, "documentExtensions");
    }

    public DiffMetadata analyze(Repository repository, ObjectId baseCommit, ObjectId headCommit) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(baseCommit, "baseCommit");
        Objects.requireNonNull(headCommit, "headCommit");

        try (Git git = Git.wrap(repository)) {
            CanonicalTreeParser baseTree = prepareTreeParser(repository, baseCommit);
            CanonicalTreeParser headTree = prepareTreeParser(repository, headCommit);
            return buildMetadata(git, baseTree, headTree);
        } catch (GitAPIException | IOException e) {
            throw new DiffAnalysisException("Failed to analyze diff", e);
        }
    }

    public DiffMetadata analyzeWorkingTree(Repository repository, ObjectId baseCommit) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(baseCommit, "baseCommit");

        try (Git git = Git.wrap(repository)) {
            CanonicalTreeParser baseTree = prepareTreeParser(repository, baseCommit);
            FileTreeIterator workingTree = new FileTreeIterator(repository);
            return buildMetadata(git, baseTree, workingTree);
        } catch (GitAPIException | IOException e) {
            throw new DiffAnalysisException("Failed to analyze working tree diff", e);
        }
    }

    private CanonicalTreeParser prepareTreeParser(Repository repository, ObjectId commitId) throws IOException {
        try (RevWalk walk = new RevWalk(repository); ObjectReader reader = repository.newObjectReader()) {
            CanonicalTreeParser treeParser = new CanonicalTreeParser();
            treeParser.reset(reader, walk.parseCommit(commitId).getTree().getId());
            return treeParser;
        }
    }

    private DiffMetadata buildMetadata(Git git, CanonicalTreeParser baseTree, AbstractTreeIterator newTree)
            throws GitAPIException {
        List<DiffEntry> entries = git.diff()
                .setOldTree(baseTree)
                .setNewTree(newTree)
                .setShowNameAndStatusOnly(true)
                .call();

        List<FileChange> changes = new ArrayList<>();
        for (DiffEntry entry : entries) {
            ChangeCategory category = categorize(entry);
            if (category != null) {
                String path = resolvePath(entry);
                changes.add(new FileChange(path, category));
            }
        }
        return new DiffMetadata(changes);
    }

    private ChangeCategory categorize(DiffEntry entry) {
        String path = resolvePath(entry);
        String extension = extensionOf(path);
        boolean isDocument = documentExtensions.contains(extension);
        ChangeType type = entry.getChangeType();

        if (isDocument) {
            if (type == ChangeType.ADD) {
                return ChangeCategory.DOCUMENT_NEW;
            }
            return ChangeCategory.DOCUMENT_UPDATED;
        }
        return ChangeCategory.NON_DOCUMENT;
    }

    private String resolvePath(DiffEntry entry) {
        return switch (entry.getChangeType()) {
            case ADD -> entry.getNewPath();
            case DELETE -> entry.getOldPath();
            default -> entry.getNewPath();
        };
    }

    private String extensionOf(String path) {
        int idx = path.lastIndexOf('.') + 1;
        if (idx <= 0 || idx == path.length()) {
            return "";
        }
        return path.substring(idx).toLowerCase(Locale.ROOT);
    }

    public static class DiffAnalysisException extends RuntimeException {
        public DiffAnalysisException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
