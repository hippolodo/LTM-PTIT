package com.randomconnect.storage;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.*;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.*;
import software.amazon.awssdk.services.s3.presigner.model.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import java.net.URI;
import java.time.Duration;
@Configuration
public class StorageConfig {
 @Bean StorageService storage(Environment env){
  String provider=env.getProperty("STORAGE_PROVIDER","disabled");if(!java.util.Set.of("disabled","s3").contains(provider))throw new IllegalArgumentException("STORAGE_PROVIDER must be disabled or s3");
  if(!"s3".equals(provider))return new StorageService(){private ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Object storage is not configured");}public void put(String k,byte[] b,String m){throw unavailable();}public String signedGet(String k,String n,String m){throw unavailable();}public void delete(String k){throw unavailable();}};
  URI endpoint=URI.create(required(env,"STORAGE_ENDPOINT"));if(!"https".equals(endpoint.getScheme())&&!Boolean.parseBoolean(env.getProperty("STORAGE_ALLOW_HTTP","false")))throw new IllegalArgumentException("Storage requires HTTPS");
  var credentials=StaticCredentialsProvider.create(AwsBasicCredentials.create(required(env,"STORAGE_ACCESS_KEY_ID"),required(env,"STORAGE_SECRET_ACCESS_KEY")));
  var region=Region.of(env.getProperty("STORAGE_REGION","auto"));var config=S3Configuration.builder().pathStyleAccessEnabled(true).build();
  var client=S3Client.builder().region(region).endpointOverride(endpoint).credentialsProvider(credentials).serviceConfiguration(config).httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(5)).socketTimeout(Duration.ofSeconds(15))).build();
  var signer=S3Presigner.builder().region(region).endpointOverride(endpoint).credentialsProvider(credentials).serviceConfiguration(config).build();
  return new S3Storage(client,signer,required(env,"STORAGE_BUCKET"));
 }
 private String required(Environment env,String name){String value=env.getProperty(name,"");if(value.isBlank())throw new IllegalArgumentException(name+" is required");return value;}
 static class S3Storage implements StorageService,AutoCloseable {
  private final S3Client client;private final S3Presigner signer;private final String bucket;
  S3Storage(S3Client c,S3Presigner s,String b){client=c;signer=s;bucket=b;}
  public void put(String key,byte[] content,String type){try{client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(type).cacheControl("private, no-store").build(),RequestBody.fromBytes(content));}catch(Exception e){throw unavailable();}}
  public String signedGet(String key,String name,String type){String disposition=org.springframework.http.ContentDisposition.builder(type.startsWith("image/")||type.startsWith("audio/")?"inline":"attachment").filename(name,java.nio.charset.StandardCharsets.UTF_8).build().toString();var request=GetObjectRequest.builder().bucket(bucket).key(key).responseContentType(type).responseContentDisposition(disposition).responseCacheControl("private, max-age=60").build();return signer.presignGetObject(GetObjectPresignRequest.builder().signatureDuration(Duration.ofSeconds(60)).getObjectRequest(request).build()).url().toString();}
  public void delete(String key){try{client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());}catch(Exception e){throw unavailable();}}
  private ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Object storage is unavailable");}
  public void close(){client.close();signer.close();}
 }
}
