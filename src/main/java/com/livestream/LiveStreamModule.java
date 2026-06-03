package com.livestream;

import com.google.inject.AbstractModule;
import com.livestream.dao.LiveStreamDAO;
import com.livestream.dao.UserDAO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livestream.mediamtx.IngestHealthService;
import com.livestream.mediamtx.MediamtxApiClient;
import com.livestream.qos.StreamQoSService;
import com.livestream.realtime.BroadcasterControlService;
import com.livestream.realtime.LiveRoomHub;
import com.livestream.service.DevDataSeeder;
import com.livestream.service.StreamService;
import com.livestream.service.VideoService;
import org.hibernate.SessionFactory;

/**
 * Guice wiring for Control Plane services and DAOs.
 */
public class LiveStreamModule extends AbstractModule {

    private final LiveStreamConfiguration configuration;
    private final SessionFactory sessionFactory;
    private final ObjectMapper objectMapper;

    public LiveStreamModule(
            LiveStreamConfiguration configuration,
            SessionFactory sessionFactory,
            ObjectMapper objectMapper) {
        this.configuration = configuration;
        this.sessionFactory = sessionFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void configure() {
        bind(LiveStreamConfiguration.class).toInstance(configuration);
        bind(SessionFactory.class).toInstance(sessionFactory);
        bind(ObjectMapper.class).toInstance(objectMapper);
        bind(UserDAO.class);
        bind(LiveStreamDAO.class);
        bind(StreamService.class);
        bind(VideoService.class);
        bind(DevDataSeeder.class);
        bind(LiveRoomHub.class);
        bind(BroadcasterControlService.class);
        bind(MediamtxApiClient.class);
        bind(IngestHealthService.class);
        bind(StreamQoSService.class);
    }
}
