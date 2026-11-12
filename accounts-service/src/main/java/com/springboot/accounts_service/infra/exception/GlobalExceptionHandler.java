package com.springboot.accounts_service.infra.exception;

import com.springboot.accounts_service.model.account.exception.AccountNotFoundException;
import com.springboot.accounts_service.model.account.exception.DocumentAlreadyRegisteredException;
import com.springboot.accounts_service.model.account.exception.InvalidDocumentException;
import com.springboot.accounts_service.model.transfer.exception.InsufficientBalanceException;
import com.springboot.accounts_service.model.transfer.exception.SameAccountTransferException;
import com.springboot.accounts_service.model.transfer.exception.TransferNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(InvalidDocumentException.class)
    public ProblemDetail handleInvalidDocument(InvalidDocumentException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid CPF");
        return problem;
    }

    @ExceptionHandler(DocumentAlreadyRegisteredException.class)
    public ProblemDetail handleDuplicate(DocumentAlreadyRegisteredException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "An account with this CPF already exists.");
        problem.setTitle("CPF already registered");
        return problem;
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ProblemDetail handleAccountNotFound(AccountNotFoundException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Account not found");
        return problem;
    }

    @ExceptionHandler(SameAccountTransferException.class)
    public ProblemDetail handleSameAccountTransfer(SameAccountTransferException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, "Source and destination accounts must be different.");
        problem.setTitle("Invalid transfer");
        return problem;
    }

    @ExceptionHandler(TransferNotFoundException.class)
    public ProblemDetail handleTransferNotFound(TransferNotFoundException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Transfer not found");
        return problem;
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ProblemDetail handleInsufficientBalance(InsufficientBalanceException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, "Insufficient funds to complete this transfer.");
        problem.setTitle("Insufficient balance");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        logger.error("Unexpected error", ex);
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
        problem.setTitle("Internal server error");
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        var problem = ex.getBody();
        problem.setTitle("Invalid data");
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }
}
