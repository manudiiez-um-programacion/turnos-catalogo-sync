package com.um.catalogsync.shared.infrastructure.client;

import com.um.catalogsync.shared.infrastructure.client.dto.ProblemDto;
import org.springframework.web.client.RestClient;

/**
 * Lee el {@code code} funcional de una respuesta de error de la catedra.
 *
 * <p>Vive en {@code shared} porque el formato es transversal: §13 lo define
 * una vez para todos los endpoints, y los clientes de los distintos slices
 * —el snapshot hoy, los que vengan despues— tienen que interpretarlo igual.
 * Que el adaptador de {@code sync} importe este {@code infrastructure} no
 * rompe la regla de que un slice no importa el {@code infrastructure} de
 * otro: {@code shared} es, por definicion del ADR, el slice de los
 * transversales sin dueño de negocio, y el manejo de
 * {@code application/problem+json} es una de las responsabilidades que lista.
 * Un {@code ProblemReader} por slice serian cuatro lecturas del mismo
 * formato que pueden divergir.
 *
 * <p><b>Tolerante por diseño.</b> Nunca lanza y nunca devuelve basura: si el
 * cuerpo no es un problem+json, esta vacio, es HTML o es cualquier otra
 * cosa, devuelve {@code null}. No es laxitud: un error de la catedra no
 * tiene por que venir con el cuerpo que la referencia documenta —un 503 de
 * un proxy, el catch-all de frontend que responde HTML (verificado en
 * #11)— y en ese momento ya estamos manejando un error. Si el intento de
 * leer el {@code code} fallara con su propia excepcion, el error real
 * quedaria tapado por un problema de parseo.
 *
 * <p>Clase estatica y sin estado, no un {@code @Component}: no tiene
 * colaboradores que inyectar. La deserializacion la hace el propio
 * {@link RestClient} con sus conversores, asi que tampoco hace falta un
 * {@code ObjectMapper} propio —que seria una segunda configuracion de
 * Jackson conviviendo con la del cliente.
 */
public final class ProblemReader {

    private ProblemReader() {
    }

    /**
     * El {@code code} del cuerpo, o {@code null} si no hay uno utilizable.
     *
     * <p>Consume el cuerpo de la respuesta, asi que se llama una sola vez y
     * solo en el camino de error: despues de esto el cuerpo ya no se puede
     * deserializar como la respuesta exitosa.
     *
     * @param respuesta la respuesta de error, todavia sin leer
     */
    public static String codeOf(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse
            respuesta) {
        try {
            ProblemDto problema = respuesta.bodyTo(ProblemDto.class);
            return problema == null ? null : problema.code();
        } catch (RuntimeException noEsUnProblemJson) {
            // Se descarta a proposito y sin log: quien llama esta por lanzar
            // una excepcion que ya nombra el estado HTTP y la ruta, que es la
            // informacion que sirve. Un warn aca agregaria ruido en cada 503.
            return null;
        }
    }
}
