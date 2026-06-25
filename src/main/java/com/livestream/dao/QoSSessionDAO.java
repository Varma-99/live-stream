package com.livestream.dao;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.livestream.api.dto.QoSEventDto;
import com.livestream.api.dto.StreamPostmortemResponse;
import com.livestream.api.dto.StreamQoSResponse;
import com.livestream.api.dto.ViewerSessionQoSDto;
import com.livestream.model.LiveStream;
import com.livestream.model.QoSEventRecord;
import com.livestream.model.QoSSessionRecord;
import com.livestream.model.QoSViewerSessionRecord;
import io.dropwizard.hibernate.AbstractDAO;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;

@Singleton
public class QoSSessionDAO extends AbstractDAO<QoSSessionRecord> {

    @Inject
    public QoSSessionDAO(SessionFactory sessionFactory) {
        super(sessionFactory);
    }

    public QoSSessionRecord save(
            LiveStream stream,
            long startedAtMs,
            Long endedAtMs,
            boolean zombieStop,
            int overallScore,
            String rootCause,
            long durationMs,
            String deliveryMode,
            List<QoSEventDto> events,
            List<ViewerSessionQoSDto> viewerSessions) {
        QoSSessionRecord record = new QoSSessionRecord();
        record.setStream(stream);
        record.setStartedAtMs(startedAtMs);
        record.setEndedAtMs(endedAtMs);
        record.setZombieStop(zombieStop);
        record.setOverallScore(overallScore);
        record.setRootCause(rootCause);
        record.setDurationMs(durationMs);
        record.setDeliveryMode(deliveryMode);
        QoSSessionRecord persisted = persist(record);

        for (QoSEventDto event : events) {
            QoSEventRecord er = new QoSEventRecord();
            er.setSession(persisted);
            er.setAtEpochMs(event.getAtEpochMs());
            er.setStage(event.getStage());
            er.setType(event.getType());
            er.setMessage(event.getMessage());
            er.setDetail(event.getDetail());
            currentSession().persist(er);
        }

        for (ViewerSessionQoSDto vs : viewerSessions) {
            QoSViewerSessionRecord vr = new QoSViewerSessionRecord();
            vr.setSession(persisted);
            vr.setPresenceId(vs.getPresenceId());
            vr.setQualityLabel(vs.getQualityLabel());
            vr.setWatchMs(vs.getWatchMs());
            vr.setTtffMs(vs.getTtffMs());
            vr.setStalls(vs.getStalls());
            vr.setQualitySwitches(vs.getQualitySwitches());
            vr.setPacketLossPct(vs.getPacketLossPct());
            vr.setRttMs(vs.getRttMs());
            vr.setJitterMs(vs.getJitterMs());
            vr.setDownloadKbps(vs.getDownloadKbps());
            vr.setAvgDelayMs(vs.getAvgDelayMs());
            currentSession().persist(vr);
        }

        return persisted;
    }

    public List<StreamPostmortemResponse> findByStreamId(long streamId) {
        List<QoSSessionRecord> records = currentSession()
                .createQuery(
                        "FROM QoSSessionRecord r WHERE r.stream.id = :streamId ORDER BY r.startedAtMs DESC",
                        QoSSessionRecord.class)
                .setParameter("streamId", streamId)
                .getResultList();
        List<StreamPostmortemResponse> result = new ArrayList<>();
        for (QoSSessionRecord record : records) {
            result.add(toPostmortem(record));
        }
        return result;
    }

    public List<StreamPostmortemResponse> findRecent(int limit) {
        List<QoSSessionRecord> records = currentSession()
                .createQuery(
                        "FROM QoSSessionRecord r ORDER BY r.startedAtMs DESC",
                        QoSSessionRecord.class)
                .setMaxResults(Math.max(1, limit))
                .getResultList();
        List<StreamPostmortemResponse> result = new ArrayList<>();
        for (QoSSessionRecord record : records) {
            result.add(toPostmortem(record));
        }
        return result;
    }

    private StreamPostmortemResponse toPostmortem(QoSSessionRecord record) {
        List<QoSEventRecord> events = currentSession()
                .createQuery(
                        "FROM QoSEventRecord e WHERE e.session.id = :sid ORDER BY e.atEpochMs ASC",
                        QoSEventRecord.class)
                .setParameter("sid", record.getId())
                .getResultList();

        List<QoSEventDto> timeline = events.stream()
                .map(e -> new QoSEventDto(e.getAtEpochMs(), e.getStage(), e.getType(), e.getMessage(), e.getDetail()))
                .toList();

        StreamQoSResponse summary = new StreamQoSResponse(
                record.getStream().getId(),
                record.getOverallScore() != null ? record.getOverallScore() : 0,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of());

        return new StreamPostmortemResponse(
                record.getStream().getId(),
                record.getDurationMs() != null ? record.getDurationMs() : 0,
                record.isZombieStop(),
                summary,
                record.getRootCause(),
                timeline);
    }
}
