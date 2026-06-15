package com.hivemq.platform.demo.di.module;

import com.hivemq.platform.demo.di.scope.ApplicationScope;
import dagger.Module;
import dagger.Provides;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Module
public class ConcurrencyModule {

    @Provides
    @ApplicationScope
    Executor ioExecutor() {
        // name the virtual threads (io-0, io-1, …) so they show up in logs instead of an empty [ ]
        return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("io-", 0).factory());
    }
}
