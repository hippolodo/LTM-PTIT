package com.randomconnect;
import com.randomconnect.auth.*;
import com.randomconnect.persistence.*;
import com.randomconnect.service.ConversationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT) @Transactional
class HistoryIntegrationTest {
 @Autowired UserRepository users;@Autowired IdentityService identities;@Autowired ConversationService chats;@Autowired ConversationRepository conversations;@Autowired ParticipantRepository participants;@Autowired SocketTickets tickets;
 private Actor user(){var u=new UserAccount();u.username="u"+UUID.randomUUID().toString().substring(0,15);u.email=u.username+"@example.invalid";u.displayName=u.username;u.passwordHash="test-only-hash";users.saveAndFlush(u);return new Actor("USER",u.id,null);}
 @Test void registeredHistoryDeduplicatesPaginatesAndEnforcesMembership(){var a=user();var b=user();var outsider=user();String id=UUID.randomUUID().toString();assertThat(chats.create(id,a,b).get("persistentHistory")).isEqualTo(true);String retry=UUID.randomUUID().toString();var first=chats.text(a,id,"hello",retry);assertThat(chats.text(a,id,"hello",retry).created()).isFalse();chats.text(b,id,"reply",UUID.randomUUID().toString());assertThat((List<?>)chats.page(a,id,0,1).get("items")).hasSize(1);assertThat(chats.page(a,id,0,1).get("totalPages")).isEqualTo(2);assertThatThrownBy(()->chats.page(outsider,id,0,10)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);chats.receipt(b,id,(String)first.payload().get("messageId"),"READ");assertThat(chats.receipt(b,id,(String)first.payload().get("messageId"),"DELIVERED").get("status")).isEqualTo("READ");chats.close(id);chats.hide(a,id);assertThat((List<?>)chats.history(a,0,20).get("items")).isEmpty();assertThat((List<?>)chats.history(b,0,20).get("items")).hasSize(1);assertThatThrownBy(()->chats.detail(a,id)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);}
 @Test void mixedAndGuestChatsNeverBecomeAccountHistory(){var a=user();var g=identities.createGuest();var guest=new Actor("GUEST",g.guestId(),null);String id=UUID.randomUUID().toString();assertThat(chats.create(id,a,guest).get("persistentHistory")).isEqualTo(false);chats.text(guest,id,"temporary",UUID.randomUUID().toString());assertThat((List<?>)chats.history(a,0,20).get("items")).isEmpty();assertThat(conversations.findById(id).orElseThrow().expiresAt).isNotNull();chats.close(id);assertThatThrownBy(()->chats.text(a,id,"late",UUID.randomUUID().toString())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);}
 @Test void socketTicketIsSingleUse(){var g=identities.createGuest();var actor=new Actor("GUEST",g.guestId(),null);String ticket=tickets.issue(actor);assertThat(tickets.consume(ticket).id()).isEqualTo(actor.id());assertThatThrownBy(()->tickets.consume(ticket)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);}
 @Test void databaseRejectsParticipantWithTwoIdentities(){var a=user();var g=identities.createGuest();String id=UUID.randomUUID().toString();chats.create(id,a,new Actor("GUEST",g.guestId(),null));var invalid=new Participant();invalid.conversationId=id;invalid.userId=a.id();invalid.guestSessionId=g.guestId();assertThatThrownBy(()->participants.saveAndFlush(invalid)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);}
}
