package souther.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
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

    /**
     * {@inheritDoc}
     *
     * <p>Absence is what the read itself reports and not what a question asked beforehand says.
     * Whether a module built a class is one thing; whether this process can see the file is
     * another, and a reading that asked whether the file was there and treated every no as the
     * first would answer that a class was never built for a directory it may not enter. What is
     * not there is an answer; anything else that stops the read is a failure and says so.
     */
    @Override
    public Optional<ClassModel> read(Path file) {
        try {
            return Optional.of(ClassFile.of().parse(Files.readAllBytes(file)));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("the class file at " + file + " cannot be read", e);
        }
    }
}
