package com.livestream.api;

import com.google.inject.Inject;
import com.livestream.LiveStreamConfiguration;
import com.livestream.api.dto.PublicConfigResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/config")
@Produces(MediaType.APPLICATION_JSON)
public class ConfigResource {

    private final LiveStreamConfiguration configuration;

    @Inject
    public ConfigResource(LiveStreamConfiguration configuration) {
        this.configuration = configuration;
    }

    @GET
    @Path("/public")
    public PublicConfigResponse publicConfig() {
        return new PublicConfigResponse(
                configuration.getPublicWebBase(),
                configuration.getStreamDelivery(),
                configuration.getMediamtxWebrtcBase(),
                mediaServerStartScript());
    }

    private String mediaServerStartScript() {
        if (configuration.isSrsDelivery()) {
            if (configuration.isEdgeClusterMode()) {
                return "SRS_CLUSTER_MODE=edge ./scripts/start-srs.sh";
            }
            return "./scripts/start-srs.sh";
        }
        if (configuration.isMediamtxDelivery()) {
            return "./scripts/start-mediamtx.sh";
        }
        return null;
    }
}
