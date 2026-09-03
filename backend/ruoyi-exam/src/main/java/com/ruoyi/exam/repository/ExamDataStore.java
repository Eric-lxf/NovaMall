package com.ruoyi.exam.repository;

import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.support.TransactionTemplate;
import com.ruoyi.exam.support.ExamException;

/** Internal SQL boundary. Identifiers are compile-time service constants, never user input. */
public class ExamDataStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;
    public ExamDataStore(DataSource source, Clock clock) {
        jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(10);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source)); transactions.setTimeout(15);
        this.clock = clock;
    }
    public LocalDateTime now() { return LocalDateTime.now(clock); }
    public <T> T tx(Supplier<T> action) { return transactions.execute(status -> action.get()); }
    public List<Map<String,Object>> rows(String sql, Object... args) { return jdbc.queryForList(sql, args); }
    public Map<String,Object> one(String sql, Object... args) {
        var rows = rows(sql, args); if (rows.isEmpty()) throw ExamException.notFound(); return rows.get(0);
    }
    public Map<String,Object> entity(String table, long id) { return one("select * from " + table + " where id=?", id); }
    public Map<String,Object> lock(String table, long id) { return one("select * from " + table + " where id=? for update", id); }
    public int update(String sql, Object... args) { return jdbc.update(sql, args); }
    public long count(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }
    public long insert(String table, Map<String,Object> values) {
        var keys = new ArrayList<>(values.keySet());
        String sql = "insert into " + table + " (" + String.join(",", keys) + ") values (" + String.join(",", Collections.nCopies(keys.size(), "?")) + ")";
        var holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < keys.size(); i++) ps.setObject(i + 1, values.get(keys.get(i)));
            return ps;
        }, holder);
        return Objects.requireNonNull(holder.getKey()).longValue();
    }
    public static Map<String,Object> values(Object... pairs) {
        var result = new LinkedHashMap<String,Object>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i+1]); return result;
    }
    public static long number(Map<String,Object> row, String key) { return ((Number) row.get(key)).longValue(); }
    public static String string(Map<String,Object> row, String key) { return Objects.toString(row.get(key), ""); }
    public void verifySchema() {
        for (String table : List.of("exam_file","exam_source","exam_source_version","exam_source_fragment","exam_knowledge_point",
                "exam_blueprint","exam_question","exam_question_version","exam_question_source","exam_question_check","exam_question_review",
                "exam_paper","exam_paper_version","exam_paper_item","exam_job","exam_task_item","exam_ai_call","exam_export","exam_owner_lock")) {
            jdbc.queryForList("select * from " + table + " where 1=0");
        }
    }
}
