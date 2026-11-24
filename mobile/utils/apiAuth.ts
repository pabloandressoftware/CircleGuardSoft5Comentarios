import { storage } from './storage';

/**
 * Cabeceras con el JWT emitido por auth-service. Los endpoints protegidos
 * (admin, analiticas, archivos) lo exigen; los publicos lo ignoran.
 */
export async function authHeaders(): Promise<Record<string, string>> {
  const token = await storage.getItem('circleguard_token');
  return token ? { Authorization: `Bearer ${token}` } : {};
}

export async function withAuth(
  init: RequestInit = {},
): Promise<RequestInit> {
  const auth = await authHeaders();
  return {
    ...init,
    headers: {
      ...(init.headers || {}),
      ...auth,
    },
  };
}
