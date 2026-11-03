package com.randomconnect;

import com.randomconnect.config.WebSocketConfig;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WebSocketConfigTest {
 @Test void explicitOriginsMayContainWhitespaceAndDuplicates() {
  assertThatCode(()->new WebSocketConfig(null," https://app.vercel.app, http://localhost:5173, https://app.vercel.app ")).doesNotThrowAnyException();
 }
 @Test void emptyWildcardAndUrlPathsCannotBecomeAllowedOrigins() {
  for(String origins:new String[]{"",", ,","*","https://*.vercel.app","https://app.vercel.app/","https://app.vercel.app/chat","https://user:password@app.vercel.app","https://app.vercel.app?x=1"})
   assertThatThrownBy(()->new WebSocketConfig(null,origins)).isInstanceOf(IllegalArgumentException.class);
 }
}
