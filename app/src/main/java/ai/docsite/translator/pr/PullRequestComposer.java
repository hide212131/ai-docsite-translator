package ai.docsite.translator.pr;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

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
                builder.append("- ").append(file);
                context.originalFileLinkContext()
                        .map(ctx -> originalFileLink(ctx, file))
                        .ifPresent(link -> builder.append(" ([original](").append(link).append("))"));
                builder.append(System.lineSeparator());
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
                          Optional<OriginalFileLinkContext> originalFileLinkContext,
                          List<String> conflictFailures,
                          List<String> translationFailures) {

        public Context {
            upstreamCommitLink = Objects.requireNonNull(upstreamCommitLink, "upstreamCommitLink");
            translationCommitLink = translationCommitLink == null ? Optional.empty() : translationCommitLink;
            files = List.copyOf(files == null ? List.of() : files);
            originalFileLinkContext = originalFileLinkContext == null ? Optional.empty() : originalFileLinkContext;
            conflictFailures = List.copyOf(conflictFailures == null ? List.of() : conflictFailures);
            translationFailures = List.copyOf(translationFailures == null ? List.of() : translationFailures);
        }
    }

    public record OriginalFileLinkContext(String repositoryWebUrl, String commitSha) {

        public OriginalFileLinkContext {
            repositoryWebUrl = Objects.requireNonNull(repositoryWebUrl, "repositoryWebUrl");
            commitSha = Objects.requireNonNull(commitSha, "commitSha");
            if (repositoryWebUrl.isBlank()) {
                throw new IllegalArgumentException("repositoryWebUrl must not be blank");
            }
            if (commitSha.isBlank()) {
                throw new IllegalArgumentException("commitSha must not be blank");
            }
        }
    }

    private String originalFileLink(OriginalFileLinkContext context, String file) {
        if (file == null || file.isBlank()) {
            return null;
        }
        String encodedPath = Arrays.stream(file.split("/"))
                .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(Collectors.joining("/"));
        return context.repositoryWebUrl() + "/blob/" + context.commitSha() + '/' + encodedPath;
    }
}
