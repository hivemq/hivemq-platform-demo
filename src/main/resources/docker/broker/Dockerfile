FROM hivemq/hivemq4:latest

# Use the image's default broker config (default ports 1883/8080, default Control Center user admin/hivemq).
# Only Pulse is customized; pulse.xml reads ${ENV:PULSE_TOKEN}, supplied at runtime.
COPY pulse.xml /opt/hivemq/pulse/conf/config.xml
