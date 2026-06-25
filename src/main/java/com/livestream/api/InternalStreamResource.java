package com.livestream.api;

import com.google.inject.Inject;
import com.livestream.LiveStreamConfiguration;
import com.livestream.api.dto.InternalHealthResponse;
import com.livestream.api.dto.StreamPostmortemResponse;
import com.livestream.api.dto.StreamQoSResponse;
import com.livestream.api.dto.StreamResponse;
import com.livestream.api.dto.ViewerQoSEventRequest;
import com.livestream.api.dto.ViewerQoSStatsRequest;
import com.livestream.cluster.InternalApiAuth;
import com.livestream.cluster.ViewerQoSRouter;
import com.livestream.qos.QoSEventType;
import com.livestream.qos.StreamQoSService;
import com.livestream.realtime.BroadcasterControlService;
import com.livestream.redis.RedisService;
import com.livestream.service.StreamService;
import io.dropwizard.hibernate.UnitOfWork;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Instance-to-instance control plane (Phase 7). Not for browser clients.
 */
@Path("/internal")
@Produces(MediaType.APPLICATION_JSON)
public class InternalStreamResource {

    private final LiveStreamConfiguration configuration;
    private final RedisService redisService;
    private final BroadcasterControlService broadcasterControlService;
    private final StreamService streamService;
    private final StreamQoSService streamQoSService;
    private final ViewerQoSRouter viewerQoSRouter;

    @Inject
    public InternalStreamResource(
            LiveStreamConfiguration configuration,
            RedisService redisService,
            BroadcasterControlService broadcasterControlService,
            StreamService streamService,
            StreamQoSService streamQoSService,
            ViewerQoSRouter viewerQoSRouter) {
        this.configuration = configuration;
        this.redisService = redisService;
        this.broadcasterControlService = broadcasterControlService;
        this.streamService = streamService;
        this.streamQoSService = streamQoSService;
        this.viewerQoSRouter = viewerQoSRouter;
    }

    @GET
    @Path("/health")
    public InternalHealthResponse health() {
        return new InternalHealthResponse(configuration.getInstanceId(), true);
    }

    @POST
    @Path("/streams/{id}/stop")
    @UnitOfWork
    public Response stopStream(
            @PathParam("id") Long streamId, @HeaderParam(InternalApiAuth.HEADER) String token) {
        InternalApiAuth.requireToken(configuration, token);
        if (!isOwner(streamId)) {
            return Response.status(Response.Status.CONFLICT)
                    .entity("Stream is not owned by this instance")
                    .build();
        }
        StreamResponse response = streamService.executeLocalStop(streamId);
        return Response.ok(response).build();
    }

    @GET
    @Path("/streams/{id}/qos")
    @UnitOfWork
    public Response streamQoS(
            @PathParam("id") Long streamId, @HeaderParam(InternalApiAuth.HEADER) String token) {
        InternalApiAuth.requireToken(configuration, token);
        if (!isOwner(streamId)) {
            return Response.status(Response.Status.CONFLICT)
                    .entity("Stream is not owned by this instance")
                    .build();
        }
        streamService.requireStreamExists(streamId);
        return Response.ok(streamQoSService.snapshot(streamId)).build();
    }

    @GET
    @Path("/streams/{id}/postmortem")
    @UnitOfWork
    public Response postmortem(
            @PathParam("id") Long streamId, @HeaderParam(InternalApiAuth.HEADER) String token) {
        InternalApiAuth.requireToken(configuration, token);
        if (!isOwner(streamId)) {
            return Response.status(Response.Status.CONFLICT)
                    .entity("Stream is not owned by this instance")
                    .build();
        }
        streamService.requireStreamExists(streamId);
        return Response.ok(streamQoSService.postmortem(streamId)).build();
    }

    @POST
    @Path("/streams/{id}/qos/viewer-event")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewerEvent(
            @PathParam("id") Long streamId,
            @HeaderParam(InternalApiAuth.HEADER) String token,
            @Valid ViewerQoSEventRequest request) {
        InternalApiAuth.requireToken(configuration, token);
        if (!isOwner(streamId)) {
            return Response.status(Response.Status.CONFLICT)
                    .entity("Stream is not owned by this instance")
                    .build();
        }
        viewerQoSRouter.recordViewerEvent(
                streamId,
                request.getPresenceId(),
                parseViewerEventType(request.getEventType()),
                request.getMessage() != null ? request.getMessage() : request.getEventType(),
                request.getDetail());
        return Response.noContent().build();
    }

    @POST
    @Path("/streams/{id}/qos/viewer-stats")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewerStats(
            @PathParam("id") Long streamId,
            @HeaderParam(InternalApiAuth.HEADER) String token,
            @Valid ViewerQoSStatsRequest request) {
        InternalApiAuth.requireToken(configuration, token);
        if (!isOwner(streamId)) {
            return Response.status(Response.Status.CONFLICT)
                    .entity("Stream is not owned by this instance")
                    .build();
        }
        viewerQoSRouter.recordViewerStats(streamId, request);
        return Response.noContent().build();
    }

    private static QoSEventType parseViewerEventType(String raw) {
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
                case "WATCH_SEC" -> QoSEventType.VIEWER_LEAVE;
                default -> throw new IllegalArgumentException("Unknown eventType: " + raw);
            };
        }
    }

    private boolean isOwner(long streamId) {
        String self = configuration.getInstanceId();
        if (redisService.isEnabled()) {
            return redisService.getStreamOwner(streamId).map(self::equals).orElseGet(
                    () -> broadcasterControlService.isLocallyOwned(streamId));
        }
        return broadcasterControlService.isLocallyOwned(streamId);
    }
}
