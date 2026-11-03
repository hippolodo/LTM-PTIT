package com.randomconnect.service;
import java.util.*;
import com.randomconnect.model.*;
import org.springframework.stereotype.Component;
/** All methods are called under MatchmakingService's state lock. */
@Component
public class RoomService {
 private final Map<String,ChatRoom> rooms=new HashMap<>();
 public ChatRoom create(GuestSession a,GuestSession b) { var r=new ChatRoom(a.id,b.id); rooms.put(r.id(),r); a.roomId=b.roomId=r.id(); return r; }
 public ChatRoom get(String id) { return id==null ? null : rooms.get(id); }
 public ChatRoom remove(String id) { return id==null ? null : rooms.remove(id); }
 public String peer(GuestSession guest,String roomId) {
  var room=get(roomId);
  if(room==null || !room.id().equals(guest.roomId)) throw new IllegalArgumentException("NOT_IN_ROOM");
  return room.peer(guest.id);
 }
 public int size() { return rooms.size(); }
}
