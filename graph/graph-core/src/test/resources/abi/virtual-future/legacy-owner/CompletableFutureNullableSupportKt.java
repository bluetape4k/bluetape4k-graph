package io.bluetape4k.concurrent.virtualthread;

import kotlin.jvm.functions.Function0;

import java.util.concurrent.CompletableFuture;

/**
 * Compile-only shape of the graph-core owner removed by #542.
 */
public final class CompletableFutureNullableSupportKt {

    private CompletableFutureNullableSupportKt() {
    }

    public static <V> CompletableFuture<V> virtualFutureOfNullable(Function0<? extends V> block) {
        return CompletableFuture.completedFuture(block.invoke());
    }
}
