package com.livestream.api;

import com.google.inject.Inject;
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
    @Path("/{id}/stop")
    @UnitOfWork
    public StreamResponse stopStream(@PathParam("id") Long streamId) {
        return streamService.stopStream(streamId);
    }
}
