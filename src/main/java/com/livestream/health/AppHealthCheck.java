package com.livestream.health;

import com.codahale.metrics.health.HealthCheck;

/**
 * Simple health check — visit http://localhost:8081/healthcheck when the server runs.
 */
public class AppHealthCheck extends HealthCheck {

    @Override
    protected Result check() {
        return Result.healthy("live-stream Control Plane is up");
    }
}
