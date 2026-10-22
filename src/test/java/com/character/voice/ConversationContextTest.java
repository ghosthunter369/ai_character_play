package com.character.voice;

import com.character.model.entity.ChatHistory;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ConversationContextTest {
    @Test void keepsNewestHistoryInOrderWithinUtf8Budget() {
        var newest=ChatHistory.builder().messageType("ai").message("最新回复").build();
        var older=ChatHistory.builder().messageType("user").message("旧".repeat(2000)).build();
        var messages=ConversationContext.build("角色",List.of(newest,older),"问题",512);
        assertEquals(List.of("角色","最新回复","问题"),messages.stream().map(m->m.get("content")).toList());
        assertTrue(messages.stream().mapToInt(m->m.get("content").getBytes(StandardCharsets.UTF_8).length+64).sum()<=512);
    }
    @Test void boundsSystemAndPreservesFullCurrentInput() {
        String input="问".repeat(2000);
        var messages=ConversationContext.build("角色😀".repeat(3000),List.of(),input,12000);
        assertTrue(messages.getFirst().get("content").getBytes(StandardCharsets.UTF_8).length<=4000);
        assertEquals(input,messages.getLast().get("content"));
        assertThrows(IllegalArgumentException.class,()->ConversationContext.build("角色",List.of(),input,1000));
    }
}
