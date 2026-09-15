package com.fortrow.jflux.runtime;

import com.fortrow.jflux.exceptions.ExceptionHandling;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Resolves, prioritizes, and executes built-in and dynamically loaded functions. */
public final class RuntimeFunctionRegistry implements AutoCloseable {
    private final Map<String, List<Registration>> registrations = new HashMap<>();
    private final Set<String> overrideable = new HashSet<>();
    private final ExecutorService executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
    private boolean closed;

    public synchronized void registerBuiltin(RuntimeFunction function) {
        registerBuiltin(function, false);
    }

    public synchronized void registerBuiltin(RuntimeFunction function, boolean serverOverrideable) {
        requireFunction(function);
        register(function, descriptor(function.id(), "builtin", RuntimeFunctionSource.BUILTIN,
                List.of(), "builtin", "", false), null);
        if(serverOverrideable) overrideable.add(function.id());
    }

    public synchronized void registerApplicationOverride(RuntimeFunction function, String version,
                                                          List<String> replaces) {
        requireFunction(function);
        requireOverrideTargets(replaces, "Application override");
        register(function, descriptor(function.id(), version, RuntimeFunctionSource.APPLICATION_OVERRIDE,
                replaces, "application", "", true), null);
    }

    public synchronized void registerPackage(RuntimePackage runtimePackage) {
        Objects.requireNonNull(runtimePackage, "runtimePackage");
        RuntimeFunctionDescriptor packageDescriptor = runtimePackage.descriptor();
        for(RuntimeFunction function : runtimePackage.functions()) {
            RuntimeFunctionDescriptor descriptor = descriptor(function.id(), packageDescriptor.version(),
                    packageDescriptor.source(), packageDescriptor.replaces(), packageDescriptor.packageId(),
                    packageDescriptor.keyId(), packageDescriptor.overrideRequested(), packageDescriptor.expiresAt());
            registerDynamic(function, descriptor, runtimePackage);
        }
    }

    public synchronized void register(RuntimeFunction function, RuntimeFunctionDescriptor descriptor,
                                      RuntimePackage owner) {
        requireFunction(function);
        Objects.requireNonNull(descriptor, "descriptor");
        if(!function.id().equals(descriptor.id()) && !descriptor.replaces().contains(function.id())) {
            throw new IllegalArgumentException("Function ID does not match descriptor: " + function.id());
        }
        Registration registration = new Registration(function, descriptor, owner);
        add(function.id(), registration);
        for(String replacement : descriptor.replaces()) add(replacement, registration);
    }

    public synchronized RuntimeFunction resolve(String function) {
        return select(function).function();
    }

    public RuntimeResult execute(RuntimeRequest request) {
        Objects.requireNonNull(request, "request");
        Registration registration;
        synchronized(this) {
            if(closed) return RuntimeResult.failure("Runtime is closed");
            try {
                registration = select(request.function());
            } catch(RuntimeException failure) {
                ExceptionHandling.report(failure);
                return RuntimeResult.failure(failure.getMessage());
            }
        }

        RuntimeContext context = new RuntimeContext(request, registration.descriptor().source(),
                registration.descriptor().packageId(), null,
                Map.of("version", registration.descriptor().version()));
        Future<RuntimeResult> future = null;
        try {
            future = executor.submit(() -> registration.function().execute(context));
            return future.get(request.timeout().toNanos(), TimeUnit.NANOSECONDS);
        } catch(TimeoutException failure) {
            if(future != null) future.cancel(true);
            return RuntimeResult.failure("Function timed out after " + request.timeout());
        } catch(InterruptedException failure) {
            Thread.currentThread().interrupt();
            if(future != null) future.cancel(true);
            return RuntimeResult.failure("Function execution was interrupted");
        } catch(CancellationException failure) {
            return RuntimeResult.failure("Function execution was cancelled");
        } catch(ExecutionException failure) {
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            ExceptionHandling.report(cause);
            return RuntimeResult.failure(cause.getMessage());
        } catch(RuntimeException failure) {
            ExceptionHandling.report(failure);
            return RuntimeResult.failure(failure.getMessage());
        }
    }

    public synchronized List<RuntimeFunctionDescriptor> descriptors() {
        return registrations.values().stream().flatMap(List::stream)
                .map(Registration::descriptor).distinct().toList();
    }

    @Override
    public synchronized void close() {
        if(closed) return;
        closed = true;
        registrations.values().stream().flatMap(List::stream).map(Registration::owner)
                .filter(Objects::nonNull).distinct().forEach(RuntimePackage::close);
        registrations.clear();
        overrideable.clear();
        executor.shutdownNow();
    }

    private Registration select(String function) {
        if(function == null || function.isBlank()) throw new IllegalArgumentException("function is required");
        return registrations.getOrDefault(function, List.of()).stream()
                .filter(candidate -> !candidate.descriptor().expired(Instant.now()))
                .max(Comparator.comparingInt((Registration candidate) -> candidate.descriptor().source().priority())
                        .thenComparing(candidate -> candidate.descriptor().version()))
                .orElseThrow(() -> new IllegalArgumentException("No function registered: " + function));
    }

    private void registerDynamic(RuntimeFunction function, RuntimeFunctionDescriptor descriptor,
                                 RuntimePackage owner) {
        if(descriptor.overrideRequested() && descriptor.replaces().isEmpty()) {
            throw new IllegalArgumentException("Package requested an override without replacement targets");
        }
        if(!descriptor.replaces().isEmpty()) {
            if(!descriptor.overrideRequested()) throw new IllegalArgumentException("Package must explicitly request an override");
            requireOverrideTargets(descriptor.replaces(), "Runtime package");
        }
        if(registrations.containsKey(function.id()) && !descriptor.replaces().contains(function.id())) {
            throw new IllegalArgumentException("Function collision is not an authorized override: " + function.id());
        }
        register(function, descriptor, owner);
    }

    private void requireOverrideTargets(List<String> targets, String source) {
        if(targets == null || targets.isEmpty()) throw new IllegalArgumentException(source + " requires replacement targets");
        for(String target : targets) {
            if(!overrideable.contains(target)) throw new IllegalArgumentException(source + " cannot replace protected function: " + target);
        }
    }

    private void add(String id, Registration registration) {
        registrations.computeIfAbsent(id, ignored -> new ArrayList<>()).add(registration);
    }

    private static void requireFunction(RuntimeFunction function) {
        if(function == null || function.id() == null || function.id().isBlank()) {
            throw new IllegalArgumentException("Runtime function and ID are required");
        }
    }

    private static RuntimeFunctionDescriptor descriptor(String id, String version, RuntimeFunctionSource source,
                                                         List<String> replaces, String packageId, String keyId,
                                                         boolean overrideRequested) {
        return descriptor(id, version, source, replaces, packageId, keyId, overrideRequested, null);
    }

    private static RuntimeFunctionDescriptor descriptor(String id, String version, RuntimeFunctionSource source,
                                                         List<String> replaces, String packageId, String keyId,
                                                         boolean overrideRequested, Instant expiresAt) {
        return new RuntimeFunctionDescriptor(id, version, source, replaces, expiresAt,
                packageId, keyId, overrideRequested);
    }

    private record Registration(RuntimeFunction function, RuntimeFunctionDescriptor descriptor,
                                RuntimePackage owner) { }
}
