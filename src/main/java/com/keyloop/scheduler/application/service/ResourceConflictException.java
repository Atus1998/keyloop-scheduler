package com.keyloop.scheduler.application.service;

import java.util.Objects;

/** The database refused an insert because another booking holds the same resource. */
public final class ResourceConflictException extends RuntimeException {

  public enum Kind {
    /** An exclusion constraint fired (SQLState 23P01). */
    EXCLUSION,
    /** The insert lost a deadlock (SQLState 40P01). */
    DEADLOCK
  }

  private final Kind kind;

  public ResourceConflictException(Kind kind, Throwable cause) {
    super("Resource conflict: " + Objects.requireNonNull(kind, "kind"), cause);
    this.kind = kind;
  }

  public Kind kind() {
    return kind;
  }
}
