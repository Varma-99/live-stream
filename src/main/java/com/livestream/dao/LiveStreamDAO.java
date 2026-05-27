package com.livestream.dao;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.livestream.model.LiveStream;
import com.livestream.model.StreamStatus;
import io.dropwizard.hibernate.AbstractDAO;
import java.util.EnumSet;
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

    /** Streams viewers can watch (live or paused — ingest still running). */
    public List<LiveStream> findBroadcasting() {
        return currentSession()
                .createQuery(
                        "FROM LiveStream s JOIN FETCH s.broadcaster WHERE s.status IN :statuses ORDER BY s.startedAt DESC",
                        LiveStream.class)
                .setParameter("statuses", EnumSet.of(StreamStatus.LIVE, StreamStatus.PAUSED))
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
                        "SELECT COUNT(s) FROM LiveStream s WHERE s.broadcaster.id = :broadcasterId AND s.status IN :statuses",
                        Long.class)
                .setParameter("broadcasterId", broadcasterId)
                .setParameter("statuses", EnumSet.of(StreamStatus.LIVE, StreamStatus.PAUSED))
                .getSingleResult();
        return count > 0;
    }

    public long countBroadcasting() {
        Long count = currentSession()
                .createQuery("SELECT COUNT(s) FROM LiveStream s WHERE s.status IN :statuses", Long.class)
                .setParameter("statuses", EnumSet.of(StreamStatus.LIVE, StreamStatus.PAUSED))
                .getSingleResult();
        return count;
    }
}
