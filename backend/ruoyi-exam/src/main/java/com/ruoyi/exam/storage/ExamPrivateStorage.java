package com.ruoyi.exam.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;

/** Private blob primitive, not an upload API. The root must be writable only by the service account. */
public final class ExamPrivateStorage {
    private final Path root;
    private static final long MAX_BYTES = 10L * 1024 * 1024;

    public ExamPrivateStorage(Path requestedRoot, List<Path> publicRoots) throws IOException {
        if (!requestedRoot.isAbsolute()) throw new IOException("Private root must be absolute");
        root = canonical(requestedRoot);
        for (Path publicRoot : publicRoots) {
            Path logicalPrivate = requestedRoot.toAbsolutePath().normalize();
            Path logicalPublic = publicRoot.toAbsolutePath().normalize();
            Path exposed = canonical(publicRoot.toAbsolutePath());
            if (logicalPrivate.startsWith(logicalPublic) || logicalPublic.startsWith(logicalPrivate)
                    || root.startsWith(exposed) || exposed.startsWith(root)) {
                throw new IOException("Private root overlaps a public storage root");
            }
        }
        // Configuration checks do not create directories or touch user files.
    }

    public String write(InputStream input) throws IOException {
        Files.createDirectories(root);
        checkRoot();
        String key = UUID.randomUUID() + ".bin";
        Path target = resolve(key);
        boolean created = false;
        try {
            try (var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS)) {
                created = true;
                byte[] buffer = new byte[8192];
                long total = 0;
                for (int count; (count = input.read(buffer)) != -1;) {
                    total += count;
                    if (total > MAX_BYTES) throw new IOException("Private blob exceeds 10 MB");
                    output.write(buffer, 0, count);
                }
            }
            return key;
        } catch (IOException failure) {
            if (created) Files.deleteIfExists(target); // Only this operation's newly created partial file.
            throw failure;
        }
    }

    public InputStream open(String key) throws IOException {
        checkRoot();
        Path target = resolve(key);
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || !target.toRealPath().getParent().equals(root)) {
            throw new IOException("Private blob unavailable");
        }
        return Files.newInputStream(target, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
    }

    /** Internal compensation/retention hook; no public path or delete endpoint accepts this key. */
    public void remove(String key) throws IOException {
        checkRoot();
        Files.deleteIfExists(resolve(key));
    }

    private Path resolve(String key) throws IOException {
        if (key == null || !key.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.bin")) {
            throw new IOException("Invalid storage key");
        }
        return root.resolve(key);
    }

    private void checkRoot() throws IOException {
        if (!root.toRealPath().equals(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Private root was replaced or redirected");
        }
    }

    private static Path canonical(Path input) throws IOException {
        Path absolute = input.toAbsolutePath().normalize();
        Path existing = absolute;
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) existing = existing.getParent();
        if (existing == null) throw new IOException("Storage volume unavailable");
        return existing.toRealPath().resolve(existing.relativize(absolute)).normalize();
    }
}
