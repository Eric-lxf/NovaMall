package com.ruoyi.exam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "exam")
public class ExamProperties {
    private boolean enabled;
    private boolean workerEnabled = true;
    private String privateRoot = "";
    private boolean aiEnabled;
    private String documentImage = "";

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
}
