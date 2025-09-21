package ai.docsite.translator.pr;

import java.util.List;

/**
 * Placeholder PR composer that will eventually build PR titles and bodies.
 */
public class PullRequestComposer {

    public String composeTitle(String targetSha) {
        return "Translate updates for " + targetSha;
    }

    public String composeBody(List<String> files) {
        return String.join(System.lineSeparator(), files);
    }
}
