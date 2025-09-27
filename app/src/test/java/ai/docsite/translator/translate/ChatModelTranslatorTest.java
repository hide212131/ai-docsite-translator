package ai.docsite.translator.translate;

import static org.assertj.core.api.Assertions.assertThat;

import ai.docsite.translator.writer.DefaultLineStructureAdjuster;
import ai.docsite.translator.writer.DefaultLineStructureAnalyzer;
import dev.langchain4j.model.chat.ChatModel;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatModelTranslatorTest {

    @Test
    @DisplayName("Splits model response into individual lines and trims trailing whitespace")
    void splitsResponseIntoLines() {
        ChatModel stubModel = new ChatModel() {
            @Override
            public String chat(String prompt) {
                return "line-one  \nline-two\n";
            }
        };
        ChatModelTranslator translator = new ChatModelTranslator(stubModel, "TestProvider", "test-model");

        List<String> result = translator.translate(List.of("Line 1", "Line 2"));

        assertThat(result).containsExactly("line-one", "line-two");
    }

    @Test
    @DisplayName("Trims superfluous closing code fences from model output")
    void trimsExtraCodeFenceLines() {
        ChatModel stubModel = new ChatModel() {
            @Override
            public String chat(String prompt) {
                return """
## 無効化

アプリケーションの設定に応じて、src/main/resources/application.yml または application-dev.yml に追加します。

```yaml
spring:
  docker:
    compose:
      enabled: false
```

## 代替案

- [Colima](https://github.com/abiosoft/colima#getting-started)
""";
            }
        };

        ChatModelTranslator translator = new ChatModelTranslator(stubModel, "Gemini", "models/test");
        List<String> source = List.of(
                "  - _JAVA_OPTIONS=-Xmx512m -Xms256m",
                "```",
                "",
                "## Disabling",
                "",
                "Add to src/main/resources/application.yml or application-dev.yml depending on your application configuration.",
                "",
                "```yaml",
                "spring:",
                "  docker:",
                "    compose:",
                "      enabled: false",
                "```",
                "",
                "## Alternatives",
                "",
                "- [Colima](https://github.com/abiosoft/colima#getting-started)"
        );

        List<String> rawTranslation = translator.translate(source);
        System.out.println("raw=" + rawTranslation);
        LineStructureFormatter formatter = new LineStructureFormatter(new DefaultLineStructureAnalyzer(), new DefaultLineStructureAdjuster());
        List<String> formatted = formatter.format(source, rawTranslation);
        System.out.println("formatted=" + formatted);

        assertThat(formatted)
                .hasSize(source.size())
                .anyMatch(line -> line.contains("enabled: false"))
                .anyMatch(line -> line.contains("## 代替案"))
                .anyMatch(line -> line.contains("Colima"));
    }
}
