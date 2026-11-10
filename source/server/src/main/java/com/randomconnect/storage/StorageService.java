package com.randomconnect.storage;
public interface StorageService {
 void put(String key,byte[] content,String contentType);
 String signedGet(String key,String filename,String contentType);
 void delete(String key);
}
