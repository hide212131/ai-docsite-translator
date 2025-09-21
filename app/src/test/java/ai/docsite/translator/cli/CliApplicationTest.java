package ai.docsite.translator.cli;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class CliApplicationTest {

    @Test
    void mainRunsWithoutThrowing() {
        assertThatCode(() -> CliApplication.main(new String[0])).doesNotThrowAnyException();
    }
}
