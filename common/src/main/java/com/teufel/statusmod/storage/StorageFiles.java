package com.teufel.statusmod.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class StorageFiles {
    private StorageFiles() {}

    public static void backupCorrupted(Path source, String backupName) {
        try {
            if (!Files.exists(source)) return;
            Path backup = source.resolveSibling(backupName);
            Files.copy(source, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {}
    }
}
