// La foto de perfil se guarda como data URL en usuario.fotoUrl y viaja en cada respuesta de
// /api/medicos (la landing la pide entera). Un PNG original de un celular pesaba ~650 KB en base64
// por profesional, así que acá se reduce siempre antes de guardarla: lado mayor de 400 px (alcanza
// para la tarjeta y el perfil en pantallas retina) y JPEG, que la deja en ~20–40 KB.

export const PROFILE_PHOTO_MAX_SIDE = 400
export const PROFILE_PHOTO_QUALITY = 0.82

// Dimensiones que entran en un cuadrado de maxSide manteniendo la proporción; nunca agranda.
export function fitWithin(width: number, height: number, maxSide = PROFILE_PHOTO_MAX_SIDE): { width: number; height: number } {
  if (width <= 0 || height <= 0) return { width: 0, height: 0 }
  const scale = Math.min(1, maxSide / Math.max(width, height))
  return { width: Math.max(1, Math.round(width * scale)), height: Math.max(1, Math.round(height * scale)) }
}

// Dibuja la imagen reducida sobre fondo blanco (un PNG con transparencia quedaría negro en JPEG).
export function renderProfilePhoto(source: CanvasImageSource, sourceWidth: number, sourceHeight: number): string | null {
  const { width, height } = fitWithin(sourceWidth, sourceHeight)
  if (!width || !height) return null
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext('2d')
  if (!ctx) return null
  ctx.fillStyle = '#ffffff'
  ctx.fillRect(0, 0, width, height)
  ctx.imageSmoothingQuality = 'high'
  ctx.drawImage(source, 0, 0, width, height)
  return canvas.toDataURL('image/jpeg', PROFILE_PHOTO_QUALITY)
}

export function fileToProfilePhoto(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      URL.revokeObjectURL(url)
      const dataUrl = renderProfilePhoto(img, img.naturalWidth, img.naturalHeight)
      if (dataUrl) resolve(dataUrl)
      else reject(new Error('No se pudo procesar la imagen'))
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('El archivo no es una imagen válida'))
    }
    img.src = url
  })
}
