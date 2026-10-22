package com.character.service;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.character.mapper.ChatHistoryMapper;
import com.character.service.impl.ChatHistoryServiceImpl;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.apache.ibatis.session.SqlSession;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/** Real MyBatis mapper + SQL database + history business method, without external services. */
class ChatHistoryDatabaseTest {
    @Test void databaseHistoryIsIsolatedByUserAndConversationAndRestoredInOrder() throws Exception {
        JdbcDataSource dataSource=new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:"+java.util.UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc=new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE chat_history(id bigint primary key,message varchar(2000),message_type varchar(32),app_id bigint,user_id bigint,conversation_id varchar(64),turn_id varchar(64),create_time timestamp,update_time timestamp,is_delete int default 0)");
        jdbc.update("INSERT INTO chat_history(id,message,message_type,app_id,user_id,conversation_id,create_time) VALUES(1,'belongs-to-first-user','user',10,100,'session-a',CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO chat_history(id,message,message_type,app_id,user_id,conversation_id,create_time) VALUES(2,'other-session','user',10,200,'session-b',CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO chat_history(id,message,message_type,app_id,user_id,conversation_id,create_time) VALUES(3,'first','user',10,200,'session-a',CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO chat_history(id,message,message_type,app_id,user_id,conversation_id,create_time) VALUES(4,'second','user',10,200,'session-a',CURRENT_TIMESTAMP)");
        MybatisSqlSessionFactoryBean factory=new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        var sqlFactory=factory.getObject();
        assertNotNull(sqlFactory);
        sqlFactory.getConfiguration().addMapper(ChatHistoryMapper.class);
        try(SqlSession session=sqlFactory.openSession(true)) {
            ChatHistoryServiceImpl service=new ChatHistoryServiceImpl(null);
            ReflectionTestUtils.setField(service,"baseMapper",session.getMapper(ChatHistoryMapper.class));
            var memory=MessageWindowChatMemory.withMaxMessages(25);
            assertEquals(2,service.loadChatHistoryToMemory(10,200L,"session-a",memory,25));
            assertEquals("first",((UserMessage)memory.messages().get(0)).singleText());
            assertEquals("second",((UserMessage)memory.messages().get(1)).singleText());
            assertTrue(service.addChatMessage(10L,"third","user",200L,"session-a"));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM chat_history WHERE message='third' AND user_id=200 AND conversation_id='session-a'",Integer.class));
        }
    }
}
