package com.randomconnect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.randomconnect.storage.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class BrowserVoiceTest {
 @BeforeEach void requireTools() throws Exception {
  for(String command:new String[]{"ffmpeg","ffprobe"}) {
   try {
    var version=new ProcessBuilder(command,"-version").redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
    Assumptions.assumeTrue(version.waitFor(5,TimeUnit.SECONDS)&&version.exitValue()==0,command+" unavailable");
   } catch(java.io.IOException e){Assumptions.assumeTrue(false,command+" unavailable");}
  }
 }
 @Test void validatesStreamingWebmWithoutContainerDuration() throws Exception {
  var file=Files.createTempFile("voice-browser-",".webm");
  try {
   var process=new ProcessBuilder("ffmpeg","-v","error","-y","-f","lavfi","-i","sine=frequency=440:duration=1","-c:a","libopus","-f","webm","-live","1",file.toString())
    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
   assertThat(process.waitFor(10,TimeUnit.SECONDS)).isTrue();assertThat(process.exitValue()).isZero();
   var validator=new UploadValidator(10485760,120,new AudioProbe("ffprobe",new ObjectMapper()));
   var result=validator.validate("voice.webm","audio/webm;codecs=opus",Files.readAllBytes(file),true);
   assertThat(result.kind()).isEqualTo("AUDIO");assertThat(result.duration()).isBetween(.9,1.2);
  } finally {Files.deleteIfExists(file);}
 }

 @Test void webmFallbackStillRejectsVideoTracks() throws Exception {
  var file=Files.createTempFile("voice-video-",".webm");
  try {
   var process=new ProcessBuilder("ffmpeg","-v","error","-y","-f","lavfi","-i","color=c=black:s=16x16:r=1:d=1",
    "-f","lavfi","-i","sine=frequency=440:duration=1","-c:v","libvpx","-c:a","libopus","-f","webm","-live","1",file.toString())
    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
   assertThat(process.waitFor(10,TimeUnit.SECONDS)).isTrue();assertThat(process.exitValue()).isZero();
   var validator=new UploadValidator(10485760,120,new AudioProbe("ffprobe",new ObjectMapper()));
   assertThatThrownBy(()->validator.validate("voice.webm","audio/webm",Files.readAllBytes(file),true))
    .isInstanceOf(UploadValidationException.class).hasMessage("Voice must contain audio only");
  } finally {Files.deleteIfExists(file);}
 }
}
