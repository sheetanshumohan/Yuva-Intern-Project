package com.foodplant.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.util.*;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log=LoggerFactory.getLogger(ApiExceptionHandler.class);
  public record Problem(Instant timestamp,int status,String error,String message,String path,Map<String,String> fields) {}
  private ResponseEntity<Problem> reply(HttpStatus status,String message,HttpServletRequest request,Map<String,String> fields) {
    return ResponseEntity.status(status).body(new Problem(Instant.now(),status.value(),status.getReasonPhrase(),message,request.getRequestURI(),fields));
  }
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<Problem> validation(MethodArgumentNotValidException ex,HttpServletRequest req) {
    Map<String,String> fields = new LinkedHashMap<>();
    for(FieldError f:ex.getBindingResult().getFieldErrors()) fields.putIfAbsent(f.getField(),f.getDefaultMessage());
    return reply(HttpStatus.BAD_REQUEST,"Validation failed",req,fields);
  }
  @ExceptionHandler(NoSuchElementException.class)
  ResponseEntity<Problem> missing(NoSuchElementException ex,HttpServletRequest req) { return reply(HttpStatus.NOT_FOUND,ex.getMessage(),req,Map.of()); }
  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<Problem> resource(NoResourceFoundException ex,HttpServletRequest req) { return reply(HttpStatus.NOT_FOUND,"Resource not found",req,Map.of()); }
  @ExceptionHandler(ConflictException.class)
  ResponseEntity<Problem> conflict(ConflictException ex,HttpServletRequest req) { return reply(HttpStatus.CONFLICT,ex.getMessage(),req,Map.of()); }
  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<Problem> integrity(DataIntegrityViolationException ex,HttpServletRequest req) { return reply(HttpStatus.CONFLICT,"Record conflicts with existing references or constraints",req,Map.of()); }
  @ExceptionHandler({IllegalArgumentException.class,MethodArgumentTypeMismatchException.class,HttpMessageNotReadableException.class})
  ResponseEntity<Problem> badRequest(Exception ex,HttpServletRequest req) { return reply(HttpStatus.BAD_REQUEST,"Invalid request parameters or payload",req,Map.of()); }
  @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
  ResponseEntity<Problem> statusException(org.springframework.web.server.ResponseStatusException ex,HttpServletRequest req) {
    HttpStatus status=HttpStatus.resolve(ex.getStatusCode().value());
    return reply(status==null?HttpStatus.BAD_REQUEST:status,
        status==HttpStatus.UNAUTHORIZED?"Invalid credentials":"Request rejected",req,Map.of());
  }
  @ExceptionHandler({org.springframework.dao.OptimisticLockingFailureException.class,
                     org.springframework.dao.PessimisticLockingFailureException.class,
                     org.springframework.transaction.TransactionSystemException.class})
  ResponseEntity<Problem> concurrentWrite(Exception ex,HttpServletRequest req) {
    log.warn("Concurrent write or transaction conflict at {}",req.getRequestURI(),ex);
    return reply(HttpStatus.CONFLICT,"Concurrent update conflict; retry safely",req,Map.of());
  }
  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<Problem> forbidden(AccessDeniedException ex,HttpServletRequest req) { return reply(HttpStatus.FORBIDDEN,"Insufficient permissions",req,Map.of()); }
  @ExceptionHandler(Exception.class)
  ResponseEntity<Problem> unexpected(Exception ex,HttpServletRequest req) {
    log.error("Unhandled API exception at {}",req.getRequestURI(),ex);
    return reply(HttpStatus.INTERNAL_SERVER_ERROR,"An unexpected server error occurred",req,Map.of());
  }
}
