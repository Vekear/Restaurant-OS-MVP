#!/usr/bin/env node
/**
 * =============================================================================
 * 餐厅点餐系统 MVP — 秘钥/凭证泄露扫描（G5 门禁）
 *
 * 背景：brief §4.6/§4.7 与 ADR-0006 要求 AccessKeySecret、微信 AppSecret、
 *       JWT_SECRET、数据库密码等真实凭证永不入库；.env.example 只放占位值。
 *       本脚本对工作区（不含 node_modules/dist/target 等生成物）做正则门禁。
 *
 * 扫描范围：仓库全量文本文件，排除 node_modules、dist、target、.git、.runtime、
 *           .agent-teams、unpackage、coverage、test-results、playwright-report、
 *           blob-report、storage、logs、.idea、.vscode、.cache、.vite
 *
 * 检测规则（任一命中即 exit 1）：
 *   1. 私钥块：-----BEGIN (RSA|EC|DSA|OPENSSH|ENCRYPTED)? PRIVATE KEY-----
 *   2. 阿里云 AccessKeyId：LTAI + 12~30 位字母数字（真实形态）
 *   3. 敏感键被硬编码字面量：JWT_SECRET / TABLE_QR_SECRET / DB_PASSWORD /
 *      MYSQL_ROOT_PASSWORD / ACCESS_KEY_SECRET / WECHAT_APP_SECRET 等，
 *      以及通用键 password / secret / token / api_key / access_key（阈值见
 *      minLengthFor(normalizedKey)）
 *
 * 放行（不算违规，--verbose 可查看明细）：
 *   - .env.example / *.env.example（契约要求只放占位值，按 T19a 契约放行）
 *   - 本地未提交的 .env / .env.local / .env.*.local（已 .gitignore）
 *   - 环境变量引用 ${VAR} / ${VAR:-default}、模板 {{...}}、<...>
 *   - 明确的占位/演示值：your-*、example、placeholder、change-me、dummy、
 *     fake、dev-only、local-only、mock、test-only、fixture、★★★、****、... 等
 *   - brief §6/§9 公布的本机演示值：123456、root123456、admin123 等
 *     （这些值本身不是秘密；真实生产凭证一律违规）
 *   - 生成文件：锁文件、.map、min.js/css（非人工维护，另行按需审计）
 *
 * 退出码：0 = 未发现真实凭证；1 = 发现违规；2 = 运行错误（参数/IO）
 * 运行：  node scripts/check-no-secrets.mjs [--root <项目根>] [--verbose]
 * =============================================================================
 */

import { readdirSync, readFileSync, statSync } from 'node:fs';
import { basename, dirname, extname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import process from 'node:process';

const DEFAULT_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');

const EXCLUDED_DIR_NAMES = new Set([
  'node_modules',
  'dist',
  'target',
  '.git',
  '.runtime',
  '.agent-teams',
  'unpackage',
  'coverage',
  'test-results',
  'playwright-report',
  'blob-report',
  'storage',
  'logs',
  '.idea',
  '.vscode',
  '.cache',
  '.vite',
]);

const EXCLUDED_FILE_PATTERNS = [
  /^\.env$/i,
  /^\.env\.local$/i,
  /^\.env\..*\.local$/i,
  /\.env\.example$/i,
  /^pnpm-lock\.yaml$/i,
  /^package-lock\.json$/i,
  /^yarn\.lock$/i,
  /^npm-shrinkwrap\.json$/i,
  /\.min\.(?:js|css)$/i,
  /\.map$/i,
];

const BINARY_EXTENSIONS = new Set([
  '.png', '.jpg', '.jpeg', '.gif', '.webp', '.ico', '.bmp', '.avif', '.tiff',
  '.woff', '.woff2', '.ttf', '.eot', '.otf',
  '.jar', '.class', '.zip', '.gz', '.tgz', '.7z', '.rar', '.bz2', '.xz',
  '.pdf', '.xlsx', '.xls', '.docx', '.doc', '.pptx', '.ppt',
  '.mp3', '.mp4', '.wav', '.webm', '.mov', '.avi',
  '.exe', '.dll', '.so', '.dylib', '.bin', '.dat', '.db', '.sqlite',
  '.p12', '.jks', '.keystore', '.der',
]);

const MAX_FILE_BYTES = 2 * 1024 * 1024;

/** 明确按“密钥键名”识别的配置键（归一化后：小写、下划线分隔） */
const SPECIFIC_SECRET_KEYS = new Set([
  'jwt_secret',
  'jwt_secret_key',
  'table_qr_secret',
  'db_password',
  'mysql_root_password',
  'mysql_password',
  'database_password',
  'datasource_password',
  'spring_datasource_password',
  'redis_password',
  'spring_redis_password',
  'oss_access_key_secret',
  'oss_access_key_id',
  'access_key_secret',
  'access_key_id',
  'aliyun_access_key_secret',
  'wechat_app_secret',
  'wx_app_secret',
  'wechat_secret',
  'payment_secret',
  'payment_callback_secret',
  'pay_secret',
  'admin_password',
]);

/** 通用敏感键：按词包含识别（password / secret / token / api_key / access_key） */
const GENERIC_SECRET_KEY_RE = /(password|passwd|secret|token|api_?key|access_?key)/;

/** brief §6/§9 明确公布的本机演示环境值：不是秘密，放行且不告警 */
const KNOWN_DEMO_VALUES = new Set([
  '123456',
  'root123456',
  'admin123',
  'merchant123',
  'customer123',
  'root',
  'password',
  'changeme',
]);

/** 占位/演示值标记：命中即放行（真实凭证不应包含这些片段） */
const PLACEHOLDER_RE =
  /(your[-_ ]?|example|sample|placeholder|dummy|fake|change[-_ ]?me|changeme|replace[-_ ]?(?:with|me)|not[-_ ]?a[-_ ]?real|redacted|masked|dev[-_ ]?only|local[-_ ]?only|please[-_ ]?change|test[-_]|fixture|mock|todo|xxx|★|\*{3,}|\.\.\.|…|示例|占位|演示|请替换|替换|随机)/i;

const PRIVATE_KEY_RE = /-----BEGIN (?:[A-Z0-9]+ )?PRIVATE KEY-----/;
const ALIYUN_ACCESS_KEY_RE = /\bLTAI[A-Za-z0-9]{12,30}\b/g;

const ASSIGN_RE =
  /\b([A-Za-z_][A-Za-z0-9_.-]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|`([^`]*)`|([^\s,;)\]}]+))/g;
const COLON_RE =
  /(?:"([A-Za-z_][A-Za-z0-9_.-]*)"|'([A-Za-z_][A-Za-z0-9_.-]*)'|([A-Za-z_][A-Za-z0-9_.-]*))\s*:\s*(?:"([^"]*)"|'([^']*)'|`([^`]*)`|([^\s,;#}]+))/g;

const violations = [];
const exemptions = [];
const stats = {
  files: 0,
  envExample: 0,
  localEnv: 0,
  generated: 0,
  binary: 0,
  large: 0,
  skippedDirs: 0,
  symlinks: 0,
};

function toPosix(filePath) {
  return filePath.replace(/\\/g, '/');
}

function usage() {
  console.log(
    [
      '用法: node scripts/check-no-secrets.mjs [--root <项目根>] [--verbose]',
      '',
      '  --root <目录>  指定仓库根目录（默认：脚本所在仓库）',
      '  --verbose      打印放行明细（.env.example、占位值等）',
      '  --help         显示本帮助',
      '',
      '退出码: 0 = 未发现真实凭证；1 = 存在违规；2 = 运行错误',
    ].join('\n'),
  );
}

function parseArgs(argv) {
  const options = { root: DEFAULT_ROOT, verbose: false };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === '--root') {
      const next = argv[i + 1];
      if (!next) throw new Error('--root 缺少目录参数');
      options.root = resolve(next);
      i += 1;
    } else if (arg.startsWith('--root=')) {
      const value = arg.slice('--root='.length);
      if (!value) throw new Error('--root 缺少目录参数');
      options.root = resolve(value);
    } else if (arg === '--verbose' || arg === '-v') {
      options.verbose = true;
    } else if (arg === '--help' || arg === '-h') {
      usage();
      process.exit(0);
    } else {
      throw new Error(`未知参数: ${arg}（使用 --help 查看用法）`);
    }
  }
  return options;
}

function normalizeKey(key) {
  return key
    .replace(/([a-z0-9])([A-Z])/g, '$1_$2')
    .replace(/[^A-Za-z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '')
    .toLowerCase();
}

function isSecretKey(normalizedKey) {
  return SPECIFIC_SECRET_KEYS.has(normalizedKey) || GENERIC_SECRET_KEY_RE.test(normalizedKey);
}

/** 不同敏感键的最小可疑长度（过短的值多半是开关/端口/枚举） */
function minLengthFor(normalizedKey) {
  if (/password|passwd/.test(normalizedKey)) return 6;
  if (/token/.test(normalizedKey)) return 24;
  if (/access_?key/.test(normalizedKey)) return 20;
  return 16;
}

function maskValue(value) {
  const head = value.slice(0, 2);
  return `${head}***（长度 ${value.length}）`;
}

/**
 * 判定一个字面量是否应放行。
 * @returns {{allowed: boolean, reason?: string}}
 */
function evaluateLiteral(rawValue, normalizedKey) {
  let value = String(rawValue ?? '').trim();
  value = value.replace(/[;,)]+$/, '');

  if (value === '' || value === '""' || value === "''") {
    return { allowed: true, reason: 'empty' };
  }
  if (
    value.includes('${') ||
    value.includes('{{') ||
    value.includes('%(') ||
    /^<.*>$/.test(value) ||
    value.startsWith('<')
  ) {
    return { allowed: true, reason: 'env/template' };
  }
  if (/^[$%][A-Za-z_(]/.test(value)) {
    return { allowed: true, reason: 'variable-reference' };
  }

  const lower = value.toLowerCase();
  if (KNOWN_DEMO_VALUES.has(lower)) {
    return { allowed: true, reason: 'known-demo-value' };
  }
  if (PLACEHOLDER_RE.test(value)) {
    return { allowed: true, reason: 'placeholder' };
  }

  const minLength = minLengthFor(normalizedKey);
  if (value.length >= minLength) return { allowed: false };
  return { allowed: true, reason: `too-short(<${minLength})` };
}

/** 无引号右值是否为表达式/属性访问（不是字面量） */
function isExpressionLike(value) {
  // JWT 形如 eyJ...x.y.z，无引号时也可能出现在 YAML/日志里，按字面量处理
  if (/^eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/.test(value)) return false;
  // 正则/模式常量：/.../ 或包含正则元字符
  if (value.startsWith('/')) return true;
  return /[()[\]{}<>=+*|&!?,]/.test(value) || value.includes('.') || /\s/.test(value);
}

function recordExemption(file, line, detail) {
  exemptions.push({ file, line, detail });
}

function scanLine(lineText, lineNo, relPath) {
  const privateKey = PRIVATE_KEY_RE.exec(lineText);
  if (privateKey) {
    violations.push({
      file: relPath,
      line: lineNo,
      type: 'private-key',
      detail: '出现私钥块（PRIVATE KEY），必须立即移除并轮换',
    });
  }

  for (const match of lineText.matchAll(ALIYUN_ACCESS_KEY_RE)) {
    const token = match[0];
    if (PLACEHOLDER_RE.test(token)) {
      recordExemption(relPath, lineNo, `阿里云 AccessKeyId 占位值 ${token}（第 ${lineNo} 行）`);
    } else {
      violations.push({
        file: relPath,
        line: lineNo,
        type: 'aliyun-access-key-id',
        detail: `硬编码阿里云 AccessKeyId: ${token.slice(0, 6)}***（长度 ${token.length}）`,
      });
    }
  }

  const seen = new Set();
  const inspect = (rawKey, rawValue, quoted) => {
    if (!rawKey || rawValue === undefined) return;
    const normalizedKey = normalizeKey(rawKey);
    if (!isSecretKey(normalizedKey)) return;
    const dedupeKey = `${normalizedKey}|${rawValue}`;
    if (seen.has(dedupeKey)) return;
    seen.add(dedupeKey);

    if (!quoted && isExpressionLike(String(rawValue))) {
      recordExemption(
        relPath,
        lineNo,
        `敏感键 ${rawKey} 右值为表达式/引用（非字面量，第 ${lineNo} 行）`,
      );
      return;
    }

    const verdict = evaluateLiteral(rawValue, normalizedKey);
    if (verdict.allowed) {
      recordExemption(
        relPath,
        lineNo,
        `敏感键 ${rawKey} 放行（${verdict.reason}，值 ${maskValue(String(rawValue).trim())}，第 ${lineNo} 行）`,
      );
      return;
    }
    violations.push({
      file: relPath,
      line: lineNo,
      type: 'hardcoded-secret',
      detail: `敏感键 ${rawKey} 硬编码字面量 ${maskValue(String(rawValue).trim())}`,
    });
  };

  for (const match of lineText.matchAll(ASSIGN_RE)) {
    const quoted = match[2] !== undefined || match[3] !== undefined || match[4] !== undefined;
    inspect(match[1], match[2] ?? match[3] ?? match[4] ?? match[5], quoted);
  }
  for (const match of lineText.matchAll(COLON_RE)) {
    const quoted = match[4] !== undefined || match[5] !== undefined || match[6] !== undefined;
    inspect(match[1] ?? match[2] ?? match[3], match[4] ?? match[5] ?? match[6] ?? match[7], quoted);
  }
}

function shouldSkipFile(fullPath) {
  const name = basename(fullPath);
  if (EXCLUDED_FILE_PATTERNS.some((pattern) => pattern.test(name))) {
    if (/\.env\.example$/i.test(name)) stats.envExample += 1;
    else if (/^\.env/.test(name)) stats.localEnv += 1;
    else stats.generated += 1;
    return true;
  }
  return false;
}

function walk(dir, files, root) {
  let entries;
  try {
    entries = readdirSync(dir, { withFileTypes: true });
  } catch (err) {
    throw new Error(`无法读取目录 ${toPosix(relative(root, dir))}: ${err.message}`);
  }
  entries.sort((a, b) => a.name.localeCompare(b.name));
  for (const entry of entries) {
    const fullPath = join(dir, entry.name);
    if (entry.isSymbolicLink()) {
      stats.symlinks += 1;
      continue;
    }
    if (entry.isDirectory()) {
      if (EXCLUDED_DIR_NAMES.has(entry.name)) {
        stats.skippedDirs += 1;
        continue;
      }
      walk(fullPath, files, root);
    } else if (entry.isFile()) {
      if (shouldSkipFile(fullPath)) continue;
      files.push(fullPath);
    }
  }
}

function scanFile(filePath, root) {
  const relPath = toPosix(relative(root, filePath));
  const ext = extname(filePath).toLowerCase();
  if (BINARY_EXTENSIONS.has(ext)) {
    stats.binary += 1;
    return;
  }

  let size;
  try {
    size = statSync(filePath).size;
  } catch (err) {
    throw new Error(`无法读取文件 ${relPath}: ${err.message}`);
  }
  if (size > MAX_FILE_BYTES) {
    stats.large += 1;
    return;
  }

  let text;
  try {
    text = readFileSync(filePath, 'utf8');
  } catch (err) {
    throw new Error(`无法读取文件 ${relPath}: ${err.message}`);
  }
  if (text.includes('\u0000')) {
    stats.binary += 1;
    return;
  }

  stats.files += 1;
  const lines = text.split(/\r?\n/);
  for (let i = 0; i < lines.length; i += 1) {
    const line = lines[i];
    if (line.length === 0) continue;
    if (!/secret|password|passwd|token|api[_-]?key|access[_-]?key|LTAI|PRIVATE KEY/i.test(line)) {
      continue;
    }
    scanLine(line, i + 1, relPath);
  }
}

function main() {
  let options;
  try {
    options = parseArgs(process.argv.slice(2));
  } catch (err) {
    console.error(`[ERROR] ${err.message}`);
    process.exit(2);
  }

  const startedAt = process.hrtime.bigint();
  const root = options.root;

  console.log('============================================================');
  console.log(' restaurant-mvp · 秘钥/凭证泄露扫描（G5 门禁）');
  console.log('============================================================');
  console.log(` 项目根目录 : ${root}`);
  console.log(` 排除目录   : ${[...EXCLUDED_DIR_NAMES].join('、')}`);
  console.log(' 放行策略   : .env.example / 本地 .env / 环境变量引用 / 占位与演示值（--verbose 查看明细）');
  console.log('');

  try {
    const files = [];
    walk(root, files, root);
    for (const file of files) scanFile(file, root);
  } catch (err) {
    console.error(`[ERROR] 扫描失败: ${err.message}`);
    process.exit(2);
  }

  const elapsedMs = Number(process.hrtime.bigint() - startedAt) / 1e6;

  if (options.verbose) {
    console.log(`[INFO] 跳过目录 ${stats.skippedDirs} 个、符号链接 ${stats.symlinks} 个`);
    console.log(
      `[INFO] 放行：.env.example ${stats.envExample} 个、本地 .env ${stats.localEnv} 个、生成文件 ${stats.generated} 个、二进制 ${stats.binary} 个、超限 ${stats.large} 个`,
    );
    for (const exemption of exemptions.slice(0, 80)) {
      console.log(`[放行] ${exemption.file}  ${exemption.detail}`);
    }
    if (exemptions.length > 80) console.log(`[放行] ... 其余 ${exemptions.length - 80} 条省略`);
    if (exemptions.length > 0) console.log('');
  }

  const statsLine = `扫描文件 ${stats.files} 个 / 放行占位或引用 ${exemptions.length} 处 / 耗时 ${elapsedMs.toFixed(1)} ms`;

  if (violations.length > 0) {
    console.log(`[FAIL] 发现 ${violations.length} 处疑似真实凭证：`);
    const seen = new Set();
    for (const violation of violations) {
      const key = `${violation.file}|${violation.line}|${violation.type}|${violation.detail}`;
      if (seen.has(key)) continue;
      seen.add(key);
      console.log(`  - ${violation.file}:${violation.line}  [${violation.type}] ${violation.detail}`);
    }
    console.log('');
    console.log('处理建议：移除并轮换该凭证；配置改用环境变量注入；占位值请写成 your-xxx / change-me / ${VAR}。');
    console.log(`结果: 失败（${violations.length} 处违规 / ${statsLine}）→ exit 1`);
    process.exit(1);
  }

  console.log('[PASS] 未发现真实凭证（阿里云 AK/Secret、私钥、硬编码 JWT_SECRET/DB_PASSWORD 等）');
  console.log('');
  console.log(`结果: 通过（0 违规 / ${statsLine}）→ exit 0`);
  process.exit(0);
}

main();
