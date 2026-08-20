package com.fortrow.jflux.files;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.exceptions.types.CommonIOException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** A small, reusable wrapper around one filesystem file. */
public class FileObject {

    private final PathObject path;
    private final File file;
    private byte[] data = new byte[0];

    public FileObject(PathObject path, String filename) throws CommonIOException {
        if (path == null || filename == null || filename.isBlank()) {
            throw ExceptionHandling.invalid("A directory path and filename are required.");
        }
        this.path = path.clone();
        this.file = getNativeFile(filename);
    }

    public FileObject(File file) throws CommonIOException {
        if (file == null) throw ExceptionHandling.invalid("File is required.");
        Path filePath = file.toPath();
        if (!Files.exists(filePath, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw new CommonIOException("The system path '" + file + "' does not exist.");
        }
        if (!Files.isRegularFile(filePath, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw new CommonIOException("The system path '" + file + "' is not a file.");
        }
        this.path = PathUtil.fromString(filePath.getParent() == null ? "." : filePath.getParent().toString());
        this.file = file;
    }

    public boolean delete() {
        try {
            return Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            ExceptionHandling.report(e);
            return false;
        }
    }

    /** Reads the complete file into the in-memory buffer and returns its byte count. */
    public int readToEnd() {
        try {
            data = Files.readAllBytes(file.toPath());
            return data.length;
        } catch (IOException e) {
            ExceptionHandling.report(e);
            data = new byte[0];
            return 0;
        }
    }

    /** Reads the complete file and exposes checked failure to callers that need it. */
    public int readToEndOrThrow() throws IOException {
        data = Files.readAllBytes(file.toPath());
        return data.length;
    }

    /** Returns the size of the currently loaded buffer. */
    public int size() {
        return data.length;
    }

    /** Returns the current on-disk size without loading the file. */
    public long fileSize() throws IOException {
        return Files.size(file.toPath());
    }

    public byte[] getBytes(int length) {
        checkRange(0, length);
        return java.util.Arrays.copyOf(data, length);
    }

    public byte[] getBytes(int start, int length) {
        checkRange(start, length);
        return java.util.Arrays.copyOfRange(data, start, start + length);
    }

    /** Returns a defensive copy of the loaded bytes. */
    public byte[] bytes() {
        return data.clone();
    }

    public String[] getLines() {
        return getLines(StandardCharsets.UTF_8);
    }

    public String[] getLines(Charset charset) {
        return string(charset).split("\\R", -1);
    }

    public String string() {
        return string(StandardCharsets.UTF_8);
    }

    public String string(Charset charset) {
        if (charset == null) throw ExceptionHandling.invalid("Charset is required.");
        return new String(data, charset);
    }

    public void overwrite(byte[] bytes) {
        if (bytes == null) throw ExceptionHandling.invalid("Data is required.");
        write(bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public void overwrite(String content) {
        if (content == null) throw ExceptionHandling.invalid("Content is required.");
        overwrite(content.getBytes(StandardCharsets.UTF_8));
    }

    public void append(byte[] bytes) {
        if (bytes == null) throw ExceptionHandling.invalid("Data is required.");
        write(bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public void append(String content) {
        if (content == null) throw ExceptionHandling.invalid("Content is required.");
        append(content.getBytes(StandardCharsets.UTF_8));
    }

    public Path path() {
        return file.toPath();
    }

    public File getFile() {
        return file;
    }

    private File getNativeFile(String filename) throws CommonIOException {
        Path nativePath = path.clone(filename).toPath();
        try {
            Path parent = nativePath.getParent();
            if (parent != null) Files.createDirectories(parent);
            if (Files.exists(nativePath, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    && !Files.isRegularFile(nativePath, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                throw new CommonIOException("The system path '" + nativePath + "' is not a file.");
            }
            if (!Files.exists(nativePath, java.nio.file.LinkOption.NOFOLLOW_LINKS)) Files.createFile(nativePath);
            return nativePath.toFile();
        } catch (CommonIOException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new CommonIOException("Unable to create file '" + nativePath + "'.", e);
        }
    }

    private void write(byte[] bytes, StandardOpenOption... options) {
        try {
            Files.write(file.toPath(), bytes, options);
            data = contains(options, StandardOpenOption.APPEND) ? appendToLoadedData(bytes) : bytes.clone();
        } catch (IOException e) {
            throw ExceptionHandling.failure("Unable to write file '" + file + "'.", e);
        }
    }

    private byte[] appendToLoadedData(byte[] bytes) {
        byte[] combined = java.util.Arrays.copyOf(data, data.length + bytes.length);
        System.arraycopy(bytes, 0, combined, data.length, bytes.length);
        return combined;
    }

    private boolean contains(StandardOpenOption[] options, StandardOpenOption target) {
        for (StandardOpenOption option : options) if (option == target) return true;
        return false;
    }

    private void checkRange(int start, int length) {
        if (start < 0 || length < 0 || start > data.length - length) {
            String message = "Requested range [" + start + ", " + (start + length)
                    + ") is outside loaded data of length " + data.length + ".";
            ExceptionHandling.report(new IndexOutOfBoundsException(message));
            throw new IndexOutOfBoundsException(message);
        }
    }
}
