package com.keyloop.scheduler.adapter.in.web;

/** The request has the wrong shape. The message is safe to send to the client. */
final class InvalidRequestException extends RuntimeException {

  InvalidRequestException(String message) {
    super(message);
  }
}
