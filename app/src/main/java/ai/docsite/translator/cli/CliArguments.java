package ai.docsite.translator.cli;

import ai.docsite.translator.config.Mode;
import java.net.URI;
import picocli.CommandLine;

@CommandLine.Command(name = "ai-docsite-translator", mixinStandardHelpOptions = true, description = "AI-powered docsite translator")
public class CliArguments {

    @CommandLine.Option(names = "--mode", converter = ModeConverter.class, defaultValue = "BATCH", description = "Execution mode: batch or dev")
    private Mode mode = Mode.BATCH;

    @CommandLine.Option(names = "--upstream-url", description = "Upstream repository URL", paramLabel = "URL")
    private URI upstreamUrl;

    @CommandLine.Option(names = "--origin-url", description = "Origin repository URL", paramLabel = "URL")
    private URI originUrl;

    @CommandLine.Option(names = "--origin-branch", description = "Origin branch name", paramLabel = "BRANCH")
    private String originBranch;

    @CommandLine.Option(names = "--translation-branch-template", description = "Template for translation branch names", paramLabel = "TEMPLATE")
    private String translationBranchTemplate;

    @CommandLine.Option(names = "--since", description = "Process commits since the specified reference (dev mode only)", paramLabel = "REF")
    private String since;

    @CommandLine.Option(names = "--dry-run", description = "Perform a dry run without pushing or creating PRs")
    private boolean dryRun;

    public Mode mode() {
        return mode;
    }

    public URI upstreamUrl() {
        return upstreamUrl;
    }

    public URI originUrl() {
        return originUrl;
    }

    public String originBranch() {
        return originBranch;
    }

    public String translationBranchTemplate() {
        return translationBranchTemplate;
    }

    public String since() {
        return since;
    }

    public boolean dryRun() {
        return dryRun;
    }
}
