package com.randomconnect.storage;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.nio.file.*;
import java.util.concurrent.*;
@Component
public class AudioProbe {
 private final String command;private final ObjectMapper json;
 public AudioProbe(@Value("${FFPROBE_PATH:ffprobe}") String command,ObjectMapper mapper){this.command=command;json=mapper;}
 public double duration(byte[] bytes){Path file=null;Process process=null;
  try {file=Files.createTempFile("rc-voice-",".media");Files.write(file,bytes);process=new ProcessBuilder(command,"-v","error","-protocol_whitelist","file,pipe","-show_entries","stream=codec_type:format=duration:packet=pts_time,duration_time","-of","json",file.toString()).redirectErrorStream(true).start();final Process child=process;
   byte[] output;try(var executor=Executors.newVirtualThreadPerTaskExecutor()){var read=executor.submit(()->child.getInputStream().readNBytes(2097153));try{output=read.get(8,TimeUnit.SECONDS);if(output.length>2097152)throw new IllegalArgumentException("Audio metadata too large");}catch(Exception e){child.destroyForcibly();throw e;}}
   if(!process.waitFor(2,TimeUnit.SECONDS)||process.exitValue()!=0)throw new IllegalArgumentException("Invalid audio");var data=json.readTree(output);boolean audio=false;for(var stream:data.path("streams")){if(!"audio".equals(stream.path("codec_type").asText()))throw new IllegalArgumentException("Voice must contain audio only");audio=true;}if(!audio)throw new IllegalArgumentException("No audio track");
   double duration=data.path("format").path("duration").asDouble(0);{double first=Double.POSITIVE_INFINITY,last=Double.NEGATIVE_INFINITY;for(var packet:data.path("packets")){if(!packet.has("pts_time"))continue;double start=packet.path("pts_time").asDouble();first=Math.min(first,start);last=Math.max(last,start+packet.path("duration_time").asDouble(0));}if(Double.isFinite(last-first))duration=Math.max(duration,last-first);}if(!Double.isFinite(duration)||duration<=0)throw new IllegalArgumentException("Audio has no valid duration");return duration;
  }catch(java.io.IOException e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Audio validation requires an available ffprobe executable");}catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalArgumentException("Invalid or unsupported audio");}finally{if(process!=null&&process.isAlive())process.destroyForcibly();if(file!=null)try{Files.deleteIfExists(file);}catch(Exception ignored){}}
 }
}
