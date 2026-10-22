export type RangoObservabilidad = '1h' | '24h' | '7d';
export type CategoriaObservabilidad = 'recursos' | 'trafico' | 'latencia' | 'eventos';

export interface ServicioSaludDetalle {
  id: string;
  host: string;
  puerto: number;
  estado: string;
}

export interface ServicioSalud {
  nombre: string;
  estado: 'UP' | 'DOWN' | 'DEGRADADO' | 'NO_REGISTRADO' | 'DESCONOCIDO' | string;
  version: string;
  instancias: number;
  detalles: ServicioSaludDetalle[];
}

export interface AdminSaludResponse {
  consulConectado: boolean;
  servicios: ServicioSalud[];
  timestamp: string;
}

export interface PuntoMetrica {
  timestamp: number;
  valor: number;
}

export interface SerieMetrica {
  nombre: string;
  puntos: PuntoMetrica[];
}

export interface MetricasRecursos {
  cpuVps: PuntoMetrica[];
  ramVps: PuntoMetrica[];
  discoLibreGb: number;
  discoTotalGb: number;
  discoPorcentajeLibre: number;
  contenedoresCpu: SerieMetrica[];
  contenedoresRam: SerieMetrica[];
}

export interface MetricasTrafico {
  reqTotalPorMinuto: PuntoMetrica[];
  errores5xx: PuntoMetrica[];
  rechazos429: PuntoMetrica[];
  porServicio: SerieMetrica[];
}

export interface RutaLenta {
  ruta: string;
  latenciaMs: number;
}

export interface MetricasLatencia {
  p50: PuntoMetrica[];
  p95: PuntoMetrica[];
  p99: PuntoMetrica[];
  rutasMasLentas: RutaLenta[];
}

export interface ColaRabbitMQ {
  cola: string;
  mensajes: number;
}

export interface MetricasEventos {
  outboxPendientes: PuntoMetrica[];
  outboxAntiguedadSegundos: PuntoMetrica[];
  colasRabbitMQ: ColaRabbitMQ[];
  dlqRabbitMQ: ColaRabbitMQ[];
}

export interface ObservabilidadResponse<T> {
  categoria: CategoriaObservabilidad;
  rango: RangoObservabilidad;
  timestamp: number;
  vmConectado?: boolean;
  metricas: T;
}

export type AdminObservabilidadResponse<T> = ObservabilidadResponse<T>;
