/**
 * Global Configuration for CircleGuard Mobile (HU-07).
 *
 * Como se resuelve cada URL base, en este orden:
 *   1. EXPO_PUBLIC_<SERVICIO>_BASE_URL, si se definio para ese servicio.
 *   2. EXPO_PUBLIC_API_BASE_URL, compartida por todos los servicios.
 *      Con "/" la app llama a /api/v1/... en su mismo origen: es como se compila
 *      la imagen web desplegada, y el proxy HTTPS de cada ambiente reparte las rutas.
 *      Asi la MISMA imagen sirve en dev, stage y prod.
 *   3. El puerto local de cada servicio (desarrollo con docker-compose.dev.yml).
 *
 * Expo solo incrusta las variables escritas literalmente como process.env.EXPO_PUBLIC_*,
 * por eso no se leen con un alias ni con un indice dinamico.
 */
export function resolveBaseUrl(
  specific: string | undefined,
  shared: string | undefined,
  localDefault: string,
): string {
  const chosen = [specific, shared].find((value) => value !== undefined && value.trim() !== '');
  if (chosen === undefined) {
    return localDefault;
  }
  // Sin barra final: las llamadas ya empiezan con /api/v1. "/" queda como "" (mismo origen).
  return chosen.trim().replace(/\/+$/, '');
}

const SHARED_API_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL;

export const AUTH_BASE_URL = resolveBaseUrl(process.env.EXPO_PUBLIC_AUTH_BASE_URL, SHARED_API_BASE_URL, 'http://localhost:8180'); // Auth Service
export const DASHBOARD_BASE_URL = resolveBaseUrl(process.env.EXPO_PUBLIC_DASHBOARD_BASE_URL, SHARED_API_BASE_URL, 'http://localhost:8084'); // Dashboard Service
export const FILE_BASE_URL = resolveBaseUrl(process.env.EXPO_PUBLIC_FILE_BASE_URL, SHARED_API_BASE_URL, 'http://localhost:8085'); // File Service
export const FORM_BASE_URL = resolveBaseUrl(process.env.EXPO_PUBLIC_FORM_BASE_URL, SHARED_API_BASE_URL, 'http://localhost:8086'); // Form Service
export const GATEWAY_BASE_URL = resolveBaseUrl(process.env.EXPO_PUBLIC_GATEWAY_BASE_URL, SHARED_API_BASE_URL, 'http://localhost:8087'); // Gateway Service
export const IDENTITY_BASE_URL = resolveBaseUrl(process.env.EXPO_PUBLIC_IDENTITY_BASE_URL, SHARED_API_BASE_URL, 'http://localhost:8083'); // Identity Service
export const NOTIFICATION_BASE_URL = resolveBaseUrl(process.env.EXPO_PUBLIC_NOTIFICATION_BASE_URL, SHARED_API_BASE_URL, 'http://localhost:8082'); // Notification Service
export const PROMOTION_BASE_URL = resolveBaseUrl(process.env.EXPO_PUBLIC_PROMOTION_BASE_URL, SHARED_API_BASE_URL, 'http://localhost:8088'); // Promotion Service
