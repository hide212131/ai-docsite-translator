package ai.docsite.translator.translate;

import java.util.ArrayList;
import java.util.List;

/**
 * Mock translator used for dry-run scenarios.
 */
public class MockTranslator implements Translator {

    @Override
    public List<String> translate(List<String> sourceLines) {
        List<String> result = new ArrayList<>(sourceLines.size());
        for (String line : sourceLines) {
            result.add("[MOCK] " + line);
        }
        return result;
    }
}
