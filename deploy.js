// Deploy MANUAL de emergencia. El deploy normal es automático: GitHub Actions al crear un tag
// vX.Y.Z en main (ver docs/flujo/releases.md). Usá esto solo si Actions no está disponible.
//
//   node deploy.js vX.Y.Z     mismo camino que Actions: el VPS baja las imágenes de esa versión de
//                             GHCR, recrea los contenedores, verifica y vuelve atrás si falla
//                             (scripts/vps/deploy-remoto.sh). La versión tiene que estar publicada.
//   node deploy.js --compilar compila las imágenes en el VPS a partir del commit actual (más lento
//                             y le saca CPU a producción mientras compila). Solo si GHCR no anda.
//
// Credenciales: VPS_HOST, VPS_USER, VPS_PRIVATE_KEY_PATH y VPS_HOST_FINGERPRINT por variables de
// entorno o en .env.vps (ignorado por git). Ver docs/entornos/secretos.md. No hay contraseñas acá.
// Las variables de producción viven en /srv/tranqui/.env del VPS: este script no sube ningún .env.

const { Client } = require('ssh2');
const fs = require('fs');
const os = require('os');
const path = require('path');
const crypto = require('crypto');
const { execSync } = require('child_process');

function leerEnvVps() {
  const archivo = path.join(__dirname, '.env.vps');
  if (!fs.existsSync(archivo)) return {};
  const vars = {};
  for (const linea of fs.readFileSync(archivo, 'utf8').split(/\r?\n/)) {
    const m = /^\s*([A-Z_]+)\s*=\s*(.*?)\s*$/.exec(linea);
    if (m) vars[m[1]] = m[2].replace(/^["']|["']$/g, '');
  }
  return vars;
}

function configSsh() {
  const archivo = leerEnvVps();
  const v = k => process.env[k] || archivo[k];
  const host = v('VPS_HOST');
  const clave = v('VPS_PRIVATE_KEY_PATH');
  if (!host || !clave) {
    console.error('Faltan VPS_HOST y VPS_PRIVATE_KEY_PATH (variables de entorno o .env.vps). Ver docs/entornos/secretos.md.');
    process.exit(1);
  }
  const cfg = {
    host,
    port: Number(v('VPS_PORT') || 22),
    username: v('VPS_USER') || 'root',
    privateKey: fs.readFileSync(clave.replace(/^~/, os.homedir())),
    readyTimeout: 20000,
  };
  const huella = v('VPS_HOST_FINGERPRINT');
  if (huella) {
    cfg.hostVerifier = k => `SHA256:${crypto.createHash('sha256').update(k).digest('base64').replace(/=+$/, '')}` === huella.trim();
  } else {
    console.warn('Aviso: sin VPS_HOST_FINGERPRINT no se verifica la identidad del servidor.');
  }
  return cfg;
}

// Ejecuta comandos en orden por SSH; corta en el primero que falla.
function ejecutar(conn, comandos) {
  return new Promise((resolve, reject) => {
    let i = 0;
    const siguiente = () => {
      if (i >= comandos.length) return resolve();
      console.log(`\n--- [${i + 1}/${comandos.length}] ---`);
      conn.exec(comandos[i], (err, stream) => {
        if (err) return reject(err);
        stream.on('data', d => process.stdout.write(d.toString()));
        stream.stderr.on('data', d => process.stderr.write(d.toString()));
        stream.on('close', code => {
          if (code !== 0) return reject(new Error(`El paso ${i + 1} falló (código ${code}).`));
          i++;
          siguiente();
        });
      });
    };
    siguiente();
  });
}

function subir(conn, archivos) {
  return new Promise((resolve, reject) => {
    conn.sftp((err, sftp) => {
      if (err) return reject(err);
      let pendientes = archivos.length;
      for (const [local, remoto] of archivos) {
        sftp.fastPut(local, remoto, {}, e => {
          if (e) return reject(e);
          if (--pendientes === 0) resolve();
        });
      }
    });
  });
}

async function main() {
  const args = process.argv.slice(2);
  const compilar = args.includes('--compilar');
  const version = args.find(a => /^v\d+\.\d+\.\d+$/.test(a));
  if (!compilar && !version) {
    console.error('Uso: node deploy.js vX.Y.Z   |   node deploy.js --compilar');
    process.exit(1);
  }

  const pendientes = execSync('git status --porcelain --untracked-files=no', { cwd: __dirname }).toString().trim();
  if (pendientes) {
    console.error('Hay cambios sin commitear en archivos del repo: commiteá antes de desplegar.\n' + pendientes);
    process.exit(1);
  }
  const commit = execSync('git rev-parse --short HEAD', { cwd: __dirname }).toString().trim();

  const conn = new Client();
  await new Promise((resolve, reject) => conn.on('ready', resolve).on('error', reject).connect(configSsh()));
  console.log('Conectado al VPS.');

  try {
    await ejecutar(conn, ['mkdir -p /srv/tranqui']);
    await subir(conn, [
      [path.join(__dirname, 'docker-compose.yml'), '/srv/tranqui/docker-compose.yml'],
      [path.join(__dirname, 'scripts/vps/deploy-remoto.sh'), '/srv/tranqui/deploy-remoto.sh'],
    ]);

    if (!compilar) {
      console.log(`Desplegando ${version} desde GHCR...`);
      await ejecutar(conn, [`bash /srv/tranqui/deploy-remoto.sh ${version}`]);
    } else {
      // Compila en el VPS el commit actual. La imagen queda etiquetada como si fuera de GHCR con la
      // versión "local-<commit>", así docker compose la usa sin bajar nada.
      const etiqueta = `local-${commit}`;
      console.log(`Empaquetando el commit ${commit} para compilar en el VPS...`);
      const lista = execSync('git ls-files -z -- backend frontend', { cwd: __dirname }).toString().split('\0').filter(Boolean);
      const listaPath = path.join(os.tmpdir(), 'tranqui-deploy-files.txt');
      const tarPath = path.join(os.tmpdir(), 'tranqui-project.tar.gz');
      fs.writeFileSync(listaPath, lista.join('\n') + '\n');
      const forceLocal = process.platform === 'win32' ? '--force-local ' : '';
      execSync(`tar ${forceLocal}-czf "${tarPath}" -T "${listaPath}"`, { cwd: __dirname, stdio: 'inherit' });
      await subir(conn, [[tarPath, '/root/tranqui-codigo.tar.gz']]);
      await ejecutar(conn, [
        'rm -rf /srv/tranqui/codigo && mkdir -p /srv/tranqui/codigo && tar -xzf /root/tranqui-codigo.tar.gz -C /srv/tranqui/codigo',
        `docker build -t ghcr.io/tommikimmel/tranqui-backend:${etiqueta} /srv/tranqui/codigo/backend`,
        `docker build -t ghcr.io/tommikimmel/tranqui-frontend:${etiqueta} /srv/tranqui/codigo/frontend`,
        // deploy-remoto.sh hace pull: con imágenes locales que no existen en GHCR el pull falla,
        // así que en este modo se levanta directo y se verifica igual.
        `cd /srv/tranqui && TRANQUI_VERSION=${etiqueta} docker compose -p app --env-file /srv/tranqui/.env -f docker-compose.yml up -d --remove-orphans`,
        'for i in $(seq 1 36); do curl -fsS -o /dev/null --max-time 10 https://tranquisalud.com/ && curl -fsS -o /dev/null --max-time 10 https://tranquisalud.com/api/health && exit 0; sleep 5; done; echo "No respondió: revisar con docker compose -p app logs" >&2; exit 1',
        `echo ${etiqueta} > /srv/tranqui/version-actual && echo "$(date -Iseconds) ${etiqueta} ok (compilado en el VPS)" >> /srv/tranqui/deploys.log`,
      ]);
    }
    console.log('\nDeploy terminado y verificado.');
  } finally {
    conn.end();
  }
}

main().catch(e => {
  console.error('\nError:', e.message);
  process.exit(1);
});
