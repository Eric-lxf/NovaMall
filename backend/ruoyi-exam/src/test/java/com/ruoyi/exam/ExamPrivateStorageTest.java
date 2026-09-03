package com.ruoyi.exam;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.ruoyi.exam.storage.ExamPrivateStorage;

class ExamPrivateStorageTest {
    @TempDir Path temp;

    @Test void rejectsRelativeRoots() {
        assertThrows(IOException.class, () -> new ExamPrivateStorage(Path.of("relative"), List.of(temp.resolve("public"))));
    }

    @Test void rejectsPublicRootOverlapInBothDirections() {
        Path exposed = temp.resolve("public");
        for (Path unsafe : List.of(exposed, exposed.resolve("private"), temp)) {
            assertThrows(IOException.class, () -> new ExamPrivateStorage(unsafe, List.of(exposed)));
        }
    }

    @Test void constructingStorageDoesNotCreateDirectories() throws Exception {
        new ExamPrivateStorage(temp.resolve("private"), List.of(temp.resolve("public")));
        assertFalse(Files.exists(temp.resolve("private")));
    }

    @Test void randomPrivateKeyCanBeReadButPathsAreRejected() throws Exception {
        var storage = new ExamPrivateStorage(temp.resolve("private"), List.of(temp.resolve("public")));
        String key = storage.write(new ByteArrayInputStream("private fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        try (var input = storage.open(key)) { assertEquals("private fixture", new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)); }
        for (String bad : List.of("../public/secrets", "D:/secret.txt", "https://example.com/a", key + "/../b")) {
            assertThrows(IOException.class, () -> storage.open(bad));
        }
    }

    @Test void oversizedBlobRemovesOnlyItsPartialOutput() throws Exception {
        var storage = new ExamPrivateStorage(temp.resolve("private"), List.of(temp.resolve("public")));
        String retained = storage.write(new ByteArrayInputStream(new byte[]{1}));
        assertThrows(IOException.class, () -> storage.write(new ByteArrayInputStream(new byte[10 * 1024 * 1024 + 1])));
        try (var files = Files.list(temp.resolve("private"))) { assertEquals(List.of(retained), files.map(p -> p.getFileName().toString()).toList()); }
    }

    @Test void inputFailureDoesNotLeavePartialOutput() throws Exception {
        var storage = new ExamPrivateStorage(temp.resolve("private"), List.of(temp.resolve("public")));
        var broken = new java.io.InputStream() { @Override public int read() throws IOException { throw new IOException("fixture failure"); } };
        assertThrows(IOException.class, () -> storage.write(broken));
        try (var files = Files.list(temp.resolve("private"))) { assertEquals(0, files.count()); }
    }
}
