package ai.docsite.translator.git;

import ai.docsite.translator.diff.DiffMetadata;
import java.nio.file.Path;
import java.util.Objects;
import org.eclipse.jgit.api.MergeResult.MergeStatus;

/**
 * Result data returned after preparing the translation branch.
 */
public record GitWorkflowResult(Path upstreamDirectory,
                                Path originDirectory,
                                String translationBranch,
                                String targetCommitSha,
                                String targetCommitShortSha,
                                DiffMetadata diffMetadata,
                                MergeStatus mergeStatus) {

    public GitWorkflowResult {
        Objects.requireNonNull(upstreamDirectory, "upstreamDirectory");
        Objects.requireNonNull(originDirectory, "originDirectory");
        Objects.requireNonNull(translationBranch, "translationBranch");
        Objects.requireNonNull(targetCommitSha, "targetCommitSha");
        Objects.requireNonNull(targetCommitShortSha, "targetCommitShortSha");
        Objects.requireNonNull(diffMetadata, "diffMetadata");
        Objects.requireNonNull(mergeStatus, "mergeStatus");
    }

    public static GitWorkflowResult empty(Path upstreamDirectory, Path originDirectory) {
        return new GitWorkflowResult(upstreamDirectory, originDirectory, "", "", "", DiffMetadata.empty(), MergeStatus.ALREADY_UP_TO_DATE);
    }
}
