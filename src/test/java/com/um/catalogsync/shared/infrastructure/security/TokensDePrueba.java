package com.um.catalogsync.shared.infrastructure.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

/**
 * Fabrica tokens HS256 reales para las pruebas de seguridad.
 *
 * <p><b>Por que se firman de verdad y no se usa {@code jwt()} de
 * spring-security-test.</b> Ese ayudante inyecta una autenticacion ya
 * construida y se saltea el decodificador, que es justamente lo que estas
 * pruebas tienen que ejercitar: que la firma se verifique con el secreto
 * correcto, que un token vencido se rechace y que el token de una identidad no
 * sirva en la cadena de la otra. Con un token simulado los cuatro casos del
 * criterio de aceptacion de #17 pasarian sin probar nada.
 *
 * <p>Los secretos y el emisor son los de {@code application-test.yaml}. Si se
 * cambian alla, hay que cambiarlos aca: es duplicacion a proposito, porque el
 * valor tiene que ser visible en la prueba para que se entienda cual token se
 * esta fabricando.
 */
final class TokensDePrueba {

    static final String SECRETO_USUARIO = "secreto-de-prueba-del-usuario-final-0123456789";
    static final String SECRETO_INTERNO = "secreto-de-prueba-del-servicio-de-turnos-9876543210";
    static final String EMISOR = "turnos-reservas-service";

    /** Cualquier secreto valido que no sea ninguno de los dos configurados. */
    static final String SECRETO_AJENO = "secreto-de-otro-firmante-que-no-corresponde-000";

    private TokensDePrueba() {}

    /** Token de usuario final, como lo emitiria el servicio de turnos. */
    static String deUsuario() {
        return firmar(SECRETO_USUARIO, claims("usuario.final", EMISOR, List.of("ROLE_USER"), "auth", 1));
    }

    /** Token tecnico interno con la autoridad que habilita la API interna. */
    static String deServicioDeTurnos() {
        return firmar(
                SECRETO_INTERNO,
                claims(
                        "turnos-reservas-service",
                        EMISOR,
                        List.of(SecurityConfig.AUTORIDAD_TURNOS),
                        "authorities",
                        1));
    }

    /**
     * Token tecnico interno bien formado pero sin la autoridad.
     *
     * <p>Es el cuarto caso del criterio de #17: la firma esta bien, el emisor
     * esta bien, no esta vencido, y aun asi no habilita la ruta. Tiene que dar
     * 403 y no 401, porque la accion a tomar es distinta.
     */
    static String deServicioSinAutoridad() {
        return firmar(
                SECRETO_INTERNO,
                claims("otro-servicio", EMISOR, List.of("SERVICE_OTRO"), "authorities", 1));
    }

    /** Token de usuario vencido hace una hora. */
    static String deUsuarioVencido() {
        return firmar(SECRETO_USUARIO, claims("usuario.final", EMISOR, List.of("ROLE_USER"), "auth", -1));
    }

    /** Token de usuario firmado con un secreto que este servicio no conoce. */
    static String deUsuarioConOtraFirma() {
        return firmar(SECRETO_AJENO, claims("usuario.final", EMISOR, List.of("ROLE_USER"), "auth", 1));
    }

    /** Token de usuario bien firmado pero emitido por otro. */
    static String deUsuarioConOtroEmisor() {
        return firmar(
                SECRETO_USUARIO,
                claims("usuario.final", "https://otro-emisor.example", List.of("ROLE_USER"), "auth", 1));
    }

    private static JWTClaimsSet claims(
            String sujeto, String emisor, List<String> autoridades, String claimDeAutoridades, int horas) {

        Instant ahora = Instant.now();
        return new JWTClaimsSet.Builder()
                .subject(sujeto)
                .issuer(emisor)
                .issueTime(Date.from(ahora.minus(1, ChronoUnit.MINUTES)))
                .expirationTime(Date.from(ahora.plus(horas, ChronoUnit.HOURS)))
                .claim(claimDeAutoridades, autoridades)
                .build();
    }

    private static String firmar(String secreto, JWTClaimsSet claims) {
        try {
            SignedJWT token = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            token.sign(new MACSigner(secreto.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            return token.serialize();
        } catch (JOSEException error) {
            throw new IllegalStateException("No se pudo firmar el token de prueba", error);
        }
    }
}
