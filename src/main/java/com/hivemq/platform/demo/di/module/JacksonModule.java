package com.hivemq.platform.demo.di.module;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.toml.TomlFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.hivemq.platform.demo.di.qualifier.Toml;
import com.hivemq.platform.demo.di.qualifier.Yaml;
import com.hivemq.platform.demo.di.scope.ApplicationScope;
import dagger.Module;
import dagger.Provides;

@Module
public class JacksonModule {

    @Provides
    @ApplicationScope
    ObjectMapper objectMapper() {
        return new ObjectMapper()
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    @Provides
    @ApplicationScope
    @Yaml
    ObjectMapper yamlMapper(ObjectMapper objectMapper) {
        return objectMapper.copyWith(new YAMLFactory()).setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE);
    }

    @Provides
    @ApplicationScope
    @Toml
    ObjectMapper tomlMapper(ObjectMapper objectMapper) {
        return objectMapper.copyWith(new TomlFactory()).setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE);
    }
}
