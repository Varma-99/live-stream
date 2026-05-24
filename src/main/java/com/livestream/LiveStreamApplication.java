package com.livestream;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.livestream.api.IllegalArgumentExceptionMapper;
import com.livestream.api.IllegalStateExceptionMapper;
import com.livestream.api.StreamResource;
import com.livestream.health.AppHealthCheck;
import com.livestream.service.DevDataSeeder;
import com.livestream.service.VideoService;
import com.livestream.web.HlsAssetServlet;
import com.livestream.model.LiveStream;
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
            new HibernateBundle<LiveStreamConfiguration>(User.class, LiveStream.class) {
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
                new LiveStreamModule(configuration, hibernateBundle.getSessionFactory()));

        VideoService videoService = injector.getInstance(VideoService.class);
        environment.lifecycle().manage(videoService);
        HlsAssetServlet.register(environment, configuration.getHlsOutputDir());

        environment.healthChecks().register("app", new AppHealthCheck());
        environment.jersey().register(injector.getInstance(StreamResource.class));
        environment.jersey().register(new IllegalArgumentExceptionMapper());
        environment.jersey().register(new IllegalStateExceptionMapper());

        if (configuration.isDevMode()) {
            injector.getInstance(DevDataSeeder.class).seedIfEmpty();
            LOGGER.info("Dev mode: using embedded H2 database (config-dev.yml)");
        }

        LOGGER.info("live-stream started — delivery={}", configuration.getStreamDelivery());
        LOGGER.info("REST: GET /streams, POST /streams/start, POST /streams/{id}/stop");
        LOGGER.info("Web UI: http://localhost:8080/ui/");
        if (configuration.isMediamtxDelivery()) {
            LOGGER.info("MediaMTX: run ./scripts/start-mediamtx.sh (RTMP :1935, WebRTC :8889)");
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
