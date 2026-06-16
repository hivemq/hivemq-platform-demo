package com.hivemq.platform.demo.di.component;

import com.hivemq.platform.demo.config.Configuration;
import com.hivemq.platform.demo.console.ConsoleProgress;
import com.hivemq.platform.demo.containers.ContainersRunner;
import com.hivemq.platform.demo.di.module.ConcurrencyModule;
import com.hivemq.platform.demo.di.module.ConfigurationModule;
import com.hivemq.platform.demo.di.module.ConsoleModule;
import com.hivemq.platform.demo.di.module.DockerModule;
import com.hivemq.platform.demo.di.module.JacksonModule;
import com.hivemq.platform.demo.di.module.MqttModule;
import com.hivemq.platform.demo.di.module.NetworkModule;
import com.hivemq.platform.demo.di.module.RxModule;
import com.hivemq.platform.demo.di.scope.ApplicationScope;
import com.hivemq.platform.demo.domain.dto.ArgsDto;
import com.hivemq.platform.demo.mqtt.MockDataPublisher;
import com.hivemq.platform.demo.oauth2.LoopbackServer;
import dagger.BindsInstance;
import dagger.Component;

@ApplicationScope
@Component(
        modules = {
            ConfigurationModule.class,
            JacksonModule.class,
            NetworkModule.class,
            RxModule.class,
            ConcurrencyModule.class,
            DockerModule.class,
            MqttModule.class,
            ConsoleModule.class
        })
public interface ApplicationComponent {

    Configuration configuration();

    LoopbackServer loopbackServer();

    ContainersRunner containersRunner();

    MockDataPublisher mockDataPublisher();

    ConsoleProgress consoleProgress();

    SessionComponent.Factory sessionFactory();

    @Component.Factory
    interface Factory {
        ApplicationComponent create(@BindsInstance ArgsDto args);
    }
}
