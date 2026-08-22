package com.tranqui.app.repository;

import com.tranqui.app.model.InvoiceSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InvoiceSequenceRepository extends JpaRepository<InvoiceSequence, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM InvoiceSequence s WHERE s.puntoVenta = :ptoVta AND s.cbteTipo = :cbteTipo")
    Optional<InvoiceSequence> findByPuntoVentaAndCbteTipoForUpdate(@Param("ptoVta") Integer ptoVta, @Param("cbteTipo") Integer cbteTipo);

    Optional<InvoiceSequence> findByPuntoVentaAndCbteTipo(Integer puntoVenta, Integer cbteTipo);
}
