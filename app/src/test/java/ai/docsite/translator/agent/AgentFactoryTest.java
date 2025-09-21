package ai.docsite.translator.agent;

import static org.assertj.core.api.Assertions.assertThat;

import ai.docsite.translator.pr.PullRequestComposer;
import ai.docsite.translator.pr.PullRequestService;
import ai.docsite.translator.translate.TranslationService;
import ai.docsite.translator.writer.DefaultLineStructureAdjuster;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AgentFactoryTest {

    @Test
    void registersExpectedTools() {
        AgentFactory factory = new AgentFactory(new SimpleRoutingChatModel(),
                new TranslationService(),
                new PullRequestService(new PullRequestComposer()),
                new DefaultLineStructureAdjuster());

        assertThat(factory.registeredToolTypes())
                .extracting(Class::getSimpleName)
                .containsExactlyInAnyOrder("DiffTool", "LineStructureAdjusterTool", "TranslationTool", "PullRequestTool");
    }
}
