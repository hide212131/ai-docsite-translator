package ai.docsite.translator.diff;

import java.util.List;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;

/**
 * Detects whether changes between two versions of a file are minor enough to skip translation.
 * Minor changes include typo fixes, small punctuation changes, whitespace adjustments, etc.
 */
public class MinorChangeDetector {

    private static final int MAX_MINOR_CHANGE_CHARS = 15;
    private static final double MAX_MINOR_CHANGE_RATIO = 0.10; // 10% of total content
    private static final int MIN_CONTENT_SIZE_FOR_RATIO = 200; // Only apply ratio check for larger content
    private static final double MAX_EDIT_DISTANCE_RATIO_FOR_REPLACE = 0.2; // 20% edit distance threshold for replacements

    public MinorChangeDetector() {
    }

    /**
     * Determines if all changes in the edit list are minor enough to skip translation.
     *
     * @param edits the edit list from diff analysis
     * @param baseLines the original lines before changes
     * @param newLines the new lines after changes
     * @return true if all changes are minor (typo-level), false otherwise
     */
    public boolean isMinorChangeOnly(EditList edits, List<String> baseLines, List<String> newLines) {
        if (edits.isEmpty()) {
            return true; // No changes at all
        }

        int totalChangedChars = 0;
        int totalContentChars = calculateTotalChars(newLines);

        for (Edit edit : edits) {
            if (edit.getType() == Edit.Type.DELETE) {
                // Deletions count towards total changed characters. Large deletions (e.g., removing sections)
                // will exceed the thresholds and be treated as substantial changes.
                int deletedChars = calculateCharsInRange(baseLines, edit.getBeginA(), edit.getEndA());
                totalChangedChars += deletedChars;
                continue;
            }

            if (edit.getType() == Edit.Type.INSERT) {
                int insertedChars = calculateCharsInRange(newLines, edit.getBeginB(), edit.getEndB());
                totalChangedChars += insertedChars;
                continue;
            }

            if (edit.getType() == Edit.Type.REPLACE) {
                // For replacements, check if they're minor edits (like typo fixes)
                String oldText = extractText(baseLines, edit.getBeginA(), edit.getEndA());
                String newText = extractText(newLines, edit.getBeginB(), edit.getEndB());
                
                int editDistance = levenshteinDistance(oldText, newText);
                
                // If the edit distance exceeds either the absolute threshold OR the relative threshold,
                // consider it a substantial change
                int maxLength = Math.max(oldText.length(), newText.length());
                
                if (editDistance > MAX_MINOR_CHANGE_CHARS || editDistance > maxLength * MAX_EDIT_DISTANCE_RATIO_FOR_REPLACE) {
                    // This is a substantial change, not a minor typo
                    return false;
                }
                
                totalChangedChars += editDistance;
            }
        }

        // For small content, use absolute threshold only
        if (totalContentChars < MIN_CONTENT_SIZE_FOR_RATIO) {
            return totalChangedChars <= MAX_MINOR_CHANGE_CHARS;
        }
        
        // For larger content, if either threshold is exceeded, consider it substantial
        if (totalChangedChars > MAX_MINOR_CHANGE_CHARS 
                || (double) totalChangedChars / totalContentChars > MAX_MINOR_CHANGE_RATIO) {
            return false;
        }

        return true;
    }

    private int calculateTotalChars(List<String> lines) {
        return lines.stream().mapToInt(String::length).sum();
    }

    private int calculateCharsInRange(List<String> lines, int start, int end) {
        int total = 0;
        for (int i = start; i < end && i < lines.size(); i++) {
            total += lines.get(i).length();
        }
        return total;
    }

    private String extractText(List<String> lines, int start, int end) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end && i < lines.size(); i++) {
            if (i > start) {
                sb.append('\n');
            }
            sb.append(lines.get(i));
        }
        return sb.toString();
    }

    /**
     * Calculates the Levenshtein distance between two strings.
     * This measures the minimum number of single-character edits required to change one string into another.
     */
    private int levenshteinDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();

        if (len1 == 0) return len2;
        if (len2 == 0) return len1;

        int[] prev = new int[len2 + 1];
        int[] curr = new int[len2 + 1];

        for (int j = 0; j <= len2; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= len1; i++) {
            curr[0] = i;
            for (int j = 1; j <= len2; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(Math.min(
                    curr[j - 1] + 1,     // insertion
                    prev[j] + 1),        // deletion
                    prev[j - 1] + cost   // substitution
                );
            }
            int[] temp = prev;
            prev = curr;
            curr = temp;
        }

        return prev[len2];
    }
}
