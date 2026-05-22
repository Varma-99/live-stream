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
                    LOGGER.info("Seeded dev users: broadcaster1 (id=1), viewer1 (id=2)");
                }
                transaction.commit();
            } catch (RuntimeException e) {
                transaction.rollback();
                throw e;
            }
        }
    }
}
