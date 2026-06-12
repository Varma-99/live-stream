package com.livestream.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class IngestQoSDto {

    @JsonProperty
    private long ingestKbpsAvg;

    @JsonProperty
    private long ingestKbpsP95;

    @JsonProperty
    private double ingestKbpsStdDev;

    @JsonProperty
    private int ingestStallCount;

    @JsonProperty
    private long ingestStallTotalMs;

    @JsonProperty
    private int ingestRecoveryCount;

    @JsonProperty
    private long ingestRecoveryTotalMs;

    @JsonProperty
    private int ingestHealthScore;

    @JsonProperty
    private boolean ingestUnstable;

    @JsonProperty
    private long currentIngestKbps;

    public IngestQoSDto() {}

    public IngestQoSDto(
            long ingestKbpsAvg,
            long ingestKbpsP95,
            double ingestKbpsStdDev,
            int ingestStallCount,
            long ingestStallTotalMs,
            int ingestRecoveryCount,
            long ingestRecoveryTotalMs,
            int ingestHealthScore,
            boolean ingestUnstable,
            long currentIngestKbps) {
        this.ingestKbpsAvg = ingestKbpsAvg;
        this.ingestKbpsP95 = ingestKbpsP95;
        this.ingestKbpsStdDev = ingestKbpsStdDev;
        this.ingestStallCount = ingestStallCount;
        this.ingestStallTotalMs = ingestStallTotalMs;
        this.ingestRecoveryCount = ingestRecoveryCount;
        this.ingestRecoveryTotalMs = ingestRecoveryTotalMs;
        this.ingestHealthScore = ingestHealthScore;
        this.ingestUnstable = ingestUnstable;
        this.currentIngestKbps = currentIngestKbps;
    }
}
