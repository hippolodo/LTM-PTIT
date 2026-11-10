package com.randomconnect.config;
import com.randomconnect.service.RetentionService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
@Component
public class StartupRecovery implements ApplicationRunner {
 private final RetentionService retention;public StartupRecovery(RetentionService r){retention=r;}
 public void run(ApplicationArguments args){retention.reconcile();}
}
