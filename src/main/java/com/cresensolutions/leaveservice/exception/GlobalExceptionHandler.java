package com.cresensolutions.userservice.exception;

import com.cresensolutions.userservice.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(CustomException ex) {
        return new ResponseEntity<>(
                new ErrorResponse(ex.getMessage(), ex.getStatus()),
                HttpStatus.valueOf(ex.getStatus())
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        return new ResponseEntity<>(
                new ErrorResponse("Something went wrong", 500),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }
}