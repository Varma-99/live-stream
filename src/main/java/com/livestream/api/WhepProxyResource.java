package com.livestream.api;

import com.google.inject.Inject;
import com.livestream.LiveStreamConfiguration;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Same-origin WHEP proxy so the viewer posts to :8080 instead of :8889 (avoids CORS / Failed to fetch).
 */
@Path("/whep")
public class WhepProxyResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(WhepProxyResource.class);
    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private final LiveStreamConfiguration configuration;

    @Inject
    public WhepProxyResource(LiveStreamConfiguration configuration) {
        this.configuration = configuration;
    }

    @POST
    @Path("{path: .+}")
    @Consumes("application/sdp")
    @Produces("application/sdp")
    public Response postWhep(@PathParam("path") String path, String offerSdp, @Context HttpHeaders inbound) {
        if (!configuration.isMediamtxDelivery()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity("MediaMTX delivery is not enabled")
                    .build();
        }
        String target = mediamtxBase() + "/" + path;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(target))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/sdp")
                    .POST(HttpRequest.BodyPublishers.ofString(offerSdp != null ? offerSdp : ""));
            String accept = inbound.getHeaderString(HttpHeaders.ACCEPT);
            if (accept != null) {
                builder.header(HttpHeaders.ACCEPT, accept);
            }
            String acceptPatch = inbound.getHeaderString("Accept-Patch");
            if (acceptPatch != null) {
                builder.header("Accept-Patch", acceptPatch);
            }
            HttpResponse<String> upstream = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            Response.ResponseBuilder out = Response.status(upstream.statusCode()).entity(upstream.body());
            upstream.headers().map().forEach((name, values) -> {
                if (isForwardedHeader(name)) {
                    values.forEach(v -> out.header(name, v));
                }
            });
            return out.build();
        } catch (Exception e) {
            LOGGER.warn("WHEP proxy POST {} failed: {}", target, e.getMessage());
            return Response.status(Response.Status.BAD_GATEWAY)
                    .entity("MediaMTX is not running on this Mac. In a terminal: "
                            + "cd to the project, then ./scripts/start-mediamtx.sh "
                            + "(listens on localhost:8889; your Wi-Fi IP "
                            + configuration.getPublicWebBase() + " is only for watch links).")
                    .type(MediaType.TEXT_PLAIN)
                    .build();
        }
    }

    @DELETE
    @Path("{path: .+}")
    public Response deleteWhep(@PathParam("path") String path) {
        String target = mediamtxBase() + "/" + path;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(target))
                    .timeout(Duration.ofSeconds(5))
                    .DELETE()
                    .build();
            HttpResponse<Void> upstream = HTTP.send(request, HttpResponse.BodyHandlers.discarding());
            return Response.status(upstream.statusCode()).build();
        } catch (Exception e) {
            LOGGER.debug("WHEP proxy DELETE {} failed: {}", target, e.getMessage());
            return Response.status(Response.Status.NO_CONTENT).build();
        }
    }

    private String mediamtxBase() {
        return configuration.getMediamtxWebrtcBase().replaceAll("/$", "");
    }

    private static boolean isForwardedHeader(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase();
        return lower.equals("location")
                || lower.equals("etag")
                || lower.equals("accept-patch")
                || lower.equals("content-type")
                || lower.equals("id");
    }
}
