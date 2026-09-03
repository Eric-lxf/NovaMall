package com.ruoyi.exam;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.exam.repository.ExamTaskRepository;

final class ExamTestDatabase {
    final JdbcDataSource dataSource = new JdbcDataSource();
    final JdbcTemplate jdbc;
    final ExamTaskRepository repository;

    ExamTestDatabase() throws IOException {
        dataSource.setURL("jdbc:h2:mem:exam_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
        jdbc = new JdbcTemplate(dataSource);
        // Exercise production columns/indexes, removing ONLY vendor collation/storage clauses for H2.
        // A passing test does not claim MySQL migration compatibility.
        String sql = Files.readString(Path.of(System.getProperty("examSchema")))
                .replace("CHARACTER SET ascii COLLATE ascii_bin", "")
                .replaceAll("(?s)ENGINE=InnoDB.*?;", ";");
        jdbc.execute(sql);
        repository = new ExamTaskRepository(jdbc);
    }
}
