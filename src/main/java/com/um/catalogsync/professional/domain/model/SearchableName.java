package com.um.catalogsync.professional.domain.model;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normaliza nombres para la busqueda parcial insensible a mayusculas y
 * acentos (#15).
 *
 * <p>Vive en el dominio y no en el mapper porque define la semantica de la
 * busqueda, no un detalle de almacenamiento: la misma regla tiene que
 * aplicarse al guardar el nombre y al normalizar el termino que busca el
 * usuario. Si las dos normalizaciones no coincidieran, buscar "gomez" no
 * encontraria a "Gómez" aunque el indice estuviera perfecto.
 *
 * <p>Es Java puro y estatico: una funcion, sin estado ni framework, que se
 * puede probar sola.
 */
public final class SearchableName {

    /** Marcas diacriticas que deja suelta la descomposicion NFD. */
    private static final Pattern DIACRITICOS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private static final Pattern ESPACIOS = Pattern.compile("\\s+");

    private SearchableName() {
    }

    /**
     * Nombre completo normalizado, que es lo que se guarda en la columna
     * {@code search_name} y lo que el indice de trigramas indexa.
     */
    public static String of(String firstName, String lastName) {
        String completo = ((firstName == null ? "" : firstName)
                + " "
                + (lastName == null ? "" : lastName));
        return normalize(completo);
    }

    /**
     * Normaliza un texto cualquiera con la misma regla: minusculas, sin
     * acentos y con los espacios colapsados.
     *
     * <p>La descomposicion NFD separa cada letra de su acento y despues se
     * descartan los acentos, asi que "Gómez" queda "gomez" y "Núñez" queda
     * "nunez". La ñ pierde la tilde a proposito: es el mismo criterio que
     * usa {@code unaccent} de PostgreSQL, y es lo que un usuario espera al
     * escribir "nunez" en un buscador.
     *
     * @return el texto normalizado, o cadena vacia si entra {@code null}
     */
    public static String normalize(String texto) {
        if (texto == null) {
            return "";
        }
        String descompuesto = Normalizer.normalize(texto, Normalizer.Form.NFD);
        String sinAcentos = DIACRITICOS.matcher(descompuesto).replaceAll("");
        return ESPACIOS.matcher(sinAcentos.toLowerCase(Locale.ROOT).trim()).replaceAll(" ");
    }
}
