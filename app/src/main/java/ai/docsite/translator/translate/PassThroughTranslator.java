package ai.docsite.translator.translate;

import java.util.ArrayList;
import java.util.List;

/**
 * Translator used for dry-run scenarios that preserves the original text without invoking remote APIs.
 */
public class PassThroughTranslator implements Translator {

    @Override
    public List<String> translate(List<String> sourceLines) {
        if (sourceLines == null) {
            return List.of();
        }
        return new ArrayList<>(sourceLines);
    }
}
