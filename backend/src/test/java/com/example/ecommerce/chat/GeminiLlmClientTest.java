package com.example.ecommerce.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiLlmClientTest {

    @Test
    void missingKeyDoesNotCallProvider() {
        LlmProperties properties = properties("");
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        LlmException error = assertThrows(
                LlmException.class,
                () -> client(properties, builder).complete(List.of(new ChatTurn("user", "안녕"))));

        assertEquals("챗봇 설정을 확인한 뒤 다시 시도해 주세요.", error.getMessage());
        server.verify();
    }

    @Test
    void successReturnsModelText() {
        LlmProperties properties = properties("unit-test-only");
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.test/v1/interactions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "unit-test-only"))
                .andExpect(content().string(containsString("\"model\":\"unit-test-model\"")))
                .andExpect(content().string(containsString("\"store\":false")))
                .andExpect(content().string(not(containsString("unit-test-only"))))
                .andRespond(withSuccess(
                        "{\"steps\":[{\"type\":\"model_output\",\"content\":[{\"type\":\"text\",\"text\":\"일반 텍스트 답변\"}]}]}",
                        MediaType.APPLICATION_JSON));

        String reply = client(properties, builder).complete(List.of(new ChatTurn("user", "안녕")));

        assertEquals("일반 텍스트 답변", reply);
        server.verify();
    }

    @Test
    void invalidKeyDoesNotRetryOrExposeBody() {
        LlmProperties properties = properties("unit-test-only");
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.test/v1/interactions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("secret-api-key-body"));

        LlmException error = assertThrows(
                LlmException.class,
                () -> client(properties, builder).complete(List.of(new ChatTurn("user", "안녕"))));

        assertEquals("챗봇 인증에 실패했습니다. 설정을 확인한 뒤 다시 시도해 주세요.", error.getMessage());
        assertFalse(error.getMessage().contains("secret-api-key-body"));
        server.verify();
    }

    @Test
    void quotaExceededDoesNotRetryOrSwitchModel() {
        LlmProperties properties = properties("unit-test-only");
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.test/v1/interactions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .body("{\"error\":{\"code\":\"quota_exceeded\",\"message\":\"secret-quota\"}}"));

        LlmException error = assertThrows(
                LlmException.class,
                () -> client(properties, builder).complete(List.of(new ChatTurn("user", "안녕"))));

        assertEquals("챗봇 사용량 한도에 도달했습니다. 잠시 후 다시 시도해 주세요.", error.getMessage());
        assertFalse(error.getMessage().contains("secret-quota"));
        assertFalse(error.getMessage().contains("gemini"));
        server.verify();
    }

    @Test
    void transientRateLimitRetriesOnce() {
        LlmProperties properties = properties("unit-test-only");
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.test/v1/interactions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .body("{\"error\":{\"code\":\"rate_limit_exceeded\"}}"));
        server.expect(requestTo("https://example.test/v1/interactions"))
                .andRespond(withSuccess(
                        "{\"steps\":[{\"type\":\"model_output\",\"content\":[{\"type\":\"text\",\"text\":\"다시 응답\"}]}]}",
                        MediaType.APPLICATION_JSON));

        String reply = client(properties, builder).complete(List.of(new ChatTurn("user", "안녕")));

        assertEquals("다시 응답", reply);
        server.verify();
    }

    @Test
    void serverErrorDoesNotExposeResponseBody() {
        LlmProperties properties = properties("unit-test-only");
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.test/v1/interactions"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("secret-provider-body"));
        server.expect(requestTo("https://example.test/v1/interactions"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("secret-provider-body"));

        LlmException provider = assertThrows(
                LlmException.class,
                () -> client(properties, builder).complete(List.of(new ChatTurn("user", "안녕"))));

        assertEquals("챗봇 응답을 받지 못했습니다.", provider.getMessage());
        assertFalse(provider.getMessage().contains("secret-provider-body"));
        server.verify();
    }

    @Test
    void malformedBodyDoesNotExposePayload() {
        LlmProperties properties = properties("unit-test-only");
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.test/v1/interactions"))
                .andRespond(withSuccess("{\"steps\":[],\"secret\":\"hidden\"}", MediaType.APPLICATION_JSON));

        LlmException malformed = assertThrows(
                LlmException.class,
                () -> client(properties, builder).complete(List.of(new ChatTurn("user", "안녕"))));

        assertEquals("챗봇 응답을 받지 못했습니다.", malformed.getMessage());
        assertFalse(malformed.getMessage().contains("hidden"));
        server.verify();
    }

    @Test
    void timeoutRetriesOnceThenStaysSafe() {
        LlmProperties properties = properties("unit-test-only");
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.test/v1/interactions"))
                .andRespond(request -> {
                    throw new ResourceAccessException("timeout-secret");
                });
        server.expect(requestTo("https://example.test/v1/interactions"))
                .andRespond(request -> {
                    throw new ResourceAccessException("timeout-secret");
                });

        LlmException timeout = assertThrows(
                LlmException.class,
                () -> client(properties, builder).completeWithTools(List.of(Map.of("role", "user", "content", "주문")), List.of()));

        assertEquals("챗봇 응답이 지연되고 있습니다. 잠시 후 다시 시도해 주세요.", timeout.getMessage());
        assertFalse(timeout.getMessage().contains("timeout-secret"));
        server.verify();
    }

    @Test
    void toolCallIsParsedAndResultRoundKeepsProviderSteps() {
        LlmProperties properties = properties("unit-test-only");
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.test/v1/interactions"))
                .andExpect(content().string(containsString("\"name\":\"list_my_orders\"")))
                .andExpect(content().string(not(containsString("additionalProperties"))))
                .andRespond(withSuccess(
                        "{\"steps\":[{\"type\":\"function_call\",\"id\":\"call_1\",\"name\":\"list_my_orders\",\"arguments\":{},\"signature\":\"sig-hidden\"}],\"secret\":\"hidden\"}",
                        MediaType.APPLICATION_JSON));

        GeminiLlmClient gemini = client(properties, builder);
        LlmCompletion completion = gemini.completeWithTools(
                List.of(Map.of("role", "user", "content", "최근 주문")),
                List.of(Map.of(
                        "type", "function",
                        "function", Map.of(
                                "name", "list_my_orders",
                                "description", "내 주문",
                                "parameters", Map.of("type", "object", "properties", Map.of(), "additionalProperties", false)))));

        assertEquals(1, completion.toolCalls().size());
        assertEquals("call_1", completion.toolCalls().get(0).id());
        assertEquals("list_my_orders", completion.toolCalls().get(0).name());
        assertEquals("{}", completion.toolCalls().get(0).arguments());
        assertEquals(1, completion.providerSteps().size());
        assertFalse(String.valueOf(completion.content()).contains("hidden"));
        assertFalse(completion.toolCalls().get(0).arguments().contains("sig-hidden"));
        server.verify();
    }

    @Test
    void functionResultRoundSendsProviderSteps() {
        LlmProperties properties = properties("unit-test-only");
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.test/v1/interactions"))
                .andExpect(content().string(containsString("\"type\":\"function_result\"")))
                .andExpect(content().string(containsString("\"call_id\":\"call_1\"")))
                .andExpect(content().string(containsString("sig-hidden")))
                .andExpect(content().string(containsString("주문 없음")))
                .andRespond(withSuccess(
                        "{\"steps\":[{\"type\":\"model_output\",\"content\":[{\"type\":\"text\",\"text\":\"조회된 주문이 없습니다.\"}]}]}",
                        MediaType.APPLICATION_JSON));

        LlmCompletion followUp = client(properties, builder).completeWithTools(
                List.of(
                        Map.of("role", "user", "content", "최근 주문"),
                        Map.of(
                                "role", "assistant",
                                "content", "",
                                "provider_steps", List.of(Map.of(
                                        "type", "function_call",
                                        "id", "call_1",
                                        "name", "list_my_orders",
                                        "arguments", Map.of(),
                                        "signature", "sig-hidden"))),
                        Map.of(
                                "role", "tool",
                                "name", "list_my_orders",
                                "tool_call_id", "call_1",
                                "content", "주문 없음")),
                List.of());

        assertEquals("조회된 주문이 없습니다.", followUp.content());
        assertTrue(followUp.toolCalls().isEmpty());
        server.verify();
    }

    private static GeminiLlmClient client(LlmProperties properties, RestClient.Builder builder) {
        return new GeminiLlmClient(properties, builder.build(), new ObjectMapper());
    }

    private static LlmProperties properties(String apiKey) {
        LlmProperties properties = new LlmProperties();
        properties.setApiKey(apiKey);
        properties.setBaseUrl("https://example.test/v1");
        properties.setModel("unit-test-model");
        properties.setTimeoutSeconds(15);
        return properties;
    }
}
