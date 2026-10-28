package com.example.ecommerce.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GeminiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiLlmClient.class);
    private static final String MISSING_CONFIG = "챗봇 설정을 확인한 뒤 다시 시도해 주세요.";
    private static final String AUTH_FAILED = "챗봇 인증에 실패했습니다. 설정을 확인한 뒤 다시 시도해 주세요.";
    private static final String QUOTA = "챗봇 사용량 한도에 도달했습니다. 잠시 후 다시 시도해 주세요.";
    private static final String UNAVAILABLE = "챗봇을 지금 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.";
    private static final String TIMEOUT = "챗봇 응답이 지연되고 있습니다. 잠시 후 다시 시도해 주세요.";
    private static final String PROVIDER_ERROR = "챗봇 응답을 받지 못했습니다.";
    private static final int MAX_ATTEMPTS = 2;
    private static final int MAX_TOOL_CALLS = 8;
    private static final int MAX_OUTPUT_TOKENS = 2048;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final LlmProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public GeminiLlmClient(LlmProperties properties, ObjectMapper objectMapper) {
        this(properties, buildRestClient(properties), objectMapper);
    }

    GeminiLlmClient(LlmProperties properties, RestClient restClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String complete(List<ChatTurn> messages) {
        LlmCompletion completion = completeWithTools(messagesAsMaps(messages), List.of());
        if (completion.content() == null || completion.content().isBlank()) {
            throw new LlmException(PROVIDER_ERROR);
        }
        return completion.content();
    }

    @Override
    public LlmCompletion completeWithTools(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        if (isBlank(properties.getApiKey()) || isBlank(properties.getModel()) || isBlank(properties.getBaseUrl())) {
            throw new LlmException(MISSING_CONFIG);
        }
        Map<String, Object> request = requestBody(messages, tools);
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String body = restClient.post()
                        .uri("/interactions")
                        .header("x-goog-api-key", properties.getApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(String.class);
                return readCompletion(body);
            } catch (LlmException exception) {
                throw exception;
            } catch (ResourceAccessException exception) {
                log.warn("Gemini 응답 시간 초과");
                if (attempt == MAX_ATTEMPTS) {
                    throw new LlmException(TIMEOUT);
                }
            } catch (RestClientResponseException exception) {
                int status = exception.getStatusCode().value();
                String code = errorCode(exception);
                log.warn("Gemini 호출 실패 status={}", status);
                if (attempt == MAX_ATTEMPTS || !transientFailure(status, code)) {
                    throw new LlmException(userMessage(status));
                }
            } catch (RestClientException exception) {
                log.warn("Gemini 호출 실패");
                throw new LlmException(PROVIDER_ERROR);
            }
        }
        throw new LlmException(PROVIDER_ERROR);
    }

    private Map<String, Object> requestBody(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", properties.getModel());
        String instruction = systemInstruction(messages);
        if (!instruction.isBlank()) {
            request.put("system_instruction", instruction);
        }
        request.put("input", toInput(messages));
        List<Map<String, Object>> declarations = toGeminiTools(tools);
        if (!declarations.isEmpty()) {
            request.put("tools", declarations);
        }
        request.put("generation_config", Map.of(
                "max_output_tokens", MAX_OUTPUT_TOKENS,
                "thinking_level", "low",
                "thinking_summaries", "none"));
        request.put("store", false);
        return request;
    }

    private LlmCompletion readCompletion(String body) {
        try {
            JsonNode root = objectMapper.readTree(body == null ? "" : body);
            JsonNode steps = root.path("steps");
            if (!steps.isArray() || steps.isEmpty()) {
                throw new LlmException(PROVIDER_ERROR);
            }
            List<Map<String, Object>> providerSteps = new ArrayList<>();
            List<LlmToolCall> toolCalls = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            for (JsonNode step : steps) {
                providerSteps.add(objectMapper.convertValue(step, MAP_TYPE));
                String type = step.path("type").asText("");
                if ("function_call".equals(type) && toolCalls.size() < MAX_TOOL_CALLS) {
                    toolCalls.add(new LlmToolCall(
                            step.path("id").asText(""),
                            step.path("name").asText(""),
                            argumentsJson(step.get("arguments"))));
                } else if ("model_output".equals(type)) {
                    appendText(text, step.path("content"));
                }
            }
            String content = text.toString().trim();
            if (content.isEmpty() && toolCalls.isEmpty()) {
                throw new LlmException(PROVIDER_ERROR);
            }
            return new LlmCompletion(content.isEmpty() ? null : content, toolCalls, providerSteps);
        } catch (LlmException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("Gemini 응답 형식 확인 실패");
            throw new LlmException(PROVIDER_ERROR);
        }
    }

    private String argumentsJson(JsonNode arguments) {
        if (arguments == null || arguments.isNull() || arguments.isMissingNode()) {
            return "{}";
        }
        if (arguments.isTextual()) {
            return arguments.asText();
        }
        try {
            return objectMapper.writeValueAsString(arguments);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private static void appendText(StringBuilder text, JsonNode content) {
        if (content.isTextual()) {
            text.append(content.asText());
            return;
        }
        if (!content.isArray()) {
            return;
        }
        for (JsonNode part : content) {
            if ("text".equals(part.path("type").asText("")) || part.path("text").isTextual()) {
                text.append(part.path("text").asText(""));
            }
        }
    }

    private String systemInstruction(List<Map<String, Object>> messages) {
        if (messages == null) {
            return "";
        }
        StringBuilder instruction = new StringBuilder();
        for (Map<String, Object> message : messages) {
            if (!"system".equals(String.valueOf(message.get("role")))) {
                continue;
            }
            String content = text(message.get("content"));
            if (content.isBlank()) {
                continue;
            }
            if (!instruction.isEmpty()) {
                instruction.append('\n');
            }
            instruction.append(content);
        }
        return instruction.toString();
    }

    private List<Map<String, Object>> toInput(List<Map<String, Object>> messages) {
        List<Map<String, Object>> input = new ArrayList<>();
        if (messages == null) {
            return input;
        }
        for (Map<String, Object> message : messages) {
            String role = String.valueOf(message.get("role"));
            if ("system".equals(role)) {
                continue;
            }
            if ("tool".equals(role)) {
                input.add(functionResult(message));
                continue;
            }
            if ("assistant".equals(role) && message.get("provider_steps") instanceof List<?> steps && !steps.isEmpty()) {
                for (Object step : steps) {
                    if (step instanceof Map<?, ?> map) {
                        input.add(stringKeyMap(map));
                    }
                }
                continue;
            }
            String content = text(message.get("content"));
            if (content.isBlank()) {
                continue;
            }
            String type = "assistant".equals(role) ? "model_output" : "user_input";
            input.add(textStep(type, content));
        }
        return input;
    }

    private Map<String, Object> functionResult(Map<String, Object> message) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("type", "function_result");
        step.put("name", text(message.get("name")));
        step.put("call_id", text(message.get("tool_call_id")));
        step.put("result", List.of(Map.of("type", "text", "text", text(message.get("content")))));
        return step;
    }

    private static Map<String, Object> textStep(String type, String content) {
        return Map.of(
                "type", type,
                "content", List.of(Map.of("type", "text", "text", content)));
    }

    private List<Map<String, Object>> toGeminiTools(List<Map<String, Object>> tools) {
        List<Map<String, Object>> declarations = new ArrayList<>();
        if (tools == null) {
            return declarations;
        }
        for (Map<String, Object> tool : tools) {
            Object function = tool.get("function");
            if (!(function instanceof Map<?, ?> source)) {
                continue;
            }
            Map<String, Object> declaration = new LinkedHashMap<>();
            declaration.put("type", "function");
            declaration.put("name", source.get("name"));
            declaration.put("description", source.get("description"));
            if (source.get("parameters") instanceof Map<?, ?> parameters) {
                Map<String, Object> copy = new LinkedHashMap<>();
                parameters.forEach((key, value) -> {
                    if (!"additionalProperties".equals(String.valueOf(key))) {
                        copy.put(String.valueOf(key), value);
                    }
                });
                declaration.put("parameters", copy);
            }
            declarations.add(declaration);
        }
        return declarations;
    }

    private static Map<String, Object> stringKeyMap(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(String.valueOf(key), value));
        return copy;
    }

    private String errorCode(RestClientResponseException exception) {
        try {
            String body = exception.getResponseBodyAsString();
            if (body == null || body.isBlank()) {
                return "";
            }
            JsonNode root = objectMapper.readTree(body);
            JsonNode nested = root.path("error").path("code");
            if (nested.isTextual()) {
                return nested.asText();
            }
            JsonNode top = root.path("code");
            return top.isTextual() ? top.asText() : "";
        } catch (Exception exceptionInBody) {
            return "";
        }
    }

    private static boolean transientFailure(int status, String code) {
        if ("quota_exceeded".equals(code) || "payment_required".equals(code)) {
            return false;
        }
        return status == 429 || status == 500 || status == 503 || status == 504;
    }

    private static String userMessage(int status) {
        if (status == 401 || status == 403) {
            return AUTH_FAILED;
        }
        if (status == 404) {
            return MISSING_CONFIG;
        }
        if (status == 402) {
            return UNAVAILABLE;
        }
        if (status == 429) {
            return QUOTA;
        }
        return PROVIDER_ERROR;
    }

    private static List<Map<String, Object>> messagesAsMaps(List<ChatTurn> messages) {
        List<Map<String, Object>> mapped = new ArrayList<>();
        if (messages == null) {
            return mapped;
        }
        for (ChatTurn message : messages) {
            mapped.add(Map.of(
                    "role", message.role() == null ? "" : message.role(),
                    "content", message.content() == null ? "" : message.content()));
        }
        return mapped;
    }

    private static RestClient buildRestClient(LlmProperties properties) {
        int timeout = properties.boundedTimeoutSeconds();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeout))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(timeout));
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
