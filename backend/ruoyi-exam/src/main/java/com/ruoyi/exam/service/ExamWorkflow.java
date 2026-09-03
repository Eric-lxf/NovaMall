package com.ruoyi.exam.service;

import com.ruoyi.exam.repository.ExamDataStore;
import com.ruoyi.exam.support.ExamException;
import com.ruoyi.exam.support.ExamJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.dao.DataAccessException;

/** Missing optional migrations must disable only the exam workflow, not application startup. */
public class ExamWorkflow {
    private final ExamDataStore db; private final ExamFiles files;
    private long lastCheck; private boolean ready;
    public ExamWorkflow(ExamDataStore db,ExamFiles files) { this.db=db; this.files=files; }
    public synchronized boolean ready() {
        long now=System.nanoTime();
        if(lastCheck==0 || now-lastCheck>30_000_000_000L) {
            lastCheck=now;
            try { db.verifySchema(); ready=true; } catch(DataAccessException unavailable) { ready=false; }
        }
        return ready;
    }
    public void requireReady() { if(!ready()) throw new ExamException("EXAM_NOT_READY","命题业务表尚未就绪，请执行命题业务增量迁移"); }
    public ObjectNode capabilities() { return ExamJson.object().put("workflowReady",ready()).put("privateStorageReady",files.configured()); }
}
