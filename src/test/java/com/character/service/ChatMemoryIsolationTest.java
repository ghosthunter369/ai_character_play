package com.character.service;

import com.character.service.impl.ChatHistoryServiceImpl;
import com.character.model.entity.ChatHistory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real loader; only the database transport is substituted here. */
class ChatMemoryIsolationTest {
    @Test
    void historyLoaderConstrainsTheRequestedConversation() {
        ChatHistoryServiceImpl service = new ChatHistoryServiceImpl(null) {
            @Override public List<ChatHistory> list(Wrapper<ChatHistory> wrapper) {
                QueryWrapper<?> query = (QueryWrapper<?>) wrapper;
                assertTrue(query.getSqlSegment().contains("conversation_id"));
                assertTrue(query.getParamNameValuePairs().containsValue("session-one"));
                return List.of();
            }
        };
        service.loadChatHistoryToMemory(100L, 202L, "session-one", MessageWindowChatMemory.withMaxMessages(25), 25);
    }
    @Test
    void historyLoaderConstrainsTheAuthenticatedUser() {
        ChatHistoryServiceImpl service = new ChatHistoryServiceImpl(null) {
            @Override public List<ChatHistory> list(Wrapper<ChatHistory> wrapper) {
                QueryWrapper<?> query = (QueryWrapper<?>) wrapper;
                assertTrue(query.getSqlSegment().contains("user_id"),
                        "History query must constrain user_id before restoring shared-role history");
                assertTrue(query.getParamNameValuePairs().containsValue(202L));
                return List.of();
            }
        };
        service.loadChatHistoryToMemory(100L, 202L, MessageWindowChatMemory.withMaxMessages(25), 25);
    }
}
