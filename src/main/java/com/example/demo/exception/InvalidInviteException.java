package com.example.demo.exception;

public class InvalidInviteException extends RuntimeException {

    public InvalidInviteException() {
        super("Invite is invalid or expired");
    }
}
