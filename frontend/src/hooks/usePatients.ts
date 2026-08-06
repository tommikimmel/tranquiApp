import { useState, useEffect, useCallback } from 'react'
import { api } from '../api/api'

export interface Patient {
  id: number
  nombre: string
  email: string
  telefono: string
  dni: string
  direccion: string
  obraSocial: string
  numAfiliado: string
  ultimaVisita: string
  prioridadClinica: string
  sinTurno?: boolean
  apellido?: string | null
  sexo?: string | null
  fechaNacimiento?: string | null
  cuil?: number | null
  mail?: string | null
  tipoDocumento?: string | null
  numeroDocumento?: number | null
  datosOfuscado?: string | null
  credencial?: {
    codEntidad?: number | null
    pan?: string | null
    plan?: string | null
    token?: string | null
  }
  domicilio?: {
    calle?: string | null
    numero?: string | null
    piso?: string | null
    dpto?: string | null
    codigoPostal?: string | null
    localidad?: string | null
    provincia?: string | null
    pais?: string | null
  }
  unreadMessagesCount?: number
}

// Shared patient directory fetch + search-filter logic used by both PatientsView
// (chat) and ClinicalHistoryView (ficha/seguimiento/informes), so the two entry
// points list/search the exact same patient roster without duplicating the fetch.
export function usePatients() {
  const [patients, setPatients] = useState<Patient[]>([])
  const [loadingPatients, setLoadingPatients] = useState(true)
  const [searchQuery, setSearchQuery] = useState('')

  const fetchPatients = useCallback(() => {
    setLoadingPatients(true)
    return Promise.all([
      api.getPacientesAtendidos().catch(() => []),
      api.getChatCanales().catch(() => [])
    ])
      .then(([pacientesRes, canalesRes]: [any[], any[]]) => {
        const list: Patient[] = pacientesRes || []
        const canales = canalesRes || []

        // Map channel recent activity for sorting (canales are ordered by backend DESC by ultimoMensaje)
        const channelOrderMap = new Map<string, { orderIndex: number; ultimoMensaje?: string }>()
        canales.forEach((c: any, idx: number) => {
          const keyId = c.id != null ? String(c.id) : null
          const keyEmail = c.email ? c.email.toLowerCase() : null
          const info = { orderIndex: idx, ultimoMensaje: c.ultimoMensaje }
          if (keyId) channelOrderMap.set(keyId, info)
          if (keyEmail) channelOrderMap.set(keyEmail, info)
        })

        const sorted = [...list].sort((a, b) => {
          const infoA = channelOrderMap.get(String(a.id)) || (a.email ? channelOrderMap.get(a.email.toLowerCase()) : undefined)
          const infoB = channelOrderMap.get(String(b.id)) || (b.email ? channelOrderMap.get(b.email.toLowerCase()) : undefined)

          if (infoA && infoB) {
            return infoA.orderIndex - infoB.orderIndex
          }
          if (infoA) return -1
          if (infoB) return 1

          const unreadA = a.unreadMessagesCount || 0
          const unreadB = b.unreadMessagesCount || 0
          if (unreadA !== unreadB) {
            return unreadB - unreadA
          }

          return a.nombre.localeCompare(b.nombre)
        })

        setPatients(sorted)
        return sorted
      })
      .catch((err: any) => {
        console.error("Error al cargar pacientes:", err)
        return []
      })
      .finally(() => {
        setLoadingPatients(false)
      })
  }, [])

  useEffect(() => {
    fetchPatients()
  }, [fetchPatients])

  const filteredPatients = patients.filter(p =>
    p.nombre.toLowerCase().includes(searchQuery.toLowerCase()) ||
    p.email.toLowerCase().includes(searchQuery.toLowerCase()) ||
    (p.dni && p.dni.includes(searchQuery))
  )

  return {
    patients,
    setPatients,
    loadingPatients,
    searchQuery,
    setSearchQuery,
    filteredPatients,
    fetchPatients,
  }
}

export function getInitials(name: string) {
  if (!name) return 'P'
  const parts = name.split(' ')
  return parts.map(p => p[0]).join('').substring(0, 2).toUpperCase()
}
