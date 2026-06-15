package com.hivemq.platform.demo.di;

import com.hivemq.platform.demo.di.scope.ApplicationScope;
import dagger.Module;
import dagger.Provides;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.concurrent.Executor;

@Module
public class RxModule {

    @Provides
    @ApplicationScope
    Scheduler ioScheduler(Executor ioExecutor) {
        return Schedulers.from(ioExecutor);
    }
}
