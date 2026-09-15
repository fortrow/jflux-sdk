package com.fortrow.jflux.jni;

import com.fortrow.jflux.shell.CommandResult;

import java.nio.file.Path;
import java.util.List;

/** Immutable result from a JNI native-library build. */
public record NativeBuildResult(
        JNIPlatform platform,
        String compiler,
        Path output,
        List<String> command,
        CommandResult commandResult) {

    public NativeBuildResult {
        platform = platform == null ? JNIPlatform.current() : platform;
        compiler = compiler == null ? "" : compiler;
        output = output == null ? null : output.toAbsolutePath().normalize();
        command = List.copyOf(command == null ? List.of() : command);
    }

    /** Whether the compiler produced a successful result. */
    public boolean success() {
        return commandResult != null && commandResult.success();
    }
}
