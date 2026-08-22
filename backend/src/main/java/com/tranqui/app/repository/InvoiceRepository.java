package com.tranqui.app.repository;

import com.tranqui.app.model.Invoice;
import com.tranqui.app.model.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    Optional<Invoice> findByPaymentId(Long paymentId);
    Optional<Invoice> findByPuntoVentaAndCbteTipoAndCbteNumero(Integer puntoVenta, Integer cbteTipo, Long cbteNumero);
    List<Invoice> findByStatus(InvoiceStatus status);

    @Query("SELECT i FROM Invoice i WHERE i.status = 'FAILED' ORDER BY i.createdAt DESC")
    List<Invoice> findFailedInvoices();

    @Query("SELECT i FROM Invoice i ORDER BY i.createdAt DESC")
    List<Invoice> findAllOrderByCreatedAtDesc();

    @Query("SELECT i FROM Invoice i WHERE i.paymentId IN :paymentIds ORDER BY i.createdAt DESC")
    List<Invoice> findByPaymentIdIn(@Param("paymentIds") List<Long> paymentIds);
}
