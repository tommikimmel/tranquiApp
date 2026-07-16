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
    return api.getPacientesAtendidos()
      .then((res: any) => {
        const list = res || []
        setPatients(list)
        return list
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
