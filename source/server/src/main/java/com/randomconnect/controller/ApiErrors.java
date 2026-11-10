package com.randomconnect.controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.Map;
@RestControllerAdvice
public class ApiErrors {
 @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> status(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("code",e.getStatusCode().value(),"message",e.getReason()==null?"Request rejected":e.getReason()));}
 @ExceptionHandler({MethodArgumentNotValidException.class,IllegalArgumentException.class,org.springframework.web.multipart.MaxUploadSizeExceededException.class}) ResponseEntity<?> invalid(Exception e){return ResponseEntity.badRequest().body(Map.of("code","INVALID_REQUEST","message","Invalid input or file size"));}
 @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> duplicate(){return ResponseEntity.status(409).body(Map.of("code","CONFLICT","message","Record already exists or violates constraints"));}
 @ExceptionHandler(Exception.class) ResponseEntity<?> internal(){return ResponseEntity.status(500).body(Map.of("code","INTERNAL_ERROR","message","Cannot complete request"));}
}
