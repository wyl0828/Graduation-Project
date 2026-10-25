package cn.communityelderly.infrastructure.storage;

import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class StorageDirectoryInitializer implements ApplicationRunner {

    private final Path storageRoot;

    public StorageDirectoryInitializer(@Value("${app.storage.root:.local-data/files}") String storageRoot) {
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Files.createDirectories(storageRoot);
    }
}
