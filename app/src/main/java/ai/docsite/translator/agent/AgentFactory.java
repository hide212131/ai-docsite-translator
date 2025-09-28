package ai.docsite.translator.agent;

import ai.docsite.translator.agent.tools.DiffTool;
import ai.docsite.translator.agent.tools.LineStructureAdjusterTool;
import ai.docsite.translator.agent.tools.PullRequestTool;
import ai.docsite.translator.agent.tools.TranslationTool;
import ai.docsite.translator.config.Config;
import ai.docsite.translator.git.GitWorkflowResult;
import ai.docsite.translator.pr.PullRequestService;
import ai.docsite.translator.translate.TranslationService;
import ai.docsite.translator.writer.LineStructureAdjuster;
import ai.docsite.translator.writer.LineStructureAnalyzer;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.model.chat.ChatModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Builds LangChain4j agents with the required toolset for our orchestration flow.
 */
public class AgentFactory {

    private final ChatModel chatModel;
    private final TranslationService translationService;
    private final PullRequestService pullRequestService;
    private final LineStructureAnalyzer lineStructureAnalyzer;
    private final LineStructureAdjuster lineStructureAdjuster;
    private final List<Class<?>> toolTypes;

    public AgentFactory(ChatModel chatModel,
                        TranslationService translationService,
                        PullRequestService pullRequestService,
                        LineStructureAnalyzer lineStructureAnalyzer,
                        LineStructureAdjuster lineStructureAdjuster) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
        this.translationService = Objects.requireNonNull(translationService, "translationService");
        this.pullRequestService = Objects.requireNonNull(pullRequestService, "pullRequestService");
        this.lineStructureAnalyzer = Objects.requireNonNull(lineStructureAnalyzer, "lineStructureAnalyzer");
        this.lineStructureAdjuster = Objects.requireNonNull(lineStructureAdjuster, "lineStructureAdjuster");
        this.toolTypes = List.of(DiffTool.class, LineStructureAdjusterTool.class, TranslationTool.class, PullRequestTool.class);
    }

    public TranslationAgent createAgent(Config config, GitWorkflowResult workflowResult) {
        List<Object> tools = new ArrayList<>();
        tools.add(new DiffTool(workflowResult.diffMetadata()));
        tools.add(new LineStructureAdjusterTool(lineStructureAnalyzer, lineStructureAdjuster));
        tools.add(new TranslationTool(translationService, workflowResult.diffMetadata()));
        tools.add(new PullRequestTool(pullRequestService, workflowResult, config));

        return AgenticServices.agentBuilder(TranslationAgent.class)
                .chatModel(chatModel)
                .tools(tools.toArray())
                .build();
    }

    public List<Class<?>> registeredToolTypes() {
        return Collections.unmodifiableList(toolTypes);
    }
}
