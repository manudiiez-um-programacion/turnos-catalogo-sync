package com.um.catalogsync.shared.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Este servicio como resource server: valida tokens y no emite ninguno.
 *
 * <p>Implementa lo decidido en el ADR 0002. Hay <b>dos cadenas de filtros</b>
 * porque hay dos identidades con dos secretos distintos:
 *
 * <ol>
 *   <li>{@value #RUTA_API_INTERNA} — la agenda vigente que consume el servicio
 *       de turnos (#18). Decodifica con el secreto interno y exige la
 *       autoridad {@value #AUTORIDAD_TURNOS}.
 *   <li>todo lo demas — la busqueda que consume la app KMP (#15, #16).
 *       Decodifica con el secreto del usuario final.
 * </ol>
 *
 * <p><b>Que gana con dos cadenas en lugar de una con dos reglas.</b> Cada
 * cadena tiene su propio decodificador, asi que los dos tipos de token no son
 * intercambiables por construccion: un token de usuario presentado en la API
 * interna no llega a la comprobacion de autoridad, falla antes, en la
 * verificacion de firma, porque esa cadena solo conoce el otro secreto. Es la
 * separacion que pide §9 sin depender de que nadie se olvide de escribir un
 * {@code hasAuthority}.
 *
 * <p><b>Denegado por defecto.</b> Las dos cadenas terminan en
 * {@code anyRequest().authenticated()}, asi que un endpoint nuevo queda cerrado
 * sin hacer nada: para abrirlo hay que agregarlo explicitamente aca. Es un
 * criterio de aceptacion de #17 y el orden correcto de los errores posibles:
 * olvidarse de proteger algo es grave, olvidarse de abrir algo se nota en la
 * primera prueba.
 *
 * <p><b>El JWT tecnico de la catedra no participa.</b> Vive en el slice
 * {@code integration} y se usa solo como credencial de salida hacia el
 * servicio central (§2 de la referencia). Ninguna de las dos cadenas lo
 * acepta: no esta firmado con ninguno de estos dos secretos, asi que un
 * intento de usarlo como credencial de entrada termina en 401. No hace falta
 * una regla que lo prohiba, lo prohibe la criptografia.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(ApiSecurityProperties.class)
public class SecurityConfig {

    /** Prefijo de la API que consume el servicio de turnos (#18). */
    public static final String RUTA_API_INTERNA = "/api/internal/**";

    /**
     * Autoridad que habilita la API interna.
     *
     * <p>Sin prefijo {@code ROLE_} a proposito: no es un rol de usuario, es la
     * identidad de un servicio. El claim lo emite el servicio de turnos, que
     * pone {@code authorities: ["SERVICE_TURNOS"]} en el token tecnico interno.
     */
    public static final String AUTORIDAD_TURNOS = "SERVICE_TURNOS";

    /** Lo unico publico sin token, junto con OpenAPI en local. */
    private static final String RUTA_HEALTH = "/actuator/health";

    private static final String[] RUTAS_OPENAPI = {
        "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    };

    private final ApiSecurityProperties propiedades;
    private final ProblemAuthenticationEntryPoint entradaNoAutenticada;
    private final ProblemAccessDeniedHandler accesoDenegado;

    /**
     * Si la documentacion OpenAPI esta encendida.
     *
     * <p>Se decide por la misma propiedad que enciende springdoc y no por el
     * perfil activo: si la documentacion esta apagada, esas rutas no existen y
     * abrirlas no tendria sentido; si esta encendida, tiene que poder leerse
     * sin token o no sirve para nada. Asi la regla de seguridad no puede
     * quedar desincronizada de la configuracion real. En el perfil
     * {@code local} viene en {@code true} y en los demas en {@code false}.
     */
    private final boolean documentacionPublicada;

    public SecurityConfig(
            ApiSecurityProperties propiedades,
            ProblemAuthenticationEntryPoint entradaNoAutenticada,
            ProblemAccessDeniedHandler accesoDenegado,
            @Value("${springdoc.api-docs.enabled:false}") boolean documentacionPublicada) {
        this.propiedades = propiedades;
        this.entradaNoAutenticada = entradaNoAutenticada;
        this.accesoDenegado = accesoDenegado;
        this.documentacionPublicada = documentacionPublicada;
    }

    /**
     * Cadena de la API interna: solo el servicio de turnos.
     *
     * <p>Va primera porque las cadenas se evaluan en orden y la segunda no
     * tiene {@code securityMatcher}: atrapa todo lo que llegue.
     */
    @Bean
    @Order(1)
    SecurityFilterChain apiInterna(HttpSecurity http) throws Exception {
        http.securityMatcher(RUTA_API_INTERNA);
        aplicarBase(http);
        http.authorizeHttpRequests(rutas -> rutas.anyRequest().hasAuthority(AUTORIDAD_TURNOS))
                .oauth2ResourceServer(
                        oauth2 ->
                                oauth2.jwt(
                                                jwt ->
                                                        jwt.decoder(
                                                                        decodificador(
                                                                                propiedades.internalJwt(),
                                                                                "JWT_INTERNAL_SECRET"))
                                                                .jwtAuthenticationConverter(
                                                                        conversorDeAutoridades("authorities")))
                                        .authenticationEntryPoint(entradaNoAutenticada)
                                        .accessDeniedHandler(accesoDenegado));
        return http.build();
    }

    /**
     * Cadena por defecto: la API que consume la app KMP y todo lo demas.
     *
     * <p>Publico solo {@value #RUTA_HEALTH} —lo necesita el healthcheck de
     * Docker Compose y cualquier orquestador, y no revela nada: en los perfiles
     * que no son {@code local} responde sin detalles— y, si esta encendida, la
     * documentacion OpenAPI. {@code /actuator/info} queda detras de token: el
     * criterio de #17 dice "publicos solo health y la documentacion", y de los
     * dos lados posibles del limite este es el barato.
     */
    @Bean
    @Order(2)
    SecurityFilterChain apiPublica(HttpSecurity http) throws Exception {
        aplicarBase(http);
        http.authorizeHttpRequests(
                        rutas -> {
                            rutas.requestMatchers(RUTA_HEALTH).permitAll();
                            if (documentacionPublicada) {
                                rutas.requestMatchers(RUTAS_OPENAPI).permitAll();
                            }
                            rutas.anyRequest().authenticated();
                        })
                .oauth2ResourceServer(
                        oauth2 ->
                                oauth2.jwt(
                                                jwt ->
                                                        jwt.decoder(
                                                                        decodificador(
                                                                                propiedades.userJwt(),
                                                                                "JWT_USER_SECRET"))
                                                                .jwtAuthenticationConverter(
                                                                        conversorDeAutoridades("auth")))
                                        .authenticationEntryPoint(entradaNoAutenticada)
                                        .accessDeniedHandler(accesoDenegado));
        return http.build();
    }

    /**
     * Lo que las dos cadenas comparten.
     *
     * <p><b>Sin sesion.</b> Un resource server no guarda estado entre
     * peticiones: la credencial viaja en cada una. Con sesiones habria dos
     * mecanismos de autenticacion conviviendo y el token dejaria de ser la
     * unica verdad.
     *
     * <p><b>CSRF apagado.</b> CSRF explota que el navegador adjunte la
     * credencial solo: con cookies pasa, con una cabecera
     * {@code Authorization} que el cliente tiene que poner a mano, no. Sin
     * sesiones ni cookies de autenticacion no hay superficie, y dejarlo
     * encendido solo rompe a los clientes.
     *
     * <p><b>CORS primero.</b> El {@code OPTIONS} de preflight llega sin
     * cabecera {@code Authorization} —el navegador no la manda nunca en el
     * preflight—, asi que si no se le permite pasar, la app KMP en navegador
     * recibe un 401 en una peticion que ni siquiera hizo.
     */
    private void aplicarBase(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(origenesPermitidos()))
                .csrf(csrf -> csrf.disable())
                .httpBasic(configuracion -> configuracion.disable())
                .formLogin(configuracion -> configuracion.disable())
                .logout(configuracion -> configuracion.disable())
                .anonymous(Customizer.withDefaults())
                .sessionManagement(
                        sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(
                        errores ->
                                errores.authenticationEntryPoint(entradaNoAutenticada)
                                        .accessDeniedHandler(accesoDenegado));
    }

    /**
     * Decodificador HS256 para uno de los dos secretos.
     *
     * <p>No es un {@code @Bean}: si hubiera dos {@code JwtDecoder} en el
     * contexto, la autoconfiguracion de Spring Boot no sabria cual usar y cada
     * cadena tendria que calificarlo. Como cada cadena recibe el suyo
     * explicitamente, el problema no existe.
     *
     * <p>Se validan <b>firma, vigencia y emisor</b>, los tres: la firma la hace
     * Nimbus con el secreto, {@link JwtTimestampValidator} rechaza vencidos y
     * los emitidos en el futuro, y {@link JwtIssuerValidator} exige que
     * {@code iss} sea el servicio de turnos. Sin el validador de emisor, un
     * token firmado con el mismo secreto por cualquier otro componente del
     * proyecto serviria aca.
     */
    private NimbusJwtDecoder decodificador(ApiSecurityProperties.Jwt configuracion, String variable) {
        SecretKeySpec clave =
                new SecretKeySpec(
                        configuracion.secretoVerificado(variable).getBytes(StandardCharsets.UTF_8),
                        "HmacSHA256");

        NimbusJwtDecoder decodificador = NimbusJwtDecoder.withSecretKey(clave).build();

        OAuth2TokenValidator<Jwt> validaciones =
                new DelegatingOAuth2TokenValidator<>(
                        new JwtTimestampValidator(), new JwtIssuerValidator(configuracion.issuer()));
        decodificador.setJwtValidator(validaciones);

        return decodificador;
    }

    /**
     * De que claim salen las autoridades de cada identidad.
     *
     * <p>Los dos nombres estan fijados por quien emite el token, no por este
     * servicio: el token tecnico interno trae {@code authorities} y el del
     * usuario final trae {@code auth}, que es el claim que usa el modelo de
     * usuario de JHipster con el que §3.2 exige ser compatible.
     *
     * <p>Sin prefijo: los valores ya vienen completos ({@code ROLE_USER},
     * {@value #AUTORIDAD_TURNOS}). Con el {@code ROLE_} que Spring agrega por
     * defecto quedarian {@code ROLE_ROLE_USER}.
     */
    private JwtAuthenticationConverter conversorDeAutoridades(String claim) {
        JwtGrantedAuthoritiesConverter autoridades = new JwtGrantedAuthoritiesConverter();
        autoridades.setAuthoritiesClaimName(claim);
        autoridades.setAuthorityPrefix("");

        JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(autoridades);
        return conversor;
    }

    /**
     * CORS restringido a los origenes configurados, nunca {@code "*"} (§9).
     *
     * <p>Lista exacta de origenes y no un patron: con
     * {@code allowedOriginPatterns} un comodin se cuela sin que nadie lo note.
     * Que {@code "*"} este prohibido lo verifica
     * {@link ApiSecurityProperties}, al arrancar.
     *
     * <p>Solo los metodos de lectura: este servicio no expone ninguna
     * escritura —la copia local se escribe desde la sincronizacion, no por
     * HTTP— asi que habilitar {@code POST} o {@code DELETE} seria ofrecer algo
     * que no existe.
     */
    @Bean
    CorsConfigurationSource origenesPermitidos() {
        CorsConfiguration configuracion = new CorsConfiguration();
        configuracion.setAllowedOrigins(propiedades.origenesCors());
        configuracion.setAllowedMethods(List.of(HttpMethod.GET.name(), HttpMethod.OPTIONS.name()));
        configuracion.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id"));
        configuracion.setMaxAge(java.time.Duration.ofMinutes(30));

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/**", configuracion);
        return fuente;
    }
}
