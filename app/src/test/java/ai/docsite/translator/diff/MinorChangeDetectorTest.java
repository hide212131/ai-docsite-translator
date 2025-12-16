package ai.docsite.translator.diff;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.junit.jupiter.api.Test;

class MinorChangeDetectorTest {

    private final MinorChangeDetector detector = new MinorChangeDetector();

    @Test
    void detectsTypoFixAsMinorChange() {
        List<String> before = List.of(
            "/*Get the list of availabel bootswatch themes*/"
        );
        List<String> after = List.of(
            "/*Get the list of available bootswatch themes*/"
        );

        EditList edits = computeEdits(before, after);
        boolean isMinor = detector.isMinorChangeOnly(edits, before, after);

        assertThat(isMinor).isTrue();
    }

    @Test
    void detectsFileNameTypoFixAsMinorChange() {
        List<String> before = List.of(
            "slug: /dependency-vulnerabities-check/"
        );
        List<String> after = List.of(
            "slug: /dependency-vulnerabilities-check/"
        );

        EditList edits = computeEdits(before, after);
        boolean isMinor = detector.isMinorChangeOnly(edits, before, after);

        assertThat(isMinor).isTrue();
    }

    @Test
    void detectsMultipleSmallTypoFixesAsMinor() {
        // Simulate the 9c8b17f commit with multiple typo fixes
        List<String> before = List.of(
            "---",
            "title: Dependency Vulnerabilities Check",
            "slug: /dependency-vulnerabities-check/",
            "last_update:",
            "  date: 2018-09-15T19:00:00-00:00",
            "---"
        );
        List<String> after = List.of(
            "---",
            "title: Dependency Vulnerabilities Check",
            "slug: /dependency-vulnerabilities-check/",
            "last_update:",
            "  date: 2018-09-15T19:00:00-00:00",
            "---"
        );

        EditList edits = computeEdits(before, after);
        boolean isMinor = detector.isMinorChangeOnly(edits, before, after);

        assertThat(isMinor).isTrue();
    }

    @Test
    void detectsWhitespaceChangesAsMinor() {
        List<String> before = List.of(
            "Some text with extra  spaces"
        );
        List<String> after = List.of(
            "Some text with extra spaces"
        );

        EditList edits = computeEdits(before, after);
        boolean isMinor = detector.isMinorChangeOnly(edits, before, after);

        assertThat(isMinor).isTrue();
    }

    @Test
    void detectsPunctuationChangesAsMinor() {
        List<String> before = List.of(
            "This is a sentence"
        );
        List<String> after = List.of(
            "This is a sentence."
        );

        EditList edits = computeEdits(before, after);
        boolean isMinor = detector.isMinorChangeOnly(edits, before, after);

        assertThat(isMinor).isTrue();
    }

    @Test
    void detectsSubstantialContentChangeAsNotMinor() {
        List<String> before = List.of(
            "This is the original text.",
            "It has some content here."
        );
        List<String> after = List.of(
            "This is completely different text.",
            "The content has been entirely replaced with new information."
        );

        EditList edits = computeEdits(before, after);
        boolean isMinor = detector.isMinorChangeOnly(edits, before, after);

        assertThat(isMinor).isFalse();
    }

    @Test
    void detectsNewParagraphAdditionAsNotMinor() {
        List<String> before = List.of(
            "# Heading",
            "Some existing content."
        );
        List<String> after = List.of(
            "# Heading",
            "Some existing content.",
            "",
            "## New Section",
            "This is a new section with substantial content that needs translation."
        );

        EditList edits = computeEdits(before, after);
        boolean isMinor = detector.isMinorChangeOnly(edits, before, after);

        assertThat(isMinor).isFalse();
    }

    @Test
    void detectsSentenceRewriteAsNotMinor() {
        List<String> before = List.of(
            "This feature allows you to configure settings."
        );
        List<String> after = List.of(
            "You can now use this feature to customize your preferences."
        );

        EditList edits = computeEdits(before, after);
        boolean isMinor = detector.isMinorChangeOnly(edits, before, after);

        assertThat(isMinor).isFalse();
    }

    @Test
    void handlesEmptyEditsAsMinor() {
        List<String> lines = List.of("Same content");
        
        EditList edits = computeEdits(lines, lines);
        boolean isMinor = detector.isMinorChangeOnly(edits, lines, lines);

        assertThat(isMinor).isTrue();
    }

    @Test
    void detectsCaseChangeAsMinorWhenSmall() {
        List<String> before = List.of(
            "readme.md"
        );
        List<String> after = List.of(
            "README.md"
        );

        EditList edits = computeEdits(before, after);
        boolean isMinor = detector.isMinorChangeOnly(edits, before, after);

        assertThat(isMinor).isTrue();
    }

    @Test
    void detectsMultilineTypoFixFromCommit9c8b17f() {
        // Real-world test case from the commit mentioned in the issue
        List<String> before = List.of(
            "angular.module('yourApp')",
            "  .controller('BootswatchController', function ($scope, BootSwatchService) {",
            "    /*Get the list of availabel bootswatch themes*/",
            "    BootSwatchService.get().then(function(themes) {",
            "      $scope.themes = themes;",
            "      $scope.themes.unshift({name:'Default',css:''});",
            "    });",
            "  });"
        );
        List<String> after = List.of(
            "angular.module('yourApp')",
            "  .controller('BootswatchController', function ($scope, BootSwatchService) {",
            "    /*Get the list of available bootswatch themes*/",
            "    BootSwatchService.get().then(function(themes) {",
            "      $scope.themes = themes;",
            "      $scope.themes.unshift({name:'Default',css:''});",
            "    });",
            "  });"
        );

        EditList edits = computeEdits(before, after);
        boolean isMinor = detector.isMinorChangeOnly(edits, before, after);

        assertThat(isMinor).isTrue();
    }

    private EditList computeEdits(List<String> baseLines, List<String> newLines) {
        RawText baseText = new RawText(toByteArray(baseLines));
        RawText newText = new RawText(toByteArray(newLines));
        DiffAlgorithm algorithm = DiffAlgorithm.getAlgorithm(DiffAlgorithm.SupportedAlgorithm.HISTOGRAM);
        return algorithm.diff(RawTextComparator.DEFAULT, baseText, newText);
    }

    private byte[] toByteArray(List<String> lines) {
        if (lines.isEmpty()) {
            return new byte[0];
        }
        String joined = String.join("\n", lines);
        return joined.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
