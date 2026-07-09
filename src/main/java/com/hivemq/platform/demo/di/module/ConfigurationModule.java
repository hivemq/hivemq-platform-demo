package com.hivemq.platform.demo.di.module;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hivemq.platform.demo.config.Configuration;
import com.hivemq.platform.demo.config.Loader;
import com.hivemq.platform.demo.di.qualifier.Yaml;
import com.hivemq.platform.demo.di.scope.ApplicationScope;
import dagger.Module;
import dagger.Provides;

@Module
public class ConfigurationModule {

    @Provides
    @ApplicationScope
    Loader configLoader(@Yaml ObjectMapper mapper) {
        return new Loader(mapper);
    }

    @Provides
    @ApplicationScope
    Configuration appConfig(Loader loader) {
        return loader.load();
    }

    @Provides
    @ApplicationScope
    Configuration.Auth0 authConfig(Configuration config) {
        return config.auth0();
    }

    @Provides
    @ApplicationScope
    Configuration.Fallback fallbackConfig(Configuration config) {
        return config.fallback();
    }
}
