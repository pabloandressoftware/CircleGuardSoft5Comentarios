import { resolveBaseUrl } from './Config';

describe('resolveBaseUrl', () => {
  test('sin variables usa el puerto local del servicio', () => {
    expect(resolveBaseUrl(undefined, undefined, 'http://localhost:8180')).toBe('http://localhost:8180');
  });

  test('con EXPO_PUBLIC_API_BASE_URL="/" llama al mismo origen', () => {
    expect(resolveBaseUrl(undefined, '/', 'http://localhost:8180')).toBe('');
  });

  test('la URL compartida se usa sin barra final', () => {
    expect(resolveBaseUrl(undefined, 'https://dev.circleguard.edu/', 'http://localhost:8180')).toBe('https://dev.circleguard.edu');
  });

  test('la URL del servicio tiene prioridad sobre la compartida', () => {
    expect(resolveBaseUrl('http://10.0.0.5:8180', '/', 'http://localhost:8180')).toBe('http://10.0.0.5:8180');
  });

  test('una variable vacia cuenta como no definida', () => {
    expect(resolveBaseUrl('', '  ', 'http://localhost:8087')).toBe('http://localhost:8087');
  });

  test('una llamada relativa queda bajo /api/v1 del mismo origen', () => {
    const base = resolveBaseUrl(undefined, '/', 'http://localhost:8180');
    expect(`${base}/api/v1/auth/login`).toBe('/api/v1/auth/login');
  });
});
