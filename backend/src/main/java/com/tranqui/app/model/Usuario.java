package com.tranqui.app.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false, length = 20)
    private Rol rol;

    @Column(name = "matricula", length = 50)
    private String matricula;

    @Column(name = "mp_access_token_encrypted", columnDefinition = "TEXT")
    private String mpAccessTokenEncrypted;

    @Column(name = "mp_user_id", length = 50)
    private String mpUserId;

    @Column(name = "telefono", length = 30)
    private String telefono;

    @Column(name = "titulo", length = 50)
    private String titulo;

    @Column(name = "especialidad", length = 100)
    private String specialty; // maps to specialty in frontend

    @Column(name = "cuit", length = 20)
    private String cuit;

    @Column(name = "precio", precision = 12, scale = 2)
    private java.math.BigDecimal precio;

    @Column(name = "tags", length = 255)
    private String tags; // comma separated tags e.g. "Ansiedad,Estrés"

    @Column(name = "color", length = 20)
    private String color;

    @Builder.Default
    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (fechaRegistro == null) {
            fechaRegistro = LocalDateTime.now();
        }
    }
}
