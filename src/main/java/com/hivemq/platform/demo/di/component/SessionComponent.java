package com.hivemq.platform.demo.di.component;

import com.hivemq.platform.demo.di.module.SessionNetworkModule;
import com.hivemq.platform.demo.di.scope.SessionScope;
import com.hivemq.platform.demo.domain.dto.Oauth2TokenDto;
import com.hivemq.platform.demo.domain.network.AgentxApi;
import com.hivemq.platform.demo.domain.network.PulseApi;
import com.hivemq.platform.demo.provision.ResourceProvisioner;
import dagger.BindsInstance;
import dagger.Subcomponent;

@SessionScope
@Subcomponent(modules = SessionNetworkModule.class)
public interface SessionComponent {

    PulseApi pulseApi();

    AgentxApi agentxApi();

    ResourceProvisioner resourceProvisioner();

    @Subcomponent.Factory
    interface Factory {
        SessionComponent create(@BindsInstance Oauth2TokenDto token);
    }
}
