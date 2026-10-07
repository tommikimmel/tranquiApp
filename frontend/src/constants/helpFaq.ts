// Preguntas frecuentes del centro de ayuda (HelpFaqModal). Fuente: investigacion/02-preguntas-
// frecuentes.md; los videos salen de los guiones de investigacion/03 y viven en public/videos/.
// Las respuestas usan un markup mínimo que interpreta HelpFaqModal: **negrita**, líneas que
// empiezan con "- " (viñetas) o "1. " (lista numerada); una línea en blanco separa párrafos.

export type FaqAudience = 'paciente' | 'profesional'

export interface FaqVideo {
  id: string
  title: string
  // Algunos videos cubren varias preguntas: start (segundos) arranca en el tramo que corresponde.
  start?: number
}

export interface FaqItem {
  id: string
  audience: FaqAudience
  question: string
  answer: string
  video?: FaqVideo
}

export function faqVideoSrc(video: FaqVideo): string {
  return `/videos/${video.id}.webm`
}

// Subtítulos (WebVTT) sincronizados con la narración de cada video.
export function faqVideoSubtitles(video: FaqVideo): string {
  return `/videos/${video.id}.vtt`
}

export const FAQ_ITEMS: FaqItem[] = [
  // ── Pacientes ────────────────────────────────────────────────────────────────────────────
  {
    id: 'P01',
    audience: 'paciente',
    question: '¿Cómo me creo una cuenta?',
    answer: `Desde **"Iniciar sesión"** (arriba a la derecha) elegís **crear cuenta como paciente**. Completás nombre, apellido, email, contraseña, fecha de nacimiento, documento y teléfono, y si querés tu obra social. Aceptás los términos y la política de privacidad.

Te llega un **código de 6 dígitos** al email: lo ingresás y la cuenta queda activa. También podés **ingresar con Google** sin crear contraseña.`,
    video: { id: 'V-P01', title: 'Cómo crear tu cuenta' },
  },
  {
    id: 'P02',
    audience: 'paciente',
    question: 'Olvidé mi contraseña, ¿cómo la recupero?',
    answer: `En la pantalla de ingreso tocá **"Recuperar contraseña"**, poné tu email y vas a recibir un código. Ingresalo junto con tu nueva contraseña.

Si entraste con Google, no tenés contraseña: usá **"Ingresar con Google"**.`,
    video: { id: 'V-P01', title: 'Recuperar tu contraseña', start: 75 },
  },
  {
    id: 'P03',
    audience: 'paciente',
    question: '¿Cómo encuentro un profesional?',
    answer: `En la página de inicio filtrá por **profesión** (psicólogo/a o psiquiatra), **especialidad**, **modalidad** (online o presencial) y **motivo de consulta**: los chips salen de los temas que atienden los profesionales. Cada tarjeta muestra la disponibilidad próxima.

Tocá la tarjeta para ver el perfil completo: formación, matrícula, experiencia, a quién atiende y precio.`,
  },
  {
    id: 'P04',
    audience: 'paciente',
    question: '¿Qué diferencia hay entre un psicólogo y un psiquiatra?',
    answer: `El **psicólogo/a** (licenciado/a en Psicología) hace psicoterapia y **no puede recetar medicación**.

El **psiquiatra** es médico: puede hacer diagnóstico, indicar tratamiento farmacológico y **emitir recetas**.

Si no sabés por dónde empezar, una primera consulta con un psicólogo/a suele ser lo indicado, y si hace falta te deriva.`,
  },
  {
    id: 'P05',
    audience: 'paciente',
    question: '¿Cómo reservo un turno?',
    answer: `En el perfil del profesional elegí el **tipo de turno** (particular u obra social), la **modalidad** (online o presencial) y el **día y horario**. Confirmá tus datos de contacto y pasás al pago.

El horario queda **bloqueado 5 minutos** para vos mientras pagás.`,
    video: { id: 'V-P05', title: 'Cómo reservar y pagar un turno' },
  },
  {
    id: 'P06',
    audience: 'paciente',
    question: '¿Cómo pago el turno?',
    answer: `El pago se hace con **Mercado Pago** (tarjeta, dinero en cuenta, etc.). Cuando se aprueba, el turno pasa a **confirmado** y te llega un mail con todos los datos.

Tranqui no cobra recargo: el pago va directo a la cuenta de Mercado Pago del profesional.`,
    video: { id: 'V-P05', title: 'Cómo reservar y pagar un turno' },
  },
  {
    id: 'P07',
    audience: 'paciente',
    question: 'Cerré la ventana de pago, ¿perdí el turno?',
    answer: `El horario se reserva **5 minutos**. Si pagaste y cerraste la pestaña, no pasa nada: el sistema verifica el pago solo y el turno se confirma.

Si no llegaste a pagar, entrá a **"Mis Turnos"** (menú de tu perfil, arriba a la derecha) y tocá **"Pagar turno"**. Si pasaron los 5 minutos y el horario se liberó, vas a tener que reservar de nuevo.`,
    video: { id: 'V-P07', title: 'Cerré la ventana de pago, ¿perdí el turno?' },
  },
  {
    id: 'P08',
    audience: 'paciente',
    question: '¿Puedo atenderme con mi obra social o prepaga?',
    answer: `Depende del profesional. Si trabaja con tu cobertura, al reservar vas a ver la opción de **obra social**, donde elegís la cobertura y cargás tu **número de afiliado**. Algunos profesionales tienen tarifas especiales para coberturas puntuales.

Podés guardar tu obra social en **Mi Cuenta** para no cargarla cada vez.`,
  },
  {
    id: 'P09',
    audience: 'paciente',
    question: '¿Cómo me uno a la videollamada?',
    answer: `En los turnos **online** se genera un link de **Google Meet**. Lo encontrás en el mail de confirmación, en **"Mis Turnos"** y en el inicio el día del turno (**"Unirse a la llamada"**).

El link **se activa 10 minutos antes**. Buscá un lugar tranquilo y con buena conexión.`,
  },
  {
    id: 'P10',
    audience: 'paciente',
    question: 'Tengo un turno presencial, ¿cómo llego?',
    answer: `En la confirmación, en el mail y en **"Mis Turnos"** está la **dirección del consultorio** con un **mapa**. Te recomendamos llegar 10 minutos antes.`,
  },
  {
    id: 'P11',
    audience: 'paciente',
    question: '¿Cómo cancelo un turno? ¿Me devuelven la plata?',
    answer: `En **"Mis Turnos"** tocá **"Cancelar"** en el turno.

- Si faltan **más de 48 horas**, el pago se **reembolsa automáticamente** al mismo medio de pago.
- Si faltan **menos de 48 horas**, el turno se cancela pero **no corresponde reembolso**.

Antes de confirmar, la app te muestra cuál de los dos casos aplica. Si el turno lo cancela el profesional, el reembolso es total siempre.`,
  },
  {
    id: 'P12',
    audience: 'paciente',
    question: '¿Puedo cambiar el día u horario de mi turno?',
    answer: `Por ahora la reprogramación la hace **el profesional**. Escribile por el chat o respondé el mail de confirmación.

Si preferís, podés cancelar y reservar otro horario, teniendo en cuenta la regla de las 48 horas.`,
  },
  {
    id: 'P13',
    audience: 'paciente',
    question: 'Me llegó un mail para confirmar asistencia, ¿qué hago?',
    answer: `Uno o dos días antes del turno (a las 9 de la mañana) te llega un mail con dos botones:

- **"Confirmar asistencia"** le avisa al profesional que vas a estar.
- **"No podré asistir"** cancela el turno con la misma política de reembolso que una cancelación normal.

No hace falta iniciar sesión.`,
  },
  {
    id: 'P14',
    audience: 'paciente',
    question: '¿Cómo pido una receta, un certificado o un informe?',
    answer: `En el perfil del profesional, además de las consultas, aparecen los **documentos** que ofrece (receta, certificado, informe), cada uno con su precio. Elegís el documento, pagás y el profesional te lo entrega: las recetas electrónicas te llegan firmadas y el resto por mail.

Las **recetas solo las emiten psiquiatras**.`,
  },
  {
    id: 'P15',
    audience: 'paciente',
    question: '¿Cómo hablo con mi profesional?',
    answer: `Desde la tarjeta o el perfil del profesional, con el botón **"Chatear"**. El chat no reemplaza a la sesión ni sirve para urgencias.`,
  },
  {
    id: 'P16',
    audience: 'paciente',
    question: '¿Cómo cambio mis datos, mi obra social o las notificaciones?',
    answer: `En **Mi Cuenta** podés editar tus datos personales y documento, tu obra social y número de afiliado, tu contraseña, y activar o desactivar las notificaciones por **email** (confirmaciones y recordatorios de tus turnos).`,
  },
  {
    id: 'P17',
    audience: 'paciente',
    question: '¿Mis datos son confidenciales?',
    answer: `Sí. Los datos de salud son **datos sensibles** (Ley 25.326) y solo los ve tu profesional.

En **Mi Cuenta** podés **solicitar una copia** de todos tus datos, y en la **Política de privacidad** se detalla cómo se guardan y con quién se comparten (por ejemplo, Mercado Pago para cobrar).`,
  },
  {
    id: 'P18',
    audience: 'paciente',
    question: '¿Cómo pido una copia de mis datos?',
    answer: `En **Mi Cuenta** tocá **"Solicitar copia de mis datos"**. El equipo te la envía por email.`,
  },
  {
    id: 'P19',
    audience: 'paciente',
    question: '¿Cómo elimino mi cuenta?',
    answer: `En **Mi Cuenta**, al final, está **eliminar cuenta**: escribís **ELIMINAR** para confirmar.

Es **permanente** y **cancela todos tus turnos futuros**. Revisá antes los reembolsos que te correspondan.`,
  },
  {
    id: 'P20',
    audience: 'paciente',
    question: 'Estoy en una crisis, ¿qué hago?',
    answer: `Tranqui **no es un servicio de urgencias**. Si estás en riesgo, comunicate ya con las líneas gratuitas que figuran en la página de inicio (24 hs, todo el país) o acercate a la guardia más cercana.`,
  },
  {
    id: 'P21',
    audience: 'paciente',
    question: 'Tengo un problema, ¿cómo contacto a soporte?',
    answer: `Tocá **"Soporte"** (menú de tu perfil o acceso rápido del inicio), contanos qué pasa (por ejemplo, "Problema con un pago") y se crea un **ticket**. La respuesta te llega en la misma sección y por email.`,
  },

  // ── Profesionales ────────────────────────────────────────────────────────────────────────
  {
    id: 'R01',
    audience: 'profesional',
    question: '¿Cómo me registro como profesional?',
    answer: `En **"Iniciar sesión"** elegí crear cuenta como **profesional**. Indicás tu **profesión principal** (Licenciado/a en Psicología, Médico/a Psiquiatra u otro profesional de salud mental), especialidad, título y **matrícula** (tipo MN/MP o Colegio de Psicólogos, provincia y número).

Después cargás tus **datos fiscales** (CUIT, condición frente al IVA, razón social, domicilio fiscal), que se usan para las facturas de tu suscripción. La app te sugiere un plan según tu profesión. Por último confirmás tu email con un código de 6 dígitos.`,
    video: { id: 'V-R01', title: 'Cómo registrarte como profesional' },
  },
  {
    id: 'R02',
    audience: 'profesional',
    question: 'Ya me registré, ¿por qué no aparezco en el buscador?',
    answer: `Para aparecer tenés que cumplir **todo** esto:

1. **Perfil completo**: datos personales y documento, CUIL, matrícula, título, especialidad, **foto**, descripción, a quién atendés, temas que tratás, institución de formación, años de experiencia y experiencia laboral. Si atendés presencial, también el **domicilio**.
2. **Mercado Pago conectado**, para poder cobrar.
3. **Suscripción activa**.
4. **Verificación** de tu matrícula por el equipo de Tranqui.`,
    video: { id: 'V-R02', title: 'Qué necesitás para aparecer en el buscador' },
  },
  {
    id: 'R03',
    audience: 'profesional',
    question: '¿Cómo completo mi perfil público?',
    answer: `En **Configuración** completás todo lo que ve el paciente: foto, descripción, especialidad, modalidades (online y/o presencial), dirección del consultorio con el mapa, formación, experiencia laboral, temas que tratás, pacientes que atendés (adultos, adolescentes…) y tus redes (Instagram, LinkedIn).`,
  },
  {
    id: 'R04',
    audience: 'profesional',
    question: '¿Cómo y cuándo me verifican?',
    answer: `El equipo de Tranqui revisa tu matrícula con los datos que cargaste. En **Configuración** vas a ver **"Cuenta no verificada"** hasta que se apruebe, y después **"Cuenta verificada"**.

Si tarda o hay un dato mal cargado, escribí a soporte.`,
  },
  {
    id: 'R05',
    audience: 'profesional',
    question: '¿Cómo conecto Mercado Pago para cobrar mis turnos?',
    answer: `En **Configuración → Integración con Mercado Pago** tocá conectar e iniciá sesión en tu cuenta de Mercado Pago para autorizar a Tranqui. A partir de ahí, lo que pagan tus pacientes va **directo a tu cuenta**.

Sin esta conexión no podés aparecer en el buscador.`,
  },
  {
    id: 'R06',
    audience: 'profesional',
    question: '¿Cómo conecto Google Calendar y qué se sincroniza?',
    answer: `En **Configuración → Integración con Google Calendar** autorizás tu cuenta de Google. Se sincronizan dos cosas:

- **Cada turno confirmado** (online o presencial) se crea como evento en tu calendario. Los online traen el link de Meet.
- **Tus eventos personales** de Google bloquean esos horarios en Tranqui, para que nadie reserve cuando estás ocupado/a.

Los turnos confirmados antes de conectar se suben solos en los minutos siguientes.`,
  },
  {
    id: 'R07',
    audience: 'profesional',
    question: '¿Cómo configuro mis días y horarios de atención?',
    answer: `En **Agenda → Disponibilidad semanal** cargás los bloques de cada día (por ejemplo, lunes de 9 a 13 y de 14 a 18), la **duración del turno** y el **intervalo entre turnos**. La app calcula los **horarios reservables** que ve el paciente.`,
  },
  {
    id: 'R08',
    audience: 'profesional',
    question: '¿Cómo defino mis honorarios y servicios?',
    answer: `En **Honorarios y servicios** creás tus servicios:

- **Consultas**, que **ocupan un turno** en tu agenda. Pueden tener precio distinto para online y presencial, y una cobertura específica.
- **Recetas y documentos**, que **no ocupan turno**.

Podés reordenarlos (así los ve el paciente), archivarlos y **aumentar todos los precios** en un porcentaje con redondeo. Al cargar el precio podés escribir "90k" o "90 mil".`,
  },
  {
    id: 'R09',
    audience: 'profesional',
    question: '¿Dónde veo mis turnos del día y cómo inicio una sesión?',
    answer: `En **Inicio** están las **sesiones de hoy** y los **próximos eventos**. Cada turno online tiene el botón de **Google Meet**. Desde el turno también podés **marcar la asistencia** del paciente.`,
  },
  {
    id: 'R10',
    audience: 'profesional',
    question: '¿Cómo reprogramo o cancelo el turno de un paciente?',
    answer: `Desde el turno en **Inicio** o en la **Agenda**: con **reprogramar** elegís la nueva fecha y hora y el paciente recibe el aviso; con **cancelar**, el paciente recibe el reembolso.

Si tenés Google Calendar conectado, el evento se actualiza o se borra solo.`,
  },
  {
    id: 'R11',
    audience: 'profesional',
    question: 'Un paciente pidió un documento, ¿cómo se lo entrego?',
    answer: `En **Inicio → Documentos solicitados** ves las recetas, certificados e informes pagados y pendientes de entregar. La receta electrónica la emitís desde la app y le llega firmada al paciente. Los demás documentos se los enviás por mail.`,
  },
  {
    id: 'R12',
    audience: 'profesional',
    question: '¿Cómo emito una receta electrónica?',
    answer: `Solo para **médicos psiquiatras** con **código REFEPS** cargado en Configuración. Desde **"Emitir receta"** elegís al paciente (tiene que tener el perfil completo), el medicamento, la dosis y la frecuencia. La receta sale con tu sello y se valida con QBI2.

La emisión de recetas electrónicas **todavía no está habilitada**: estamos terminando la integración oficial y te vamos a avisar cuando esté disponible.`,
  },
  {
    id: 'R13',
    audience: 'profesional',
    question: '¿Para qué sirven las "Notas y pendientes"?',
    answer: `En la **Agenda** podés anotar recordatorios **clínicos** o **administrativos** (por ejemplo, "Llamar a la prepaga de Rossi"), marcarlos como **urgentes** y tacharlos cuando los resolvés.`,
  },
  {
    id: 'R14',
    audience: 'profesional',
    question: '¿Cómo veo y gestiono a mis pacientes?',
    answer: `En **Pacientes** está el listado de quienes se atendieron con vos: datos de contacto, cobertura y el chat. Si a un paciente le falta algún dato (por ejemplo, para emitirle una receta), la app te avisa qué campos completar.`,
  },
  {
    id: 'R15',
    audience: 'profesional',
    question: '¿Cuánto cuesta Tranqui y qué incluye cada plan?',
    answer: `- **Consultorio**: pensado para psicólogos/as. Agenda, cobros, perfil público y pacientes por zona.
- **Clínico**: pensado para psiquiatras. Todo lo anterior más el módulo de recetas electrónicas oficiales.

Los dos se pueden pagar **mensual** o **anual** (con 2 meses sin cargo). Los precios vigentes los ves en la pantalla de planes.`,
  },
  {
    id: 'R16',
    audience: 'profesional',
    question: '¿Tranqui me cobra comisión por cada turno?',
    answer: `**No.** Pagás solo la suscripción. Lo que cobra cada turno va directo a tu Mercado Pago, sin comisión de Tranqui; Mercado Pago aplica sus propias comisiones.`,
  },
  {
    id: 'R17',
    audience: 'profesional',
    question: '¿Cómo contrato o pago mi suscripción?',
    answer: `Cuando tu perfil está listo, la app te muestra los planes. Elegís el plan y el ciclo (**mensual o anual**) y pagás con **Mercado Pago**. El débito se renueva automáticamente.

Apenas Mercado Pago confirma el pago, tu perfil queda visible.`,
  },
  {
    id: 'R18',
    audience: 'profesional',
    question: '¿Cómo cancelo la renovación automática?',
    answer: `En **Configuración → Suscripción**, con **"Cancelar renovación automática"**. No se te vuelve a cobrar y **mantenés el acceso hasta el fin del período que ya pagaste**: la fecha aparece en pantalla y te llega por mail.

Si Mercado Pago no confirma la cancelación, la app te avisa y no se cancela nada, para que no te sigan debitando sin saberlo.`,
  },
  {
    id: 'R19',
    audience: 'profesional',
    question: 'Mi perfil está oculto, ¿cómo lo vuelvo a activar?',
    answer: `Si la suscripción venció o se canceló, al entrar vas a ver la pantalla para **volver al buscador**. Elegís plan y ciclo, pagás, y el perfil vuelve a ser visible apenas Mercado Pago confirma.

Tu agenda y tus pacientes se conservan.`,
  },
  {
    id: 'R20',
    audience: 'profesional',
    question: '¿Dónde descargo las facturas de mi suscripción?',
    answer: `Todavía no se emiten facturas: la integración con ARCA no está lista. Cuando se habilite, cada pago va a generar una **Factura C** descargable desde **Configuración → Suscripción**.`,
  },
  {
    id: 'R21',
    audience: 'profesional',
    question: '¿Cuándo y cómo cobro los turnos?',
    answer: `El paciente paga al reservar y el dinero se acredita en **tu cuenta de Mercado Pago** según los plazos de Mercado Pago.

Si cancelás vos, o el paciente cancela con más de 48 hs, el reembolso sale de esa misma cuenta.`,
  },
  {
    id: 'R22',
    audience: 'profesional',
    question: '¿Cómo contacto a soporte?',
    answer: `Desde **"Soporte"** (arriba a la derecha en tu panel), abriendo un ticket. La respuesta te llega en la misma sección y por email.`,
  },
]
