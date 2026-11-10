package com.randomconnect.auth;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public final class Secrets {
 private static final SecureRandom RANDOM=new SecureRandom();
 private Secrets(){}
 public static String token(){byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
 public static String hash(String raw){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 public static boolean matches(String raw,String hash){return MessageDigest.isEqual(hash(raw).getBytes(StandardCharsets.US_ASCII),hash.getBytes(StandardCharsets.US_ASCII));}
}
