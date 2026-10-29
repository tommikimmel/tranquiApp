package com.tranqui.eventos.contratos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.eventos.EventObjectMapperFactory;
import com.tranqui.eventos.ValidadorEventos;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.RecordComponent;
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
        List<String> diferencias = new ArrayList<>();
        comparar(fixture(ruta), real, "$", diferencias);
        if (!diferencias.isEmpty()) {
            throw new AssertionError("La salida no cumple el contrato " + ruta + ":\n  " + String.join("\n  ", diferencias));
        }
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
        if (esperado.isObject()) {
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
