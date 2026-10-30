// Tests del cálculo de cobertura de los cambios: node --test scripts/ci/cobertura-cambios.test.mjs
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { lineasCambiadas, lineasCubiertas } from './cobertura-cambios.mjs'

test('toma las líneas agregadas de cada tramo del diff', () => {
  const diff = [
    'diff --git a/svc/src/main/java/com/x/A.java b/svc/src/main/java/com/x/A.java',
    '+++ b/svc/src/main/java/com/x/A.java',
    '@@ -3,0 +4,2 @@',
    '+linea 4',
    '+linea 5',
    '@@ -10 +12 @@',
    '-vieja',
    '+nueva',
    '@@ -20,3 +23,0 @@',
  ].join('\n')
  assert.deepEqual([...lineasCambiadas(diff).get('svc/src/main/java/com/x/A.java')], [4, 5, 12])
})

test('lee del reporte solo las líneas instrumentadas y si se ejecutaron', () => {
  const xml = '<report><package name="com/x"><class name="com/x/A"/><sourcefile name="A.java">'
    + '<line nr="4" mi="0" ci="2" mb="0" cb="0"/><line nr="5" mi="3" ci="0" mb="0" cb="0"/>'
    + '</sourcefile></package></report>'
  const lineas = lineasCubiertas(xml).get('com/x/A.java')
  assert.equal(lineas.get(4), true)
  assert.equal(lineas.get(5), false)
  assert.equal(lineas.has(12), false)
})
