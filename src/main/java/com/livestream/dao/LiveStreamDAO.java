package com.livestream.dao;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.livestream.model.LiveStream;
import com.livestream.model.StreamStatus;
import io.dropwizard.hibernate.AbstractDAO;
import java.util.List;
import java.util.Optional;
import org.hibernate.SessionFactory;

@Singleton
public class LiveStreamDAO extends AbstractDAO<LiveStream> {

    @Inject
    public LiveStreamDAO(SessionFactory sessionFactory) {
        super(sessionFactory);
    }

    public List<LiveStream> findByStatus(StreamStatus status) {
        return currentSession()
                .createQuery(
                        "FROM LiveStream s JOIN FETCH s.broadcaster WHERE s.status = :status ORDER BY s.startedAt DESC",
                        LiveStream.class)
                .setParameter("status", status)
                .getResultList();
    }

    public LiveStream create(LiveStream stream) {
        return persist(stream);
    }

    public Optional<LiveStream> findById(Long id) {
        return Optional.ofNullable(get(id));
    }

    public long countByStatus(StreamStatus status) {
        Long count = currentSession()
                .createQuery("SELECT COUNT(s) FROM LiveStream s WHERE s.status = :status", Long.class)
                .setParameter("status", status)
                .getSingleResult();
        return count;
    }

    public boolean hasActiveStreamForBroadcaster(Long broadcasterId) {
        Long count = currentSession()
                .createQuery(
                        "SELECT COUNT(s) FROM LiveStream s WHERE s.broadcaster.id = :broadcasterId AND s.status = :status",
                        Long.class)
                .setParameter("broadcasterId", broadcasterId)
                .setParameter("status", StreamStatus.LIVE)
                .getSingleResult();
        return count > 0;
    }
}
