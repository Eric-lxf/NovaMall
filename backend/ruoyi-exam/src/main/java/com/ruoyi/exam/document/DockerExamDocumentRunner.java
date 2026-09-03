package com.ruoyi.exam.document;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.exam.config.ExamProperties;
import com.ruoyi.exam.support.*;

/** No upload is parsed by the web JVM. The fixed image receives bytes on stdin, never a host mount or credentials. */
public class DockerExamDocumentRunner implements ExamDocumentRunner {
    private final ExamProperties properties;
    public DockerExamDocumentRunner(ExamProperties properties) { this.properties=properties; }
    public boolean configured() { return properties.getDocumentImage()!=null && properties.getDocumentImage().matches("[a-z0-9][a-z0-9._/:-]{0,180}(@sha256:[a-f0-9]{64})?"); }
    public JsonNode run(JsonNode input) {
        if(!configured()) throw new ExamException("EXAM_DOCUMENT_WORKER_NOT_READY","请先构建并配置隔离文档镜像 EXAM_DOCUMENT_IMAGE");
        String name="novamall-exam-"+UUID.randomUUID(); Process process=null;
        var pipes=Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"exam-document-pipe"); t.setDaemon(true); return t;});
        try {
            process=new ProcessBuilder("docker","run","--rm","--pull=never","--name",name,"--network=none","--read-only",
                    "--cap-drop=ALL","--security-opt=no-new-privileges","--pids-limit=64","--memory=512m","--memory-swap=512m","--cpus=1",
                    "--tmpfs=/tmp:rw,noexec,nosuid,size=192m,mode=1777","--user=65534:65534","-i",properties.getDocumentImage())
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            Process running=process;
            var reader=pipes.submit(()->bounded(running.getInputStream(),16*1024*1024));
            var writer=pipes.submit(()->{ try(var output=running.getOutputStream()) { output.write(input.toString().getBytes(StandardCharsets.UTF_8)); } return true; });
            if(!process.waitFor(90,TimeUnit.SECONDS)) throw new ExamException("EXAM_DOCUMENT_TIMEOUT","文档处理超过 90 秒，已停止");
            writer.get(2,TimeUnit.SECONDS); byte[] output=reader.get(2,TimeUnit.SECONDS);
            if(process.exitValue()!=0) throw new ExamException("EXAM_DOCUMENT_WORKER_FAILED","隔离文档进程不可用或资源限制触发");
            var mapper=new com.fasterxml.jackson.databind.ObjectMapper(com.fasterxml.jackson.core.JsonFactory.builder()
                    .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .streamReadConstraints(com.fasterxml.jackson.core.StreamReadConstraints.builder().maxNestingDepth(20).maxStringLength(16*1024*1024).build()).build())
                    .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
            var response=mapper.readTree(output);
            if(!response.path("ok").asBoolean()) {
                String code=response.path("errorCode").asText("");
                if(!code.matches("EXAM_[A-Z_]{1,50}")) code="EXAM_DOCUMENT_FAILED";
                throw new ExamException(code,"文档处理失败；请检查格式、加密/扫描状态和任务环境");
            }
            return response.path("result");
        } catch(ExamException error) { throw error; }
        catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new ExamException("EXAM_DOCUMENT_INTERRUPTED","文档任务已中断"); }
        catch(Exception unavailable) { throw new ExamException("EXAM_DOCUMENT_WORKER_FAILED","无法调用隔离文档工作进程，请检查 Docker 和镜像"); }
        finally {
            if(process!=null && process.isAlive()) process.destroyForcibly(); pipes.shutdownNow();
            // Exact generated container name only; never a wildcard, host path or caller-selected identifier.
            try { var cleanup=new ProcessBuilder("docker","rm","-f",name).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
                if(!cleanup.waitFor(5,TimeUnit.SECONDS)) cleanup.destroyForcibly();
            } catch(Exception ignored) { /* --rm handles the normal exit; operator can identify this exact prefix after host failure. */ }
        }
    }
    private byte[] bounded(InputStream input,int max) throws IOException {
        byte[] bytes=input.readNBytes(max+1); if(bytes.length>max) throw new IOException("output limit"); return bytes;
    }
}
