package com.livestream;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.livestream.api.IllegalArgumentExceptionMapper;
import com.livestream.api.IllegalStateExceptionMapper;
import com.livestream.api.ConfigResource;
import com.livestream.api.StreamQoSResource;
import com.livestream.api.StreamResource;
import com.livestream.api.WhepProxyResource;
import com.livestream.health.AppHealthCheck;
import com.livestream.service.DevDataSeeder;
import com.livestream.mediamtx.IngestHealthService;
import com.livestream.realtime.BroadcasterControlService;
import com.livestream.service.VideoService;
import com.livestream.web.HlsAssetServlet;
import com.livestream.model.LiveStream;
import com.livestream.model.QoSEventRecord;
import com.livestream.model.QoSSessionRecord;
import com.livestream.model.QoSViewerSessionRecord;
import com.livestream.model.User;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.hibernate.HibernateBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dropwizard entry point — Control Plane (Phase 2: metadata + REST APIs).
 * <p>
 * Run: {@code server config/config.yml}
 */
public class LiveStreamApplication extends Application<LiveStreamConfiguration> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LiveStreamApplication.class);

    private final HibernateBundle<LiveStreamConfiguration> hibernateBundle =
            new HibernateBundle<LiveStreamConfiguration>(
                    User.class,
                    LiveStream.class,
                    QoSSessionRecord.class,
                    QoSEventRecord.class,
                    QoSViewerSessionRecord.class) {
                @Override
                public DataSourceFactory getDataSourceFactory(LiveStreamConfiguration configuration) {
                    return configuration.getDataSourceFactory();
                }
            };

    public static void main(String[] args) throws Exception {
        new LiveStreamApplication().run(args);
    }

    @Override
    public String getName() {
        return "live-stream";
    }

    @Override
    public void initialize(Bootstrap<LiveStreamConfiguration> bootstrap) {
        bootstrap.addBundle(hibernateBundle);
        bootstrap.addBundle(new AssetsBundle("/assets", "/ui", "index.html"));
    }

    @Override
    public void run(LiveStreamConfiguration configuration, Environment environment) throws Exception {
        Injector injector = Guice.createInjector(
                new LiveStreamModule(
                        configuration,
                        hibernateBundle.getSessionFactory(),
                        environment.getObjectMapper()));

        VideoService videoService = injector.getInstance(VideoService.class);
        environment.lifecycle().manage(videoService);
        environment.lifecycle().manage(injector.getInstance(IngestHealthService.class));
        environment.lifecycle().manage(injector.getInstance(BroadcasterControlService.class));
        HlsAssetServlet.register(environment, configuration.getHlsOutputDir());

        environment.healthChecks().register("app", new AppHealthCheck());
        environment.jersey().register(injector.getInstance(StreamResource.class));
        environment.jersey().register(injector.getInstance(StreamQoSResource.class));
        environment.jersey().register(injector.getInstance(WhepProxyResource.class));
        environment.jersey().register(injector.getInstance(ConfigResource.class));
        environment.jersey().register(new IllegalArgumentExceptionMapper());
        environment.jersey().register(new IllegalStateExceptionMapper());

        if (configuration.isDevMode()) {
            DevDataSeeder seeder = injector.getInstance(DevDataSeeder.class);
            seeder.seedIfEmpty();
            seeder.ensureDummyBroadcaster();
            LOGGER.info("Dev mode: using embedded H2 database (config-dev.yml)");
        }

        LOGGER.info("live-stream started — streamDelivery={}", configuration.getStreamDelivery());
        if (configuration.isSrsDelivery()) {
            LOGGER.info("Media server: SRS (./scripts/start-srs.sh) — WHEP proxied to {}", configuration.getSrsWhepBase());
        } else if (configuration.isMediamtxDelivery()) {
            LOGGER.info("Media server: MediaMTX (./scripts/start-mediamtx.sh) — WHEP proxied to {}", configuration.getMediamtxWebrtcBase());
        }
        LOGGER.info("REST: streams, coupon, join/heartbeat/room (viewer count)");
        LOGGER.info("Web UI (this machine): http://localhost:8080/ui/");
        if (configuration.getPublicWebBase() != null && !configuration.getPublicWebBase().isBlank()) {
            LOGGER.info("Web UI (Wi-Fi / LAN): {}/ui/", configuration.getPublicWebBase().replaceAll("/$", ""));
        }
        if (configuration.isSrsDelivery()) {
            LOGGER.info("SRS: run ./scripts/start-srs.sh (RTMP :1935, API/WHEP :1985, players :8088)");
            LOGGER.info("WHEP viewer URL: same-origin /whep/... (proxied to SRS {})", configuration.getSrsWhepBase());
            if (configuration.isAbrEnabled()) {
                LOGGER.info("ABR: 3 RTMP/WHEP rungs (high/mid/low) — viewer switches URL from WebRTC stats");
            }
            if (configuration.isSrsApiEnabled()) {
                LOGGER.info("Ingest telemetry: SRS API {}", configuration.getSrsApiBase());
            }
        } else if (configuration.isMediamtxDelivery()) {
            LOGGER.info("MediaMTX: run ./scripts/start-mediamtx.sh (RTMP :1935, WebRTC :8889, API :9997)");
            LOGGER.info("WHEP viewer URL: same-origin /whep/... (proxied to {})", configuration.getMediamtxWebrtcBase());
            if (configuration.isAbrEnabled()) {
                LOGGER.info("ABR: 3 RTMP/WHEP rungs (high/mid/low) — viewer switches URL from WebRTC stats");
            }
            if (configuration.isMediamtxApiEnabled()) {
                LOGGER.info("Ingest telemetry: MediaMTX API {}", configuration.getMediamtxApiBase());
            }
        } else {
            LOGGER.info("HLS example: http://localhost:8080/hls/1/index.m3u8");
        }
        LOGGER.info("HLS output dir: {}", configuration.getHlsOutputDir());
        LOGGER.info("FFmpeg path: {} (videoInput={})", configuration.getFfmpegPath(), configuration.getVideoInput());
        if ("camera".equalsIgnoreCase(configuration.getVideoInput())) {
            LOGGER.info("Camera device: {} — grant Camera/Mic to IntelliJ in System Settings → Privacy", configuration.getCameraDevice());
        }
    }
}
