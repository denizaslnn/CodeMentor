package com.codementor.codeservice.exception;

public class UsernameAlreadyExistsException extends AppException {
    public UsernameAlreadyExistsException(String username) {
        super("error.user.alreadyexists", "USERNAME_ALREADY_EXISTS", username);
    }
}