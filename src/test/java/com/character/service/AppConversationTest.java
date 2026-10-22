package com.character.service;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.character.mapper.*;
import com.character.model.entity.User;
import com.character.service.impl.*;
import com.character.voice.*;
import com.sun.net.httpserver.HttpServer;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.ResolvableType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.test.StepVerifier;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** Actual AppService + MyBatis SQL history + network SSE, including cancelled generation. */
class AppConversationTest {
    @Test void cancellationDoesNotPersistPartialAssistantAndNextTurnUsesIsolatedDatabaseContext() throws Exception {
        var source=new JdbcDataSource(); source.setURL("jdbc:h2:mem:"+java.util.UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE app(app_id bigint primary key,description varchar,app_name varchar,init_prompt varchar,cover varchar,user_id bigint,prologue varchar,priority int,create_time timestamp,edit_time timestamp,is_delete int default 0)");
        jdbc.execute("CREATE TABLE chat_history(id bigint primary key,message varchar(20000),message_type varchar(32),app_id bigint,user_id bigint,conversation_id varchar(64),turn_id varchar(64),create_time timestamp,update_time timestamp,is_delete int default 0)");
        jdbc.update("INSERT INTO app(app_id,app_name,init_prompt) VALUES(10,'role','You are a teacher')");
        jdbc.update("INSERT INTO chat_history(id,message,message_type,app_id,user_id,conversation_id,create_time) VALUES(1,'secret-user','user',10,999,'a',CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO chat_history(id,message,message_type,app_id,user_id,conversation_id,create_time) VALUES(2,'secret-conversation','user',10,100,'b',CURRENT_TIMESTAMP)");
        var factory=new MybatisSqlSessionFactoryBean(); factory.setDataSource(source);
        var sf=factory.getObject(); assertNotNull(sf);
        sf.getConfiguration().addMapper(AppMapper.class); sf.getConfiguration().addMapper(ChatHistoryMapper.class);
        var request=new AtomicReference<String>();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/chat/completions",exchange->{
            String body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8); request.set(body);
            exchange.sendResponseHeaders(200,0);
            try(var out=exchange.getResponseBody()) {
                out.write("data: {\"choices\":[{\"delta\":{\"content\":\"回答\"}}]}\n\n".getBytes(StandardCharsets.UTF_8)); out.flush();
                var messages=new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).path("messages");
                if("cancel-me".equals(messages.get(messages.size()-1).path("content").asText())) {
                    for(int i=0;i<1000;i++) { out.write((": "+" ".repeat(8192)+"\n\n").getBytes(StandardCharsets.UTF_8)); out.flush(); Thread.sleep(5); }
                } else out.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
            } catch(Exception cancelled) { /* Real network cancellation interrupts the writer. */ }
        }); server.start();
        try(var session=sf.openSession(true)) {
            var apps=new AppServiceImpl(null); var history=new ChatHistoryServiceImpl(apps);
            ReflectionTestUtils.setField(apps,"baseMapper",session.getMapper(AppMapper.class));
            ReflectionTestUtils.setField(history,"baseMapper",session.getMapper(ChatHistoryMapper.class));
            ReflectionTestUtils.setField(apps,"chatHistoryService",history);
            var beans=new DefaultListableBeanFactory();
            ReflectionTestUtils.setField(apps,"roleKnowledge",new RoleKnowledge(beans.getBeanProvider(dev.langchain4j.model.embedding.EmbeddingModel.class),beans.getBeanProvider(ResolvableType.forClassWithGenerics(dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore.class,dev.langchain4j.data.segment.TextSegment.class))));
            var env=new MockEnvironment().withProperty("LLM_BASE_URL","http://127.0.0.1:"+server.getAddress().getPort()+"/v1").withProperty("LLM_API_KEY","test");
            ReflectionTestUtils.setField(apps,"chatCompletionClient",new ChatCompletionClient(env));
            var user=new User(); user.setId(100L);
            StepVerifier.create(apps.chatInConversation(10L,"cancel-me",user,"a")).expectNext("回答").thenCancel().verify(Duration.ofSeconds(5));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM chat_history WHERE message_type='ai'",Integer.class));
            StepVerifier.create(apps.chatInConversation(10L,"next-turn",user,"a")).expectNext("回答").expectComplete().verify(Duration.ofSeconds(5));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM chat_history WHERE message_type='ai' AND conversation_id='a' AND user_id=100",Integer.class));
            assertFalse(request.get().contains("secret-user")); assertFalse(request.get().contains("secret-conversation"));
            assertTrue(request.get().contains("cancel-me"));
            assertEquals(1,new com.fasterxml.jackson.databind.ObjectMapper().readTree(request.get()).path("messages").findValuesAsText("content").stream().filter("next-turn"::equals).count());
        } finally { server.stop(0); }
    }
}
