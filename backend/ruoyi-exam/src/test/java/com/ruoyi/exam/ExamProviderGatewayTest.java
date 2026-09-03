package com.ruoyi.exam;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.ruoyi.blog.domain.AiProvider;
import com.ruoyi.blog.service.*;
import com.ruoyi.exam.ai.ProviderExamModelGateway;
import com.ruoyi.exam.ai.ExamAiCallOptions;
import com.ruoyi.exam.config.ExamProperties;
import com.ruoyi.exam.support.*;
import okhttp3.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Local OkHttp application interceptor: no DNS, network, live credentials or paid calls. */
class ExamProviderGatewayTest {
    AiProviderService service; AiProvider provider; ExamProperties properties; ProviderExamModelGateway gateway;
    AtomicInteger calls=new AtomicInteger(); AtomicReference<Request> sent=new AtomicReference<>();
    String response="{\"choices\":[{\"message\":{\"content\":\"{}\"},\"finish_reason\":\"stop\"}],\"usage\":{\"total_tokens\":17}}"; int code=200;
    @BeforeEach void setup() {
        service=mock(AiProviderService.class); provider=new AiProvider(); provider.setId(1L); provider.setName("测试模型"); provider.setEnabled(1);
        provider.setProviderType("openai_compatible"); provider.setBaseUrl("https://api.openai.com/v1"); provider.setApiKey("unit-test-placeholder-not-a-key");
        when(service.resolveForModule(anyString())).thenAnswer(call->new AiResolvedModelConfig(provider,"test-model",null,null,AiResolvedModelConfig.ConfigSource.MODULE_OVERRIDE));
        var client=new OkHttpClient.Builder().addInterceptor(chain->{
            calls.incrementAndGet(); sent.set(chain.request());
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("test")
                    .header("x-request-id","local-test-request").body(ResponseBody.create(response,MediaType.parse("application/json"))).build();
        }).build();
        when(service.httpClient(provider)).thenReturn(client); properties=new ExamProperties(); properties.setAiEnabled(true); gateway=new ProviderExamModelGateway(service,properties);
    }
    @Test void openAiRequestAndUsageMetadataArePreserved() throws Exception {
        var result=gateway.complete("exam_generate",gateway.describe("exam_generate"),"system",ExamJson.object(),new ExamAiCallOptions(4096,"disabled","low"));
        assertEquals("local-test-request",result.requestId()); assertEquals(17,result.usage().path("total_tokens").asInt());
        assertEquals("https://api.openai.com/v1/chat/completions",sent.get().url().toString());
        var buffer=new okio.Buffer(); sent.get().body().writeTo(buffer); var body=ExamJson.parse(buffer.readUtf8());
        assertEquals(4096,body.path("max_completion_tokens").asInt()); assertFalse(body.has("max_tokens")); assertFalse(body.has("temperature"));
        assertEquals(1,calls.get());
    }
    @Test void compatibleProviderUsesMaxTokensAndUnknownUsageIsNotZero() throws Exception {
        provider.setBaseUrl("https://model.example.invalid"); response="{\"choices\":[{\"message\":{\"content\":\"{}\"},\"finish_reason\":\"stop\"}]}";
        var result=gateway.complete("exam_generate",gateway.describe("exam_generate"),"system",ExamJson.object(),new ExamAiCallOptions(4096,"disabled","low"));
        assertTrue(result.usage().isEmpty()); var buffer=new okio.Buffer(); sent.get().body().writeTo(buffer);
        assertEquals(4096,ExamJson.parse(buffer.readUtf8()).path("max_tokens").asInt());
    }
    @Test void fingerprintChangesFailBeforeDispatch() {
        var expected=gateway.describe("exam_generate"); assertFalse(expected.toString().contains(provider.getApiKey())); provider.setApiKey("rotated-unit-test-placeholder");
        assertThrows(ExamException.class,()->gateway.complete("exam_generate",expected,"system",ExamJson.object(),new ExamAiCallOptions(4096,"disabled","low"))); assertEquals(0,calls.get());
    }
    @Test void serverErrorIsUncertainNotAutomaticallyRetriedAndDoesNotExposeRemoteBody() {
        code=503; response="PRIVATE_TEST_SOURCE_SHOULD_NOT_APPEAR";
        var failure=assertThrows(ExamException.class,()->gateway.complete("exam_generate",gateway.describe("exam_generate"),"system",ExamJson.object(),new ExamAiCallOptions(4096,"disabled","low")));
        assertEquals("EXAM_RESULT_UNCERTAIN",failure.getErrorCode()); assertFalse(failure.getMessage().contains(response)); assertEquals(1,calls.get());
    }
    @Test void unsupportedAndCredentialBearingEndpointsAreRejected() {
        for(String endpoint:new String[]{"http://model.example.invalid","https://user:pass@example.invalid","https://example.invalid?token=test"}) {
            provider.setBaseUrl(endpoint); assertThrows(ExamException.class,()->gateway.describe("exam_generate"));
        } assertEquals(0,calls.get());
    }
    @Test void disabledFeatureDoesNotResolveOrContactProvider() {
        properties.setAiEnabled(false); assertThrows(ExamException.class,()->gateway.describe("exam_generate"));
        verify(service,never()).resolveForModule(anyString()); assertEquals(0,calls.get());
    }
    private void deepSeek() {
        provider.setBaseUrl("https://api.deepseek.com/v1");
        when(service.resolveForModule(anyString())).thenAnswer(call->new AiResolvedModelConfig(provider,"deepseek-v4-pro",null,new java.math.BigDecimal("0.20"),AiResolvedModelConfig.ConfigSource.MODULE_OVERRIDE));
    }
    private com.fasterxml.jackson.databind.JsonNode sentBody() throws Exception {
        var buffer=new okio.Buffer(); sent.get().body().writeTo(buffer); return ExamJson.parse(buffer.readUtf8());
    }
    @Test void deepSeekExtractionExplicitlyDisablesThinkingAndRequestsJson() throws Exception {
        deepSeek(); var expected=gateway.describe("exam_generate");
        gateway.complete("exam_generate",expected,"必须返回 JSON",ExamJson.object(),ExamAiCallOptions.read(expected,"extract"));
        var body=sentBody(); assertEquals(8192,body.path("max_tokens").asInt());
        assertEquals("disabled",body.path("thinking").path("type").asText());
        assertEquals("json_object",body.path("response_format").path("type").asText());
        assertFalse(body.has("reasoning_effort")); assertTrue(body.has("temperature")); assertEquals(1,calls.get());
    }
    @Test void deepSeekGenerationAndVerificationUseSeparatePoliciesAndOmitIgnoredTemperature() throws Exception {
        deepSeek(); properties.setGenerateMaxOutputTokens(12288); properties.setVerifyMaxOutputTokens(24576); properties.setVerifyReasoningEffort("high");
        var expected=gateway.describe("exam_verify");
        gateway.complete("exam_verify",expected,"JSON",ExamJson.object(),ExamAiCallOptions.read(expected,"verify"));
        assertEquals(24576,sentBody().path("max_tokens").asInt()); assertEquals("high",sentBody().path("reasoning_effort").asText());
        assertEquals("enabled",sentBody().path("thinking").path("type").asText()); assertFalse(sentBody().has("temperature"));
        assertEquals(12288,ExamAiCallOptions.read(expected,"generate").maxOutputTokens());
    }
    @Test void policyChangesInvalidateConfirmationBeforeNetworkCall() {
        deepSeek(); var expected=gateway.describe("exam_generate"); properties.setGenerateReasoningEffort("high");
        assertThrows(ExamException.class,()->gateway.complete("exam_generate",expected,"JSON",ExamJson.object(),ExamAiCallOptions.read(expected,"generate")));
        assertEquals(0,calls.get());
    }
    @Test void providerSpecificOptionsAreNotSentToOtherCompatibleHosts() throws Exception {
        deepSeek(); provider.setBaseUrl("https://deepseek.example.invalid/v1");
        var expected=gateway.describe("exam_generate"); gateway.complete("exam_generate",expected,"JSON",ExamJson.object(),ExamAiCallOptions.read(expected,"extract"));
        assertFalse(sentBody().has("thinking")); assertFalse(sentBody().has("reasoning_effort")); assertFalse(sentBody().has("response_format"));
    }
    @Test void invalidOutputPolicyFailsBeforeModelDispatch() {
        for(int limit:new int[]{0,1023,32769,Integer.MAX_VALUE}) {
            properties.setExtractMaxOutputTokens(limit); assertThrows(ExamException.class,()->gateway.describe("exam_generate"));
        }
        properties.setExtractMaxOutputTokens(8192); properties.setVerifyReasoningEffort("unknown");
        assertThrows(ExamException.class,()->gateway.describe("exam_verify")); assertEquals(0,calls.get());
    }
}
