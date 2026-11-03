package com.tranqui.app.eventos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxRecord, Long> {

    Optional<OutboxRecord> findByEventId(UUID eventId);

    List<OutboxRecord> findByAggregateIdOrderByIdAsc(String aggregateId);

    @Query(value = "SELECT * FROM outbox WHERE published_at IS NULL ORDER BY id ASC LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxRecord> findPendientesConLock(@Param("limit") int limit);

    @Query("SELECT COUNT(o) FROM OutboxRecord o WHERE o.publishedAt IS NULL")
    long countPendientes();

    @Query("SELECT MIN(o.createdAt) FROM OutboxRecord o WHERE o.publishedAt IS NULL")
    OffsetDateTime findMinCreatedAtPendiente();

    @Modifying
    @Query(value = "DELETE FROM outbox WHERE published_at IS NOT NULL AND published_at < :limite", nativeQuery = true)
    int eliminarPublicadasAnterioresA(@Param("limite") OffsetDateTime limite);

    @Modifying
    @Query(value = "DELETE FROM eventos_procesados WHERE procesado_en < :limite", nativeQuery = true)
    int eliminarInboxAnterioresA(@Param("limite") OffsetDateTime limite);
}
