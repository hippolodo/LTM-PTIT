package com.randomconnect.config;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
@Component @Profile("!test")
public class DatabasePolicy {
 public DatabasePolicy(@Value("${DB_HOST:localhost}") String host,@Value("${DB_PASSWORD:}") String password,@Value("${DB_SSL_MODE:VERIFY_IDENTITY}") String ssl) {
  if(!host.matches("[A-Za-z0-9.-]+"))throw new IllegalArgumentException("DB_HOST must be an exact hostname without a URL scheme or spaces");
  if(password.isBlank())throw new IllegalArgumentException("DB_PASSWORD must be configured");
  if(!ssl.equals("VERIFY_IDENTITY"))throw new IllegalArgumentException("DB_SSL_MODE must be VERIFY_IDENTITY to verify the certificate and hostname");
 }
}
