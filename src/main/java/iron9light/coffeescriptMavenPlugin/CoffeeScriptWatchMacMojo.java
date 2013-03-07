package iron9light.coffeescriptMavenPlugin;

import com.barbarysoftware.watchservice.*;
import com.barbarysoftware.watchservice.WatchEvent;
import com.barbarysoftware.watchservice.WatchKey;
import com.barbarysoftware.watchservice.WatchService;
import org.apache.maven.plugin.MojoFailureException;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
//import java.nio.file.*;
//import java.nio.file.attribute.BasicFileAttributes;

/**
 * Compile coffeescript files to javascript files, and recompile as soon as a change occurs.
 *
 * @author iron9light
 * @goal watch-mac
 */
public class CoffeeScriptWatchMacMojo extends CoffeeScriptMojoBase {
    /**
     * Delete javascript file when the coffeescript file deleted.
     *
     * @parameter default-value="true"
     */
    private Boolean allowedDelete;

    private static final WatchEvent.Kind<?>[] watchEvents = {StandardWatchEventKind.ENTRY_CREATE, StandardWatchEventKind.ENTRY_MODIFY, StandardWatchEventKind.ENTRY_DELETE};

    @Override
    protected void doExecute(CoffeeScriptCompiler compiler, Path sourceDirectory, Path outputDirectory) throws Exception {
        try {
            compileCoffeeFilesInDir(compiler, sourceDirectory, outputDirectory);
        } catch (MojoFailureException ignored) {
        }
        watch(compiler, sourceDirectory, outputDirectory);
    }

    private void watch(final CoffeeScriptCompiler compiler, final Path sourceDirectory, final Path outputDirectory) throws IOException, InterruptedException {
        WatchService watchService = startWatching(sourceDirectory);
        for (boolean changed = true; ; ) {
            if (changed) {
                getLog().info("Waiting for changes...");
                changed = false;
            }

            WatchKey watchKey = watchService.take();

            for (WatchEvent<?> event : watchKey.pollEvents()) {
                File file = (File) event.context();
                if (file == null) continue;

                getLog().debug(String.format("watched %s - %s", event.kind().name(), file));
                if (file.isDirectory()) {
                    getLog().debug("is directory");
                    if (event.kind().name().equals(StandardWatchEventKinds.ENTRY_CREATE.name())) {
                        // watch created folder.
                        WatchableFile watchableFile = new WatchableFile(file);
                        watchableFile.register(watchService, watchEvents);
                        getLog().debug(String.format("watch %s", file));
                    }
                    continue;
                }

                if (!isCoffeeFile(file.toPath())) {
                    getLog().debug(String.format("skip non-coffeescript"));
                    continue;
                }

                String coffeeFileName = sourceDirectory.relativize(file.toPath()).toString();
                String jsFileName = getJsFileName(coffeeFileName);
                Path jsFile = outputDirectory.resolve(jsFileName);

                if (event.kind().name().equals(StandardWatchEventKinds.ENTRY_DELETE.name())) {
                    if (allowedDelete && Files.deleteIfExists(jsFile)) {
                        getLog().info(String.format("deleted %s with %s", jsFileName, coffeeFileName));
                        changed = true;
                    }
                } else if (event.kind().name().equals(StandardWatchEventKinds.ENTRY_MODIFY.name()) || event.kind().name().equals(StandardWatchEventKinds.ENTRY_CREATE.name())) {
                    compileCoffeeFile(compiler, file.toPath(), jsFile, coffeeFileName, jsFileName);
                    changed = true;
                }
            }

            watchKey.reset();
        }
    }

    private WatchService startWatching(Path sourceDirectory) throws IOException {
        final WatchService watchService = WatchService.newWatchService();

        Files.walkFileTree(sourceDirectory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                WatchableFile watchableFile = new WatchableFile(dir.toFile());
                watchableFile.register(watchService, watchEvents);
                getLog().debug(String.format("watch %s", dir));
                return FileVisitResult.CONTINUE;
            }
        });
        return watchService;
    }
}
