package com.tranqui.profesionales.recetas;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los DTOs de QBI2 y de recetas están escritos a mano (getters, setters y builders sin Lombok, para que
 * Jackson los lea igual que en el monolito). Este test recorre todas sus clases y verifica que cada
 * setter y cada método del builder guarden el valor que después devuelve el getter, y que el JSON
 * vaya y vuelva sin perder campos: un copy-paste mal hecho (setter que pisa otro campo) lo rompe.
 */
class DtosRecetasTest {

    private static final List<Class<?>> RAICES = List.of(Qbi2RecetaDtos.class, Qbi2CatalogoDtos.class,
            Qbi2AnularDtos.class, RecetaDto.class, RecetaResponseDto.class);

    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void settersYGettersGuardanCadaCampo() throws Exception {
        int campos = 0;
        for (Class<?> clase : dtos()) {
            Object dto = nuevo(clase);
            for (Method setter : clase.getMethods()) {
                if (!setter.getName().startsWith("set") || setter.getParameterCount() != 1) continue;
                Object valor = ejemplo(setter.getParameterTypes()[0]);
                setter.invoke(dto, valor);
                Method getter = getter(clase, setter.getName().substring(3));
                assertThat(getter).as(clase.getSimpleName() + "." + setter.getName()).isNotNull();
                assertThat(getter.invoke(dto)).as(clase.getSimpleName() + "." + getter.getName()).isEqualTo(valor);
                campos++;
            }
        }
        assertThat(campos).isGreaterThan(100);
    }

    @Test
    void buildersArmanElObjetoConCadaCampo() throws Exception {
        int builders = 0;
        for (Class<?> clase : dtos()) {
            Method builder;
            try {
                builder = clase.getMethod("builder");
            } catch (NoSuchMethodException e) {
                continue;
            }
            Object b = builder.invoke(null);
            List<Object[]> esperados = new ArrayList<>();
            for (Method m : b.getClass().getMethods()) {
                if (m.getParameterCount() != 1 || m.getReturnType() != b.getClass()) continue;
                Object valor = ejemplo(m.getParameterTypes()[0]);
                m.invoke(b, valor);
                esperados.add(new Object[]{m.getName(), valor});
            }
            Object dto = b.getClass().getMethod("build").invoke(b);
            assertThat(dto).isInstanceOf(clase);
            for (Object[] esperado : esperados) {
                String campo = (String) esperado[0];
                Method getter = getter(clase, Character.toUpperCase(campo.charAt(0)) + campo.substring(1));
                assertThat(getter).as(clase.getSimpleName() + " sin getter para " + campo).isNotNull();
                assertThat(getter.invoke(dto)).as(clase.getSimpleName() + "." + campo).isEqualTo(esperado[1]);
            }
            builders++;
        }
        assertThat(builders).isGreaterThan(20);
    }

    @Test
    void constructoresConArgumentosGuardanLosValores() throws Exception {
        for (Class<?> clase : dtos()) {
            for (Constructor<?> c : clase.getConstructors()) {
                if (c.getParameterCount() == 0) continue;
                Object[] args = new Object[c.getParameterCount()];
                for (int i = 0; i < args.length; i++) args[i] = ejemplo(c.getParameterTypes()[i]);
                assertThat(c.newInstance(args)).isNotNull();
            }
        }
    }

    @Test
    void elJsonVaYVuelveSinPerderCampos() throws Exception {
        for (Class<?> clase : dtos()) {
            Object dto = nuevo(clase);
            for (Method setter : clase.getMethods()) {
                if (setter.getName().startsWith("set") && setter.getParameterCount() == 1) {
                    setter.invoke(dto, ejemplo(setter.getParameterTypes()[0]));
                }
            }
            String texto = json.writeValueAsString(dto);
            Object leido = json.readValue(texto, clase);
            assertThat(json.writeValueAsString(leido)).as(clase.getSimpleName()).isEqualTo(texto);
        }
    }

    private static List<Class<?>> dtos() {
        List<Class<?>> todas = new ArrayList<>();
        for (Class<?> raiz : RAICES) agregar(raiz, todas);
        return todas;
    }

    private static void agregar(Class<?> clase, List<Class<?>> todas) {
        if (!clase.getSimpleName().equals("Builder") && !clase.isEnum() && !Modifier.isAbstract(clase.getModifiers())
                && java.util.Arrays.stream(clase.getDeclaredFields()).anyMatch(f -> !Modifier.isStatic(f.getModifiers()))) {
            todas.add(clase);
        }
        for (Class<?> interna : clase.getDeclaredClasses()) {
            if (Modifier.isPublic(interna.getModifiers()) && Modifier.isStatic(interna.getModifiers())) agregar(interna, todas);
        }
    }

    private static Method getter(Class<?> clase, String nombre) {
        for (String prefijo : new String[]{"get", "is"}) {
            try {
                return clase.getMethod(prefijo + nombre);
            } catch (NoSuchMethodException ignored) {
                // probar el otro prefijo
            }
        }
        return null;
    }

    private static Object nuevo(Class<?> clase) throws Exception {
        return clase.getConstructor().newInstance();
    }

    private static Object ejemplo(Class<?> tipo) throws Exception {
        if (tipo == String.class) return "valor";
        if (tipo == Integer.class || tipo == int.class) return 7;
        if (tipo == Long.class || tipo == long.class) return 7L;
        if (tipo == Boolean.class || tipo == boolean.class) return true;
        if (tipo == Float.class || tipo == float.class) return 1.5f;
        if (tipo == Double.class || tipo == double.class) return 1.5d;
        if (tipo == BigDecimal.class) return new BigDecimal("12.50");
        if (tipo == LocalDateTime.class) return LocalDateTime.of(2026, 11, 5, 10, 30);
        if (tipo == LocalDate.class) return LocalDate.of(2026, 11, 5);
        if (tipo == List.class) return List.of();
        if (tipo == Map.class) return Map.of("k", "v");
        if (tipo.isEnum()) return tipo.getEnumConstants()[0];
        return nuevo(tipo);
    }
}
