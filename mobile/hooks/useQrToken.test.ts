import { renderHook, act, waitFor } from '@testing-library/react-native';
import axios from 'axios';
import { useQrToken } from './useQrToken';

jest.mock('axios');
const mockedGet = axios.get as jest.Mock;

const qrResponse = (qrToken: string, expiresIn = '60') => ({ data: { qrToken, expiresIn } });

describe('useQrToken', () => {
  beforeEach(() => {
    jest.useFakeTimers();
    mockedGet.mockReset();
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  test('pide un QR a auth con el JWT de la sesion y arranca la cuenta en 60 s', async () => {
    mockedGet.mockResolvedValueOnce(qrResponse('qr-1'));

    const { result } = renderHook(() => useQrToken('test-id', 'auth-token'));

    await waitFor(() => expect(result.current.token).toBe('qr-1'));
    expect(result.current.timeLeft).toBe(60);
    expect(mockedGet).toHaveBeenCalledWith(
      expect.stringContaining('/api/v1/auth/qr/generate'),
      { headers: { Authorization: 'Bearer auth-token' } },
    );
  });

  test('no pide QR sin anonymousId o sin sesion', () => {
    const { result: sinId } = renderHook(() => useQrToken(null, 'auth-token'));
    const { result: sinSesion } = renderHook(() => useQrToken('test-id', null));

    expect(sinId.current.token).toBeNull();
    expect(sinSesion.current.token).toBeNull();
    expect(mockedGet).not.toHaveBeenCalled();
  });

  test('descuenta un segundo por segundo', async () => {
    mockedGet.mockResolvedValueOnce(qrResponse('qr-1'));
    const { result } = renderHook(() => useQrToken('test-id', 'auth-token'));
    await waitFor(() => expect(result.current.token).toBe('qr-1'));

    act(() => {
      jest.advanceTimersByTime(1000);
    });

    expect(result.current.timeLeft).toBe(59);
  });

  test('rota el QR y reinicia la cuenta al llegar a cero', async () => {
    mockedGet
      .mockResolvedValueOnce(qrResponse('qr-1'))
      .mockResolvedValueOnce(qrResponse('qr-2'));
    const { result } = renderHook(() => useQrToken('test-id', 'auth-token'));
    await waitFor(() => expect(result.current.token).toBe('qr-1'));

    act(() => {
      jest.advanceTimersByTime(60000);
    });

    await waitFor(() => expect(result.current.token).toBe('qr-2'));
    expect(result.current.timeLeft).toBe(60);
    expect(mockedGet).toHaveBeenCalledTimes(2);
  });

  test('usa la vigencia en segundos que informa auth', async () => {
    mockedGet.mockResolvedValueOnce(qrResponse('qr-90', '90'));

    const { result } = renderHook(() => useQrToken('test-id', 'auth-token'));

    await waitFor(() => expect(result.current.token).toBe('qr-90'));
    expect(result.current.timeLeft).toBe(90);
  });
});
