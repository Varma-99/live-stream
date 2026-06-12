package com.livestream;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.core.Configuration;
import io.dropwizard.db.DataSourceFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Maps fields from {@code config/config.yml}.
 */
public class LiveStreamConfiguration extends Configuration {

    @Valid
    @NotNull
    private DataSourceFactory database = new DataSourceFactory();

    @NotEmpty
    private String ffmpegPath = "/usr/local/bin/ffmpeg";

    @NotEmpty
    private String hlsOutputDir = "./data/hls";

    /** When true, seeds demo users on startup (see config-dev.yml). */
    private boolean devMode = false;

    /** When true, FFmpeg uses a test pattern instead of waiting for RTMP (legacy). */
    private boolean useTestVideo = true;

    /** Video source: {@code test}, {@code camera} (macOS), or {@code rtmp}. */
    private String videoInput = "test";

    /** AVFoundation device string, e.g. {@code 0:0} = first camera + first mic. */
    private String cameraDevice = "0:0";

    private int rtmpPort = 1935;

    /** {@code hls}, {@code mediamtx}, or {@code srs} — RTMP+WebRTC modes publish RTMP for WHEP playback. */
    private String streamDelivery = "hls";

    private String mediamtxRtmpPublishBase = "rtmp://127.0.0.1:1935";

    private String mediamtxWebrtcBase = "http://127.0.0.1:8889";

    private String mediamtxApiBase = "http://127.0.0.1:9997";

    private boolean mediamtxApiEnabled = true;

    private String srsRtmpPublishBase = "rtmp://127.0.0.1:1935";

    /** SRS HTTP API + WHEP signaling (typically :1985). */
    private String srsWhepBase = "http://127.0.0.1:1985";

    private String srsApiBase = "http://127.0.0.1:1985";

    private boolean srsApiEnabled = true;

    /** Three RTMP/WHEP rungs (high / mid / low) when using RTMP+WebRTC delivery. */
    private boolean abrEnabled = true;

    /**
     * LAN base URL for phones on Wi-Fi, e.g. {@code http://10.255.51.42:8080}.
     * WHEP URLs use {@link #mediamtxWebrtcBase}; FFmpeg RTMP ingest stays on localhost.
     */
    private String publicWebBase = "http://127.0.0.1:8080";

    @JsonProperty("database")
    public DataSourceFactory getDataSourceFactory() {
        return database;
    }

    @JsonProperty("database")
    public void setDataSourceFactory(DataSourceFactory database) {
        this.database = database;
    }

    @JsonProperty
    public String getFfmpegPath() {
        return ffmpegPath;
    }

    @JsonProperty
    public void setFfmpegPath(String ffmpegPath) {
        this.ffmpegPath = ffmpegPath;
    }

    @JsonProperty
    public String getHlsOutputDir() {
        return hlsOutputDir;
    }

    @JsonProperty
    public void setHlsOutputDir(String hlsOutputDir) {
        this.hlsOutputDir = hlsOutputDir;
    }

    @JsonProperty
    public boolean isDevMode() {
        return devMode;
    }

    @JsonProperty
    public void setDevMode(boolean devMode) {
        this.devMode = devMode;
    }

    @JsonProperty
    public boolean isUseTestVideo() {
        return useTestVideo;
    }

    @JsonProperty
    public void setUseTestVideo(boolean useTestVideo) {
        this.useTestVideo = useTestVideo;
    }

    @JsonProperty
    public int getRtmpPort() {
        return rtmpPort;
    }

    @JsonProperty
    public void setRtmpPort(int rtmpPort) {
        this.rtmpPort = rtmpPort;
    }

    @JsonProperty
    public String getVideoInput() {
        return videoInput;
    }

    @JsonProperty
    public void setVideoInput(String videoInput) {
        this.videoInput = videoInput;
    }

    @JsonProperty
    public String getCameraDevice() {
        return cameraDevice;
    }

    @JsonProperty
    public void setCameraDevice(String cameraDevice) {
        this.cameraDevice = cameraDevice;
    }

    @JsonProperty
    public String getStreamDelivery() {
        return streamDelivery;
    }

    @JsonProperty
    public void setStreamDelivery(String streamDelivery) {
        this.streamDelivery = streamDelivery;
    }

    @JsonProperty
    public String getMediamtxRtmpPublishBase() {
        return mediamtxRtmpPublishBase;
    }

    @JsonProperty
    public void setMediamtxRtmpPublishBase(String mediamtxRtmpPublishBase) {
        this.mediamtxRtmpPublishBase = mediamtxRtmpPublishBase;
    }

    @JsonProperty
    public String getMediamtxWebrtcBase() {
        return mediamtxWebrtcBase;
    }

    @JsonProperty
    public void setMediamtxWebrtcBase(String mediamtxWebrtcBase) {
        this.mediamtxWebrtcBase = mediamtxWebrtcBase;
    }

    public boolean isMediamtxDelivery() {
        return "mediamtx".equalsIgnoreCase(streamDelivery);
    }

    public boolean isSrsDelivery() {
        return "srs".equalsIgnoreCase(streamDelivery);
    }

    /** FFmpeg → RTMP → WHEP (MediaMTX or SRS). */
    public boolean isRtmpWebRtcDelivery() {
        return isMediamtxDelivery() || isSrsDelivery();
    }

    @JsonProperty
    public String getMediamtxApiBase() {
        return mediamtxApiBase;
    }

    @JsonProperty
    public void setMediamtxApiBase(String mediamtxApiBase) {
        this.mediamtxApiBase = mediamtxApiBase;
    }

    @JsonProperty
    public boolean isMediamtxApiEnabled() {
        return mediamtxApiEnabled;
    }

    @JsonProperty
    public void setMediamtxApiEnabled(boolean mediamtxApiEnabled) {
        this.mediamtxApiEnabled = mediamtxApiEnabled;
    }

    @JsonProperty
    public boolean isAbrEnabled() {
        return abrEnabled;
    }

    @JsonProperty
    public void setAbrEnabled(boolean abrEnabled) {
        this.abrEnabled = abrEnabled;
    }

    @JsonProperty
    public String getPublicWebBase() {
        return publicWebBase;
    }

    @JsonProperty
    public void setPublicWebBase(String publicWebBase) {
        this.publicWebBase = publicWebBase;
    }

    @JsonProperty
    public String getSrsRtmpPublishBase() {
        return srsRtmpPublishBase;
    }

    @JsonProperty
    public void setSrsRtmpPublishBase(String srsRtmpPublishBase) {
        this.srsRtmpPublishBase = srsRtmpPublishBase;
    }

    @JsonProperty
    public String getSrsWhepBase() {
        return srsWhepBase;
    }

    @JsonProperty
    public void setSrsWhepBase(String srsWhepBase) {
        this.srsWhepBase = srsWhepBase;
    }

    @JsonProperty
    public String getSrsApiBase() {
        return srsApiBase;
    }

    @JsonProperty
    public void setSrsApiBase(String srsApiBase) {
        this.srsApiBase = srsApiBase;
    }

    @JsonProperty
    public boolean isSrsApiEnabled() {
        return srsApiEnabled;
    }

    @JsonProperty
    public void setSrsApiEnabled(boolean srsApiEnabled) {
        this.srsApiEnabled = srsApiEnabled;
    }
}
