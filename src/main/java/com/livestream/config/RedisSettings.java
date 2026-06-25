package com.livestream.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Redis connection settings from {@code redis:} block in YAML config.
 */
public class RedisSettings {

    private String host = "127.0.0.1";
    private int port = 6379;
    private String password = "";
    private int timeoutMs = 2000;
    private int poolMaxTotal = 128;
    private int poolMaxIdle = 64;
    private int poolMinIdle = 8;

    @JsonProperty
    public String getHost() {
        return host;
    }

    @JsonProperty
    public void setHost(String host) {
        this.host = host;
    }

    @JsonProperty
    public int getPort() {
        return port;
    }

    @JsonProperty
    public void setPort(int port) {
        this.port = port;
    }

    @JsonProperty
    public String getPassword() {
        return password;
    }

    @JsonProperty
    public void setPassword(String password) {
        this.password = password;
    }

    @JsonProperty
    public int getTimeoutMs() {
        return timeoutMs;
    }

    @JsonProperty
    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    @JsonProperty
    public int getPoolMaxTotal() {
        return poolMaxTotal;
    }

    @JsonProperty
    public void setPoolMaxTotal(int poolMaxTotal) {
        this.poolMaxTotal = poolMaxTotal;
    }

    @JsonProperty
    public int getPoolMaxIdle() {
        return poolMaxIdle;
    }

    @JsonProperty
    public void setPoolMaxIdle(int poolMaxIdle) {
        this.poolMaxIdle = poolMaxIdle;
    }

    @JsonProperty
    public int getPoolMinIdle() {
        return poolMinIdle;
    }

    @JsonProperty
    public void setPoolMinIdle(int poolMinIdle) {
        this.poolMinIdle = poolMinIdle;
    }
}
