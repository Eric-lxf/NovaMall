package com.ruoyi.exam.service;

import java.io.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import com.ruoyi.exam.config.ExamProperties;
import com.ruoyi.exam.repository.ExamDataStore;
import com.ruoyi.exam.security.ExamAccessPolicy;
import com.ruoyi.exam.security.ExamActor;
import com.ruoyi.exam.storage.ExamPrivateStorage;
import com.ruoyi.exam.support.*;
import static com.ruoyi.exam.repository.ExamDataStore.*;

public class ExamFiles {
    private final ExamDataStore db;
    private final ExamProperties properties;
    private final List<Path> publicRoots;
    public ExamFiles(ExamDataStore db, ExamProperties properties, List<Path> publicRoots) {
        this.db = db; this.properties = properties; this.publicRoots = publicRoots;
    }
    public boolean configured() { try { storage(); return true; } catch (RuntimeException failure) { return false; } }
    private ExamPrivateStorage storage() {
        try {
            if (properties.getPrivateRoot() == null || properties.getPrivateRoot().isBlank()) throw new IOException("not configured");
            return new ExamPrivateStorage(Path.of(properties.getPrivateRoot()), publicRoots);
        } catch (IOException | java.nio.file.InvalidPathException failure) {
            throw new ExamException("EXAM_STORAGE_NOT_READY", "请配置独立且不公开的绝对路径 EXAM_PRIVATE_ROOT");
        }
    }
    public <T> T create(ExamActor actor, byte[] bytes, String name, String mime, String purpose, Function<Long,T> action) {
        ExamJson.require(bytes.length > 0 && bytes.length <= 10 * 1024 * 1024, "文件大小超出 10 MB");
        ExamJson.require(name != null && !name.isBlank() && name.length() <= 180 && !name.contains("/") && !name.contains("\\")
                && name.codePoints().noneMatch(Character::isISOControl), "文件名无效");
        var storage = storage();
        return withQuotaLock(() -> {
                if(db.count("select coalesce(sum(size_bytes),0) from exam_file where owner_user_id=?",actor.userId())+bytes.length>512L*1024*1024
                        || db.count("select coalesce(sum(size_bytes),0) from exam_file")+bytes.length>10L*1024*1024*1024)
                    throw new ExamException("EXAM_STORAGE_QUOTA","私有文件额度不足：每用户 512 MB，命题模块总计 10 GB，请联系管理员归档");
                final String key;
                try { key=storage.write(new ByteArrayInputStream(bytes)); }
                catch(IOException failure) { throw new ExamException("EXAM_STORAGE_FAILED","私有文件写入失败，请检查磁盘与权限"); }
                // A nested source/export write may succeed locally but its outer lease transaction can still roll back.
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        if(status!=STATUS_COMMITTED) try { storage.remove(key); }
                        catch(IOException cleanupFailure) { org.slf4j.LoggerFactory.getLogger(ExamFiles.class).warn("Exam rollback file cleanup failed; private-storage reconciliation required"); }
                    }
                });
                long id = db.insert("exam_file", values("owner_user_id",actor.userId(),"storage_key",key,"original_name",name,
                        "mime_type",mime,"size_bytes",bytes.length,"sha256",ExamJson.hash(bytes),"purpose",purpose,"created_at",db.now()));
                return action.apply(id);
            });
    }
    /** File-producing transactions always acquire this before source locks, including export commits. */
    public <T> T withQuotaLock(Supplier<T> action) {
        return db.tx(() -> {
            db.update("insert ignore into exam_owner_lock (owner_user_id) values (-1)");
            db.one("select * from exam_owner_lock where owner_user_id=-1 for update");
            return action.get();
        });
    }
    public record Download(String name, String mime, byte[] bytes) { }
    public Download read(ExamActor actor, long id) {
        var file = db.entity("exam_file", id); ExamAccessPolicy.requireOwner(actor, number(file,"owner_user_id"));
        return readKnown(file);
    }
    public Download readKnown(Map<String,Object> file) {
        try (var input = storage().open(string(file,"storage_key"))) {
            byte[] bytes = input.readNBytes(10 * 1024 * 1024 + 1);
            if (bytes.length != number(file,"size_bytes") || !ExamJson.hash(bytes).equals(string(file,"sha256"))) throw new IOException("integrity mismatch");
            return new Download(string(file,"original_name"),string(file,"mime_type"),bytes);
        } catch (IOException failure) { throw new ExamException("EXAM_FILE_UNAVAILABLE", "私有文件不可用或完整性检查失败"); }
    }
}
