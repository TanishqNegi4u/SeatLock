package com.seatlock.exception;

public class UserNotAdmittedException extends RuntimeException {
    public UserNotAdmittedException(String userId) {
        super("User is not admitted from the waiting room: " + userId);
    }
}
