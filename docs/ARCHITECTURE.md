> Tài liệu MVP trước nâng cấp. Với database/auth/history/file/voice, xem [Database](DATABASE_DESIGN.md), [Auth](AUTHENTICATION.md), [History và events hiện hành](CHAT_HISTORY.md), [Storage](FILE_AUDIO_SHARING.md), [Deployment](DEPLOYMENT_GUIDE.md). Các quy tắc matching/signaling giữ lại trừ phần được tài liệu mới cập nhật.

# RandomConnect architecture

Vue 3 + Pinia + Router -> native WebSocket JSON -> Spring Boot 3.5 / Java 21.
Media flows through RTCPeerConnection directly or TURN; never through Spring.

## Audit (2026-11-03)
Workspace was empty and was not a Git repository. No template README or INSTRUCTION.md existed. No instructor file has been created or modified. Group fields remain TODO.

## State and concurrency
Server is authoritative for IDLE / WAITING / IN_ROOM. Client reports CONNECTING / CONNECTED only from peer connection events.
One lock protects sessions, queue and rooms. Transitions append notifications to an ordered outbox under lock; a single dispatcher delivers outside the lock. This preserves transition ordering without blocking the state lock on network IO. Room relay deliveries are revalidated before send. Socket sends are bounded by Spring ConcurrentWebSocketSessionDecorator.
One process, RAM only, no persistence or horizontal scaling. Restart disconnects all guests. No chat/media recording.

## Acceptance
Health; two guests match once; three guests leave one waiting; relay once in both directions; Next requeues only requester; Stop never requeues; disconnect clears state; wrong/stale room rejected; early ICE queued; media cleanup and actual browser video/audio require manual verification.

## TURN
IceConfigService signs per-guest HMAC-SHA1 credentials, lifetime 600 seconds, from server-only environment. Fresh ICE configuration is delivered on MATCH_FOUND. VITE overrides are public; never place the shared secret in the client. No relay deployment has been verified.

## Browser resources
Media and RTCPeerConnection live in shallow refs/service closures. Signals are serialized per peer generation. Room reset closes peer handlers/timers and clears pending ICE. Local track preferences survive Next/reacquisition. Stop closes the socket in addition to LEAVE_ROOM, resolving the race when a waiting guest was matched just before Stop. No automatic reconnect/rematch.
