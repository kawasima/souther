package souther.lsp;

/**
 * Raised on the thread that owns a session when the thread reading its frames could not go on.
 *
 * <p>An {@link Error}, so that the catch which turns a fault into a failed request does not turn
 * this into one. A request that cannot be answered costs that request; a connection that cannot be
 * read costs the session, and there is nothing left to write the failure to.
 */
final class ConnectionLost extends Error {
    private static final long serialVersionUID = 1L;

    ConnectionLost(Throwable cause) {
        super("the connection could not be read", cause);
    }
}
