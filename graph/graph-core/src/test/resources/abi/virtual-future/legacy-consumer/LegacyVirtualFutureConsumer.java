package io.bluetape4k.graph.vt.abi;

import io.bluetape4k.concurrent.virtualthread.CompletableFutureNullableSupportKt;
import java.util.concurrent.CompletableFuture;
import kotlin.jvm.functions.Function0;

/** Minimal consumer precompiled against the graph-core generated owner. */
public final class LegacyVirtualFutureConsumer {

    private LegacyVirtualFutureConsumer() {
    }

    public static CompletableFuture<String> invoke() {
        return CompletableFutureNullableSupportKt.virtualFutureOfNullable(new Function0<String>() {
            @Override
            public String invoke() {
                return null;
            }
        });
    }
}
