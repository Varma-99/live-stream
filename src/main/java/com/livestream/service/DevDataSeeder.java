package com.livestream.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.livestream.model.User;
import com.livestream.model.UserRole;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Inserts demo users when the database is empty (dev profile only).
 */
@Singleton
public class DevDataSeeder {

    private static final Logger LOGGER = LoggerFactory.getLogger(DevDataSeeder.class);

    private final SessionFactory sessionFactory;

    @Inject
    public DevDataSeeder(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public void seedIfEmpty() {
        try (Session session = sessionFactory.openSession()) {
            Transaction transaction = session.beginTransaction();
            try {
                Long count = session.createQuery("SELECT COUNT(u) FROM User u", Long.class)
                        .getSingleResult();
                if (count == 0) {
                    session.persist(new User("broadcaster1", "Demo Broadcaster", UserRole.BROADCASTER));
                    session.persist(new User("viewer1", "Demo Viewer", UserRole.VIEWER));
                    session.persist(new User("dummy_streamer", "Dummy Streamer", UserRole.BROADCASTER));
                    LOGGER.info("Seeded dev users: broadcaster1, viewer1, dummy_streamer");
                }
                transaction.commit();
            } catch (RuntimeException e) {
                transaction.rollback();
                throw e;
            }
        }
    }

    /** H2 dev DB may predate dummy_streamer — add without wiping data. */
    public void ensureDummyBroadcaster() {
        try (Session session = sessionFactory.openSession()) {
            Transaction transaction = session.beginTransaction();
            try {
                Long exists = session.createQuery(
                                "SELECT COUNT(u) FROM User u WHERE u.username = :username", Long.class)
                        .setParameter("username", "dummy_streamer")
                        .getSingleResult();
                if (exists == 0) {
                    session.persist(new User("dummy_streamer", "Dummy Streamer", UserRole.BROADCASTER));
                    LOGGER.info("Seeded dummy_streamer user");
                }
                transaction.commit();
            } catch (RuntimeException e) {
                transaction.rollback();
                throw e;
            }
        }
    }
}
