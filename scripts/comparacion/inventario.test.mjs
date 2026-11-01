// Tests del inventario de la Fase 11: node --test scripts/comparacion/inventario.test.mjs
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { antARegex, destinos, endpointsDe, llamadasFrontend, reglasGateway } from './inventario.mjs'

test('los patrones Ant del gateway', () => {
  assert.ok(antARegex('/api/turnos/**').test('/api/turnos/1/cancelar'))
  assert.ok(antARegex('/api/turnos/**').test('/api/turnos'))
  assert.ok(antARegex('/api/medicos/*/turnos-disponibles').test('/api/medicos/16/turnos-disponibles'))
  assert.ok(!antARegex('/api/medicos/*/turnos-disponibles').test('/api/medicos/16/x/turnos-disponibles'))
  assert.ok(!antARegex('/api/medicos').test('/api/medicos/perfil'))
})

test('endpoints con la ruta de la clase, varios métodos y @PreAuthorize', () => {
  const fuente = `@RestController
@RequestMapping("/api/turnos")
public class TurnoController {
    @PostMapping(value = "/{id}/cancelar", produces = "application/json")
    @PreAuthorize("hasRole('PACIENTE')")
    public ResponseEntity<Void> cancelar(@PathVariable Long id) { return null; }

    @GetMapping
    public List<String> listar() { return null; }

    @RequestMapping(value = {"/a", "/b"}, method = RequestMethod.PUT)
    public void dos() {}
}`
  assert.deepEqual(endpointsDe(fuente).map((e) => `${e.metodo} ${e.ruta} ${e.java} ${e.rol ?? ''}`.trim()), [
    "POST /api/turnos/{id}/cancelar cancelar hasRole('PACIENTE')",
    'GET /api/turnos listar',
    'PUT /api/turnos/a dos',
    'PUT /api/turnos/b dos',
  ])
})

test('las reglas del gateway en orden y su destino según el compose', () => {
  const yml = `
        - id: turnos-reservar
          uri: \${tranqui.services.turnos.uri:\${tranqui.services.monolito.uri}}
          predicates:
            - Path=/api/turnos/reservar
        - id: profesionales-monolito
          uri: \${tranqui.services.monolito.uri}
          predicates:
            - Path=/api/medicos/**,/api/x`
  const reglas = reglasGateway(yml)
  assert.deepEqual(reglas.map((r) => [r.id, r.prop, r.paths]), [
    ['turnos-reservar', 'turnos', ['/api/turnos/reservar']],
    ['profesionales-monolito', 'monolito', ['/api/medicos/**', '/api/x']],
  ])
  assert.deepEqual(destinos('TRANQUI_SERVICES_MONOLITO_URI: http://backend:8080\nTRANQUI_SERVICES_CHAT_WS_URI: lb://chat'),
    { monolito: 'backend', 'chat-ws': 'services/chat' })
})

test('las llamadas de api.ts', () => {
  const fuente = `export const api = {
  getMedicos: () => apiFetch('/medicos'),
  cancelarTurno: (turnoId: number | string) => apiFetch(\`/turnos/\${turnoId}/cancelar\`, { method: 'POST' }),
};`
  assert.deepEqual(llamadasFrontend(fuente), [
    { nombre: 'getMedicos', metodo: 'GET', ruta: '/api/medicos' },
    { nombre: 'cancelarTurno', metodo: 'POST', ruta: '/api/turnos/{}/cancelar' },
  ])
})
