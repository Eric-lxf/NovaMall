package com.ruoyi.exam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "exam")
public class ExamProperties {
    private boolean enabled;
    private boolean workerEnabled = true;
    private String privateRoot = "";
    private boolean aiEnabled;
    private String documentImage = "";
    private int extractMaxOutputTokens = 8192;
    private int generateMaxOutputTokens = 16384;
    private int verifyMaxOutputTokens = 16384;
    private String generateReasoningEffort = "low";
    private String verifyReasoningEffort = "low";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isWorkerEnabled() { return workerEnabled; }
    public void setWorkerEnabled(boolean workerEnabled) { this.workerEnabled = workerEnabled; }
    public String getPrivateRoot() { return privateRoot; }
    public void setPrivateRoot(String privateRoot) { this.privateRoot = privateRoot; }
    public boolean isAiEnabled() { return aiEnabled; }
    public void setAiEnabled(boolean aiEnabled) { this.aiEnabled=aiEnabled; }
    public String getDocumentImage() { return documentImage; }
    public void setDocumentImage(String documentImage) { this.documentImage=documentImage; }
    public int getExtractMaxOutputTokens() { return extractMaxOutputTokens; }
    public void setExtractMaxOutputTokens(int value) { extractMaxOutputTokens=value; }
    public int getGenerateMaxOutputTokens() { return generateMaxOutputTokens; }
    public void setGenerateMaxOutputTokens(int value) { generateMaxOutputTokens=value; }
    public int getVerifyMaxOutputTokens() { return verifyMaxOutputTokens; }
    public void setVerifyMaxOutputTokens(int value) { verifyMaxOutputTokens=value; }
    public String getGenerateReasoningEffort() { return generateReasoningEffort; }
    public void setGenerateReasoningEffort(String value) { generateReasoningEffort=value; }
    public String getVerifyReasoningEffort() { return verifyReasoningEffort; }
    public void setVerifyReasoningEffort(String value) { verifyReasoningEffort=value; }
}
