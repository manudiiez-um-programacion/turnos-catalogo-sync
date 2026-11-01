package com.um.catalogsync.shared.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.um.catalogsync.shared.infrastructure.AbstractPostgresIntegrationTest;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Fija que la API de este servicio sea de <b>solo lectura</b>.
 *
 * <p>Es un criterio de aceptacion de #18 y una regla del proyecto: la copia
 * local se escribe desde la sincronizacion con la catedra, que es la fuente
 * autoritativa, y nunca desde otro servicio por HTTP. Si alguien agrega un
 * {@code @PostMapping} —aunque sea "solo para probar"— esta prueba falla y hay
 * que discutirlo, en lugar de que aparezca una escritura en una API que el
 * contrato promete de solo lectura.
 *
 * <p><b>Por que se inspecciona el mapeo real y no el codigo fuente.</b> Un
 * {@code grep} por {@code @PostMapping} se saltea un
 * {@code @RequestMapping(method = POST)}, una ruta registrada por una
 * {@code RouterFunction} o un controlador heredado de una libreria.
 * Preguntarle a Spring que rutas quedaron registradas cubre todas esas formas,
 * porque es la misma tabla que usa para despachar.
 */
class ApiDeSoloLecturaTest extends AbstractPostgresIntegrationTest {

    /**
     * {@code HEAD} y {@code OPTIONS} no escriben nada: Spring las resuelve sola
     * para un mapeo {@code GET} y las necesita el preflight de CORS.
     */
    private static final Set<RequestMethod> LECTURA =
            Set.of(RequestMethod.GET, RequestMethod.HEAD, RequestMethod.OPTIONS);

    /**
     * Todos los mapeos del contexto, no solo los de los controladores.
     *
     * <p>Hay dos beans de este tipo: el de la aplicacion y el de los endpoints
     * de Actuator. Se revisan los dos a proposito —es un mapa y no un bean
     * unico— porque un endpoint de Actuator que escriba, como
     * {@code /actuator/shutdown}, seria tambien una escritura en esta API. Hoy
     * solo se exponen {@code health} e {@code info}, y esta prueba falla si eso
     * cambia sin que nadie lo note.
     */
    /**
     * La unica ruta que puede no restringir el metodo.
     *
     * <p>{@code /error} es de {@code BasicErrorController}, de Spring Boot, y
     * responde a cualquier metodo a proposito: cualquier peticion puede fallar,
     * incluida una que el servidor rechazo por el metodo. No es un endpoint de
     * esta API, no recibe cuerpo y no escribe nada, asi que se exceptua por
     * nombre y no relajando la regla.
     */
    private static final String RUTA_DE_ERROR = "/error";

    @Autowired
    private Map<String, RequestMappingHandlerMapping> mapeos;

    @Test
    @DisplayName("ningun endpoint acepta un metodo que escriba")
    void nadaEscribe() {
        for (RequestMappingInfo info : rutasRegistradas()) {
            Set<RequestMethod> metodos = info.getMethodsCondition().getMethods();

            assertThat(metodos)
                    .as("el endpoint %s declara un metodo de escritura", info)
                    .isSubsetOf(LECTURA);
        }
    }

    @Test
    @DisplayName("ningun endpoint queda sin restringir el metodo")
    void todosDeclaranSuMetodo() {
        // Un mapeo con la condicion de metodo vacia acepta POST, PUT y DELETE
        // sin que nadie lo haya escrito: pasaria la prueba de arriba porque el
        // conjunto vacio es subconjunto de cualquiera, y seria una escritura
        // abierta. Por eso se exige que cada ruta diga a que metodo responde.
        for (RequestMappingInfo info : rutasRegistradas()) {
            if (esLaRutaDeError(info)) {
                continue;
            }
            assertThat(info.getMethodsCondition().getMethods())
                    .as("el endpoint %s no restringe el metodo HTTP", info)
                    .isNotEmpty();
        }
    }

    private static boolean esLaRutaDeError(RequestMappingInfo info) {
        return info.getPathPatternsCondition() != null
                && info.getPathPatternsCondition().getPatternValues().contains(RUTA_DE_ERROR);
    }

    private java.util.List<RequestMappingInfo> rutasRegistradas() {
        return mapeos.values().stream()
                .flatMap(mapeo -> mapeo.getHandlerMethods().keySet().stream())
                .toList();
    }
}
