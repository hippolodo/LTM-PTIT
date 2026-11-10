package com.randomconnect.storage;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.apache.tika.Tika;
import java.util.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.zip.*;
@Component
public class UploadValidator {
 public record Validated(String name,String mime,String kind,Double duration){}
 private final long maxBytes;private final double maxSeconds;private final AudioProbe audio;private final Tika tika=new Tika();
 public UploadValidator(@Value("${UPLOAD_MAX_BYTES:10485760}") long bytes,@Value("${AUDIO_MAX_SECONDS:120}") double seconds,AudioProbe audio){maxBytes=bytes;maxSeconds=seconds;this.audio=audio;}
 public Validated validate(String name,String declared,byte[] bytes,boolean voice){
  if(name==null||name.length()>180||name.contains("/")||name.contains("\\")||name.contains("..")||name.chars().anyMatch(c->c<32||c==127)||bytes.length==0||bytes.length>maxBytes)throw new IllegalArgumentException("Invalid file name or size");
  String mime=tika.detect(bytes),kind="FILE";Double duration=null;String lower=name.toLowerCase(Locale.ROOT);
  if(voice){
   if(!(mime.contains("webm")||mime.contains("ogg")||mime.contains("mp4")||mime.contains("m4a")||mime.contains("wav")))throw new IllegalArgumentException("Unsupported audio format");
   duration=audio.duration(bytes);if(duration>maxSeconds+0.25)throw new IllegalArgumentException("Voice message is too long");kind="AUDIO";
   mime=mime.contains("webm")?"audio/webm":mime.contains("ogg")?"audio/ogg":mime.contains("wav")?"audio/wav":"audio/mp4";
  }else if(bytes.length>=4&&bytes[0]=='P'&&bytes[1]=='K'){docx(bytes);mime="application/vnd.openxmlformats-officedocument.wordprocessingml.document";}
  else if(Set.of("image/png","image/jpeg","image/webp").contains(mime)){kind="IMAGE";}
  else if(mime.equals("application/pdf")){String end=new String(Arrays.copyOfRange(bytes,Math.max(0,bytes.length-2048),bytes.length),StandardCharsets.ISO_8859_1);if(!end.contains("%%EOF"))throw new IllegalArgumentException("Invalid PDF");}
  else if(mime.equals("text/plain")){try{String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();if(text.chars().anyMatch(c->c==0||(c<32&&c!=9&&c!=10&&c!=13)))throw new IllegalArgumentException("Invalid text file");}catch(CharacterCodingException e){throw new IllegalArgumentException("Text must be UTF-8");}}
  else throw new IllegalArgumentException("Unsupported file content");
  var extensions=Map.of("image/png",List.of(".png"),"image/jpeg",List.of(".jpg",".jpeg"),"image/webp",List.of(".webp"),"application/pdf",List.of(".pdf"),"text/plain",List.of(".txt"),"application/vnd.openxmlformats-officedocument.wordprocessingml.document",List.of(".docx"),"audio/webm",List.of(".webm"),"audio/ogg",List.of(".ogg"),"audio/wav",List.of(".wav"),"audio/mp4",List.of(".m4a",".mp4"));
  if(extensions.get(mime).stream().noneMatch(lower::endsWith))throw new IllegalArgumentException("File extension does not match content");
  String claim=declared==null?"":declared.split(";")[0].trim().toLowerCase(Locale.ROOT);if(!claim.isEmpty()&&!claim.equals("application/octet-stream")&&!claim.equals(mime)&&!(voice&&Set.of("audio/x-m4a","audio/x-wav","video/webm","video/mp4","application/ogg").contains(claim)))throw new IllegalArgumentException("Declared MIME does not match content");
  return new Validated(name,mime,kind,duration);
 }
 private void docx(byte[] bytes){boolean types=false,document=false;long expanded=0;int entries=0;try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))){ZipEntry entry;byte[] buffer=new byte[8192];while((entry=zip.getNextEntry())!=null){if(++entries>1000||entry.getName().contains("..")||entry.getName().startsWith("/")||entry.getName().contains("\\")||entry.getName().toLowerCase(Locale.ROOT).endsWith("vbaproject.bin"))throw new IllegalArgumentException("Unsafe DOCX");types|=entry.getName().equals("[Content_Types].xml");document|=entry.getName().equals("word/document.xml");int n;while((n=zip.read(buffer))!=-1){expanded+=n;if(expanded>33554432)throw new IllegalArgumentException("DOCX expands too much");}}}catch(IOException e){throw new IllegalArgumentException("Invalid DOCX");}if(!types||!document)throw new IllegalArgumentException("Not a DOCX document");}
}
