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
  const professionalName = medico.nombre ? `${medico.nombre} ${medico.apellido || ''}`.trim() : 'Lic. María Paula Rossi';
  doc.text(professionalName, 120, currentY);

  doc.setFont('helvetica', 'normal');
  doc.setFontSize(9);
  doc.setTextColor(...textMuted);
  const titleStr = medico.titulo || 'Psiquiatra y Especialista en Neurociencias';
  const matriculaStr = medico.matricula || medico.matriculaNumero ? `M.N. ${medico.matricula || medico.matriculaNumero}` : 'M.N. 49281';
  doc.text(titleStr, 120, currentY + 4);
  doc.text(matriculaStr, 120, currentY + 8);

  // Save the PDF
  const filename = `informe_${String(report.tipoInforme).toLowerCase().replace(/_/g, '_')}_${patientFullName.toLowerCase().replace(/\s+/g, '_')}_${report.fecha || 'sindate'}.pdf`;
  doc.save(filename);
}
