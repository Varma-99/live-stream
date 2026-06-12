package com.livestream.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class DropCouponRequest {

    @NotBlank
    @Size(min = 2, max = 32)
    @Pattern(regexp = "[A-Za-z0-9_-]+")
    @JsonProperty
    private String code;

    @Min(1)
    @Max(90)
    @JsonProperty
    private int percentOff;

    @Min(10)
    @Max(600)
    @JsonProperty
    private int durationSec;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public int getPercentOff() {
        return percentOff;
    }

    public void setPercentOff(int percentOff) {
        this.percentOff = percentOff;
    }

    public int getDurationSec() {
        return durationSec;
    }

    public void setDurationSec(int durationSec) {
        this.durationSec = durationSec;
    }
}
