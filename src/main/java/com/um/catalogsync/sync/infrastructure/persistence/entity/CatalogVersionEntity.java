package com.um.catalogsync.sync.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Representacion JPA de la version local.
 *
 * <p>Tiene {@code @Id} aunque conceptualmente no haya identidad que elegir:
 * la tabla guarda un unico valor, no una coleccion. El id existe para que
 * el CHECK {@code id = 1} de la migracion pueda hacer imposible una segunda
 * fila. Es la base la que sostiene la invariante, no el codigo.
 */
@Entity
@Table(name = "catalog_version")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogVersionEntity {

    /**
     * El unico id que la tabla acepta, por el CHECK
     * {@code ck_catalog_version_single_row}.
     */
    public static final short ID_UNICA = 1;

    @Id
    @Column(name = "id", nullable = false)
    private Short id;

    @Column(name = "applied_version", nullable = false)
    private long appliedVersion;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;

    /**
     * El origen como texto, por el mismo criterio que en
     * {@code WeeklyScheduleEntity}: la entidad JPA no lleva tipos de
     * dominio, y la conversion pasa por el mapper en lugar de por
     * {@code Enum.valueOf} dentro de Hibernate.
     *
     * <p>{@code length = 16} acompaña al {@code VARCHAR(16)} de la
     * migracion, que ademas tiene un CHECK con los dos literales validos.
     */
    @Column(name = "source", nullable = false, length = 16)
    private String source;
}
