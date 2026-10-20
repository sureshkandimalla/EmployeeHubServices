package com.employeehub.repository;

import com.employeehub.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByEntityTypeAndEntityId(String entityType, Long entityId);

    // Powers the grid-level "does this row already have a document, and if
    // so which one" indicator — one query for every row on the page instead
    // of a fetch per row.
    List<Document> findByEntityType(String entityType);

    // Documents whose Valid Until falls inside [today, cutoff] and haven't
    // been alerted on yet — see NotificationService#checkExpiringDocuments.
    // Bounded below by "today" deliberately: an unbounded "validUntil <=
    // cutoff" would also match every already-long-expired historical
    // document already in the database (e.g. a superseded old passport) the
    // very first time this job runs, flooding HR with stale alerts for
    // things already resolved. Only documents newly entering the lead-time
    // window from here on get caught — a known limitation for anything that
    // expired before this feature existed.
    List<Document> findByEntityTypeAndValidUntilBetweenAndExpirationAlertSentAtIsNull(
            String entityType, LocalDate from, LocalDate to);
}
