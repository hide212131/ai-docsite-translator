package ai.docsite.translator.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CliApplicationTest {

    @Test
    void runCompletesWithSuccessWhenRequiredArgsProvided() {
        CliApplication application = new CliApplication();
        int exitCode = application.run(new String[] {
                "--upstream-url", "https://example.com/up.git",
                "--origin-url", "https://example.com/origin.git",
                "--dry-run"
        });

        assertThat(exitCode).isZero();
    }
}
