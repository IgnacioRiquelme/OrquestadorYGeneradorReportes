const express = require('express');
const bodyParser = require('body-parser');
const jwt = require('jsonwebtoken');
const fs = require('fs');
const path = require('path');
const cors = require('cors');

const DB_FILE = path.join(__dirname, 'activations.json');
const SECRET = process.env.LICENSE_SECRET || 'dev_secret_change_me';
const PORT = process.env.PORT || 3000;

if (!fs.existsSync(DB_FILE)) {
  fs.writeFileSync(DB_FILE, JSON.stringify({ activations: {} }, null, 2));
}

function loadDB() {
  return JSON.parse(fs.readFileSync(DB_FILE, 'utf8'));
}

function saveDB(db) {
  fs.writeFileSync(DB_FILE, JSON.stringify(db, null, 2));
}

const app = express();
app.use(bodyParser.json());
app.use(cors());

// Simple activation: accepts any code starting with "KEY-" or the special "TEST-KEY"
app.post('/activate', (req, res) => {
  const { code, installationId } = req.body || {};
  if (!code || !installationId) return res.status(400).json({ error: 'code and installationId required' });

  // Very simple validation logic for demo
  const valid = code === 'TEST-KEY' || code.startsWith('KEY-');
  if (!valid) return res.status(403).json({ error: 'Invalid activation code' });

  const db = loadDB();
  const now = Math.floor(Date.now() / 1000);
  const expiresIn = 60 * 60 * 24 * 30; // 30 days
  const payload = { installationId, iat: now, exp: now + expiresIn };
  const token = jwt.sign(payload, SECRET);

  db.activations[installationId] = { code, token, activatedAt: now, expiresAt: now + expiresIn, revoked: false };
  saveDB(db);

  res.json({ token, expiresAt: db.activations[installationId].expiresAt });
});

app.post('/revoke', (req, res) => {
  const { installationId } = req.body || {};
  if (!installationId) return res.status(400).json({ error: 'installationId required' });

  const db = loadDB();
  if (!db.activations[installationId]) return res.status(404).json({ error: 'Not found' });

  db.activations[installationId].revoked = true;
  saveDB(db);
  res.json({ ok: true });
});

app.post('/status', (req, res) => {
  const { installationId } = req.body || {};
  if (!installationId) return res.status(400).json({ error: 'installationId required' });
  const db = loadDB();
  const record = db.activations[installationId];
  if (!record) return res.json({ status: 'unknown' });
  if (record.revoked) return res.json({ status: 'revoked' });
  return res.json({ status: 'active', expiresAt: record.expiresAt });
});

app.listen(PORT, () => {
  console.log(`License server running on http://localhost:${PORT}`);
});
