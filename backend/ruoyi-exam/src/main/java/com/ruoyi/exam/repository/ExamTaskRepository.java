package com.ruoyi.exam.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.dao.InvalidDataAccessResourceUsageException;

import com.ruoyi.exam.domain.ExamTask;

/** Each mutation is a single bounded SQL statement. No network call lives in a DB transaction. */
public class ExamTaskRepository {
    private final JdbcTemplate jdbc;
    private static final String COLUMNS = "id,owner_user_id,kind,title,idempotency_key,request_hash,status,attempt_no,revision,"
            + "lease_owner,lease_until,error_code,result_summary,created_at,updated_at";

    public ExamTaskRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void verifySchema() {
        jdbc.query("select " + COLUMNS + " from exam_task where 1=0", (rs, row) -> map(rs));
        // Columns alone are insufficient: missing uniqueness would make concurrent submissions unsafe.
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            var metadata = connection.getMetaData();
            String table = metadata.storesUpperCaseIdentifiers() ? "EXAM_TASK" : "exam_task";
            var indexes = new HashMap<String, List<String>>();
            try (var rows = metadata.getIndexInfo(connection.getCatalog(), connection.getSchema(), table, true, false)) {
                while (rows.next()) {
                    String name = rows.getString("INDEX_NAME"), column = rows.getString("COLUMN_NAME");
                    if (name != null && column != null && !rows.getBoolean("NON_UNIQUE")) {
                        indexes.computeIfAbsent(name, ignored -> new ArrayList<>()).add(column.toLowerCase(Locale.ROOT));
                    }
                }
            }
            if (indexes.values().stream().noneMatch(columns -> columns.equals(List.of("owner_user_id", "kind", "idempotency_key")))) {
                throw new InvalidDataAccessResourceUsageException("Exam task idempotency constraint missing");
            }
            return null;
        });
    }

    public Optional<ExamTask> find(long id) {
        return jdbc.query("select " + COLUMNS + " from exam_task where id=?", (rs, row) -> map(rs), id).stream().findFirst();
    }

    public Optional<ExamTask> findByKey(long owner, String kind, String key) {
        return jdbc.query("select " + COLUMNS + " from exam_task where owner_user_id=? and kind=? and idempotency_key=?",
                (rs, row) -> map(rs), owner, kind, key).stream().findFirst();
    }

    public long insert(long owner, String title, String key, String hash, LocalDateTime now) {
        var holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var ps = connection.prepareStatement("insert into exam_task "
                    + "(owner_user_id,kind,title,idempotency_key,request_hash,status,attempt_no,revision,created_at,updated_at) "
                    + "values (?,'SYSTEM_CHECK',?,?,?,'QUEUED',0,0,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, owner); ps.setString(2, title); ps.setString(3, key); ps.setString(4, hash);
            ps.setObject(5, now); ps.setObject(6, now);
            return ps;
        }, holder);
        return holder.getKey().longValue();
    }

    public List<ExamTask> page(long owner, String status, int offset, int limit) {
        String filter = status == null ? "" : " and status=?";
        Object[] args = status == null ? new Object[]{owner, limit, offset} : new Object[]{owner, status, limit, offset};
        return jdbc.query("select " + COLUMNS + " from exam_task where owner_user_id=?" + filter
                + " order by id desc limit ? offset ?", (rs, row) -> map(rs), args);
    }

    public long count(long owner, String status) {
        return status == null
                ? jdbc.queryForObject("select count(*) from exam_task where owner_user_id=?", Long.class, owner)
                : jdbc.queryForObject("select count(*) from exam_task where owner_user_id=? and status=?", Long.class, owner, status);
    }

    public List<ExamTask> queued(int limit) {
        return jdbc.query("select " + COLUMNS + " from exam_task where status='QUEUED' order by id limit ?",
                (rs, row) -> map(rs), limit);
    }

    public boolean claim(ExamTask task, String worker, LocalDateTime now) {
        return jdbc.update("update exam_task set status='RUNNING',attempt_no=attempt_no+1,revision=revision+1,"
                + "lease_owner=?,lease_until=?,updated_at=? where id=? and status='QUEUED' and revision=? and attempt_no<3",
                worker, now.plusSeconds(60), now, task.id(), task.revision()) == 1;
    }

    public boolean heartbeat(long id, int attempt, String worker, LocalDateTime now) {
        return jdbc.update("update exam_task set lease_until=? where id=? and status='RUNNING' and attempt_no=? "
                + "and lease_owner=? and lease_until>?", now.plusSeconds(60), id, attempt, worker, now) == 1;
    }

    public boolean finish(long id, int attempt, String worker, String status, String error, String result, LocalDateTime now) {
        if (!List.of("SUCCEEDED", "PARTIAL_SUCCESS", "FAILED", "NEEDS_CONFIRMATION").contains(status)) throw new IllegalArgumentException("Invalid terminal state");
        return jdbc.update("update exam_task set status=?,error_code=?,result_summary=?,revision=revision+1,"
                + "lease_owner=null,lease_until=null,updated_at=? where id=? and status='RUNNING' and attempt_no=? "
                + "and lease_owner=? and lease_until>?", status, error, result, now, id, attempt, worker, now) == 1;
    }

    public boolean cancel(ExamTask task, LocalDateTime now) {
        return jdbc.update("update exam_task set status=case when status='RUNNING' then 'CANCEL_REQUESTED' else 'CANCELLED' end,"
                + "revision=revision+1,updated_at=? where id=? and revision=? and status in ('QUEUED','RUNNING','NEEDS_CONFIRMATION')",
                now, task.id(), task.revision()) == 1;
    }

    public void acknowledgeCancel(long id, int attempt, String worker, LocalDateTime now) {
        jdbc.update("update exam_task set status='CANCELLED',lease_owner=null,lease_until=null,revision=revision+1,updated_at=? "
                + "where id=? and status='CANCEL_REQUESTED' and attempt_no=? and lease_owner=?", now, id, attempt, worker);
    }

    public boolean retry(ExamTask task, LocalDateTime now) {
        // P1 exposes only a side-effect-free SYSTEM_CHECK. AI retries require a separate budget/idempotency design.
        return jdbc.update("update exam_task set status='QUEUED',error_code=null,result_summary=null,revision=revision+1,"
                + "updated_at=? where id=? and revision=? and kind='SYSTEM_CHECK' and status='FAILED' and attempt_no<3",
                now, task.id(), task.revision()) == 1;
    }

    public void recoverExpired(LocalDateTime now) {
        jdbc.update("update exam_task set status=case when status='CANCEL_REQUESTED' then 'CANCELLED' "
                + "when kind<>'SYSTEM_CHECK' then 'NEEDS_CONFIRMATION' when attempt_no>=3 then 'FAILED' else 'QUEUED' end,"
                + "error_code=case when kind<>'SYSTEM_CHECK' then 'EXAM_RESULT_UNCERTAIN' else 'EXAM_LEASE_EXPIRED' end,"
                + "lease_owner=null,lease_until=null,revision=revision+1,updated_at=? "
                + "where status in ('RUNNING','CANCEL_REQUESTED') and lease_until<=?", now, now);
    }

    private ExamTask map(ResultSet rs) throws SQLException {
        return new ExamTask(rs.getLong("id"), rs.getLong("owner_user_id"), rs.getString("kind"), rs.getString("title"),
                rs.getString("idempotency_key"), rs.getString("request_hash"), rs.getString("status"), rs.getInt("attempt_no"),
                rs.getLong("revision"), rs.getString("lease_owner"), rs.getObject("lease_until", LocalDateTime.class),
                rs.getString("error_code"), rs.getString("result_summary"), rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class));
    }
}
