package io.bluetape4k.concurrent.virtualthread;

import java.util.concurrent.CompletableFuture;
import kotlin.jvm.functions.Function0;

/** Compile-only shape of the graph-core owner removed by #542. */
public final class CompletableFutureNullableSupportKt {

    private CompletableFutureNullableSupportKt() {
    }

    public static <V> CompletableFuture<V> virtualFutureOfNullable(Function0<? extends V> block) {
        return CompletableFuture.completedFuture(block.invoke());
    }
}
