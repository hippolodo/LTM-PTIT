package com.randomconnect;
import com.randomconnect.storage.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
class UploadSecurityTest {
 final UploadValidator files=new UploadValidator(1024,120,mock(AudioProbe.class));
 @Test void textValidatesContentAndName(){assertThat(files.validate("hello.txt","text/plain","hello".getBytes(StandardCharsets.UTF_8),false).mime()).isEqualTo("text/plain");for(String name:new String[]{"../hello.txt","a/b.txt","a\\b.txt","bad\r\n.txt"})assertThatThrownBy(()->files.validate(name,"text/plain",new byte[]{65},false)).isInstanceOf(IllegalArgumentException.class);}
 @Test void rejectsOversizeAndForgedMime(){assertThatThrownBy(()->files.validate("big.txt","text/plain",new byte[1025],false)).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->files.validate("fake.png","image/png","this is not an image".getBytes(StandardCharsets.UTF_8),false)).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->files.validate("fake.pdf","application/pdf","hello".getBytes(StandardCharsets.UTF_8),false)).isInstanceOf(IllegalArgumentException.class);}
 @Test void unsupportedExecutableIsRejected(){assertThatThrownBy(()->files.validate("run.exe","application/octet-stream",new byte[]{77,90,0,0,0},false)).isInstanceOf(IllegalArgumentException.class);}
 @Test void tooLongVoiceIsRejectedAfterServerProbe(){var probe=mock(AudioProbe.class);when(probe.duration(any())).thenReturn(121.0);var validator=new UploadValidator(10485760,120,probe);byte[] wave=wave(1);assertThatThrownBy(()->validator.validate("voice.wav","audio/wav",wave,true)).isInstanceOf(IllegalArgumentException.class);}
 static byte[] wave(int seconds){int length=8000*2*seconds;var b=java.nio.ByteBuffer.allocate(44+length).order(java.nio.ByteOrder.LITTLE_ENDIAN);b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36+length).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short)1).putShort((short)1).putInt(8000).putInt(16000).putShort((short)2).putShort((short)16).put("data".getBytes(StandardCharsets.US_ASCII)).putInt(length);return b.array();}
 @Test void actualFfprobeChecksAudioDuration(){try{var process=new ProcessBuilder("ffprobe","-version").start();org.junit.jupiter.api.Assumptions.assumeTrue(process.waitFor()==0);}catch(Exception e){org.junit.jupiter.api.Assumptions.assumeTrue(false,"ffprobe not installed in this environment");}double duration=new AudioProbe("ffprobe",new ObjectMapper()).duration(wave(1));assertThat(duration).isBetween(.99,1.01);}
}
