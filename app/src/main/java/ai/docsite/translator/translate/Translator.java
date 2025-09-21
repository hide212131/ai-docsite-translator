package ai.docsite.translator.translate;

import java.util.List;

/**
 * Low-level translator responsible for converting source text into target language.
 */
public interface Translator {

    List<String> translate(List<String> sourceLines);
}
