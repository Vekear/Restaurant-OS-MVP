#!/usr/bin/env node
/**
 * =============================================================================
 * 餐厅点餐系统 MVP — 禁用项扫描（严禁 TypeScript）
 *
 * 背景：brief §3/§5 与 plan §1 要求 Web（Vue3 JavaScript）与微信小程序（UniApp
 *       Vue3 JavaScript）**禁止 TypeScript**；G5 门禁需可复现的自动检查。
 *
 * 扫描范围：web/、miniprogram/（递归；跳过 node_modules、dist、unpackage、
 *           .runtime、.git、coverage、test-results、playwright-report、
 *           blob-report、.cache、.vite、.idea、.vscode）
 *
 * 违规判定（任一命中即 exit 1）：
 *   1. TypeScript 源码文件：*.ts / *.tsx / *.mts / *.cts（含 *.d.ts）
 *   2. tsconfig 文件：tsconfig*.json
 *   3. Vue SFC 中出现 lang="ts" / lang='ts' / lang=ts（含 <script setup lang="ts">）
 *   4. package.json 的 dependencies / devDependencies / optionalDependencies
 *      声明 TypeScript 相关依赖（typescript、@typescript-eslint/*、vue-tsc、
 *      ts-node、tsx、typescript-eslint 等）
 *      —— 仅出现在 peerDependencies（peer 元数据）时显式豁免并打印 [豁免]，
 *         不视为违规（应用自身不会因此获得 TS 构建能力）。
 *
 * 退出码：0 = 扫描通过（无违规）；1 = 发现违规；2 = 运行错误（参数/目录/IO）
 * 运行：  node scripts/check-no-ts.mjs [--root <项目根>] [--verbose]
 * =============================================================================
 */

import { readdirSync, readFileSync } from 'node:fs';
import { basename, dirname, extname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import process from 'node:process';

const DEFAULT_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const TARGET_DIRS = ['web', 'miniprogram'];

/** 构建/依赖/运行态目录：不属于交付源码，跳过（与 brief 约定一致） */
const EXCLUDED_DIR_NAMES = new Set([
  'node_modules',
  'dist',
  'unpackage',
  '.runtime',
  '.git',
  '.idea',
  '.vscode',
  'coverage',
  'test-results',
  'playwright-report',
  'blob-report',
  '.cache',
  '.vite',
]);

const TS_SOURCE_EXTS = new Set(['.ts', '.tsx', '.mts', '.cts']);

/** TypeScript 相关依赖名（应用级声明即视为违规；peer 元数据显式豁免） */
function isTypescriptDependency(name) {
  return (
    /^typescript(-|$)/.test(name) ||
    name.startsWith('@typescript-eslint/') ||
    ['vue-tsc', 'ts-node', 'tsx', '@vue/tsconfig'].includes(name)
  );
}

const violations = [];
const exemptions = [];
const stats = {
  files: 0,
  vue: 0,
  packageJson: 0,
  skippedDirs: 0,
  symlinks: 0,
};

function toPosix(filePath) {
  return filePath.replace(/\\/g, '/');
}

function usage() {
  console.log(
    [
      '用法: node scripts/check-no-ts.mjs [--root <项目根>] [--verbose]',
      '',
      '  --root <目录>  指定仓库根目录（默认：脚本所在仓库）',
      '  --verbose      打印被排除的目录/文件等细节',
      '  --help         显示本帮助',
      '',
      '退出码: 0 = 通过；1 = 存在 TypeScript 违规；2 = 运行错误',
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

function walk(dir, files) {
  const entries = readdirSync(dir, { withFileTypes: true }).sort((a, b) =>
    a.name.localeCompare(b.name),
  );
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
      walk(fullPath, files);
    } else if (entry.isFile()) {
      files.push(fullPath);
    }
  }
}

/** 第 1 个匹配所在的 1-based 行号 */
function lineAt(text, index) {
  let line = 1;
  for (let i = 0; i < index; i += 1) {
    if (text.charCodeAt(i) === 10) line += 1;
  }
  return line;
}

/** 在 JSON 文本中定位某个键首次出现的行号（失败时回退 1） */
function lineOfJsonKey(text, key) {
  const index = text.indexOf(`"${key}"`);
  return index < 0 ? 1 : lineAt(text, index);
}

function checkVue(filePath, relPath) {
  const text = readFileSync(filePath, 'utf8');
  stats.vue += 1;
  const langRe = /\blang\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))/gi;
  for (const match of text.matchAll(langRe)) {
    const value = (match[1] ?? match[2] ?? match[3] ?? '').trim().toLowerCase();
    if (value === 'ts' || value === 'tsx') {
      violations.push({
        file: relPath,
        line: lineAt(text, match.index ?? 0),
        type: 'vue-lang-ts',
        detail: `Vue SFC 出现 lang="${value}"（禁止 TypeScript）`,
      });
    }
  }
}

function checkPackageJson(filePath, relPath) {
  const text = readFileSync(filePath, 'utf8');
  stats.packageJson += 1;
  let pkg;
  try {
    pkg = JSON.parse(text);
  } catch (err) {
    violations.push({
      file: relPath,
      line: 1,
      type: 'package-json-invalid',
      detail: `package.json 无法解析: ${err.message}`,
    });
    return;
  }

  const sections = ['dependencies', 'devDependencies', 'optionalDependencies', 'peerDependencies'];
  const declared = new Map(); // name -> [{ section, version }]
  for (const section of sections) {
    const deps = pkg[section];
    if (!deps || typeof deps !== 'object') continue;
    for (const [name, version] of Object.entries(deps)) {
      if (!declared.has(name)) declared.set(name, []);
      declared.get(name).push({ section, version: String(version) });
    }
  }

  for (const [name, places] of declared) {
    if (!isTypescriptDependency(name)) continue;
    const nonPeer = places.filter((place) => place.section !== 'peerDependencies');
    if (nonPeer.length === 0) {
      exemptions.push({
        file: relPath,
        line: lineOfJsonKey(text, name),
        type: 'peer-dependency',
        detail: `${name} 仅出现在 peerDependencies 元数据（显式豁免）`,
      });
      continue;
    }
    violations.push({
      file: relPath,
      line: lineOfJsonKey(text, name),
      type: 'typescript-dependency',
      detail: `声明 TypeScript 依赖 ${name}（${nonPeer
        .map((place) => `${place.section}@${place.version}`)
        .join(', ')}）`,
    });
  }
}

function checkFile(filePath, root) {
  stats.files += 1;
  const relPath = toPosix(relative(root, filePath));
  const baseName = basename(filePath);
  const ext = extname(filePath).toLowerCase();

  if (TS_SOURCE_EXTS.has(ext)) {
    violations.push({
      file: relPath,
      line: 1,
      type: 'typescript-source',
      detail: baseName.endsWith('.d.ts')
        ? '存在 .d.ts 类型声明文件（TypeScript 源码）'
        : `存在 ${ext} TypeScript 源文件`,
    });
  }

  if (/^tsconfig.*\.json$/i.test(baseName)) {
    violations.push({
      file: relPath,
      line: 1,
      type: 'tsconfig',
      detail: '存在 tsconfig 配置文件',
    });
  }

  if (ext === '.vue') checkVue(filePath, relPath);
  if (baseName === 'package.json') checkPackageJson(filePath, relPath);
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

  const missing = TARGET_DIRS.filter((name) => {
    try {
      return !readdirSync(join(root, name), { withFileTypes: true });
    } catch {
      return true;
    }
  });

  console.log('============================================================');
  console.log(' restaurant-mvp · 禁用项扫描（严禁 TypeScript）');
  console.log('============================================================');
  console.log(` 项目根目录 : ${root}`);
  console.log(` 扫描目标   : ${TARGET_DIRS.map((name) => `${name}/`).join('、')}`);
  console.log(` 排除目录   : ${[...EXCLUDED_DIR_NAMES].join('、')}`);
  console.log('');

  if (missing.length > 0) {
    console.error(`[ERROR] 扫描目标目录不存在或不可读: ${missing.join('、')}`);
    console.error('        （门禁要求 web/ 与 miniprogram/ 均已交付；如为早期阶段请先创建目录）');
    process.exit(2);
  }

  try {
    const files = [];
    for (const target of TARGET_DIRS) walk(join(root, target), files);
    for (const file of files) checkFile(file, root);
  } catch (err) {
    console.error(`[ERROR] 扫描失败: ${err.message}`);
    process.exit(2);
  }

  const elapsedMs = Number(process.hrtime.bigint() - startedAt) / 1e6;

  if (options.verbose) {
    console.log(`[INFO] 跳过目录 ${stats.skippedDirs} 个、符号链接 ${stats.symlinks} 个`);
    console.log('');
  }

  for (const exemption of exemptions) {
    console.log(`[豁免] ${exemption.file}:${exemption.line}  ${exemption.detail}`);
  }
  if (exemptions.length > 0) console.log('');

  if (violations.length > 0) {
    console.log(`[FAIL] 发现 ${violations.length} 处 TypeScript 违规：`);
    for (const violation of violations) {
      console.log(`  - ${violation.file}:${violation.line}  [${violation.type}] ${violation.detail}`);
    }
    console.log('');
    console.log('提示：Web/小程序必须使用 JavaScript；请移除 .ts 文件、tsconfig、lang="ts" 与 typescript 依赖。');
    console.log(
      `结果: 失败（${violations.length} 处违规 / 扫描文件 ${stats.files} 个（.vue ${stats.vue}、package.json ${stats.packageJson}） / 耗时 ${elapsedMs.toFixed(1)} ms）→ exit 1`,
    );
    process.exit(1);
  }

  console.log('[PASS] 未发现 TypeScript 源码 / tsconfig / lang="ts" / typescript 依赖');
  console.log('');
  console.log(
    `结果: 通过（0 违规 / 扫描文件 ${stats.files} 个（.vue ${stats.vue}、package.json ${stats.packageJson}） / 耗时 ${elapsedMs.toFixed(1)} ms）→ exit 0`,
  );
  process.exit(0);
}

main();
