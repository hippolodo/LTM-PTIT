package com.randomconnect.auth;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
@Component
public class RequestLimits {
 private record Window(long until,int count){}
 private final Map<String,Window> windows=new HashMap<>();
 public synchronized void take(String key,int maximum,long milliseconds){long now=System.currentTimeMillis();if(windows.size()>2048)windows.entrySet().removeIf(e->e.getValue().until<now);if(windows.size()>4096&&!windows.containsKey(key))throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Please retry later");var w=windows.get(key);if(w==null||w.until<now)w=new Window(now+milliseconds,0);if(w.count>=maximum)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Please slow down");windows.put(key,new Window(w.until,w.count+1));}
}
