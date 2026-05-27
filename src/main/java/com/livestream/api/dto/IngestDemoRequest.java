package com.livestream.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class IngestDemoRequest {

  @JsonProperty
  @Min(10)
  @Max(300)
  private int durationSec = 60;

  public int getDurationSec() {
    return durationSec;
  }

  public void setDurationSec(int durationSec) {
    this.durationSec = durationSec;
  }
}
