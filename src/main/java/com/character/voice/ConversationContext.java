package com.character.voice;

import com.character.model.entity.ChatHistory;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Conservative UTF-8 byte budget, including message framing; newest history wins. */
public final class ConversationContext {
    private ConversationContext() {}
    public static List<Map<String,String>> build(String system, List<ChatHistory> newestFirst, String input, int budget) {
        system = truncate(system, 4000);
        int used = bytes(system) + bytes(input) + 256;
        if (used > budget) throw new IllegalArgumentException("当前消息超过模型上下文预算，请缩短消息");
        List<Map<String,String>> selected = new ArrayList<>();
        for (ChatHistory row : newestFirst) {
            if (!Set.of("user", "ai").contains(row.getMessageType()) || row.getMessage() == null) continue;
            int size = bytes(row.getMessage()) + 64;
            if (used + size > budget) break;
            used += size;
            selected.add(Map.of("role", "ai".equals(row.getMessageType()) ? "assistant" : "user", "content", row.getMessage()));
        }
        Collections.reverse(selected);
        List<Map<String,String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", system));
        messages.addAll(selected);
        messages.add(Map.of("role", "user", "content", input));
        return messages;
    }
    private static int bytes(String text) { return text.getBytes(StandardCharsets.UTF_8).length; }
    private static String truncate(String text, int budget) {
        StringBuilder result = new StringBuilder(); int used = 0;
        for (int point : text.codePoints().toArray()) {
            String value = new String(Character.toChars(point)); int size = bytes(value);
            if (used + size > budget) break;
            result.append(value); used += size;
        }
        return result.toString();
    }
}
