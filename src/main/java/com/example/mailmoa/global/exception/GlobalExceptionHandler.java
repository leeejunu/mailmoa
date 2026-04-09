package com.example.mailmoa.global.exception;

import com.example.mailmoa.mail.application.exception.MailNotFoundException;
import com.example.mailmoa.mailaccount.application.exception.NaverAuthException;
import com.example.mailmoa.user.application.exception.DuplicateEmailException;
import com.example.mailmoa.user.application.exception.InvalidCredentialsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ExceptionResponse> handleDuplicateEmail(DuplicateEmailException e) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ExceptionResponse(HttpStatus.CONFLICT.value(), e.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ExceptionResponse> handleInvalidCredentials(InvalidCredentialsException e) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ExceptionResponse(HttpStatus.UNAUTHORIZED.value(), e.getMessage()));
    }

    @ExceptionHandler(MailNotFoundException.class)
    public ResponseEntity<ExceptionResponse> handleMailNotFound(MailNotFoundException e) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ExceptionResponse(HttpStatus.NOT_FOUND.value(), e.getMessage()));
    }

    @ExceptionHandler(NaverAuthException.class)
    public ResponseEntity<ExceptionResponse> handleNaverAuth(NaverAuthException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ExceptionResponse(HttpStatus.BAD_REQUEST.value(), e.getMessage()));
    }
}
