package com.um.catalogsync.shared.infrastructure.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * El documento OpenAPI que este servicio publica.
 *
 * <p>Declara las <b>dos</b> identidades del ADR 0002 como esquemas de seguridad
 * separados, y no un unico "bearerAuth". Es lo que hace que el documento diga
 * la verdad: cada operacion referencia el esquema que realmente exige, asi que
 * quien lee el contrato ve que el JWT del usuario final no abre la API interna
 * sin tener que leer el codigo de {@code SecurityConfig}.
 *
 * <p>La version del documento es la del <b>contrato</b>, no la del artefacto.
 * Sube cuando cambia lo que el otro lado tiene que saber: un campo nuevo
 * opcional no la mueve, quitar un campo, renombrarlo o cambiar un
 * {@code code} si. El criterio se documenta en #20.
 */
@Configuration
public class OpenApiConfig {

    /** Esquema del JWT tecnico interno entre los dos backends del proyecto. */
    public static final String SEGURIDAD_INTERNA = "jwt-interno";

    /** Esquema del JWT del usuario final que emite el servicio de turnos. */
    public static final String SEGURIDAD_USUARIO = "jwt-usuario";

    private static final String VERSION_DEL_CONTRATO = "1.0.0";

    @Bean
    OpenAPI contratoDelServicio() {
        return new OpenAPI()
                // Un servidor relativo y declarado a mano. Sin esto springdoc
                // deduce la URL del origen que atendio la peticion, que es un
                // dato del entorno —y en las pruebas, un puerto aleatorio—:
                // terminaria dentro del contrato versionado y lo volveria
                // distinto en cada generacion. La URL real depende del
                // despliegue y esta externalizada.
                .servers(
                        List.of(
                                new Server()
                                        .url("/")
                                        .description("El origen donde este servicio esta desplegado")))
                .info(
                        new Info()
                                .title("Catalogo y sincronizacion")
                                .version(VERSION_DEL_CONTRATO)
                                .description(
                                        """
                                        Copia local del catalogo de la catedra: categorias, \
                                        profesionales y horarios semanales.

                                        Dos APIs con dos audiencias y dos credenciales \
                                        distintas. La agenda vigente, bajo /api/internal, la \
                                        consume el servicio de turnos con el JWT tecnico \
                                        interno del proyecto y la autoridad SERVICE_TURNOS; es \
                                        la unica via por la que ese servicio accede a estos \
                                        datos (§3). La busqueda de profesionales la consume la \
                                        app KMP con el JWT del usuario final.

                                        Toda la API es de solo lectura: la copia local se \
                                        escribe desde la sincronizacion con la catedra, nunca \
                                        por HTTP.

                                        Los errores vienen en application/problem+json con un \
                                        code estable. Los clientes deciden por status y code, \
                                        nunca por el texto de detail (§13).\
                                        """))
                .components(
                        new Components()
                                .addSecuritySchemes(SEGURIDAD_INTERNA, esquemaBearer(
                                        "JWT tecnico interno del proyecto, emitido por el servicio de"
                                                + " turnos y firmado con JWT_INTERNAL_SECRET. Requiere"
                                                + " la autoridad SERVICE_TURNOS. No es el JWT tecnico"
                                                + " de la catedra, que no se acepta como credencial de"
                                                + " entrada."))
                                .addSecuritySchemes(SEGURIDAD_USUARIO, esquemaBearer(
                                        "JWT del usuario final, emitido por el servicio de turnos al"
                                                + " iniciar sesion y firmado con JWT_USER_SECRET.")));
    }

    private SecurityScheme esquemaBearer(String descripcion) {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description(descripcion);
    }
}
