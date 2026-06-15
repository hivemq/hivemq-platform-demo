package com.hivemq.platform.demo.provision;

public record ProvisionResult(String pulseToken, String registrationToken) {
    public static ProvisionResult from(String pulseToken, String registrationToken) {
        return new ProvisionResult(pulseToken, registrationToken);
    }
}
