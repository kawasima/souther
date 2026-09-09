package souther.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** The reading that goes to the file system, which is the one every fork shares. */
final class ReadClassFiles implements ClassFiles {

    @Override
    public List<Path> list(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(each -> each.toString().endsWith(".class")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("the compiled output at " + root + " cannot be listed",
                    e);
        }
    }

    @Override
    public Optional<ClassModel> read(Path file) {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(ClassFile.of().parse(Files.readAllBytes(file)));
        } catch (IOException e) {
            throw new UncheckedIOException("the class file at " + file + " cannot be read", e);
        }
    }
}
