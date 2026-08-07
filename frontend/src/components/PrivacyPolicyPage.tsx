import { useNavigate } from 'react-router-dom'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import '../styles/privacy.css'

const CONTACT_EMAIL = 'soporte@tranquisalud.com'
const RESPONSABLE_NOMBRE = 'Enso Tomás García Criscuolo'
const RESPONSABLE_CUIT = '20-47473505-3'
const LAST_UPDATED = '7 de agosto de 2026'

const SECTIONS: { id: string; title: string }[] = [
  { id: 'responsable', title: '1. Responsable del tratamiento' },
  { id: 'alcance', title: '2. Alcance de esta política' },
  { id: 'datos-recolectados', title: '3. Qué datos recolectamos' },
  { id: 'datos-sensibles', title: '4. Datos sensibles y de salud' },
  { id: 'finalidades', title: '5. Para qué usamos tus datos' },
  { id: 'base-legal', title: '6. Base legal y consentimiento' },
  { id: 'terceros', title: '7. Con quién compartimos tus datos' },
  { id: 'transferencia-internacional', title: '8. Transferencia internacional de datos' },
  { id: 'google', title: '9. Uso específico de datos de Google' },
  { id: 'conservacion', title: '10. Plazo de conservación' },
  { id: 'seguridad', title: '11. Medidas de seguridad' },
  { id: 'derechos', title: '12. Tus derechos (ARCO)' },
  { id: 'menores', title: '13. Menores de edad' },
  { id: 'cookies', title: '14. Cookies y tecnologías similares' },
  { id: 'cambios', title: '15. Cambios a esta política' },
  { id: 'autoridad', title: '16. Autoridad de control' },
  { id: 'contacto', title: '17. Contacto' },
]

function Section({ id, title, children }: { id: string; title: string; children: React.ReactNode }) {
  return (
    <section id={id} className="privacy-section">
      <h2>{title}</h2>
      {children}
    </section>
  )
}

export default function PrivacyPolicyPage({ currentUser }: { currentUser?: any }) {
  useDocumentTitle('Política de Privacidad — Tranqui App')
  const navigate = useNavigate()

  return (
    <div className="privacy-page">
      <header className="privacy-page__header">
        <a href="/" className="privacy-page__logo" aria-label="Tranqui App - Inicio">
          <img src="/tranqui-icon.png" alt="" aria-hidden="true" className="privacy-page__logo-icon" />
          tranqui
        </a>
        <button
          className="btn btn--sm btn--secondary"
          onClick={() => navigate(currentUser ? '/panel' : '/')}
        >
          {currentUser ? 'Ir a mi Panel' : 'Volver al Inicio'}
        </button>
      </header>

      <main className="privacy-page__main">
        <div className="privacy-doc">
          <h1>Política de Privacidad</h1>
          <p className="privacy-doc__updated">Última actualización: {LAST_UPDATED}</p>

          <p>
            En Tranqui App nos tomamos en serio la privacidad de tu información, especialmente porque
            gran parte de lo que manejamos son datos de salud. Esta política explica, en lenguaje claro,
            qué datos recolectamos, para qué los usamos, con quién los compartimos y qué derechos tenés
            sobre ellos — incluyendo los datos que accedemos a través de tu cuenta de Google cuando la
            usás para iniciar sesión o sincronizar tu calendario.
          </p>
          <p>
            Esta política cumple con la Ley N° 25.326 de Protección de los Datos Personales de la
            República Argentina y su normativa complementaria, y con la Política de Datos de Usuario de
            los Servicios de API de Google (Google API Services User Data Policy), incluyendo sus
            requisitos de Uso Limitado.
          </p>

          <nav className="privacy-toc" aria-label="Tabla de contenidos">
            <p className="privacy-toc__title">Contenido</p>
            <ol>
              {SECTIONS.map((s) => (
                <li key={s.id}>
                  <a href={`#${s.id}`}>{s.title}</a>
                </li>
              ))}
            </ol>
          </nav>

          <Section id="responsable" title="1. Responsable del tratamiento">
            <p>
              El responsable del tratamiento de los datos personales recolectados a través de Tranqui App
              (el "Sitio", la "Plataforma") es:
            </p>
            <ul>
              <li><strong>Razón social:</strong> {RESPONSABLE_NOMBRE}</li>
              <li><strong>CUIT:</strong> {RESPONSABLE_CUIT}</li>
              <li><strong>Nombre comercial:</strong> Tranqui App / Tranqui Neurociencias</li>
              <li><strong>Sitio web:</strong> tranquisalud.com</li>
              <li><strong>Contacto:</strong> <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a></li>
            </ul>
          </Section>

          <Section id="alcance" title="2. Alcance de esta política">
            <p>
              Esta política aplica a toda persona que use Tranqui App: pacientes, profesionales de la
              salud mental (psicólogos y psiquiatras) y visitantes del Sitio. Al crear una cuenta, reservar
              un turno, iniciar sesión con Google o usar cualquier otra función de la Plataforma, aceptás
              las prácticas descriptas acá.
            </p>
            <p>
              Si no estás de acuerdo con esta política, te pedimos que no uses la Plataforma. Si sos
              profesional de la salud y cargás datos de pacientes en la Plataforma (por ejemplo, historia
              clínica o seguimiento diario), sos responsable de contar con la base legal correspondiente
              (por ejemplo, el consentimiento del paciente) para hacerlo.
            </p>
          </Section>

          <Section id="datos-recolectados" title="3. Qué datos recolectamos">
            <p>Según el tipo de cuenta y el uso que le des a la Plataforma, recolectamos:</p>
            <ul>
              <li>
                <strong>Datos de identidad:</strong> nombre, apellido, sexo, fecha de nacimiento, tipo y
                número de documento, CUIL/CUIT.
              </li>
              <li>
                <strong>Datos de contacto:</strong> email, teléfono/WhatsApp.
              </li>
              <li>
                <strong>Domicilio:</strong> dirección particular o del consultorio (incluyendo, si la
                ingresás mediante el mapa, coordenadas de ubicación aproximadas).
              </li>
              <li>
                <strong>Datos de cuenta:</strong> contraseña (almacenada de forma encriptada, nunca en
                texto plano), estado de verificación de email.
              </li>
              <li>
                <strong>Datos de cobertura de salud (pacientes):</strong> obra social o prepaga, número de
                afiliado y credenciales asociadas, usados para emitir recetas electrónicas.
              </li>
              <li>
                <strong>Datos profesionales (psicólogos/psiquiatras):</strong> matrícula, especialidad,
                institución de formación, años de experiencia, código REFEPS (registro que habilita la
                firma electrónica de recetas), descripción de perfil, tarifas.
              </li>
              <li>
                <strong>Datos de salud (ver sección 4):</strong> diagnósticos, medicación prescripta,
                notas de seguimiento e historia clínica.
              </li>
              <li>
                <strong>Datos de pago:</strong> no almacenamos números de tarjeta. Los pagos se procesan a
                través de Mercado Pago; guardamos únicamente las credenciales de conexión (encriptadas)
                que cada profesional vincula a su propia cuenta de Mercado Pago para cobrar sus turnos
                directamente, sin intermediación de comisiones de nuestra parte.
              </li>
              <li>
                <strong>Datos de Google:</strong> ver sección 9, dedicada específicamente a esto.
              </li>
              <li>
                <strong>Datos técnicos:</strong> la cookie de sesión estrictamente necesaria para mantener
                tu inicio de sesión (ver sección 14).
              </li>
            </ul>
          </Section>

          <Section id="datos-sensibles" title="4. Datos sensibles y de salud">
            <p>
              La Ley 25.326 considera "datos sensibles" a los datos que revelan, entre otras cosas,
              información referente a la salud. Buena parte de lo que procesamos en Tranqui App entra en
              esta categoría: diagnósticos, medicación prescripta, notas de evolución y seguimiento del
              estado de ánimo, e historia clínica.
            </p>
            <p>
              Tratamos estos datos con un nivel de protección reforzado: acceso restringido únicamente al
              paciente y al profesional que lo atiende, cifrado de credenciales asociadas, y registros de
              auditoría en los procesos que involucran a terceros (por ejemplo, la emisión de recetas
              electrónicas). Nunca usamos datos de salud con fines publicitarios ni los vendemos.
            </p>
            <p>
              Al usar la Plataforma como paciente, o al cargar datos de un paciente como profesional,
              prestás tu consentimiento expreso para el tratamiento de estos datos sensibles con la única
              finalidad de brindar el servicio de salud mental contratado.
            </p>
          </Section>

          <Section id="finalidades" title="5. Para qué usamos tus datos">
            <ul>
              <li>Crear y administrar tu cuenta, y verificar tu identidad.</li>
              <li>Conectar pacientes con profesionales y gestionar la reserva, confirmación y recordatorio de turnos.</li>
              <li>Brindar la consulta en sí (por videollamada o presencial) y su seguimiento clínico.</li>
              <li>Emitir recetas electrónicas válidas legalmente, lo que requiere enviar tus datos a QBI2/Innovamed (ver sección 7).</li>
              <li>Procesar pagos entre paciente y profesional.</li>
              <li>Enviarte notificaciones operativas por WhatsApp o email (confirmaciones de turno, recordatorios, recetas emitidas, códigos de verificación).</li>
              <li>Sincronizar turnos con tu Google Calendar, si sos profesional y activás esa función.</li>
              <li>Prevenir fraude, abuso y uso indebido de la Plataforma, y cumplir obligaciones legales (por ejemplo, las que exige la normativa de historia clínica).</li>
              <li>Mejorar la Plataforma en base a su uso agregado y no identificado individualmente.</li>
            </ul>
            <p>No usamos tus datos para publicidad dirigida ni los cedemos con fines comerciales a terceros ajenos a la prestación del servicio.</p>
          </Section>

          <Section id="base-legal" title="6. Base legal y consentimiento">
            <p>
              Tratamos tus datos personales en base a tu consentimiento (otorgado al crear tu cuenta y
              aceptar esta política), a la necesidad de ejecutar el servicio que solicitás (por ejemplo,
              coordinar un turno o emitir una receta), y al cumplimiento de obligaciones legales aplicables
              a la prestación de servicios de salud en Argentina (entre otras, la Ley 26.529 de Derechos
              del Paciente y su normativa sobre historia clínica).
            </p>
          </Section>

          <Section id="terceros" title="7. Con quién compartimos tus datos">
            <p>
              No vendemos tus datos. Los compartimos únicamente con los terceros necesarios para prestar
              el servicio, en la medida estrictamente necesaria para cada finalidad:
            </p>
            <ul>
              <li>
                <strong>QBI2/Innovamed:</strong> plataforma de recetas electrónicas homologada. Recibe los
                datos de identidad y salud del paciente y del profesional (nombre, documento, matrícula,
                REFEPS, diagnóstico, medicación) necesarios para generar una receta electrónica con validez
                legal.
              </li>
              <li>
                <strong>Mercado Pago:</strong> procesa los pagos de los turnos. Cada profesional conecta su
                propia cuenta de Mercado Pago; nosotros no accedemos a tus datos de tarjeta ni de cuenta
                bancaria.
              </li>
              <li>
                <strong>Twilio (WhatsApp Business API):</strong> envía las notificaciones de WhatsApp
                (confirmaciones de turno, recordatorios, avisos de receta emitida) a tu número de teléfono.
              </li>
              <li>
                <strong>Resend:</strong> envía los emails transaccionales (verificación de cuenta,
                recuperación de contraseña).
              </li>
              <li>
                <strong>Google:</strong> ver sección 9.
              </li>
            </ul>
            <p>
              También podemos divulgar datos si una ley, orden judicial o autoridad competente así lo
              requiere, o para proteger los derechos, la seguridad o la propiedad de Tranqui App, sus
              usuarios o terceros.
            </p>
          </Section>

          <Section id="transferencia-internacional" title="8. Transferencia internacional de datos">
            <p>
              Algunos de los proveedores mencionados en la sección anterior (Google, Twilio, Resend)
              procesan datos en servidores ubicados fuera de la República Argentina, típicamente en
              Estados Unidos. Al usar la Plataforma, aceptás esta transferencia internacional, que
              realizamos únicamente hacia proveedores que aplican estándares de seguridad y protección de
              datos adecuados para las finalidades descriptas en esta política, conforme lo previsto por
              el artículo 12 de la Ley 25.326.
            </p>
          </Section>

          <Section id="google" title="9. Uso específico de datos de Google">
            <p>
              Tranqui App usa distintos servicios de Google API. Esta sección detalla, para cada uno, qué
              datos accedemos, cómo los usamos, cómo los almacenamos y cómo los compartimos (o no).
            </p>

            <h3>9.1 Inicio de sesión con Google</h3>
            <p>
              Si elegís iniciar sesión con tu cuenta de Google, accedemos únicamente a tu <strong>nombre</strong>{' '}
              y tu <strong>dirección de email</strong>, provistos por Google Identity Services al momento de
              autenticarte. Usamos estos datos exclusivamente para crear o reconocer tu cuenta en Tranqui
              App. No accedemos a tu contraseña de Google, tus contactos, ni ningún otro dato de tu cuenta
              de Google a través de este método.
            </p>

            <h3>9.2 Sincronización de Google Calendar (solo profesionales, opcional)</h3>
            <p>
              Si sos psicólogo/a o psiquiatra y activás voluntariamente la sincronización con Google
              Calendar desde tu panel, te pedimos autorización para acceder al permiso{' '}
              <code>https://www.googleapis.com/auth/calendar.events</code>, que nos permite:
            </p>
            <ul>
              <li>
                <strong>Escribir:</strong> crear en tu calendario un evento por cada turno confirmado en
                Tranqui App, con el nombre del paciente, el horario de la consulta y el enlace de
                videollamada.
              </li>
              <li>
                <strong>Leer:</strong> consultar los eventos existentes en tu calendario principal para
                calcular tus horarios ya ocupados y evitar ofrecer turnos superpuestos. Solo usamos el
                título y el horario de esos eventos con esa única finalidad; no los mostramos a pacientes
                ni los usamos para ningún otro fin.
              </li>
            </ul>
            <p>
              Los tokens de acceso y de actualización de tu cuenta de Google se almacenan{' '}
              <strong>encriptados</strong> en nuestra base de datos y nunca se comparten con terceros.
              Podés desconectar tu Google Calendar en cualquier momento desde Configuración → Google
              Calendar; al hacerlo, revocamos el acceso y eliminamos los datos de eventos sincronizados.
            </p>

            <h3>9.3 Google Maps</h3>
            <p>
              Usamos la API de Google Maps/Places para mostrarte un mapa y ayudarte a completar
              direcciones (por ejemplo, la dirección de un consultorio). Esta función no accede a tu
              cuenta de Google ni a datos personales más allá del texto de la dirección que escribís.
            </p>

            <h3>9.4 Cumplimiento con la Política de Google</h3>
            <p>
              El uso que hacemos de la información recibida a través de las APIs de Google se ajusta a la{' '}
              <a href="https://developers.google.com/terms/api-services-user-data-policy" target="_blank" rel="noreferrer">
                Política de Datos de Usuario de los Servicios de API de Google
              </a>{' '}
              (Google API Services User Data Policy), incluidos sus requisitos de Uso Limitado
              (Limited Use). No usamos datos obtenidos de las APIs de Google para publicidad, no los
              transferimos a terceros salvo lo necesario para operar la funcionalidad solicitada (por
              ejemplo, el enlace de videollamada dentro del evento de calendario), y ningún humano lee el
              contenido de tus eventos de calendario salvo que sea necesario por seguridad, para cumplir la
              ley, o con tu consentimiento explícito.
            </p>
          </Section>

          <Section id="conservacion" title="10. Plazo de conservación">
            <p>
              Conservamos tus datos mientras tu cuenta esté activa y sea necesario para las finalidades
              descriptas. Si solicitás la baja de tu cuenta, eliminamos o anonimizamos los datos que no
              estemos obligados a conservar por ley.
            </p>
            <p>
              Los registros de historia clínica y las recetas electrónicas están sujetos a plazos mínimos
              de conservación establecidos por la normativa sanitaria argentina (Ley 26.529 y
              modificatorias), que pueden exceder la baja de tu cuenta.
            </p>
          </Section>

          <Section id="seguridad" title="11. Medidas de seguridad">
            <p>Aplicamos medidas técnicas y organizativas razonables para proteger tus datos, entre ellas:</p>
            <ul>
              <li>Contraseñas almacenadas siempre con hash, nunca en texto plano.</li>
              <li>Tokens de terceros (Google, Mercado Pago) almacenados encriptados en la base de datos.</li>
              <li>Conexión cifrada (HTTPS/TLS) en todo el Sitio.</li>
              <li>Acceso a datos clínicos restringido al paciente y al profesional que lo atiende.</li>
              <li>Registro de auditoría de las llamadas a servicios externos que procesan datos sensibles (por ejemplo, QBI2/Innovamed), con enmascaramiento de credenciales en los logs.</li>
            </ul>
            <p>
              Ningún sistema es 100% infalible. Si detectamos un incidente de seguridad que afecte tus
              datos personales, te notificaremos conforme lo exige la normativa aplicable.
            </p>
          </Section>

          <Section id="derechos" title="12. Tus derechos (ARCO)">
            <p>
              De acuerdo con la Ley 25.326, tenés derecho a acceder, rectificar, actualizar y suprimir tus
              datos personales, así como a oponerte a su tratamiento en los casos que la ley prevé
              (derechos ARCO). También podés solicitar información sobre a quién se los cedimos.
            </p>
            <p>
              Podés ejercer estos derechos escribiéndonos a{' '}
              <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a>. El ejercicio del derecho de acceso es
              gratuito hasta una vez cada seis meses, salvo que acredites un interés legítimo para
              ejercerlo con mayor frecuencia. Responderemos tu solicitud dentro de los plazos que establece
              la ley.
            </p>
          </Section>

          <Section id="menores" title="13. Menores de edad">
            <p>
              Tranqui App está pensada para personas mayores de 18 años. Si un/a profesional atiende a un
              paciente menor de edad, el tratamiento de sus datos requiere el consentimiento de sus padres,
              madres o representantes legales, conforme a la Ley 26.061 de Protección Integral de los
              Derechos de Niñas, Niños y Adolescentes, y es responsabilidad del profesional tratante
              contar con ese consentimiento antes de cargar datos del menor en la Plataforma.
            </p>
          </Section>

          <Section id="cookies" title="14. Cookies y tecnologías similares">
            <p>
              Usamos una única cookie estrictamente necesaria (<code>HttpOnly</code>) para mantener tu
              sesión iniciada de forma segura. No usamos cookies de publicidad, seguimiento entre sitios ni
              analítica de comportamiento. El Sitio carga tipografías desde Google Fonts, lo que implica
              una solicitud técnica a servidores de Google (con tu dirección IP) para mostrar el diseño de
              la página.
            </p>
          </Section>

          <Section id="cambios" title="15. Cambios a esta política">
            <p>
              Podemos actualizar esta política para reflejar cambios en la Plataforma o en la normativa
              aplicable. Publicaremos cualquier cambio en esta misma página, indicando la fecha de última
              actualización. Si el cambio es sustancial, te lo notificaremos por email o dentro de la
              Plataforma.
            </p>
          </Section>

          <Section id="autoridad" title="16. Autoridad de control">
            <p>
              La AGENCIA DE ACCESO A LA INFORMACIÓN PÚBLICA (AAIP), Órgano de Control de la Ley N° 25.326,
              tiene la atribución de atender las denuncias y reclamos que interpongan quienes resulten
              afectados en sus derechos por el incumplimiento de las normas vigentes en materia de
              protección de datos personales.
            </p>
          </Section>

          <Section id="contacto" title="17. Contacto">
            <p>
              Ante cualquier duda sobre esta política o sobre el tratamiento de tus datos personales,
              escribinos a <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a>.
            </p>
          </Section>
        </div>
      </main>

      <footer className="privacy-page__footer">
        © {new Date().getFullYear()} Tranqui App. Todos los derechos reservados.
      </footer>
    </div>
  )
}
