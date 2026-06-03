package com.livestream.api;

import com.google.inject.Inject;
import com.livestream.api.dto.StreamPostmortemResponse;
import com.livestream.api.dto.StreamQoSResponse;
import com.livestream.api.dto.ViewerQoSEventRequest;
import com.livestream.api.dto.ViewerQoSStatsRequest;
import com.livestream.qos.QoSEventType;
import com.livestream.qos.StreamQoSService;
import com.livestream.service.StreamService;
import io.dropwizard.hibernate.UnitOfWork;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/streams")
@Produces(MediaType.APPLICATION_JSON)
public class StreamQoSResource {

    private final StreamQoSService streamQoSService;
    private final StreamService streamService;

    @Inject
    public StreamQoSResource(StreamQoSService streamQoSService, StreamService streamService) {
        this.streamQoSService = streamQoSService;
        this.streamService = streamService;
    }

    @GET
    @Path("/qos/ops")
    @UnitOfWork
    public com.livestream.api.dto.OpsQoSDto globalOps() {
        return streamQoSService.globalOps();
    }

    @GET
    @Path("/{id}/qos")
    @UnitOfWork
    public StreamQoSResponse streamQoS(@PathParam("id") Long streamId) {
        streamService.requireStreamExists(streamId);
        return streamQoSService.snapshot(streamId);
    }

    @GET
    @Path("/{id}/postmortem")
    @UnitOfWork
    public StreamPostmortemResponse postmortem(@PathParam("id") Long streamId) {
        streamService.requireStreamExists(streamId);
        return streamQoSService.postmortem(streamId);
    }

    @POST
    @Path("/{id}/qos/viewer-event")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewerEvent(@PathParam("id") Long streamId, @Valid ViewerQoSEventRequest request) {
        streamQoSService.ensureSession(streamId);
        QoSEventType type = parseViewerEvent(request.getEventType());
        String message = request.getMessage() != null ? request.getMessage() : type.name();
        streamQoSService.recordViewerEvent(streamId, request.getPresenceId(), type, message, request.getDetail());
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/qos/viewer-stats")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewerStats(@PathParam("id") Long streamId, @Valid ViewerQoSStatsRequest request) {
        streamQoSService.ensureSession(streamId);
        streamQoSService.recordWebRtcStats(
                streamId,
                request.getPresenceId(),
                request.getQualityLabel(),
                request.getPacketLossPct() != null ? request.getPacketLossPct() : 0,
                request.getRttMs() != null ? request.getRttMs() : 0,
                request.getJitterMs() != null ? request.getJitterMs() : 0,
                request.getDownloadKbps() != null ? request.getDownloadKbps() : 0);
        return Response.noContent().build();
    }

    private static QoSEventType parseViewerEvent(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("eventType is required");
        }
        String normalized = raw.trim().toUpperCase().replace('-', '_');
        try {
            return QoSEventType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return switch (normalized) {
                case "TTFF", "STARTUP" -> QoSEventType.VIEWER_TTFF;
                case "STALL", "REBUFFER" -> QoSEventType.VIEWER_STALL;
                case "ABR_DOWN", "QUALITY_DOWN" -> QoSEventType.DELIVERY_ABR_STEP_DOWN;
                case "ABR_UP", "QUALITY_UP" -> QoSEventType.DELIVERY_ABR_STEP_UP;
                case "RECONNECT" -> QoSEventType.DELIVERY_RECONNECT;
                case "RECOVERED", "DELIVERY_RECOVERED" -> QoSEventType.DELIVERY_RECOVERED;
                case "FATAL", "ERROR" -> QoSEventType.VIEWER_FATAL;
                case "JOIN" -> QoSEventType.VIEWER_JOIN_OK;
                default -> throw new IllegalArgumentException("Unknown eventType: " + raw);
            };
        }
    }
}
