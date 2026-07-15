const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

async function run() {
  const outputDir = path.join(__dirname, '..', '..', 'capturas_tranquiapp');
  if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir, { recursive: true });
  }

  console.log('Starting screenshot captures...');
  const browser = await chromium.launch({ headless: true });
  
  // 1. PUBLIC LANDING & LOGIN
  console.log('Capturing public pages...');
  const contextPublic = await browser.newContext({
    viewport: { width: 1280, height: 800 }
  });
  const pagePublic = await contextPublic.newPage();
  
  try {
    await pagePublic.goto('https://subdom.cloud/', { waitUntil: 'networkidle' });
    await pagePublic.screenshot({ path: path.join(outputDir, '01_landing_publico.png'), fullPage: true });
    console.log('01_landing_publico.png saved.');
    
    await pagePublic.goto('https://subdom.cloud/login', { waitUntil: 'networkidle' });
    await pagePublic.screenshot({ path: path.join(outputDir, '02_login_page.png'), fullPage: true });
    console.log('02_login_page.png saved.');
  } catch (err) {
    console.error('Error with public pages:', err);
  }
  await contextPublic.close();

  // 2. DOCTOR PAGES (medico.verificado@gmail.com)
  console.log('Logging in as Doctor...');
  const contextDoctor = await browser.newContext({
    viewport: { width: 1280, height: 800 }
  });
  const pageDoctor = await contextDoctor.newPage();
  try {
    await pageDoctor.goto('https://subdom.cloud/login', { waitUntil: 'networkidle' });
    await pageDoctor.fill('id=login-email', 'medico.verificado@gmail.com');
    await pageDoctor.fill('id=login-password', 'admin123');
    await pageDoctor.click('button[type="submit"]');
    
    // Wait for the URL to change to /panel
    await pageDoctor.waitForURL('**/panel**', { timeout: 10000 });
    await pageDoctor.waitForTimeout(3000); // Wait for animations/load
    await pageDoctor.screenshot({ path: path.join(outputDir, '03_doctor_dashboard_agenda.png'), fullPage: true });
    console.log('03_doctor_dashboard_agenda.png saved.');

    // Navigating doctor pages
    const doctorSubpaths = [
      { name: '04_doctor_pacientes.png', path: '/panel/pacientes' },
      { name: '05_doctor_chats.png', path: '/panel/chats' },
      { name: '06_doctor_configuracion.png', path: '/panel/settings' }
    ];

    for (const sub of doctorSubpaths) {
      console.log(`Navigating to ${sub.path}...`);
      await pageDoctor.goto(`https://subdom.cloud${sub.path}`, { waitUntil: 'networkidle' });
      await pageDoctor.waitForTimeout(2000);
      await pageDoctor.screenshot({ path: path.join(outputDir, sub.name), fullPage: true });
      console.log(`${sub.name} saved.`);
    }

  } catch (err) {
    console.error('Error with doctor pages:', err);
  }
  await contextDoctor.close();

  // 3. ADMIN PAGES (admin@tranqui.com)
  console.log('Logging in as Admin...');
  const contextAdmin = await browser.newContext({
    viewport: { width: 1280, height: 800 }
  });
  const pageAdmin = await contextAdmin.newPage();
  try {
    await pageAdmin.goto('https://subdom.cloud/login', { waitUntil: 'networkidle' });
    await pageAdmin.fill('id=login-email', 'admin@tranqui.com');
    await pageAdmin.fill('id=login-password', 'admin123');
    await pageAdmin.click('button[type="submit"]');
    
    await pageAdmin.waitForURL('**/panel**', { timeout: 10000 });
    await pageAdmin.waitForTimeout(3000);
    await pageAdmin.screenshot({ path: path.join(outputDir, '07_admin_dashboard.png'), fullPage: true });
    console.log('07_admin_dashboard.png saved.');
  } catch (err) {
    console.error('Error with admin page:', err);
  }
  await contextAdmin.close();

  // 4. PATIENT PAGES (paciente.completo@gmail.com)
  console.log('Logging in as Patient...');
  const contextPatient = await browser.newContext({
    viewport: { width: 1280, height: 800 }
  });
  const pagePatient = await contextPatient.newPage();
  try {
    await pagePatient.goto('https://subdom.cloud/login', { waitUntil: 'networkidle' });
    await pagePatient.fill('id=login-email', 'paciente.completo@gmail.com');
    await pagePatient.fill('id=login-password', 'admin123');
    await pagePatient.click('button[type="submit"]');
    
    await pagePatient.waitForURL('https://subdom.cloud/', { timeout: 10000 });
    await pagePatient.waitForTimeout(3000);
    await pagePatient.screenshot({ path: path.join(outputDir, '08_paciente_landing.png'), fullPage: true });
    console.log('08_paciente_landing.png saved.');

    // Go to booking page
    console.log('Navigating to booking page...');
    await pagePatient.goto('https://subdom.cloud/reserva/3', { waitUntil: 'networkidle' });
    await pagePatient.waitForTimeout(2000);
    await pagePatient.screenshot({ path: path.join(outputDir, '09_paciente_reserva.png'), fullPage: true });
    console.log('09_paciente_reserva.png saved.');
  } catch (err) {
    console.error('Error with patient pages:', err);
  }
  await contextPatient.close();

  await browser.close();
  console.log('All screenshot tasks completed!');
}

run();
