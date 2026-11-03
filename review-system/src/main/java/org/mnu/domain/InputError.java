package org.mnu.domain;

public class InputError extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final int status;
    public InputError(String message) { this(message, 400); }
    public InputError(String message, int status) { super(message); this.status = status; }
    public int getStatus() { return status; }
}
