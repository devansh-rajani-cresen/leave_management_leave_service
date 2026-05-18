package com.cresensolutions.userservice.exception;

import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {

    private final int status;

    public CustomException(String message, int status) {
        super(message);
        this.status = status;
    }
}