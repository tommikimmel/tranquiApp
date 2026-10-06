import { useNavigate } from 'react-router-dom'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import '../styles/privacy.css'

const CONTACT_EMAIL = 'soporte@tranquisalud.com'
const RESPONSABLE_NOMBRE = 'Enso Tomás García Criscuolo'
const RESPONSABLE_CUIT = '20-47473505-3'
const LAST_UPDATED = '6 de octubre de 2026'

const SECTIONS: { id: string; title: string }[] = [
  { id: 'aceptacion', title: '1. Aceptación de los términos' },
  { id: 'descripcion', title: '2. Qué es Tranqui Salud' },
  { id: 'cuentas', title: '3. Cuentas de usuario' },
  { id: 'profesionales', title: '4. Rol y responsabilidad de los profesionales' },
  { id: 'pacientes', title: '5. Obligaciones de los pacientes y reservas en representación de terceros' },
  { id: 'turnos', title: '6. Reserva, cancelación y reprogramación de turnos' },
  { id: 'pagos', title: '7. Pagos, comisiones y reembolsos' },
  { id: 'recetas', title: '8. Recetas electrónicas' },
  { id: 'integraciones', title: '9. Integraciones con Google y otros terceros' },
  { id: 'uso-aceptable', title: '10. Uso aceptable de la plataforma' },
  { id: 'propiedad-intelectual', title: '11. Propiedad intelectual' },
  { id: 'disponibilidad', title: '12. Disponibilidad del servicio' },
  { id: 'responsabilidad', title: '13. Limitación de responsabilidad' },
  { id: 'suspension', title: '14. Suspensión y baja de cuentas' },
  { id: 'cambios', title: '15. Modificaciones a estos términos' },
  { id: 'legislacion', title: '16. Ley aplicable, jurisdicción y resolución de conflictos' },
  { id: 'generales', title: '17. Disposiciones generales' },
  { id: 'contacto', title: '18. Contacto' },
]

function Section({ id, title, children }: { id: string; title: string; children: React.ReactNode }) {
  return (
    <section id={id} className="privacy-section">
      <h2>{title}</h2>
      {children}
    </section>
  )
}

export default function TermsPage({ currentUser }: { currentUser?: any }) {
  useDocumentTitle('Términos y Condiciones — Tranqui App')
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
          <h1>Términos y Condiciones</h1>
          <p className="privacy-doc__updated">Última actualización: {LAST_UPDATED}</p>

          <p>
            Estos Términos y Condiciones (los "Términos") regulan el uso de Tranqui Salud (el "Sitio", la
            "Plataforma"). Al crear una cuenta, reservar un turno o usar cualquier función del Sitio,
            aceptás estos Términos en su totalidad. Si actuás como profesional de la salud mental, también
            aceptás las obligaciones adicionales descriptas en la sección 4.
          </p>
          <p>
            Estos Términos se complementan con nuestra{' '}
            <a href="/privacidad">Política de Privacidad</a>, que describe cómo tratamos tus datos
            personales, incluyendo los que accedemos a través de tu cuenta de Google.
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

          <Section id="aceptacion" title="1. Aceptación de los términos">
            <p>
              El acceso y uso de Tranqui Salud implica la aceptación plena de estos Términos. Si no estás
              de acuerdo con alguna parte, te pedimos que no uses la Plataforma. Para crear una cuenta en
              Tranqui Salud tenés que ser mayor de 18 años o, si sos profesional actuando en representación
              de una institución, contar con la capacidad legal para obligarla.
            </p>
          </Section>

          <Section id="descripcion" title="2. Qué es Tranqui Salud">
            <p>
              Tranqui Salud es una plataforma que conecta pacientes con profesionales de la salud mental
              (psicólogos y psiquiatras) para la gestión de turnos, consultas por videollamada o
              presenciales, pagos y, cuando corresponde, emisión de recetas electrónicas.
            </p>
            <p>
              Tranqui Salud <strong>no presta servicios de salud</strong>: es un intermediario tecnológico
              entre pacientes y profesionales independientes. Cada profesional es responsable del
              contenido, la calidad y la idoneidad clínica de la atención que brinda, en los términos de la
              sección 4.
            </p>
          </Section>

          <Section id="cuentas" title="3. Cuentas de usuario">
            <p>
              Para usar ciertas funciones de la Plataforma necesitás crear una cuenta, con tu email y una
              contraseña o mediante tu cuenta de Google. Sos responsable de mantener la confidencialidad de
              tus credenciales y de toda actividad que ocurra bajo tu cuenta.
            </p>
            <p>
              Los profesionales que se registran quedan sujetos a un proceso de verificación (matrícula,
              identidad y datos profesionales) antes de poder publicar su perfil y recibir turnos. Tranqui
              Salud puede rechazar o dar de baja el registro de un profesional que no acredite
              correctamente su matrícula o idoneidad.
            </p>
            <p>
              Debés proporcionar información veraz, completa y actualizada al registrarte. Si detectamos
              datos falsos o suplantación de identidad, podemos suspender o eliminar la cuenta
              correspondiente.
            </p>
            <p>
              <strong>Tranqui Salud como encargado del tratamiento:</strong> respecto de los datos de salud
              que un profesional carga sobre sus pacientes, el profesional actúa como responsable del
              tratamiento de esos datos (Ley 25.326) y Tranqui Salud actúa como encargado del tratamiento,
              brindando la infraestructura técnica para su almacenamiento y gestión conforme a las
              instrucciones del profesional y a los estándares de seguridad descriptos en nuestra{' '}
              <a href="/privacidad">Política de Privacidad</a>.
            </p>
          </Section>

          <Section id="profesionales" title="4. Rol y responsabilidad de los profesionales">
            <p>
              Si te registrás como psicólogo/a o psiquiatra en Tranqui Salud, declarás y garantizás que:
            </p>
            <ul>
              <li>Contás con matrícula habilitante vigente para ejercer en tu jurisdicción.</li>
              <li>
                La información profesional que publicás (especialidad, formación, experiencia, tarifas, y
                en su caso código REFEPS) es veraz y está actualizada.
              </li>
              <li>
                Sos el único responsable de las decisiones clínicas, diagnósticos, prescripciones y
                seguimiento que realices a través de la Plataforma, conforme a la lex artis y a la
                normativa sanitaria aplicable (entre otras, la Ley 26.529 de Derechos del Paciente).
              </li>
              <li>
                Contás con la base legal correspondiente (por ejemplo, el consentimiento informado del
                paciente o de su representante legal) para cargar y tratar sus datos de salud en la
                Plataforma.
              </li>
              <li>
                Sos responsable de la relación contractual, tributaria y de facturación con tu paciente;
                Tranqui Salud no es parte de esa relación.
              </li>
            </ul>
            <p>
              Tranqui Salud puede suspender o dar de baja el perfil de un profesional ante denuncias
              verificables de mala praxis, incumplimiento de estos Términos o de la normativa sanitaria
              aplicable.
            </p>
          </Section>

          <Section id="pacientes" title="5. Obligaciones de los pacientes y reservas en representación de terceros">
            <p>
              Como paciente, te comprometés a brindar información veraz sobre tu identidad, tu cobertura de
              salud (si corresponde) y los motivos de consulta que compartís con el profesional. El uso
              indebido de la Plataforma para fines ajenos a la atención de salud mental (por ejemplo, acoso
              a profesionales, uso de identidades falsas o intento de fraude en los pagos) puede dar lugar
              a la suspensión de tu cuenta.
            </p>
            <p>
              <strong>Reservas para hijos, hijas o personas bajo tu representación legal:</strong> si
              reservás un turno o cargás información en nombre de un hijo/a o de una persona bajo tu
              representación legal, declarás ser su padre, madre, tutor/a o representante legal, y asumís
              la responsabilidad de contar con la capacidad legal para autorizar su atención y el
              tratamiento de sus datos personales, incluidos los datos de salud, conforme a la Ley 25.326 y
              a la Ley 26.061 de Protección Integral de los Derechos de las Niñas, Niños y Adolescentes.
              Tranqui Salud no verifica de forma independiente el vínculo de representación declarado, y no
              será responsable por declaraciones falsas en este sentido.
            </p>
          </Section>

          <Section id="turnos" title="6. Reserva, cancelación y reprogramación de turnos">
            <p>
              Los turnos se reservan según la disponibilidad publicada por cada profesional. La
              confirmación de un turno queda sujeta a la acreditación del pago cuando corresponda.
            </p>
            <p>
              <strong>Política de cancelación:</strong> podés cancelar un turno hasta <strong>48 horas
              antes</strong> de su horario de inicio y se te reembolsa el total abonado, de forma
              automática, al mismo medio de pago. Si cancelás con <strong>menos de 48 horas</strong> de
              anticipación (incluido el botón "No podré asistir" del recordatorio), el turno se cancela
              pero <strong>no corresponde reembolso</strong>. Antes de confirmar una cancelación, la
              Plataforma te indica cuál de los dos casos aplica.
            </p>
            <p>
              Si el turno lo cancela el profesional, se te reembolsa el total abonado sin importar la
              anticipación. La reprogramación de un turno la realiza el profesional. Tranqui Salud puede
              reprogramar o cancelar turnos automáticamente ante indisponibilidad detectada (por ejemplo,
              un conflicto de horario reflejado en el Google Calendar vinculado del profesional) y te
              notificará el cambio por email.
            </p>
          </Section>

          <Section id="pagos" title="7. Pagos, comisiones y reembolsos">
            <p>
              Los pagos de los turnos se procesan a través de Mercado Pago, directamente a la cuenta que
              cada profesional vincula a su perfil mediante Mercado Pago Split. Tranqui Salud no almacena
              datos de tarjetas ni de cuentas bancarias.
            </p>
            <p>
              Sobre cada transacción intervienen los siguientes conceptos, que podés consultar en el
              detalle de cobro de cada turno:
            </p>
            <ul>
              <li>
                <strong>Comisión de procesamiento de Mercado Pago:</strong> Mercado Pago cobra su propia
                comisión por el procesamiento del pago, conforme a sus términos y tarifas vigentes, ajena a
                Tranqui Salud.
              </li>
              <li>
                <strong>Comisión de Tranqui Salud:</strong> Tranqui Salud no cobra ninguna comisión sobre
                las consultas, incluida la primera consulta de un paciente nuevo captado a través del
                marketplace de la Plataforma. El profesional recibe el 100% de lo abonado por el paciente,
                menos la comisión de procesamiento de Mercado Pago descripta arriba.
              </li>
            </ul>
            <p>
              Las tarifas publicadas por cada profesional son fijadas libremente por él y pueden variar
              según el tipo de consulta. Los reembolsos ante cancelaciones se rigen por la política de
              cancelación de la sección 6; los plazos de acreditación dependen del medio de pago utilizado. Si una receta
              electrónica es rechazada después de haberse cobrado la consulta, ver sección 8. Ante
              cualquier disputa de pago, contactanos a <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a>.
            </p>
          </Section>

          <Section id="recetas" title="8. Recetas electrónicas">
            <p>
              Cuando un profesional emite una receta electrónica desde Tranqui Salud, esta se genera a
              través de QBI2/Innovamed, plataforma homologada para la emisión de recetas con validez legal
              en Argentina. Tranqui Salud actúa únicamente como canal de integración: la responsabilidad
              por el contenido, la corrección y la pertinencia clínica de cada receta es exclusiva del
              profesional que la emite.
            </p>
            <p>
              Una receta puede ser rechazada por QBI2/Innovamed si los datos del profesional o del paciente
              no cumplen los requisitos exigidos (por ejemplo, un código REFEPS inválido); en ese caso, la
              Plataforma te informará el motivo del rechazo para que puedas corregirlo. El rechazo de una
              receta no afecta el cobro de la consulta ya realizada, salvo que la política de cancelación
              del profesional indique lo contrario.
            </p>
          </Section>

          <Section id="integraciones" title="9. Integraciones con Google y otros terceros">
            <p>
              Tranqui Salud ofrece integraciones opcionales con servicios de terceros, entre ellas el
              inicio de sesión con Google y la sincronización bidireccional con Google Calendar (lectura y
              escritura de eventos) para los profesionales que la activen. El uso de estas integraciones
              está sujeto además a los términos de servicio del tercero correspondiente (por ejemplo, los{' '}
              <a href="https://policies.google.com/terms" target="_blank" rel="noreferrer">
                Términos del Servicio de Google
              </a>
              ) y al detalle de tratamiento de datos descripto en la sección 9 de nuestra{' '}
              <a href="/privacidad">Política de Privacidad</a>.
            </p>
            <p>
              Podés desconectar cualquier integración de terceros en cualquier momento desde tu panel de
              Configuración, sin que eso afecte tu acceso al resto de la Plataforma.
            </p>
          </Section>

          <Section id="uso-aceptable" title="10. Uso aceptable de la plataforma">
            <p>No está permitido usar Tranqui Salud para:</p>
            <ul>
              <li>Suplantar la identidad de otra persona o profesional.</li>
              <li>Publicar matrículas, títulos o datos profesionales falsos.</li>
              <li>Intentar vulnerar la seguridad de la Plataforma o acceder a datos de otros usuarios sin autorización.</li>
              <li>Usar la Plataforma para fines ilícitos, fraudulentos o contrarios a la buena fe.</li>
              <li>Extraer masivamente datos de la Plataforma (scraping) sin autorización expresa.</li>
            </ul>
            <p>
              El incumplimiento de esta sección puede dar lugar a la suspensión inmediata de la cuenta.
            </p>
          </Section>

          <Section id="propiedad-intelectual" title="11. Propiedad intelectual">
            <p>
              La marca "Tranqui Salud", su logo, diseño y el software que la compone son propiedad de{' '}
              {RESPONSABLE_NOMBRE} o de sus licenciantes. No está permitido copiar, modificar o distribuir
              estos elementos sin autorización previa por escrito. El contenido que cada profesional
              publica en su perfil (descripción, foto, tarifas) sigue siendo de su propiedad, y al
              publicarlo le otorgás a Tranqui Salud una licencia no exclusiva para mostrarlo dentro de la
              Plataforma.
            </p>
          </Section>

          <Section id="disponibilidad" title="12. Disponibilidad del servicio">
            <p>
              Nos esforzamos por mantener la Plataforma disponible de forma continua, pero no garantizamos
              un funcionamiento ininterrumpido o libre de errores. Podemos realizar tareas de
              mantenimiento, actualización o corrección que interrumpan temporalmente el servicio, e
              intentaremos avisar con anticipación cuando sea posible. Tranqui Salud no será responsable
              por incumplimientos derivados de causas de fuerza mayor o caso fortuito ajenos a su control
              razonable.
            </p>
          </Section>

          <Section id="responsabilidad" title="13. Limitación de responsabilidad">
            <p>
              <strong>Responsabilidad por actos de terceros:</strong> Tranqui Salud no es responsable por
              el contenido de las consultas, diagnósticos, tratamientos o prescripciones brindados por los
              profesionales, ni por decisiones que un paciente tome en base a ellos. Tampoco somos
              responsables por fallas atribuibles a proveedores externos (Mercado Pago, Google,
              Resend, QBI2/Innovamed) que estén fuera de nuestro control razonable.
            </p>
            <p>
              <strong>Responsabilidad por fallas propias de la Plataforma:</strong> en la medida permitida
              por la ley aplicable, nuestra responsabilidad frente a cualquier reclamo derivado de fallas
              atribuibles directamente a Tranqui Salud (por ejemplo, errores de nuestro software,
              indisponibilidad del servicio o incidentes de seguridad de nuestra infraestructura) se limita
              a{' '}
              <em>
                [COMPLETAR: monto fijo razonable, o el equivalente a X meses de suscripción abonada por el
                profesional, según corresponda — pendiente de definición]
              </em>
              . Esta limitación no aplica en casos de dolo o culpa grave de Tranqui Salud, ni en aquellos
              supuestos en que la ley no permita su limitación.
            </p>
          </Section>

          <Section id="suspension" title="14. Suspensión y baja de cuentas">
            <p>
              Podés dar de baja tu cuenta en cualquier momento desde tu panel de Configuración, sin
              necesidad de contactar a soporte. Si por alguna razón la baja no está disponible de forma
              autogestionada para tu tipo de cuenta, podés solicitarla escribiendo a{' '}
              <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a>, y procesaremos la baja en un plazo
              razonable.
            </p>
            <p>
              Tranqui Salud puede suspender o eliminar una cuenta, con aviso previo cuando sea
              razonablemente posible, ante incumplimientos graves de estos Términos, uso fraudulento de la
              Plataforma, o por requerimiento de una autoridad competente.
            </p>
            <p>
              Los registros de historia clínica y recetas electrónicas se conservan según los plazos
              legales aplicables (ver sección 10 de nuestra <a href="/privacidad">Política de Privacidad</a>)
              incluso después de la baja de una cuenta.
            </p>
          </Section>

          <Section id="cambios" title="15. Modificaciones a estos términos">
            <p>
              Podemos actualizar estos Términos para reflejar cambios en la Plataforma o en la normativa
              aplicable. Publicaremos cualquier cambio en esta misma página, indicando la fecha de última
              actualización. Si el cambio es sustancial, te lo notificaremos por email o dentro de la
              Plataforma antes de que entre en vigencia.
            </p>
          </Section>

          <Section id="legislacion" title="16. Ley aplicable, jurisdicción y resolución de conflictos">
            <p>
              Estos Términos se rigen por las leyes de la República Argentina. Ante cualquier controversia
              derivada de su interpretación o aplicación, las partes se someten a la jurisdicción de los
              tribunales ordinarios competentes, sin perjuicio de las normas de protección al consumidor
              que puedan resultar aplicables.
            </p>
            <p>
              Si actuás como consumidor o usuario en los términos de la Ley 24.240, podés optar además por
              acudir al Sistema de Resolución de Conflictos en las Relaciones de Consumo (COPREC) o al
              Tribunal Arbitral de Defensa del Consumidor, cuando corresponda según tu jurisdicción, sin
              perjuicio de tu derecho a acudir a la vía judicial.
            </p>
          </Section>

          <Section id="generales" title="17. Disposiciones generales">
            <p>
              <strong>Divisibilidad:</strong> si alguna cláusula de estos Términos fuera declarada inválida
              o inaplicable por autoridad competente, las restantes cláusulas mantendrán su plena vigencia.
            </p>
            <p>
              <strong>Notificaciones:</strong> las notificaciones que Tranqui Salud deba realizarte
              conforme a estos Términos se considerarán válidas si se envían al email registrado en tu
              cuenta y/o mediante aviso dentro de la Plataforma.
            </p>
          </Section>

          <Section id="contacto" title="18. Contacto">
            <p>
              Ante cualquier duda sobre estos Términos, escribinos a{' '}
              <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a>.
            </p>
            <p style={{ color: 'var(--color-text-secondary)', fontSize: 'var(--text-sm)' }}>
              Responsable de la Plataforma: {RESPONSABLE_NOMBRE} (CUIT {RESPONSABLE_CUIT}).
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
