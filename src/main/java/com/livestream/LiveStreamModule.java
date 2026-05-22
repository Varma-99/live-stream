package com.livestream;

import com.google.inject.AbstractModule;
import com.livestream.dao.LiveStreamDAO;
import com.livestream.dao.UserDAO;
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

    public LiveStreamModule(LiveStreamConfiguration configuration, SessionFactory sessionFactory) {
        this.configuration = configuration;
        this.sessionFactory = sessionFactory;
    }

    @Override
    protected void configure() {
        bind(LiveStreamConfiguration.class).toInstance(configuration);
        bind(SessionFactory.class).toInstance(sessionFactory);
        bind(UserDAO.class);
        bind(LiveStreamDAO.class);
        bind(StreamService.class);
        bind(VideoService.class);
        bind(DevDataSeeder.class);
    }
}
