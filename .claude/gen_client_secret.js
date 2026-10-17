// 生成 clientdetails.appSecret 的 AES/GCM 密文（与 ClientSecretCipher 完全一致）
// 派生：SHA-256(配置密钥) 取前 16 字节 -> AES-128；输出 Base64(IV(12) || 密文 || GCM tag(16))
const crypto = require('crypto');

const CONFIG_KEY = 'rbac-client-secret-key-please-change-in-production-0123456789';
const key = crypto.createHash('sha256').update(CONFIG_KEY, 'utf8').digest().slice(0, 16);

function encrypt(plain) {
  const iv = crypto.randomBytes(12);
  const c = crypto.createCipheriv('aes-128-gcm', key, iv);
  const ct = Buffer.concat([c.update(plain, 'utf8'), c.final()]);
  return Buffer.concat([iv, ct, c.getAuthTag()]).toString('base64');
}

function decrypt(b64) {
  const buf = Buffer.from(b64, 'base64');
  const iv = buf.slice(0, 12);
  const tag = buf.slice(buf.length - 16);
  const ct = buf.slice(12, buf.length - 16);
  const d = crypto.createDecipheriv('aes-128-gcm', key, iv);
  d.setAuthTag(tag);
  return Buffer.concat([d.update(ct), d.final()]).toString('utf8');
}

// 自检：库里 order-center 的密文解出来必须是 'order123'
const SAMPLE = 'yIBwx+lZmE1+AWObckeSg200SjEGeNXqms+f7p8BB3ZigJhE';
const roundTrip = decrypt(SAMPLE);
console.log('自检 order-center 密文解出 ->', JSON.stringify(roundTrip),
            roundTrip === 'order123' ? 'OK' : '!! 密钥或算法对不上，先别用下面的结果');

const plain = process.argv[2] || 'portal123';
const enc = encrypt(plain);
console.log('明文        :', plain);
console.log('密文(Base64):', enc);
console.log('回解校验    :', JSON.stringify(decrypt(enc)));
