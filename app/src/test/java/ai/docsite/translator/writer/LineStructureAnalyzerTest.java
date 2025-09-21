package ai.docsite.translator.writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import org.junit.jupiter.api.Test;

class LineStructureAnalyzerTest {

    private final LineStructureAnalyzer analyzer = new DefaultLineStructureAnalyzer();

    @Test
    void groupsConsecutiveLinesByType() {
        List<String> input = List.of("", "   ", "content", "more", "", "\t\t");

        LineStructureAnalysis analysis = analyzer.analyze(input);

        assertThat(analysis.totalLines()).isEqualTo(6);
        assertThat(analysis.segments())
                .extracting(LineSegment::type, LineSegment::startIndex, LineSegment::endIndexExclusive)
                .containsExactly(
                        tuple(LineType.EMPTY, 0, 1),
                        tuple(LineType.WHITESPACE, 1, 2),
                        tuple(LineType.CONTENT, 2, 4),
                        tuple(LineType.EMPTY, 4, 5),
                        tuple(LineType.WHITESPACE, 5, 6));
    }

    @Test
    void returnsEmptyAnalysisForEmptyInput() {
        LineStructureAnalysis analysis = analyzer.analyze(List.of());

        assertThat(analysis.totalLines()).isZero();
        assertThat(analysis.segments()).isEmpty();
    }
}
