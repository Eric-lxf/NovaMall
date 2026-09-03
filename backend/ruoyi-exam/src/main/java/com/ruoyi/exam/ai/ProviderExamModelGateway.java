package com.ruoyi.exam.ai;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.blog.service.*;
import com.ruoyi.exam.config.ExamProperties;
import com.ruoyi.exam.support.*;
import okhttp3.*;

/** Reuses NovaMall provider routing; deliberately disables redirects and transport retries for paid calls. */
public class ProviderExamModelGateway implements ExamModelGateway {
    private final AiProviderService providers; private final ExamProperties properties;
    public ProviderExamModelGateway(AiProviderService providers,ExamProperties properties) { this.providers=providers; this.properties=properties; }
    private AiResolvedModelConfig resolve(String module) {
        if(!properties.isAiEnabled()) throw new ExamException("EXAM_AI_DISABLED","管理员尚未启用命题 AI 调用");
        try {
            var config=providers.resolveForModule(module); var provider=config.getProvider();
            ExamJson.require(provider.getId()!=null && Integer.valueOf(1).equals(provider.getEnabled()) && provider.getApiKey()!=null && !provider.getApiKey().isBlank(),"模型服务未配置");
            ExamJson.require("openai_compatible".equals(provider.getProviderType()),"命题 MVP 当前仅启用 OpenAI-compatible 文本接口，请为命题模块配置兼容服务");
            var url=HttpUrl.parse(provider.getBaseUrl());
            ExamJson.require(url!=null && url.isHttps() && url.username().isEmpty() && url.password().isEmpty() && url.query()==null && url.fragment()==null,"命题模型地址须为无凭据的 HTTPS 地址");
            ExamJson.require(config.getTextModel()!=null && !config.getTextModel().isBlank() && config.getTextModel().length()<=160,"命题模型名无效");
            return config;
        } catch(ExamException e) { throw e; } catch(RuntimeException missing) { throw new ExamException("EXAM_MODEL_NOT_READY","模型配置不可用，请检查命题模块设置"); }
    }
    public ObjectNode describe(String module) { return describe(resolve(module)); }
    private ObjectNode describe(AiResolvedModelConfig config) {
        var p=config.getProvider();
        var identity=ExamJson.object().put("providerId",Long.toString(p.getId())).put("providerName",p.getName()).put("model",config.getTextModel())
                .put("baseUrl",p.getBaseUrl()).put("temperature",String.valueOf(config.getTemperatureOverride()));
        identity.set("callPolicies",ExamAiCallOptions.policies(properties));
        identity.put("thinkingSupported",isDeepSeekV4(config));
        // Credentials stay in the provider service and are never persisted in a task payload.
        identity.put("configFingerprint",ExamJson.hash((identity.toString()+p.getApiKey()).getBytes(StandardCharsets.UTF_8))); return identity;
    }
    private boolean isDeepSeekV4(AiResolvedModelConfig config) {
        return "api.deepseek.com".equals(HttpUrl.parse(config.getProvider().getBaseUrl()).host()) && config.getTextModel().startsWith("deepseek-v4-");
    }
    public Result complete(String module,JsonNode expected,String system,JsonNode input,ExamAiCallOptions options) {
        var config=resolve(module); ExamJson.require(describe(config).equals(expected),"模型配置发生变化，请重新确认任务");
        var p=config.getProvider(); var body=ExamJson.object().put("model",config.getTextModel()).put("stream",false);
        String base=p.getBaseUrl().replaceAll("/+$","");
        body.put(HttpUrl.parse(base).host().equals("api.openai.com")?"max_completion_tokens":"max_tokens",options.maxOutputTokens());
        boolean deepSeek=isDeepSeekV4(config);
        if(deepSeek) {
            body.putObject("thinking").put("type",options.thinking());
            if("enabled".equals(options.thinking())) body.put("reasoning_effort",options.reasoningEffort());
            body.putObject("response_format").put("type","json_object");
        }
        if(config.getTemperatureOverride()!=null && !(deepSeek && "enabled".equals(options.thinking()))) body.put("temperature",config.getTemperatureOverride());
        body.putArray("messages").add(ExamJson.object().put("role","system").put("content",system))
                .add(ExamJson.object().put("role","user").put("content",input.toString()));
        ExamJson.require(body.toString().getBytes(StandardCharsets.UTF_8).length<=80000,"模型输入超出单次 80 KB 限制，请减少依据范围");
        String endpoint=base.endsWith("/v1")?base+"/chat/completions":base+"/v1/chat/completions";
        var request=new Request.Builder().url(endpoint).header("Authorization","Bearer "+p.getApiKey())
                .post(RequestBody.create(body.toString(),MediaType.parse("application/json; charset=utf-8"))).build();
        var client=providers.httpClient(p).newBuilder().retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
                .callTimeout(90,TimeUnit.SECONDS).build(); long start=System.nanoTime();
        try(var response=client.newCall(request).execute()) {
            // Never include remote error bodies in logs or end-user errors: they can contain source text or secrets.
            if(!response.isSuccessful()) throw new ExamException(response.code()>=500?"EXAM_RESULT_UNCERTAIN":"EXAM_MODEL_HTTP_"+response.code(),"模型请求未成功，请查看任务状态后决定是否重新提交");
            if(response.body()==null) throw new ExamException("EXAM_RESULT_UNCERTAIN","模型响应为空");
            byte[] bytes=response.body().byteStream().readNBytes(1024*1024+1);
            ExamJson.require(bytes.length<=1024*1024,"模型响应超出限制");
            var json=ExamJson.parse(new String(bytes,StandardCharsets.UTF_8)); var choice=json.path("choices").path(0);
            String finish=choice.path("finish_reason").asText(""); String text=choice.path("message").path("content").asText("");
            if(!choice.path("message").path("refusal").asText("").isBlank()) throw new ExamException("EXAM_MODEL_REFUSAL","模型拒绝生成该内容");
            return new Result(text,json.path("usage").isObject()?json.path("usage"):ExamJson.object(),response.header("x-request-id",""),finish,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start));
        } catch(ExamException e) { throw e; } catch(Exception uncertain) { throw new ExamException("EXAM_RESULT_UNCERTAIN","连接中断或超时，无法确认服务端是否已完成计费调用"); }
    }
}
