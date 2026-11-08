import { randomUUID } from 'node:crypto';
import { expect, test } from '@playwright/test';
import { createClient } from 'redis';

function required(name) {
  const value = process.env[name];
  if (!value) throw new Error(`Falta la variable ${name}`);
  return value;
}

function endpoint(variable, path) {
  return `${required(variable).replace(/\/+$/, '')}${path}`;
}

test.beforeEach(() => {
  if (!['dev', 'local'].includes(process.env.E2E_ENV)) {
    throw new Error('Usa E2E_ENV=dev o E2E_ENV=local y una cuenta exclusiva de pruebas.');
  }
});

async function submitLogin(page, password) {
  await page.goto(endpoint('E2E_WEB_URL', '/login'), { waitUntil: 'domcontentloaded' });
  await page.getByPlaceholder('Username', { exact: true }).fill(required('E2E_HEALTH_USERNAME'));
  await page.getByPlaceholder('Password', { exact: true }).fill(password);
  const [response] = await Promise.all([
    page.waitForResponse(res =>
      new URL(res.url()).pathname.endsWith('/api/v1/auth/login') && res.request().method() === 'POST'),
    page.getByText('SIGN IN', { exact: true }).click(),
  ]);
  return response;
}

async function signIn(page) {
  const response = await submitLogin(page, required('E2E_HEALTH_PASSWORD'));
  expect(response.status(), 'El servicio auth aceptó la cuenta de pruebas').toBe(200);
  await expect(page.getByRole('link', { name: 'REPORT', exact: true })).toBeVisible();
}

function nextAlert(page) {
  return new Promise(resolve => page.once('dialog', async dialog => {
    const message = dialog.message();
    await dialog.accept();
    resolve(message);
  }));
}

test('login rechaza una contraseña incorrecta', async ({ page }) => {
  const alert = nextAlert(page);
  const response = await submitLogin(page, randomUUID());
  expect(response.status()).toBe(401);
  expect(await alert).toMatch(/Invalid username or password/i);
  await expect(page.getByPlaceholder('Username')).toBeVisible();
});

test('login válido muestra la pantalla de entrada', async ({ page }) => {
  await signIn(page);
});

test('enviar una respuesta del cuestionario muestra confirmación', async ({ page, request }) => {
  const formUrl = endpoint('E2E_FORM_URL', '/api/v1/questionnaires');
  let active = await request.get(`${formUrl}/active`);
  if (active.status() === 404 && process.env.E2E_SEED_QUESTIONNAIRE === '1') {
    const created = await request.post(formUrl, {
      data: {
        title: 'Health Screening (E2E)',
        version: 1,
        isActive: true,
        questions: [{ text: 'Do you have a fever?', type: 'YES_NO', orderIndex: 0 }],
      },
    });
    expect(created.ok(), 'Se pudo crear el cuestionario de pruebas').toBe(true);
    active = await request.get(`${formUrl}/active`);
  }
  expect(active.ok(), 'El ambiente de pruebas necesita un cuestionario activo').toBe(true);
  const questionnaire = await active.json();
  const question = questionnaire.questions.find(item => item.type === 'YES_NO');
  expect(question, 'El cuestionario debe incluir una pregunta YES_NO').toBeDefined();

  await signIn(page);
  await page.getByRole('link', { name: 'REPORT', exact: true }).click();
  await expect(page.getByText(questionnaire.title, { exact: true })).toBeVisible();
  await page.getByTestId(`question-${question.id}`).getByText('NO', { exact: true }).click();
  const alert = nextAlert(page);
  const [response] = await Promise.all([
    page.waitForResponse(res =>
      new URL(res.url()).pathname.endsWith('/api/v1/surveys') && res.request().method() === 'POST'),
    page.getByText('SUBMIT REPORT', { exact: true }).click(),
  ]);
  expect(response.ok(), 'Form aceptó el envío del cuestionario').toBe(true);
  expect(response.request().postDataJSON().responses[question.id]).toBe('NO');
  expect((await response.json()).responses[question.id]).toBe('NO');
  expect(await alert).toContain('Thank you for your report');
  await expect(page.getByRole('link', { name: 'REPORT', exact: true })).toBeVisible();
});

async function verifyQr(request, healthStatus, status, valid) {
  const login = await request.post(endpoint('E2E_AUTH_URL', '/api/v1/auth/login'), {
    data: { username: required('E2E_HEALTH_USERNAME'), password: required('E2E_HEALTH_PASSWORD') },
  });
  expect(login.status()).toBe(200);
  const { anonymousId, token } = await login.json();
  expect(anonymousId).toMatch(/^[a-f0-9-]{36}$/i);
  expect(typeof token).toBe('string');

  const redisUrl = new URL(required('E2E_REDIS_URL'));
  if (!['127.0.0.1', 'localhost', '[::1]'].includes(redisUrl.hostname)) {
    throw new Error('Conecta Redis por loopback o por un túnel SSH al Redis de dev.');
  }
  const redis = createClient({
    url: redisUrl.href,
    socket: { connectTimeout: 5000, reconnectStrategy: false },
  });
  redis.on('error', () => console.error('Redis de pruebas no está disponible.'));
  await redis.connect();
  const key = `user:status:${anonymousId}`;
  try {
    const [previous, ttl] = await redis.multi().get(key).pTTL(key).exec();
    const expiresAt = Date.now() + ttl;
    try {
      await redis.set(key, healthStatus, { expiration: { type: 'EX', value: 60 } });
      const qr = await request.get(endpoint('E2E_AUTH_URL', '/api/v1/auth/qr/generate'), {
        headers: { Authorization: `Bearer ${token}` },
      });
      expect(qr.status(), 'Auth emitió un QR de acceso').toBe(200);
      const { qrToken } = await qr.json();
      const validation = await request.post(endpoint('E2E_GATEWAY_URL', '/api/v1/gate/validate'), {
        data: { token: qrToken },
      });
      expect(validation.status()).toBe(200);
      const result = await validation.json();
      expect({ valid: result.valid, status: result.status }, `Validación para ${healthStatus}`)
        .toEqual({ valid, status });
      if (!valid) {
        expect(result.message, 'El rechazo corresponde al estado de salud del usuario')
          .not.toBe('Invalid or Expired Token');
      }
    } finally {
      if (previous === null || (ttl >= 0 && expiresAt <= Date.now())) {
        await redis.del(key);
      } else if (ttl >= 0) {
        await redis.set(key, previous, { expiration: { type: 'PXAT', value: expiresAt } });
      } else {
        await redis.set(key, previous);
      }
    }
  } finally {
    redis.destroy();
  }
}

test('gateway permite el acceso con un QR de una cuenta ACTIVE', async ({ request }) => {
  await verifyQr(request, 'ACTIVE', 'GREEN', true);
});

test('gateway rechaza el acceso con un QR de una cuenta SUSPECT', async ({ request }) => {
  await verifyQr(request, 'SUSPECT', 'RED', false);
});
