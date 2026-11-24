// Deletes bug reports and install counts whose expireAt has passed. Firestore's own TTL policies need a billing
// account, so a daily GitHub job runs this instead (ADR 0060). It needs FIREBASE_CLEANUP_KEY, a service account's JSON
// key with the Cloud Datastore User role. With FIRESTORE_EMULATOR_HOST set it runs against the emulator, for tests.
import { createSign } from 'node:crypto';

const COLLECTIONS = ['reports', 'installs'];
const PAGE = 400;

const emulator = process.env.FIRESTORE_EMULATOR_HOST;
const key = emulator ? null : JSON.parse(process.env.FIREBASE_CLEANUP_KEY ?? '{}');
const project = emulator ? process.env.FIREBASE_PROJECT : key.project_id;
if (!project) throw new Error('No project: set FIREBASE_CLEANUP_KEY');

const root = emulator ? `http://${emulator}/v1` : 'https://firestore.googleapis.com/v1';
const documents = `${root}/projects/${project}/databases/(default)/documents`;
const token = emulator ? 'owner' : await accessToken(key);

/** Swaps a signed JWT for an access token, the OAuth flow for service accounts. */
async function accessToken({ client_email: email, private_key: privateKey }) {
  const base64url = (value) => Buffer.from(value).toString('base64url');
  const now = Math.floor(Date.now() / 1000);
  const claims = {
    iss: email,
    scope: 'https://www.googleapis.com/auth/datastore',
    aud: 'https://oauth2.googleapis.com/token',
    iat: now,
    exp: now + 600,
  };
  const unsigned = `${base64url(JSON.stringify({ alg: 'RS256', typ: 'JWT' }))}.${base64url(JSON.stringify(claims))}`;
  const signature = createSign('RSA-SHA256').update(unsigned).sign(privateKey, 'base64url');
  const response = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion: `${unsigned}.${signature}`,
    }),
  });
  if (!response.ok) throw new Error(`Sign-in failed: ${response.status}`);
  return (await response.json()).access_token;
}

async function call(path, body) {
  const response = await fetch(`${documents}:${path}`, {
    method: 'POST',
    headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!response.ok) throw new Error(`${path} failed: ${response.status} ${await response.text()}`);
  return response.json();
}

const cutoff = new Date().toISOString();
for (const collection of COLLECTIONS) {
  let deleted = 0;
  for (;;) {
    const rows = await call('runQuery', {
      structuredQuery: {
        from: [{ collectionId: collection }],
        where: { fieldFilter: { field: { fieldPath: 'expireAt' }, op: 'LESS_THAN', value: { timestampValue: cutoff } } },
        select: { fields: [{ fieldPath: '__name__' }] },
        limit: PAGE,
      },
    });
    const names = rows.flatMap((row) => (row.document ? [row.document.name] : []));
    if (names.length === 0) break;
    const result = await call('batchWrite', { writes: names.map((name) => ({ delete: name })) });
    const failed = (result.status ?? []).filter((status) => (status.code ?? 0) !== 0);
    // Stops rather than asking for the same documents again.
    if (failed.length > 0) throw new Error(`${failed.length} ${collection} not deleted: ${failed[0].message}`);
    deleted += names.length;
  }
  console.log(`${collection}: ${deleted} deleted`);
}
