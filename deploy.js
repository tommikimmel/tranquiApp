const { Client } = require('ssh2');
const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const config = {
  host: process.env.VPS_HOST || '177.7.37.92',
  port: parseInt(process.env.VPS_PORT || '22', 10),
  username: process.env.VPS_USER || 'root',
  password: process.env.VPS_PASSWORD || 'AVH&lwJ-wLnu7669'
};

if (process.env.VPS_PRIVATE_KEY_PATH) {
  config.privateKey = fs.readFileSync(process.env.VPS_PRIVATE_KEY_PATH);
  delete config.password;
}

const os = require('os');

// Fuera del repo: el tarball es un artefacto temporal, no algo que tenga que vivir junto al código.
const localFilePath = path.join(os.tmpdir(), 'tranqui-project.tar.gz');
const remoteFilePath = '/root/project.tar.gz';

// Se despliega lo que está commiteado, no la carpeta tal cual: así producción siempre
// corresponde a un commit concreto (antes llegó a correr un WIP que no estaba en git).
// `node deploy.js --allow-dirty` permite desplegar igual con cambios sin commitear (solo los
// archivos trackeados viajan, con su contenido actual en disco).
const allowDirty = process.argv.includes('--allow-dirty');
const pendientes = execSync('git status --porcelain', { cwd: __dirname }).toString().trim();
if (pendientes && !allowDirty) {
  console.error('Hay cambios sin commitear — commiteá antes de desplegar (o usá --allow-dirty):\n' + pendientes);
  process.exit(1);
}
const commit = execSync('git rev-parse --short HEAD', { cwd: __dirname }).toString().trim();
console.log(`Empaquetando el commit ${commit}${pendientes ? ' (con cambios locales, --allow-dirty)' : ''}...`);
// Archivos de git (trackeados + nuevos no ignorados, que con el árbol limpio no hay) dentro de
// las carpetas que necesita el servidor, más el .env (que está en .gitignore a propósito y es la
// fuente de los secretos de producción). Respetar .gitignore deja afuera node_modules, target,
// dist, etc. sin tener que listarlos.
const archivos = execSync(
  'git ls-files -z --cached --others --exclude-standard -- .env.template .gitignore README.md backend devops docker-compose.yml frontend',
  { cwd: __dirname }
).toString().split('\0').filter((f) => f && fs.existsSync(path.join(__dirname, f)));
archivos.push('.env');
const listaPath = path.join(os.tmpdir(), 'tranqui-deploy-files.txt');
fs.writeFileSync(listaPath, archivos.join('\n') + '\n');
let tarCmd = 'tar';
try {
  execSync('tar --force-local --version', { stdio: 'ignore' });
  tarCmd = 'tar --force-local';
} catch (_) {}
execSync(`${tarCmd} -czf "${localFilePath}" -T "${listaPath}"`, { cwd: __dirname, stdio: 'inherit' });
console.log(`Paquete listo: ${localFilePath}`);

console.log('Connecting to VPS SSH server...');
const conn = new Client();

conn.on('ready', () => {
  console.log('SSH connection established successfully!');
  
  console.log('Uploading project.tar.gz via SFTP...');
  conn.sftp((err, sftp) => {
    if (err) {
      console.error('SFTP error:', err);
      conn.end();
      return;
    }
    
    sftp.fastPut(localFilePath, remoteFilePath, {}, (uploadErr) => {
      if (uploadErr) {
        console.error('File upload failed:', uploadErr);
        conn.end();
        return;
      }
      console.log('Uploaded project.tar.gz to VPS successfully!');
      
      console.log('Starting remote installation commands...');
      const commands = [
        // 1. Install Docker if not present
        `if ! command -v docker &> /dev/null; then
          echo "=== Installing Docker ==="
          apt-get update
          apt-get install -y docker.io docker-compose-v2
          systemctl start docker
          systemctl enable docker
        fi`,
        
        // 2. Ensure Nginx on the host is stopped and disabled so Traefik has ports 80/443
        `echo "=== Stopping Nginx on host (relying on Traefik) ==="
        systemctl stop nginx || true
        systemctl disable nginx || true`,
        
        // 3. Wipe /app before extracting instead of extracting on top of it. `tar -xzf` only
        // overwrites files that are actually present in the archive — anything left over from
        // an earlier deploy (e.g. a frontend/node_modules built from a Windows-tarred archive
        // at some point, with broken executable bits) would otherwise keep piling up and get
        // picked up by `COPY . .` in the Dockerfile, clobbering the fresh `npm install` output.
        `echo "=== Extracting project archive ==="
        rm -rf /app
        mkdir -p /app
        tar -xzf /root/project.tar.gz -C /app`,
        
        // 4. Update environment variables for production
        `echo "=== Configuring .env file ==="
        sed -i 's|APP_PUBLIC_URL=.*|APP_PUBLIC_URL=https://tranquisalud.com|g' /app/.env
        sed -i 's|FRONTEND_URL=.*|FRONTEND_URL=https://tranquisalud.com|g' /app/.env
        # Nunca sembrar cuentas de prueba (password admin123 conocida) en producción, aunque el
        # .env local que viaja en el tarball lo tenga en true.
        sed -i '/^SEED_TEST_ACCOUNTS=/d' /app/.env
        echo 'SEED_TEST_ACCOUNTS=false' >> /app/.env`,
        
        // 5. Rebuild images with --no-cache. Plain `docker compose up --build` reuses cached
        // layers whenever the copied files hash the same as before, which can silently keep
        // reusing a stale `npm install` layer (e.g. one built at some point from a Windows-tarred
        // node_modules with broken executable bits — that's how `tsc: Permission denied` shows
        // up even though the current tarball never ships node_modules at all).
        `echo "=== Rebuilding images (no cache) ==="
        cd /app
        docker compose build --no-cache`,

        // 6. Start the Docker services (Traefik will read labels and automatically configure SSL)
        `echo "=== Launching Docker Containers ==="
        cd /app
        docker compose down --remove-orphans || true
        docker rm -f tranqui-db tranqui-backend tranqui-frontend 2>/dev/null || true
        docker compose up -d --force-recreate`,
        
        // 6. Verify running containers
        `echo "=== Verification ==="
        docker ps`
      ];
      
      executeRemoteCommands(conn, commands);
    });
  });
}).connect(config);

function executeRemoteCommands(conn, commands) {
  let index = 0;
  
  function next() {
    if (index >= commands.length) {
      console.log('Deployment completed successfully!');
      conn.end();
      return;
    }
    
    const cmd = commands[index];
    console.log(`Executing command [${index + 1}/${commands.length}]...`);
    
    conn.exec(cmd, (execErr, stream) => {
      if (execErr) {
        console.error(`Command execution failed: ${cmd}`, execErr);
        conn.end();
        return;
      }
      
      stream.on('close', (code, signal) => {
        if (code !== 0) {
          // Stop instead of continuing to the next step — e.g. if the image build fails, the
          // next steps would tear down the working containers (`docker compose down`) and bring
          // up whatever image happens to exist (stale or none), silently leaving prod in a worse
          // state than before while printing "Deployment completed successfully!" at the end.
          console.error(`Command [${index + 1}/${commands.length}] failed with exit code ${code} — aborting deploy.`);
          process.exitCode = 1;
          conn.end();
          return;
        }
        index++;
        next();
      }).on('data', (data) => {
        process.stdout.write(data.toString());
      }).stderr.on('data', (data) => {
        process.stderr.write(data.toString());
      });
    });
  }
  
  next();
}
