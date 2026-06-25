package com.livestream.api;

import com.google.inject.Inject;
import com.livestream.api.dto.StreamPostmortemResponse;
import com.livestream.api.dto.StreamQoSResponse;
import com.livestream.api.dto.ViewerQoSEventRequest;
import com.livestream.api.dto.ViewerQoSStatsRequest;
import com.livestream.cluster.PeerInstanceClient;
import com.livestream.cluster.ViewerQoSRouter;
import com.livestream.LiveStreamConfiguration;
import com.livestream.qos.QoSEventType;
import com.livestream.qos.StreamQoSService;
import com.livestream.realtime.BroadcasterControlService;
import com.livestream.redis.RedisService;
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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/streams")
@Produces(MediaType.APPLICATION_JSON)
public class StreamQoSResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(StreamQoSResource.class);

    private final StreamQoSService streamQoSService;
    private final StreamService streamService;
    private final LiveStreamConfiguration configuration;
    private final RedisService redisService;
    private final BroadcasterControlService broadcasterControlService;
    private final PeerInstanceClient peerInstanceClient;
    private final ViewerQoSRouter viewerQoSRouter;

    @Inject
    public StreamQoSResource(
            StreamQoSService streamQoSService,
            StreamService streamService,
            LiveStreamConfiguration configuration,
            RedisService redisService,
            BroadcasterControlService broadcasterControlService,
            PeerInstanceClient peerInstanceClient,
            ViewerQoSRouter viewerQoSRouter) {
        this.streamQoSService = streamQoSService;
        this.streamService = streamService;
        this.configuration = configuration;
        this.redisService = redisService;
        this.broadcasterControlService = broadcasterControlService;
        this.peerInstanceClient = peerInstanceClient;
        this.viewerQoSRouter = viewerQoSRouter;
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
        return forwardOwnerRead(streamId, peerInstanceClient::forwardQoS)
                .orElseGet(() -> streamQoSService.snapshot(streamId));
    }

    @GET
    @Path("/{id}/postmortem")
    @UnitOfWork
    public StreamPostmortemResponse postmortem(@PathParam("id") Long streamId) {
        streamService.requireStreamExists(streamId);
        return forwardOwnerRead(streamId, peerInstanceClient::forwardPostmortem)
                .orElseGet(() -> streamQoSService.postmortem(streamId));
    }

    @GET
    @Path("/{id}/qos/history")
    @UnitOfWork
    public List<StreamPostmortemResponse> qosHistory(@PathParam("id") Long streamId) {
        streamService.requireStreamExists(streamId);
        return streamQoSService.historyForStream(streamId);
    }

    @GET
    @Path("/qos/history")
    @UnitOfWork
    public List<StreamPostmortemResponse> globalQosHistory(@QueryParam("limit") Integer limit) {
        int n = limit != null && limit > 0 ? limit : 20;
        return streamQoSService.recentHistory(n);
    }

    @POST
    @Path("/{id}/qos/viewer-event")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewerEvent(@PathParam("id") Long streamId, @Valid ViewerQoSEventRequest request) {
        QoSEventType type = parseViewerEvent(request.getEventType());
        String message = request.getMessage() != null ? request.getMessage() : type.name();
        viewerQoSRouter.recordViewerEvent(streamId, request.getPresenceId(), type, message, request.getDetail());
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/qos/viewer-stats")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewerStats(@PathParam("id") Long streamId, @Valid ViewerQoSStatsRequest request) {
        viewerQoSRouter.recordViewerStats(streamId, request);
        return Response.noContent().build();
    }

    private boolean isLocalOwner(long streamId) {
        String self = configuration.getInstanceId();
        if (redisService.isEnabled()) {
            return redisService.getStreamOwner(streamId).map(self::equals).orElseGet(
                    () -> broadcasterControlService.isLocallyOwned(streamId));
        }
        return broadcasterControlService.isLocallyOwned(streamId);
    }

    private <T> Optional<T> forwardOwnerRead(long streamId, OwnerReadForwarder<T> forwarder) {
        if (isLocalOwner(streamId)) {
            return Optional.empty();
        }
        Optional<String> owner = redisService.getStreamOwner(streamId);
        if (owner.isEmpty() || owner.get().equals(configuration.getInstanceId())) {
            return Optional.empty();
        }
        try {
            return Optional.of(forwarder.forward(owner.get(), streamId));
        } catch (RuntimeException e) {
            LOGGER.warn("Peer read forward failed for stream {}: {}", streamId, e.getMessage());
            return Optional.empty();
        }
    }

    @FunctionalInterface
    private interface OwnerReadForwarder<T> {
        T forward(String ownerInstanceId, long streamId);
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
                case "WATCH_SEC" -> QoSEventType.VIEWER_LEAVE;
                default -> throw new IllegalArgumentException("Unknown eventType: " + raw);
            };
        }
    }
}
