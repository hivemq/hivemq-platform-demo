package com.hivemq.platform.demo.containers;

public record Mount(String hostPath, String containerPath, boolean readOnly) {}
