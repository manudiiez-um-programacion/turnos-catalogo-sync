package com.um.catalogsync.shared.infrastructure.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion de seguridad de la API propia, toda externalizada (§9).
 *
 * <p>Son dos identidades distintas y cada una tiene su secreto, que es el
 * punto de la decision D4 (ADR 0002): el JWT del usuario final que emite el
 * servicio de turnos, y el JWT tecnico interno con el que ese mismo servicio
 * llama a la agenda vigente. Firmarlos con secretos <b>distintos</b> hace que
 * la separacion que pide §9 —un token no sirve para los endpoints de la otra
 * identidad— sea criptografica: un token de usuario presentado en la API
 * interna no falla una comprobacion de claims que alguien pueda olvidarse de
 * escribir, falla la verificacion de firma.
 *
 * <p>Ninguno de los dos es el JWT tecnico de la catedra. Ese vive en el slice
 * {@code integration}, se usa solo como credencial de <b>salida</b> hacia el
 * servicio central y no autentica a nadie contra este servicio.
 *
 * <p><b>Por que aca si se falla al arrancar y en
 * {@code CatedraApiProperties} no.</b> Esa clase tolera configuracion vacia a
 * proposito, porque el alta de la cuenta tecnica es un tramite manual que se
 * hace una sola vez y el contenedor tiene que poder levantar antes de que
 * exista. Estos secretos no son un tramite pendiente: los genera quien
 * despliega, con un comando, y sin ellos la API no puede validar
 * absolutamente nada. Un servicio arriba que no sabe verificar tokens es peor
 * que un servicio que no arranca, asi que falta de secreto es error de
 * arranque, con un mensaje que nombra la variable.
 *
 * @param userJwt validacion del JWT del usuario final
 * @param internalJwt validacion del JWT tecnico interno entre servicios
 * @param corsAllowedOrigins origenes exactos permitidos para CORS
 */
@ConfigurationProperties(prefix = "api.security")
public record ApiSecurityProperties(Jwt userJwt, Jwt internalJwt, List<String> corsAllowedOrigins) {

    /** Largo minimo en bytes de un secreto HS256: 256 bits. */
    public static final int LARGO_MINIMO_SECRETO = 32;

    public ApiSecurityProperties {
        corsAllowedOrigins = corsAllowedOrigins == null ? List.of() : List.copyOf(corsAllowedOrigins);
        rechazarComodinDeCors(corsAllowedOrigins);
    }

    /**
     * CORS nunca con {@code "*"}: lo prohibe §9 y lo repite la regla del
     * proyecto.
     *
     * <p>Se valida al construir en lugar de confiar en que nadie lo escriba:
     * la variable la completa quien despliega, y {@code "*"} es exactamente lo
     * que uno pone cuando tiene apuro y algo no funciona. Que el arranque
     * falle ahi mismo es mas barato que descubrirlo en la revision de
     * seguridad.
     */
    private static void rechazarComodinDeCors(List<String> origenes) {
        if (origenes.stream().anyMatch(origen -> "*".equals(origen.strip()))) {
            throw new IllegalArgumentException(
                    "CORS_ALLOWED_ORIGINS no puede ser \"*\": §9 exige configurar CORS"
                            + " segun los clientes permitidos. Listar los origenes exactos,"
                            + " separados por coma.");
        }
    }

    /** Los origenes, ya sin espacios ni entradas vacias. */
    public List<String> origenesCors() {
        return corsAllowedOrigins.stream().map(String::strip).filter(origen -> !origen.isEmpty()).toList();
    }

    /** Oculta los dos secretos: un objeto de propiedades acaba en un log. */
    @Override
    public String toString() {
        return "ApiSecurityProperties[userJwt=%s, internalJwt=%s, corsAllowedOrigins=%s]"
                .formatted(userJwt, internalJwt, corsAllowedOrigins);
    }

    /**
     * Lo que hace falta para validar una de las dos clases de token.
     *
     * @param secret secreto compartido con el servicio de turnos, HS256
     * @param issuer emisor exigido en el claim {@code iss}
     */
    public record Jwt(String secret, String issuer) {

        /**
         * Devuelve el secreto, o falla nombrando la variable que falta.
         *
         * <p>El mensaje incluye el comando que genera uno: un error de
         * arranque que dice "falta tal variable" sin decir como completarla
         * manda a quien despliega a buscar en la documentacion.
         *
         * @param variable nombre de la variable de entorno, para el mensaje
         */
        public String secretoVerificado(String variable) {
            if (secret == null || secret.isBlank()) {
                throw new IllegalStateException(
                        ("Falta %s. Sin ese secreto este servicio no puede validar ningun"
                                        + " token y toda la API quedaria inaccesible."
                                        + " Generar uno con: openssl rand -base64 48")
                                .formatted(variable));
            }
            if (secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                    < LARGO_MINIMO_SECRETO) {
                throw new IllegalStateException(
                        ("%s tiene menos de %d bytes y HS256 exige 256 bits. Generar uno"
                                        + " con: openssl rand -base64 48")
                                .formatted(variable, LARGO_MINIMO_SECRETO));
            }
            return secret;
        }

        /** Oculta el secreto. Ver {@code Secret} para el mismo criterio. */
        @Override
        public String toString() {
            return "Jwt[secret=***, issuer=%s]".formatted(issuer);
        }
    }
}
