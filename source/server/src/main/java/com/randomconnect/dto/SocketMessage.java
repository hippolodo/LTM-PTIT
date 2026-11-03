package com.randomconnect.dto;
import com.fasterxml.jackson.databind.JsonNode;
public record SocketMessage(String type, String requestId, String roomId, JsonNode payload) {}
