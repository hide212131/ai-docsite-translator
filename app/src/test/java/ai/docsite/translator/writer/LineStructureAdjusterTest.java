package ai.docsite.translator.writer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class LineStructureAdjusterTest {

    private final LineStructureAnalyzer analyzer = new DefaultLineStructureAnalyzer();
    private final LineStructureAdjuster adjuster = new DefaultLineStructureAdjuster();

    @Test
    void insertsMissingBlankLines() {
        List<String> original = List.of("", "Introduction", "", "Details");
        List<String> translation = List.of("イントロダクション", "詳細");
        LineStructureAnalysis analysis = analyzer.analyze(original);

        List<String> adjusted = adjuster.adjust(original, translation, analysis);

        assertThat(adjusted).containsExactly("", "イントロダクション", "", "詳細");
    }

    @Test
    void preservesWhitespaceBlocks() {
        List<String> original = List.of("   ", "項目1", "\t", "項目2");
        List<String> translation = List.of("item1", "item2");
        LineStructureAnalysis analysis = analyzer.analyze(original);

        List<String> adjusted = adjuster.adjust(original, translation, analysis);

        assertThat(adjusted).containsExactly("   ", "item1", "\t", "item2");
    }

    @Test
    void padsMissingContentLinesWithEmptyStrings() {
        List<String> original = List.of("A", "B", "C");
        List<String> translation = List.of("あ", "い");
        LineStructureAnalysis analysis = analyzer.analyze(original);

        List<String> adjusted = adjuster.adjust(original, translation, analysis);

        assertThat(adjusted).containsExactly("あ", "い", "");
    }
}
