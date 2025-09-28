package ai.docsite.translator.pr;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Builds PR titles and bodies according to the repository conventions.
 */
public class PullRequestComposer {

    public String composeTitle(String targetShortSha) {
        String shortSha = Objects.requireNonNull(targetShortSha, "targetShortSha");
        if (shortSha.isBlank()) {
            throw new IllegalArgumentException("targetShortSha must not be blank");
        }
        return "docs: sync-" + shortSha;
    }

    public String composeBody(Context context) {
        Objects.requireNonNull(context, "context");
        StringBuilder builder = new StringBuilder();
        builder.append("## Summary").append(System.lineSeparator());
        builder.append("- Upstream commit: ").append(context.upstreamCommitLink()).append(System.lineSeparator());
        builder.append("- Translation commit: ")
                .append(context.translationCommitLink().orElse("pending (dry-run)"))
                .append(System.lineSeparator());
        builder.append(System.lineSeparator());

        builder.append("## Files").append(System.lineSeparator());
        if (context.files().isEmpty()) {
            builder.append("- (no document changes)").append(System.lineSeparator());
        } else {
            for (String file : context.files()) {
                builder.append("- ").append(file).append(System.lineSeparator());
            }
        }

        if (!context.conflictFailures().isEmpty() || !context.translationFailures().isEmpty()) {
            builder.append(System.lineSeparator()).append("## Warnings").append(System.lineSeparator());
            if (!context.conflictFailures().isEmpty()) {
                builder.append("- Unresolved conflicts detected in:").append(System.lineSeparator());
                for (String file : context.conflictFailures()) {
                    builder.append("  - ").append(file).append(System.lineSeparator());
                }
            }
            if (!context.translationFailures().isEmpty()) {
                builder.append("- Translation failed for:").append(System.lineSeparator());
                for (String file : context.translationFailures()) {
                    builder.append("  - ").append(file).append(System.lineSeparator());
                }
            }
        }

        return builder.toString();
    }

    public record Context(String upstreamCommitLink,
                          Optional<String> translationCommitLink,
                          List<String> files,
                          List<String> conflictFailures,
                          List<String> translationFailures) {

        public Context {
            upstreamCommitLink = Objects.requireNonNull(upstreamCommitLink, "upstreamCommitLink");
            translationCommitLink = translationCommitLink == null ? Optional.empty() : translationCommitLink;
            files = List.copyOf(files == null ? List.of() : files);
            conflictFailures = List.copyOf(conflictFailures == null ? List.of() : conflictFailures);
            translationFailures = List.copyOf(translationFailures == null ? List.of() : translationFailures);
        }
    }
}
