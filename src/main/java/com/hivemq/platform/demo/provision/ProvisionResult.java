package com.hivemq.platform.demo.provision;

public record ProvisionResult(
        String pulseToken, String registrationToken, String orchestratorId, String orchestratorName) {}
