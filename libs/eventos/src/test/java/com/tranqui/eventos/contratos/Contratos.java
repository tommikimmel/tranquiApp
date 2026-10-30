package com.tranqui.eventos.contratos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tranqui.eventos.EventObjectMapperFactory;
import com.tranqui.eventos.ValidadorEventos;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Fase 10, T2: contratos entre servicios. Cada ruta interna y cada evento tiene un JSON canónico en
 * {@code contratos/} (este test-jar). El que produce verifica que su salida tiene la misma forma (mismos campos y
 * tipos de JSON); el que consume lo lee con su record y lo valida. Si un lado cambia un campo, se rompe el test del
 * lado que no se actualizó.
 */
public final class Contratos {

    public static final ObjectMapper MAPPER = EventObjectMapperFactory.create();

    private Contratos() {}

    /** El JSON canónico, por ejemplo {@code fixture("internas/turnos/turnos.json")}. */
    public static JsonNode fixture(String ruta) {
        try (InputStream in = Contratos.class.getResourceAsStream("/contratos/" + ruta)) {
            if (in == null) {
                throw new AssertionError("No existe el contrato contratos/" + ruta);
            }
            return MAPPER.readTree(in);
        } catch (IOException e) {
            throw new AssertionError("Contrato ilegible: " + ruta, e);
        }
    }

    /** Lado productor: lo que devuelve tiene los mismos campos con los mismos tipos de JSON que el contrato. */
    public static void mismaForma(String ruta, JsonNode real) {
        if (Boolean.getBoolean("contratos.generar") && Contratos.class.getResource("/contratos/" + ruta) == null) {
            generar(ruta, real);
            return;
        }
        List<String> diferencias = new ArrayList<>();
        comparar(fixture(ruta), real, "$", diferencias);
        if (!diferencias.isEmpty()) {
            throw new AssertionError("La salida no cumple el contrato " + ruta + ":\n  " + String.join("\n  ", diferencias));
        }
    }

    /** Con {@code -Dcontratos.generar=true}: escribe el contrato que falta en libs/eventos a partir de la salida real. */
    private static void generar(String ruta, JsonNode real) {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("libs/eventos/src/test/resources"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new AssertionError("No encuentro libs/eventos para generar " + ruta);
        }
        Path archivo = dir.resolve("libs/eventos/src/test/resources/contratos").resolve(ruta);
        try {
            Files.createDirectories(archivo.getParent());
            Files.writeString(archivo, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(recortar(real)) + "\n");
        } catch (IOException e) {
            throw new AssertionError("No pude escribir " + archivo, e);
        }
    }

    /** Una lista queda con un solo elemento, el que trae más campos con valor. */
    private static JsonNode recortar(JsonNode n) {
        if (n.isArray()) {
            ArrayNode uno = MAPPER.createArrayNode();
            JsonNode mejor = null;
            for (JsonNode e : n) {
                if (mejor == null || conValor(e) > conValor(mejor)) {
                    mejor = e;
                }
            }
            return mejor == null ? uno : uno.add(recortar(mejor));
        }
        if (n.isObject()) {
            ObjectNode copia = MAPPER.createObjectNode();
            n.fields().forEachRemaining(c -> copia.set(c.getKey(), recortar(c.getValue())));
            return copia;
        }
        return n;
    }

    private static boolean esMapaPorId(JsonNode n) {
        if (!n.isObject() || n.isEmpty()) {
            return false;
        }
        for (Iterator<String> it = n.fieldNames(); it.hasNext(); ) {
            if (!it.next().matches("-?\\d+")) {
                return false;
            }
        }
        return true;
    }

    private static long conValor(JsonNode n) {
        long total = 0;
        for (JsonNode v : n) {
            total += v.isNull() ? 0 : 1;
        }
        return total;
    }

    public static void mismaForma(String ruta, String json) {
        try {
            mismaForma(ruta, MAPPER.readTree(json));
        } catch (IOException e) {
            throw new AssertionError("Salida ilegible para " + ruta, e);
        }
    }

    /**
     * Lado consumidor: lee el contrato con el record del que consume, sin perder ningún campo (todo componente del
     * record llega con valor) y pasando las validaciones del record.
     */
    public static <T> T leer(String ruta, Class<T> tipo) {
        JsonNode nodo = fixture(ruta);
        JsonNode muestra = nodo.isArray() ? nodo.get(0) : nodo;
        T valor;
        try {
            valor = MAPPER.treeToValue(muestra, tipo);
        } catch (IOException e) {
            throw new AssertionError(tipo.getSimpleName() + " no puede leer el contrato " + ruta, e);
        }
        sinNulos(valor, tipo.getSimpleName());
        ValidadorEventos.problemas(valor).ifPresent(p -> {
            throw new AssertionError("El contrato " + ruta + " no pasa las validaciones de " + tipo.getSimpleName() + ": " + p);
        });
        return valor;
    }

    private static void sinNulos(Object valor, String donde) {
        if (valor == null || !valor.getClass().isRecord()) {
            return;
        }
        for (RecordComponent c : valor.getClass().getRecordComponents()) {
            Object v;
            try {
                v = c.getAccessor().invoke(valor);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            if (v == null) {
                throw new AssertionError(donde + "." + c.getName() + " no está en el contrato (el que produce no lo manda)");
            }
            if (v instanceof List<?> lista && !lista.isEmpty()) {
                sinNulos(lista.get(0), donde + "." + c.getName() + "[0]");
            } else {
                sinNulos(v, donde + "." + c.getName());
            }
        }
    }

    private static void comparar(JsonNode esperado, JsonNode real, String camino, List<String> diferencias) {
        if (real == null || real.isMissingNode()) {
            diferencias.add(camino + ": falta");
            return;
        }
        // null en el contrato: el campo puede venir vacío, solo se exige que esté.
        if (esperado.isNull() || real.isNull()) {
            return;
        }
        if (esperado.getNodeType() != real.getNodeType()) {
            diferencias.add(camino + ": se esperaba " + esperado.getNodeType() + " y vino " + real.getNodeType());
            return;
        }
        if (esMapaPorId(esperado)) {
            // Un mapa por id (por ejemplo, no leídos por paciente): las claves dependen de los datos, se comparan los valores.
            JsonNode muestra = esperado.elements().next();
            for (Iterator<String> it = real.fieldNames(); it.hasNext(); ) {
                String clave = it.next();
                if (!clave.matches("-?\\d+")) {
                    diferencias.add(camino + "." + clave + ": se esperaba un id como clave");
                } else {
                    comparar(muestra, real.get(clave), camino + "{}", diferencias);
                }
            }
        } else if (esperado.isObject()) {
            for (Iterator<String> it = esperado.fieldNames(); it.hasNext(); ) {
                String campo = it.next();
                comparar(esperado.get(campo), real.get(campo), camino + "." + campo, diferencias);
            }
            for (Iterator<String> it = real.fieldNames(); it.hasNext(); ) {
                String campo = it.next();
                if (!esperado.has(campo)) {
                    diferencias.add(camino + "." + campo + ": no está en el contrato");
                }
            }
        } else if (esperado.isArray() && !esperado.isEmpty() && !real.isEmpty()) {
            for (JsonNode elemento : real) {
                comparar(esperado.get(0), elemento, camino + "[]", diferencias);
            }
        }
    }

}
