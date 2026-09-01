package com.codementor.aiservice.service;

import com.codementor.aiservice.config.OpenAiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiCompatibleCodeAnalysisEngineTest {

    private static final String BASE_URL = "http://mock-vllm:8000";

    private static final String SUCCESS_BODY = """
            {
              "id": "chatcmpl-1",
              "object": "chat.completion",
              "created": 1735689600,
              "model": "mock-code-analyzer",
              "choices": [
                {
                  "index": 0,
                  "message": {"role": "assistant", "content": "Analiz sonucu"},
                  "finish_reason": "stop"
                }
              ],
              "usage": {"prompt_tokens": 5, "completion_tokens": 2, "total_tokens": 7}
            }
            """;

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
    }

    private OpenAiCompatibleCodeAnalysisEngine engine(String apiKey) {
        OpenAiProperties properties = new OpenAiProperties(
                BASE_URL, "mock-code-analyzer", apiKey, Duration.ofSeconds(5), Duration.ofSeconds(60));
        return new OpenAiCompatibleCodeAnalysisEngine(builder.build(), properties);
    }

    @Test
    void analyze_returnsAssistantContent() {
        server.expect(requestTo(BASE_URL + "/v1/chat/completions"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("mock-code-analyzer"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

        String result = engine(null).analyze("class A {}", "Guvenlik acigi var mi?");

        assertEquals("Analiz sonucu", result);
        server.verify();
    }

    @Test
    void analyze_sendsUserMessageContainingPromptAndSourceCode() {
        server.expect(requestTo(BASE_URL + "/v1/chat/completions"))
                .andExpect(jsonPath("$.messages[1].content").value(
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("Guvenlik acigi var mi?"),
                                org.hamcrest.Matchers.containsString("class A {}"))))
                .andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

        engine(null).analyze("class A {}", "Guvenlik acigi var mi?");

        server.verify();
    }

    @Test
    void analyze_omitsAuthorizationHeaderWhenApiKeyBlank() {
        server.expect(requestTo(BASE_URL + "/v1/chat/completions"))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

        engine("   ").analyze("class A {}", "prompt");

        server.verify();
    }

    @Test
    void analyze_sendsBearerTokenWhenApiKeyPresent() {
        server.expect(requestTo(BASE_URL + "/v1/chat/completions"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer sk-test-key"))
                .andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

        engine("sk-test-key").analyze("class A {}", "prompt");

        server.verify();
    }

    @Test
    void analyze_throwsWhenUpstreamFails() {
        server.expect(requestTo(BASE_URL + "/v1/chat/completions"))
                .andRespond(withServerError());

        AnalysisEngineException ex = assertThrows(AnalysisEngineException.class,
                () -> engine(null).analyze("class A {}", "prompt"));

        assertTrue(ex.getMessage().contains("mock-code-analyzer"));
    }

    @Test
    void analyze_throwsWhenChoicesEmpty() {
        String emptyChoices = """
                {"id":"chatcmpl-2","object":"chat.completion","created":1,"model":"mock-code-analyzer","choices":[]}
                """;
        server.expect(requestTo(BASE_URL + "/v1/chat/completions"))
                .andRespond(withSuccess(emptyChoices, MediaType.APPLICATION_JSON));

        assertThrows(AnalysisEngineException.class, () -> engine(null).analyze("class A {}", "prompt"));
    }

    @Test
    void analyze_doesNotLeakApiKeyInExceptionMessage() {
        server.expect(requestTo(BASE_URL + "/v1/chat/completions"))
                .andRespond(withServerError());

        AnalysisEngineException ex = assertThrows(AnalysisEngineException.class,
                () -> engine("sk-super-secret").analyze("class A {}", "prompt"));

        assertFalse(ex.getMessage().contains("sk-super-secret"));
    }
}
