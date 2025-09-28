package ai.docsite.translator.pr;

import ai.docsite.translator.config.Config;
import ai.docsite.translator.git.GitWorkflowResult;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service preparing and submitting pull request artifacts.
 */
public class PullRequestService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PullRequestService.class);

    private final PullRequestComposer composer;
    private final HttpClient httpClient;

    public PullRequestService(PullRequestComposer composer) {
        this(composer, HttpClient.newHttpClient());
    }

    public PullRequestService(PullRequestComposer composer, HttpClient httpClient) {
        this.composer = Objects.requireNonNull(composer, "composer");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    public PullRequestDraft prepareDraft(Config config,
                                         GitWorkflowResult workflowResult,
                                         List<String> files,
                                         Optional<String> translationCommitSha,
                                         List<String> conflictFailures,
                                         List<String> translationFailures) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(workflowResult, "workflowResult");
        List<String> copyFiles = List.copyOf(files == null ? List.of() : files);
        List<String> conflicts = List.copyOf(conflictFailures == null ? List.of() : conflictFailures);
        List<String> failures = List.copyOf(translationFailures == null ? List.of() : translationFailures);

        String title = composer.composeTitle(workflowResult.targetCommitShortSha());
        String upstreamLink = buildCommitLink(config.upstreamUrl(), workflowResult.targetCommitSha())
                .orElse(workflowResult.targetCommitSha());
        Optional<String> translationLink = translationCommitSha.flatMap(sha ->
                buildCommitLink(config.originUrl(), sha));
        String body = composer.composeBody(new PullRequestComposer.Context(
                upstreamLink,
                translationLink,
                copyFiles,
                conflicts,
                failures));

        return new PullRequestDraft(title, body, copyFiles, translationCommitSha, conflicts, failures);
    }

    public void printDraft(PullRequestDraft draft) {
        Objects.requireNonNull(draft, "draft");
        System.out.println("# Pull Request Preview");
        System.out.println("Title: " + draft.title());
        System.out.println();
        System.out.println(draft.body());
    }

    public Optional<String> createPullRequest(Config config,
                                              GitWorkflowResult workflowResult,
                                              PullRequestDraft draft) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(workflowResult, "workflowResult");
        Objects.requireNonNull(draft, "draft");
        Optional<String> token = config.secrets().githubToken();
        if (token.isEmpty()) {
            throw new IllegalStateException("GITHUB_TOKEN is required to create pull requests");
        }
        RepoCoordinates originCoordinates = coordinates(config.originUrl())
                .orElseThrow(() -> new IllegalStateException("Unable to determine repository from origin URL"));

        String apiUrl = "https://api.github.com/repos/" + originCoordinates.repository() + "/pulls";
        String payload = toJsonPayload(draft, workflowResult.translationBranch(), config.originBranch());

        HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + token.get())
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                Optional<String> prUrl = extractJsonValue(response.body(), "html_url");
                LOGGER.info("Created pull request {}", prUrl.orElse("(URL unavailable)"));
                return prUrl;
            }
            throw new IllegalStateException("GitHub API returned status " + response.statusCode() + ": " + response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while creating pull request", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to invoke GitHub API", ex);
        }
    }

    private Optional<String> buildCommitLink(URI uri, String commitSha) {
        if (commitSha == null || commitSha.isBlank()) {
            return Optional.empty();
        }
        return coordinates(uri).map(coords -> "https://" + coords.host() + "/" + coords.repository() + "/commit/" + commitSha);
    }

    private Optional<RepoCoordinates> coordinates(URI uri) {
        if (uri == null) {
            return Optional.empty();
        }
        String host = uri.getHost();
        String path = uri.getPath();
        if (host == null || host.isBlank()) {
            String raw = uri.toString();
            int at = raw.indexOf('@');
            int colon = raw.indexOf(':');
            if (at >= 0 && colon > at) {
                host = raw.substring(at + 1, colon);
                path = raw.substring(colon + 1);
            }
        }
        if (host == null || host.isBlank() || path == null || path.isBlank()) {
            return Optional.empty();
        }
        String sanitized = sanitizePath(path);
        if (sanitized.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new RepoCoordinates(host, sanitized));
    }

    private String toJsonPayload(PullRequestDraft draft, String headBranch, String baseBranch) {
        return "{" +
                quote("title") + ':' + quote(draft.title()) + ',' +
                quote("head") + ':' + quote(headBranch) + ',' +
                quote("base") + ':' + quote(baseBranch) + ',' +
                quote("body") + ':' + quote(draft.body()) +
                '}';
    }

    private Optional<String> extractJsonValue(String body, String key) {
        if (body == null || key == null) {
            return Optional.empty();
        }
        String needle = quote(key) + ':' + '"';
        int index = body.indexOf(needle);
        if (index < 0) {
            return Optional.empty();
        }
        int start = index + needle.length();
        int end = body.indexOf('"', start);
        if (end < 0) {
            return Optional.empty();
        }
        return Optional.of(body.substring(start, end));
    }

    private static String sanitizePath(String rawPath) {
        String value = rawPath.trim();
        if (value.startsWith("/")) {
            value = value.substring(1);
        }
        if (value.endsWith(".git")) {
            value = value.substring(0, value.length() - 4);
        }
        return value;
    }

    private String quote(String value) {
        return '"' + escape(value) + '"';
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> builder.append(ch);
            }
        }
        return builder.toString();
    }

    private record RepoCoordinates(String host, String repository) {
    }

    public record PullRequestDraft(String title,
                                   String body,
                                   List<String> files,
                                   Optional<String> translationCommitSha,
                                   List<String> conflictFailures,
                                   List<String> translationFailures) {

        public PullRequestDraft {
            title = Objects.requireNonNull(title, "title");
            body = Objects.requireNonNull(body, "body");
            files = List.copyOf(files);
            translationCommitSha = translationCommitSha == null ? Optional.empty() : translationCommitSha;
            conflictFailures = List.copyOf(conflictFailures);
            translationFailures = List.copyOf(translationFailures);
        }

        public String filesAsBullets() {
            if (files.isEmpty()) {
                return "- (no document changes)";
            }
            return files.stream().map(file -> "- " + file).collect(Collectors.joining(System.lineSeparator()));
        }
    }
}
