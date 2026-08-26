package io.bluetape4k.graph.vt.abi;

import io.bluetape4k.concurrent.virtualthread.CompletableFutureSupportKt;
import java.util.concurrent.CompletableFuture;
import kotlin.jvm.functions.Function0;

/** Minimal consumer recompiled against the official bluetape4k-core owner. */
public final class MigratedVirtualFutureConsumer {

    private MigratedVirtualFutureConsumer() {
    }

    public static CompletableFuture<String> invoke() {
        return CompletableFutureSupportKt.virtualFutureOfNullable(new Function0<String>() {
            @Override
            public String invoke() {
                return null;
            }
        });
    }
}
