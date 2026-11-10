package com.randomconnect.controller;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
@RestController
public class HealthController {
 private final DataSource database;
 public HealthController(DataSource database){this.database=database;}
 @GetMapping("/api/health") public ResponseEntity<Map<String,String>> health() {
  try(var connection=database.getConnection()){
   if(connection.isValid(2))return ResponseEntity.ok(Map.of("status","UP","service","randomconnect"));
  }catch(Exception ignored){}
  return ResponseEntity.status(503).body(Map.of("status","DOWN","service","randomconnect"));
 }
}
