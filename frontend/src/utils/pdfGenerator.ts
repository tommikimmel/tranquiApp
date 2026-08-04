// Helper to generate and download a clinical report PDF

export async function downloadReportPDF(report: any, fallbackPatient?: any) {
  const patient = report.paciente || fallbackPatient || {};
  const medico = report.medico || {};

  // 1. Dynamic jsPDF loader
  let jsPDFClass = (window as any).jspdf?.jsPDF;
  
  if (!jsPDFClass) {
    await new Promise<void>((resolve, reject) => {
      const script = document.createElement('script');
      script.src = 'https://cdnjs.cloudflare.com/ajax/libs/jspdf/2.5.1/jspdf.umd.min.js';
      script.id = 'jspdf-cdn-script';
      script.onload = () => resolve();
      script.onerror = () => reject(new Error('No se pudo cargar la librería de PDF (jsPDF).'));
      document.body.appendChild(script);
    });
    jsPDFClass = (window as any).jspdf?.jsPDF;
  }

  if (!jsPDFClass) {
    throw new Error('No se pudo inicializar la clase jsPDF.');
  }

  const doc = new jsPDFClass({
    orientation: 'portrait',
    unit: 'mm',
    format: 'a4'
  });

  // Color Palette
  const tealPrimary = [15, 118, 110]; // #0f766e
  const textDark = [30, 41, 59]; // #1e293b
  const textMuted = [100, 116, 139]; // #64748b
  const bgLight = [248, 250, 252]; // #f8fafc
  const borderLight = [226, 232, 240]; // #e2e8f0

  // Margins & Dimensions (A4 is 210 x 297 mm)
  const marginX = 20;
  let currentY = 20;
  const contentWidth = 210 - (marginX * 2);

  // --- Header ---
  doc.setFillColor(...tealPrimary);
  doc.rect(marginX, currentY, contentWidth, 8, 'F');
  currentY += 8;

  // Header Title
  doc.setTextColor(255, 255, 255);
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(12);
  doc.text('tranqui · INFORME CLÍNICO OFICIAL', marginX + 4, currentY - 3);

  // Institution Logo / Subtitle
  currentY += 8;
  doc.setTextColor(...textDark);
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(18);
  doc.text('Tranqui Neurociencias', marginX, currentY);

  doc.setFont('helvetica', 'normal');
  doc.setFontSize(9);
  doc.setTextColor(...textMuted);
  doc.text('Centro de Salud Mental & Telemedicina', marginX, currentY + 4);

  // Date alignment right
  const dateStr = `Fecha de emisión: ${report.fecha || new Date().toLocaleDateString('es-AR')}`;
  doc.text(dateStr, 210 - marginX - doc.getTextWidth(dateStr), currentY);

  currentY += 12;

  // Thin line divider
  doc.setDrawColor(...borderLight);
  doc.setLineWidth(0.5);
  doc.line(marginX, currentY, 210 - marginX, currentY);
  currentY += 6;

  // --- Patient Demographics Box ---
  doc.setFillColor(...bgLight);
  doc.rect(marginX, currentY, contentWidth, 34, 'F');
  doc.rect(marginX, currentY, contentWidth, 34, 'S');

  // Title for details
  doc.setTextColor(...tealPrimary);
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(10);
  doc.text('DATOS GENERALES DEL PACIENTE', marginX + 4, currentY + 6);

  // Label helper
  const drawLabelValue = (x: number, y: number, label: string, value: string) => {
    doc.setFont('helvetica', 'bold');
    doc.setTextColor(...textDark);
    doc.text(label, x, y);
    doc.setFont('helvetica', 'normal');
    doc.setTextColor(...textMuted);
    doc.text(value, x + doc.getTextWidth(label) + 1, y);
  };

  doc.setFontSize(9);
  // Column 1
  const patientFullName = `${patient.nombre || ''} ${patient.apellido || ''}`.trim() || 'No especificado';
  drawLabelValue(marginX + 4, currentY + 14, 'Nombre Completo:', patientFullName);

  const documentType = patient.tipoDocumento || 'DNI';
  const documentNumber = patient.numeroDocumento || patient.dni || 'No especificado';
  drawLabelValue(marginX + 4, currentY + 22, `${documentType}:`, String(documentNumber));

  const birthDate = patient.fechaNacimiento || 'No especificada';
  drawLabelValue(marginX + 4, currentY + 30, 'Fecha Nacimiento:', birthDate);

  // Column 2
  const hasHealthInsurance = !!(patient.obraSocial || patient.credencialPlan || patient.credencialPan);
  const insuranceName = hasHealthInsurance ? (patient.obraSocial || 'OSDE') : 'Ninguna / Particular';
  drawLabelValue(marginX + 100, currentY + 14, 'Obra Social:', insuranceName);

  if (hasHealthInsurance) {
    const planName = patient.credencialPlan || patient.credencial?.plan || 'No especificado';
    const affiliateNum = patient.credencialPan || patient.credencial?.pan || patient.numAfiliado || 'No especificado';
    drawLabelValue(marginX + 100, currentY + 22, 'Plan:', planName);
    drawLabelValue(marginX + 100, currentY + 30, 'Nro Afiliado:', affiliateNum);
  }

  currentY += 42;

  // --- Report Details Section ---
  doc.setTextColor(...tealPrimary);
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(11);
  doc.text(`TIPO DE INFORME: ${String(report.tipoInforme).replace(/_/g, ' ').toUpperCase()}`, marginX, currentY);
  currentY += 6;

  // Line below type
  doc.setDrawColor(...tealPrimary);
  doc.setLineWidth(0.8);
  doc.line(marginX, currentY, 210 - marginX, currentY);
  currentY += 8;

  // Detalle Clínico
  doc.setTextColor(...textDark);
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(11);
  doc.text('Detalle Clínico / Evolución:', marginX, currentY);
  currentY += 6;

  doc.setFont('helvetica', 'normal');
  doc.setFontSize(10);
  doc.setTextColor(...textDark);

  const clinicalText = report.contenido || 'No se ingresaron detalles clínicos en este informe.';
  const clinicalParagraphs = doc.splitTextToSize(clinicalText, contentWidth);
  doc.text(clinicalParagraphs, marginX, currentY);
  currentY += (clinicalParagraphs.length * 5) + 8;

  // Plan de Trabajo
  doc.setTextColor(...textDark);
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(11);
  doc.text('Plan de Trabajo / Indicaciones:', marginX, currentY);
  currentY += 6;

  doc.setFont('helvetica', 'normal');
  doc.setFontSize(10);
  doc.setTextColor(...textDark);

  const planText = report.planTrabajo || 'No se especificó plan de trabajo en este informe.';
  const planParagraphs = doc.splitTextToSize(planText, contentWidth);
  doc.text(planParagraphs, marginX, currentY);
  currentY += (planParagraphs.length * 5) + 15;

  // --- Professional Signature Box ---
  // Ensure we don't draw off the page
  if (currentY > 240) {
    doc.addPage();
    currentY = 30;
  }

  doc.setDrawColor(...borderLight);
  doc.setLineWidth(0.5);
  doc.line(120, currentY, 190, currentY);
  currentY += 5;

  doc.setFont('helvetica', 'bold');
  doc.setFontSize(10);
  doc.setTextColor(...textDark);
  const professionalName = medico.nombre ? `${medico.nombre} ${medico.apellido || ''}`.trim() : 'Médico Tratante';
  doc.text(professionalName, 120, currentY);

  doc.setFont('helvetica', 'normal');
  doc.setFontSize(9);
  doc.setTextColor(...textMuted);
  const titleStr = medico.titulo || medico.especialidad || 'Especialista en Salud Mental';
  const matriculaStr = medico.matricula || medico.matriculaNumero ? `M.N. ${medico.matricula || medico.matriculaNumero}` : 'M.N. S/N';
  doc.text(titleStr, 120, currentY + 4);
  doc.text(matriculaStr, 120, currentY + 8);

  // Save the PDF
  const filename = `informe_${String(report.tipoInforme || 'clinico').toLowerCase().replace(/_/g, '_')}_${patientFullName.toLowerCase().replace(/\s+/g, '_')}_${report.fecha || 'sindate'}.pdf`;
  doc.save(filename);
}

export async function downloadPrescriptionPDF(receta: any, sessionMedico?: any, sessionPaciente?: any) {
  const medico = receta.medico || sessionMedico || {};
  const paciente = receta.paciente || sessionPaciente || {};

  let jsPDFClass = (window as any).jspdf?.jsPDF;
  if (!jsPDFClass) {
    await new Promise<void>((resolve, reject) => {
      const script = document.createElement('script');
      script.src = 'https://cdnjs.cloudflare.com/ajax/libs/jspdf/2.5.1/jspdf.umd.min.js';
      script.id = 'jspdf-cdn-script';
      script.onload = () => resolve();
      script.onerror = () => reject(new Error('No se pudo cargar la librería jsPDF.'));
      document.body.appendChild(script);
    });
    jsPDFClass = (window as any).jspdf?.jsPDF;
  }

  if (!jsPDFClass) return;

  const doc = new jsPDFClass({ orientation: 'portrait', unit: 'mm', format: 'a4' });
  const tealPrimary = [15, 118, 110];
  const textDark = [30, 41, 59];
  const textMuted = [100, 116, 139];
  const bgLight = [248, 250, 252];
  const borderLight = [226, 232, 240];
  const marginX = 20;
  let currentY = 20;
  const contentWidth = 210 - (marginX * 2);

  // Header
  doc.setFillColor(...tealPrimary);
  doc.rect(marginX, currentY, contentWidth, 8, 'F');
  currentY += 8;
  doc.setTextColor(255, 255, 255);
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(12);
  doc.text('tranqui · RECETA MÉDICA MÓDULO PRESCRIPCIÓN', marginX + 4, currentY - 3);

  currentY += 8;
  doc.setTextColor(...textDark);
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(18);
  doc.text('Tranqui Neurociencias', marginX, currentY);

  doc.setFont('helvetica', 'normal');
  doc.setFontSize(9);
  doc.setTextColor(...textMuted);
  doc.text('Prescripción Médica Electrónica', marginX, currentY + 4);

  const fechaFormat = receta.fechaEmision ? (typeof receta.fechaEmision === 'string' && receta.fechaEmision.includes('T') ? receta.fechaEmision.split('T')[0].split('-').reverse().join('/') : String(receta.fechaEmision)) : new Date().toLocaleDateString('es-AR');
  const dateStr = `Fecha: ${fechaFormat}`;
  doc.text(dateStr, 210 - marginX - doc.getTextWidth(dateStr), currentY);

  currentY += 12;
  doc.setDrawColor(...borderLight);
  doc.setLineWidth(0.5);
  doc.line(marginX, currentY, 210 - marginX, currentY);
  currentY += 6;

  // Patient Box
  doc.setFillColor(...bgLight);
  doc.rect(marginX, currentY, contentWidth, 26, 'F');
  doc.rect(marginX, currentY, contentWidth, 26, 'S');

  doc.setTextColor(...tealPrimary);
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(10);
  doc.text('DATOS DEL PACIENTE', marginX + 4, currentY + 6);

  const patientName = `${paciente.nombre || ''} ${paciente.apellido || ''}`.trim() || 'Paciente';
  const dniStr = paciente.numeroDocumento ? `DNI: ${paciente.numeroDocumento}` : (paciente.dni ? `DNI: ${paciente.dni}` : '');

  doc.setFont('helvetica', 'bold');
  doc.setTextColor(...textDark);
  doc.setFontSize(10);
  doc.text(patientName, marginX + 4, currentY + 14);
  if (dniStr) {
    doc.setFont('helvetica', 'normal');
    doc.setTextColor(...textMuted);
    doc.setFontSize(9);
    doc.text(dniStr, marginX + 4 + doc.getTextWidth(patientName) + 6, currentY + 14);
  }

  currentY += 32;

  // Diagnosis if present
  if (receta.diagnostico) {
    doc.setFont('helvetica', 'bold');
    doc.setTextColor(...tealPrimary);
    doc.setFontSize(10);
    doc.text('DIAGNÓSTICO (CIE-10)', marginX, currentY);
    currentY += 5;
    doc.setFont('helvetica', 'normal');
    doc.setTextColor(...textDark);
    doc.setFontSize(9.5);
    doc.text(String(receta.diagnostico), marginX, currentY);
    currentY += 8;
  }

  // Medications Box
  doc.setFont('helvetica', 'bold');
  doc.setTextColor(...tealPrimary);
  doc.setFontSize(10);
  doc.text('MEDICACIÓN PRESCRIPTA', marginX, currentY);
  currentY += 6;

  doc.setFont('helvetica', 'normal');
  doc.setTextColor(...textDark);
  doc.setFontSize(10);

  const medsText = receta.medicamentos || '';
  const lines = doc.splitTextToSize(medsText, contentWidth);
  doc.text(lines, marginX, currentY);
  currentY += (lines.length * 6) + 6;

  // Indications
  if (receta.indicaciones) {
    doc.setFont('helvetica', 'bold');
    doc.setTextColor(...tealPrimary);
    doc.setFontSize(10);
    doc.text('INDICACIONES PARA EL PACIENTE', marginX, currentY);
    currentY += 5;
    doc.setFont('helvetica', 'normal');
    doc.setTextColor(...textDark);
    doc.setFontSize(9);
    const indLines = doc.splitTextToSize(receta.indicaciones, contentWidth);
    doc.text(indLines, marginX, currentY);
    currentY += (indLines.length * 5) + 10;
  }

  // Doctor Signature Block
  if (currentY > 230) {
    doc.addPage();
    currentY = 30;
  }

  doc.setDrawColor(...borderLight);
  doc.setLineWidth(0.5);
  doc.line(120, currentY, 190, currentY);
  currentY += 5;

  doc.setFont('helvetica', 'bold');
  doc.setFontSize(10);
  doc.setTextColor(...textDark);
  const docName = medico.nombre ? `${medico.nombre} ${medico.apellido || ''}`.trim() : 'Médico Prescriptor';
  doc.text(docName, 120, currentY);

  doc.setFont('helvetica', 'normal');
  doc.setFontSize(9);
  doc.setTextColor(...textMuted);
  const spec = medico.especialidad || medico.titulo || 'Psiquiatría';
  const mat = medico.matricula || medico.matriculaNumero ? `M.N. ${medico.matricula || medico.matriculaNumero}` : 'M.N. S/N';
  doc.text(spec, 120, currentY + 4);
  doc.text(mat, 120, currentY + 8);

  const pdfName = `receta_${patientName.toLowerCase().replace(/\s+/g, '_')}_${fechaFormat.replace(/\//g, '-')}.pdf`;
  doc.save(pdfName);
}
