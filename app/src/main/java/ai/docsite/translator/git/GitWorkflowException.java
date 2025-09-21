package ai.docsite.translator.git;

/**
 * Runtime exception for git workflow failures.
 */
public class GitWorkflowException extends RuntimeException {

    public GitWorkflowException(String message) {
        super(message);
    }

    public GitWorkflowException(String message, Throwable cause) {
        super(message, cause);
    }
}
