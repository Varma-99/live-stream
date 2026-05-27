package com.livestream.api;

import com.google.inject.Inject;
import com.livestream.api.dto.DropCouponRequest;
import com.livestream.api.dto.IngestDemoRequest;
import com.livestream.api.dto.JoinResponse;
import com.livestream.api.dto.PresenceRequest;
import com.livestream.api.dto.RoomSnapshot;
import com.livestream.api.dto.StartStreamRequest;
import com.livestream.api.dto.StreamResponse;
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
import java.util.List;

@Path("/streams")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class StreamResource {

    private final StreamService streamService;

    @Inject
    public StreamResource(StreamService streamService) {
        this.streamService = streamService;
    }

    @GET
    @UnitOfWork
    public List<StreamResponse> listLiveStreams() {
        return streamService.listLiveStreams();
    }

    @POST
    @Path("/start")
    @UnitOfWork
    public StreamResponse startStream(@Valid StartStreamRequest request) {
        return streamService.startStream(request.getBroadcasterId(), request.getTitle());
    }

    @POST
    @Path("/{id}/pause")
    @UnitOfWork
    public StreamResponse pauseStream(@PathParam("id") Long streamId) {
        return streamService.pauseStream(streamId);
    }

    @POST
    @Path("/{id}/resume")
    @UnitOfWork
    public StreamResponse resumeStream(@PathParam("id") Long streamId) {
        return streamService.resumeStream(streamId);
    }

    @POST
    @Path("/{id}/stop")
    @UnitOfWork
    public StreamResponse stopStream(@PathParam("id") Long streamId) {
        return streamService.stopStream(streamId);
    }

    @POST
    @Path("/{id}/coupon")
    @UnitOfWork
    public Response dropCoupon(@PathParam("id") Long streamId, @Valid DropCouponRequest request) {
        streamService.dropCoupon(streamId, request.getCode(), request.getPercentOff(), request.getDurationSec());
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/join")
    @UnitOfWork
    public JoinResponse joinRoom(@PathParam("id") Long streamId) {
        return streamService.joinRoom(streamId);
    }

    @POST
    @Path("/{id}/heartbeat")
    @UnitOfWork
    public RoomSnapshot heartbeat(@PathParam("id") Long streamId, @Valid PresenceRequest request) {
        return streamService.heartbeat(streamId, request.getPresenceId());
    }

    @POST
    @Path("/{id}/leave")
    @UnitOfWork
    public Response leaveRoom(@PathParam("id") Long streamId, @Valid PresenceRequest request) {
        streamService.leaveRoom(streamId, request.getPresenceId());
        return Response.noContent().build();
    }

    @GET
    @Path("/{id}/room")
    @UnitOfWork
    public RoomSnapshot roomSnapshot(@PathParam("id") Long streamId) {
        return streamService.roomSnapshot(streamId);
    }

    @POST
    @Path("/{id}/like")
    @UnitOfWork
    public RoomSnapshot like(@PathParam("id") Long streamId, @Valid PresenceRequest request) {
        return streamService.recordLike(streamId, request.getPresenceId());
    }

    @POST
    @Path("/{id}/broadcaster-heartbeat")
    @UnitOfWork
    public Response broadcasterHeartbeat(@PathParam("id") Long streamId) {
        streamService.broadcasterHeartbeat(streamId);
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/ingest-demo")
    @UnitOfWork
    public Response simulateIngestUnstable(
            @PathParam("id") Long streamId, @Valid IngestDemoRequest request) {
        streamService.simulateIngestUnstable(streamId, request.getDurationSec());
        return Response.noContent().build();
    }
}
