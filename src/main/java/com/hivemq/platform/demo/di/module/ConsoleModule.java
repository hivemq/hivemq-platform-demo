package com.hivemq.platform.demo.di.module;

import com.hivemq.platform.demo.console.ConsoleProgress;
import com.hivemq.platform.demo.di.scope.ApplicationScope;
import dagger.Module;
import dagger.Provides;

@Module
public class ConsoleModule {

    // counted phases: sign-in, provisioning, containers (publishing is the final steady state)
    private static final int TOTAL_PHASES = 3;

    @Provides
    @ApplicationScope
    ConsoleProgress consoleProgress() {
        return new ConsoleProgress(TOTAL_PHASES);
    }
}
