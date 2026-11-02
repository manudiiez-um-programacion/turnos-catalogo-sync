package com.um.catalogsync.professional.infrastructure.persistence.adapter;

import com.um.catalogsync.professional.application.exception.UnknownCategoryException;
import com.um.catalogsync.professional.domain.model.Professional;
import com.um.catalogsync.professional.domain.model.ProfessionalPage;
import com.um.catalogsync.professional.domain.model.ProfessionalSearchCriteria;
import com.um.catalogsync.professional.domain.ports.out.ProfessionalRepository;
import com.um.catalogsync.professional.infrastructure.persistence.entity.ProfessionalEntity;
import com.um.catalogsync.professional.infrastructure.persistence.mapper.ProfessionalMapper;
import com.um.catalogsync.professional.infrastructure.persistence.repository.JpaProfessionalRepository;
import com.um.catalogsync.professional.infrastructure.persistence.specification.ProfessionalSpecifications;
import com.um.catalogsync.schedule.domain.ports.out.WeeklyScheduleRepository;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementa el puerto de salida con JPA.
 *
 * <p>Dos cosas lo diferencian del adapter de categorias.
 *
 * <p><b>Hace flush explicito en las escrituras.</b> No es por gusto: JPA
 * puede postergar el INSERT hasta el commit de la transaccion, y el commit
 * ocurre despues de que el metodo retorna, fuera del {@code try}. Sin el
 * flush, la violacion de FK estallaria al commitear y este
 * {@code catch} no la veria nunca, asi que la traduccion a
 * {@code UnknownCategoryException} no funcionaria. El flush fuerza que el
 * SQL salga mientras todavia estamos dentro del metodo.
 *
 * <p><b>Traduce la violacion de FK a una excepcion de dominio.</b> Sin eso,
 * quien sincroniza recibiria un error de integridad generico y tendria que
 * interpretar un mensaje de PostgreSQL para entender que falto.
 *
 * <p><b>Y resuelve la busqueda enteramente en PostgreSQL</b> (#15, #16):
 * filtros, orden, paginado y total los decide la base. Es la regla de §6.2
 * vista desde el unico lugar donde podria romperse: este adaptador es el que
 * toca los datos, y lo unico que toca es la copia local.
 *
 * <p>Con el filtro de disponibilidad son <b>dos consultas</b> en la misma
 * transaccion de solo lectura: primero los ids con agenda, por el puerto del
 * slice de horarios, y despues la pagina. El por que esta en
 * {@code ProfessionalSpecifications}. Que compartan transaccion no es
 * decorativo: el caso de uso la abre en REPEATABLE READ, asi que las dos ven
 * la misma instantanea y una sincronizacion que commitee en medio no puede
 * producir una pagina que mezcle dos versiones del catalogo.
 */
@Component
@RequiredArgsConstructor
public class JpaProfessionalRepositoryAdapter implements ProfessionalRepository {

    /**
     * Nombre de la FK en {@code V1__catalogo_local.sql}. Se compara por
     * nombre y no solo por el codigo de error porque esta tabla participa
     * de dos FK distintas: la suya hacia categoria, y la de horarios hacia
     * profesional. Las dos dan el mismo SQLSTATE pero significan cosas
     * opuestas — falta el padre, o los hijos impiden el borrado — y
     * confundirlas daria un mensaje enganoso.
     */
    private static final String FK_CATEGORIA = "fk_professional_category";

    /** {@code foreign_key_violation} en el estandar SQL. */
    private static final String SQLSTATE_FK = "23503";

    /**
     * El orden del contrato de busqueda: apellido, nombre, id.
     *
     * <p>Es una constante y no un parametro de la consulta a proposito. El
     * orden es parte de lo que la app KMP puede dar por sentado, asi que no se
     * recibe del cliente: no hay un {@code ?sort=} que pueda producir una
     * respuesta distinta, ni un nombre de columna llegando desde afuera.
     *
     * <p><b>El id no es decorativo, es lo que hace el orden total.</b> Con
     * solo apellido y nombre, dos homonimos quedan en un orden que PostgreSQL
     * no garantiza estable entre consultas; el que cae justo en el limite de
     * una pagina puede repetirse en la siguiente o desaparecer. Desempatar por
     * la clave primaria elimina el empate por construccion.
     *
     * <p>Ordena por {@code lastName} y {@code firstName}, que son los nombres
     * reales, y no por {@code searchName}: un listado alfabetico tiene que
     * seguir el apellido, y {@code searchName} empieza por el nombre de pila.
     */
    private static final Sort ORDEN_ESTABLE = Sort.by("lastName", "firstName", "id").ascending();

    private final JpaProfessionalRepository jpaRepository;
    private final ProfessionalMapper mapper;

    /**
     * El puerto de horarios, solo para el filtro de disponibilidad (#16).
     *
     * <p>Es una dependencia cruzada entre slices y esta permitida porque es
     * el <b>puerto de dominio</b> del otro slice y no su
     * {@code infrastructure}: es el mismo camino que ya usa
     * {@code GetCurrentAgendaUseCaseImpl} con tres puertos de tres slices. Lo
     * que no se podria es importar su {@code @Entity} para armar un
     * {@code JOIN}, que es justamente lo que esta dependencia evita.
     */
    private final WeeklyScheduleRepository horarios;

    @Override
    @Transactional
    public Professional upsert(Professional professional) {
        try {
            ProfessionalEntity guardado =
                    jpaRepository.saveAndFlush(mapper.toEntity(professional));
            return mapper.toDomain(guardado);
        } catch (DataIntegrityViolationException e) {
            throw traducir(e, professional.getCategoryId());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Professional> findById(Long id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Professional> findAll() {
        return mapper.toDomain(jpaRepository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public ProfessionalPage search(ProfessionalSearchCriteria criteria) {
        List<Long> conAgenda = List.of();
        if (criteria.filtraPorDisponibilidad()) {
            conAgenda = horarios.findProfessionalIdsWithAgendaIn(criteria.availability());
            if (conAgenda.isEmpty()) {
                // Corta aca por dos razones. La util: nadie tiene agenda en
                // ese dia o rango, asi que el resultado ya se conoce y la
                // segunda consulta no aportaria nada. La necesaria: un
                // IN () no es SQL valido, y Hibernate lo traduce a una
                // condicion siempre falsa dejando igual una consulta que no
                // hacia falta.
                return ProfessionalPage.vacia(criteria);
            }
        }

        Page<ProfessionalEntity> pagina =
                jpaRepository.findAll(
                        ProfessionalSpecifications.de(criteria, conAgenda),
                        PageRequest.of(criteria.page(), criteria.size(), ORDEN_ESTABLE));

        return new ProfessionalPage(
                mapper.toDomain(pagina.getContent()),
                criteria.page(),
                criteria.size(),
                pagina.getTotalElements());
    }

    @Override
    @Transactional
    public List<Professional> replaceAll(Collection<Professional> professionals) {
        jpaRepository.deleteAllInBatch();
        try {
            List<ProfessionalEntity> guardados =
                    jpaRepository.saveAll(mapper.toEntity(professionals));
            // Un solo flush para todo el lote, no uno por entidad.
            jpaRepository.flush();
            return mapper.toDomain(guardados);
        } catch (DataIntegrityViolationException e) {
            throw traducir(e, null);
        }
    }

    @Override
    @Transactional
    public long deleteByIdIn(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0L;
        }
        return jpaRepository.deleteByIdIn(ids);
    }

    /**
     * Convierte la violacion en {@code UnknownCategoryException} solo si es
     * la FK hacia categoria. Cualquier otra violacion se propaga tal cual:
     * inventarle un significado seria peor que dejarla pasar.
     *
     * @param categoryId la categoria involucrada si se conoce; {@code null}
     *     en operaciones de lote, donde no se sabe cual de los
     *     profesionales fallo
     */
    private RuntimeException traducir(DataIntegrityViolationException e, Long categoryId) {
        return esViolacionDe(e, FK_CATEGORIA)
                ? new UnknownCategoryException(categoryId, e)
                : e;
    }

    private static boolean esViolacionDe(Throwable error, String nombreDeLaFk) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql
                    && SQLSTATE_FK.equals(sql.getSQLState())
                    && sql.getMessage() != null
                    && sql.getMessage().contains(nombreDeLaFk)) {
                return true;
            }
            if (causa.getCause() == causa) {
                return false;
            }
        }
        return false;
    }
}
