#!/usr/bin/env node
/**
 * =============================================================================
 * 餐厅点餐系统 MVP — Docker Compose / Nginx 部署骨架静态校验
 *
 * 背景：本机无 Docker（brief §6），`docker compose config` / `up` 无法执行；
 *       本脚本用零依赖的 YAML 子集解析器 + 文件存在性/契约断言做静态门禁，
 *       保证 compose 结构与 nginx 双配置的关键约束不被破坏。
 *
 * 校验内容：
 *   1. docker-compose.yml 可解析（YAML 子集：map/list/标量/引号/注释；拒绝 tab 缩进与重复键）
 *   2. 服务齐全：mysql / redis / backend / web / nginx；依赖关系指向存在的服务
 *   3. mysql:8.0 + healthcheck；redis:7-alpine + healthcheck
 *   4. backend/web 的 build.dockerfile 真实存在；backend 走 mysql/redis 健康依赖、
 *      DB_HOST=mysql、REDIS_HOST=redis、Actuator healthcheck
 *   5. nginx 挂载 deploy/nginx/nginx.conf 与 web_dist 共享卷、暴露 80、依赖 web 成功退出
 *   6. 所有命名卷都已在顶层 volumes 声明；绑定的宿主机文件真实存在
 *   7. nginx.conf（Docker）包含 /api /files /ws(Upgrade) /swagger-ui /v3/api-docs、
 *      /actuator/health、/healthz、gzip、client_max_body_size 10m、SPA fallback、backend:8080
 *   8. nginx.local.conf 监听 127.0.0.1:80、反代 127.0.0.1:8080、root 指向 web/dist、
 *      pid/日志在 .runtime/nginx、mime.types 绝对路径
 *   9. .env.example 覆盖 compose 中全部 ${VAR} 引用
 *  10. 若本机存在 docker CLI，额外执行 `docker compose config -q`；否则明确标注「未验证」
 *
 * 退出码：0 = 全部通过；1 = 存在失败项
 * 运行：  node scripts/check-compose.mjs
 * =============================================================================
 */

import { existsSync, readFileSync, statSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const PROJECT_ROOT_POSIX = ROOT.replace(/\\/g, '/');

const COMPOSE_FILE = join(ROOT, 'docker-compose.yml');
const ENV_FILE = join(ROOT, '.env.example');
const DOCKER_NGINX_CONF = join(ROOT, 'deploy/nginx/nginx.conf');
const LOCAL_NGINX_CONF = join(ROOT, 'deploy/nginx/nginx.local.conf');
const BACKEND_DOCKERFILE = join(ROOT, 'deploy/backend/Dockerfile');
const BACKEND_DOCKERIGNORE = join(ROOT, 'deploy/backend/Dockerfile.dockerignore');
const WEB_DOCKERFILE = join(ROOT, 'deploy/web/Dockerfile');
const WEB_DOCKERIGNORE = join(ROOT, 'deploy/web/Dockerfile.dockerignore');

const results = [];

/** 记录一条断言结果（不抛异常，便于一次性报告全部问题） */
function check(description, fn) {
  try {
    const extra = fn();
    results.push({ ok: true, description, extra: extra || '' });
  } catch (err) {
    results.push({ ok: false, description, extra: err && err.message ? err.message : String(err) });
  }
}

function fail(message) {
  throw new Error(message);
}

function assert(condition, message) {
  if (!condition) fail(message);
}

/* --------------------------------------------------------------------------- */
/* 极简 YAML 子集解析器                                                         */
/* 支持：缩进 map / 缩进 list / 引号标量 / 行内注释 / 空行；不支持锚点、块标量。   */
/* --------------------------------------------------------------------------- */

function stripComment(rawLine) {
  let quote = null;
  let escaped = false;
  for (let i = 0; i < rawLine.length; i += 1) {
    const ch = rawLine[i];
    if (escaped) {
      escaped = false;
      continue;
    }
    if (quote === '"') {
      if (ch === '\\') escaped = true;
      else if (ch === '"') quote = null;
      continue;
    }
    if (quote === "'") {
      if (ch === "'") quote = null;
      continue;
    }
    if (ch === '"' || ch === "'") {
      quote = ch;
      continue;
    }
    if (ch === '#' && (i === 0 || /\s/.test(rawLine[i - 1]))) {
      return rawLine.slice(0, i);
    }
  }
  return rawLine;
}

function parseScalar(raw, lineNo) {
  const text = raw.trim();
  if (text === '') return '';
  if (text.startsWith('"')) {
    assert(text.length >= 2 && text.endsWith('"'), `行 ${lineNo}: 双引号未闭合 -> ${text}`);
    return text
      .slice(1, -1)
      .replace(/\\"/g, '"')
      .replace(/\\\\/g, '\\');
  }
  if (text.startsWith("'")) {
    assert(text.length >= 2 && text.endsWith("'"), `行 ${lineNo}: 单引号未闭合 -> ${text}`);
    return text.slice(1, -1).replace(/''/g, "'");
  }
  return text;
}

function parseNode(lines, start, indent) {
  const first = lines[start];
  if (first.content === '-' || first.content.startsWith('- ')) {
    return parseList(lines, start, indent);
  }
  return parseMap(lines, start, indent);
}

function parseMap(lines, start, indent) {
  const obj = {};
  let i = start;
  while (i < lines.length) {
    const line = lines[i];
    if (line.indent < indent) break;
    assert(
      line.indent === indent,
      `行 ${line.lineNo}: 缩进异常（期望 ${indent} 空格，实际 ${line.indent}）`,
    );
    if (line.content === '-' || line.content.startsWith('- ')) break;

    const match = /^([A-Za-z0-9_.-]+):(?:\s+(.*))?$/.exec(line.content);
    assert(match, `行 ${line.lineNo}: 无法解析为 "key: value" -> ${line.content}`);
    const key = match[1];
    const rest = (match[2] || '').trim();
    assert(!Object.prototype.hasOwnProperty.call(obj, key), `行 ${line.lineNo}: 重复键 "${key}"`);

    i += 1;
    let value;
    if (rest === '') {
      if (i < lines.length && lines[i].indent > indent) {
        const parsed = parseNode(lines, i, lines[i].indent);
        value = parsed[0];
        i = parsed[1];
      } else {
        value = null;
      }
    } else {
      assert(
        !(i < lines.length && lines[i].indent > indent),
        `行 ${lines[i - 1].lineNo}: 标量值后出现更深缩进（疑似误写嵌套）`,
      );
      value = parseScalar(rest, line.lineNo);
    }
    obj[key] = value;
  }
  return [obj, i];
}

function parseList(lines, start, indent) {
  const arr = [];
  let i = start;
  while (i < lines.length) {
    const line = lines[i];
    if (line.indent < indent) break;
    assert(
      line.indent === indent,
      `行 ${line.lineNo}: 缩进异常（期望 ${indent} 空格，实际 ${line.indent}）`,
    );
    if (!(line.content === '-' || line.content.startsWith('- '))) break;

    const rest = line.content.slice(1).trim();
    i += 1;
    if (rest === '') {
      if (i < lines.length && lines[i].indent > indent) {
        const parsed = parseNode(lines, i, lines[i].indent);
        arr.push(parsed[0]);
        i = parsed[1];
      } else {
        arr.push(null);
      }
      continue;
    }
    assert(
      !(i < lines.length && lines[i].indent > indent),
      `行 ${line.lineNo}: 列表标量后出现更深缩进（本脚本不支持列表内嵌套块）`,
    );
    arr.push(parseScalar(rest, line.lineNo));
  }
  return [arr, i];
}

function parseYaml(text, label) {
  assert(!text.includes('\t'), `${label}: 含 tab 缩进（YAML 禁止，请用空格）`);
  const lines = [];
  const rawLines = text.replace(/^\uFEFF/, '').split(/\r?\n/);
  rawLines.forEach((raw, index) => {
    const noComment = stripComment(raw).replace(/\s+$/, '');
    if (noComment.trim() === '') return;
    const indent = noComment.length - noComment.trimStart().length;
    lines.push({
      lineNo: index + 1,
      indent,
      content: noComment.trim(),
    });
  });
  assert(lines.length > 0, `${label}: 文件为空`);
  const parsed = parseNode(lines, 0, lines[0].indent);
  if (parsed[1] !== lines.length) {
    const nextLine = lines[parsed[1]];
    fail(`行 ${nextLine ? nextLine.lineNo : '?'}: 顶层解析提前结束（缩进不一致）`);
  }
  return parsed[0];
}

/* --------------------------------------------------------------------------- */
/* 通用断言工具                                                                 */
/* --------------------------------------------------------------------------- */

function readText(file) {
  assert(existsSync(file), `文件不存在: ${file}`);
  return readFileSync(file, 'utf8');
}

function isPlainObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function expectObject(value, message) {
  assert(isPlainObject(value), message);
  return value;
}

function expectArray(value, message) {
  assert(Array.isArray(value), message);
  return value;
}

function listVolumes(service) {
  if (!service.volumes) return [];
  return expectArray(service.volumes, 'volumes 必须是列表');
}

function normalizePath(p) {
  return String(p).replace(/[\\/]+/g, '/').replace(/\/+$/, '');
}

/* --------------------------------------------------------------------------- */
/* 主流程                                                                       */
/* --------------------------------------------------------------------------- */

let compose = null;

check('docker-compose.yml 存在且可解析（YAML 子集，无 tab/重复键）', () => {
  const text = readText(COMPOSE_FILE);
  compose = expectObject(parseYaml(text, 'docker-compose.yml'), 'compose 顶层必须是 map');
  assert(isPlainObject(compose.services), '缺少 services 段');
  return `顶层键: ${Object.keys(compose).join(', ')}`;
});

check('Compose 服务齐全：mysql / redis / backend / web / nginx', () => {
  const services = expectObject(compose.services, '缺少 services 段');
  const required = ['mysql', 'redis', 'backend', 'web', 'nginx'];
  const missing = required.filter((name) => !services[name]);
  assert(missing.length === 0, `缺少服务: ${missing.join(', ')}`);
  return required.join(' / ');
});

check('depends_on 仅引用已定义服务', () => {
  const services = compose.services;
  const names = new Set(Object.keys(services));
  const problems = [];
  for (const [svcName, svc] of Object.entries(services)) {
    const deps = svc.depends_on;
    if (!deps) continue;
    if (Array.isArray(deps)) {
      deps.forEach((dep) => {
        if (!names.has(String(dep))) problems.push(`${svcName} -> ${dep}`);
      });
    } else if (isPlainObject(deps)) {
      Object.keys(deps).forEach((dep) => {
        if (!names.has(dep)) problems.push(`${svcName} -> ${dep}`);
      });
    } else {
      problems.push(`${svcName}: depends_on 结构非法`);
    }
  }
  assert(problems.length === 0, `无效依赖: ${problems.join(', ')}`);
});

check('MySQL：mysql:8.0 + healthcheck + 数据卷 + utf8mb4', () => {
  const mysql = expectObject(compose.services.mysql, '缺少 mysql 服务');
  assert(/^mysql:8(\.|$)/.test(String(mysql.image || '')), `mysql 镜像应为 mysql:8.x，实际: ${mysql.image}`);
  const env = expectObject(mysql.environment, 'mysql 缺少 environment');
  assert(env.MYSQL_ROOT_PASSWORD, 'mysql 缺少 MYSQL_ROOT_PASSWORD');
  assert(env.MYSQL_DATABASE, 'mysql 缺少 MYSQL_DATABASE');
  const hc = expectObject(mysql.healthcheck, 'mysql 缺少 healthcheck');
  assert(Array.isArray(hc.test) && hc.test.length >= 2, 'mysql healthcheck.test 应为列表');
  assert(String(hc.test[1]).includes('mysqladmin ping'), 'mysql healthcheck 应使用 mysqladmin ping');
  assert(String(hc.test[1]).includes('MYSQL_ROOT_PASSWORD'), 'healthcheck 应引用 MYSQL_ROOT_PASSWORD');
  const vols = listVolumes(mysql);
  assert(vols.some((v) => String(v).startsWith('mysql_data:')), 'mysql 未挂载 mysql_data 卷');
  assert(
    String(mysql.command || '').includes('utf8mb4') || JSON.stringify(mysql.command || '').includes('utf8mb4'),
    'mysql command 应包含 utf8mb4 字符集配置',
  );
  const ports = expectArray(mysql.ports, 'mysql 缺少端口映射');
  assert(ports.some((p) => String(p).endsWith(':3306')), 'mysql 端口映射应指向容器 3306');
});

check('Redis：redis:7-alpine + healthcheck + 持久化卷', () => {
  const redis = expectObject(compose.services.redis, '缺少 redis 服务');
  assert(/^redis:7-alpine$/.test(String(redis.image || '')), `redis 镜像应为 redis:7-alpine，实际: ${redis.image}`);
  const hc = expectObject(redis.healthcheck, 'redis 缺少 healthcheck');
  assert(Array.isArray(hc.test), 'redis healthcheck.test 应为列表');
  assert(hc.test.join(' ').includes('redis-cli ping'), 'redis healthcheck 应使用 redis-cli ping');
  const vols = listVolumes(redis);
  assert(vols.some((v) => String(v).startsWith('redis_data:')), 'redis 未挂载 redis_data 卷');
});

check('backend：Dockerfile 存在 + 依赖 mysql/redis 健康 + DB_HOST=mysql/REDIS_HOST=redis', () => {
  const backend = expectObject(compose.services.backend, '缺少 backend 服务');
  const build = expectObject(backend.build, 'backend 缺少 build');
  const context = String(build.context || '.');
  const dockerfile = String(build.dockerfile || 'Dockerfile');
  const dockerfilePath = resolve(ROOT, context, dockerfile);
  assert(existsSync(dockerfilePath) && statSync(dockerfilePath).isFile(), `backend Dockerfile 不存在: ${dockerfilePath}`);
  const deps = expectObject(backend.depends_on, 'backend 缺少 depends_on');
  assert(deps.mysql && deps.mysql.condition === 'service_healthy', 'backend 必须以 service_healthy 依赖 mysql');
  assert(deps.redis && deps.redis.condition === 'service_healthy', 'backend 必须以 service_healthy 依赖 redis');
  const env = expectObject(backend.environment, 'backend 缺少 environment');
  assert(env.DB_HOST === 'mysql', `backend DB_HOST 应为 mysql，实际: ${env.DB_HOST}`);
  assert(env.REDIS_HOST === 'redis', `backend REDIS_HOST 应为 redis，实际: ${env.REDIS_HOST}`);
  assert(env.STORAGE_LOCAL_DIR === '/app/storage', 'backend STORAGE_LOCAL_DIR 应为 /app/storage');
  assert(env.JWT_SECRET && String(env.JWT_SECRET).trim() !== '', 'backend 缺少 JWT_SECRET');
  assert(env.TABLE_QR_SECRET, 'backend 缺少 TABLE_QR_SECRET');
  const hc = expectObject(backend.healthcheck, 'backend 缺少 healthcheck');
  assert(hc.test.join(' ').includes('/actuator/health'), 'backend healthcheck 应探测 /actuator/health');
  const vols = listVolumes(backend);
  assert(vols.some((v) => String(v).startsWith('backend_storage:')), 'backend 未挂载 backend_storage 卷');
  const dockerfileText = readText(dockerfilePath);
  assert(/mvn\b/.test(dockerfileText), 'backend Dockerfile 应使用 mvn 构建');
  assert(/-DskipTests package/.test(dockerfileText), 'backend Dockerfile 应执行 -DskipTests package');
  assert(/java\s+\$JAVA_OPTS\s+-jar/.test(dockerfileText), 'backend Dockerfile ENTRYPOINT 应启动 jar');
});

check('web：Dockerfile 存在 + 静态产物共享卷 web_dist', () => {
  const web = expectObject(compose.services.web, '缺少 web 服务');
  const build = expectObject(web.build, 'web 缺少 build');
  const dockerfilePath = resolve(ROOT, String(build.context || '.'), String(build.dockerfile || 'Dockerfile'));
  assert(existsSync(dockerfilePath), `web Dockerfile 不存在: ${dockerfilePath}`);
  const vols = listVolumes(web);
  assert(vols.some((v) => String(v).startsWith('web_dist:')), 'web 未发布到 web_dist 卷');
  const dockerfileText = readText(dockerfilePath);
  assert(/pnpm build/.test(dockerfileText), 'web Dockerfile 应执行 pnpm build');
  assert(/\/app\/dist/.test(dockerfileText), 'web Dockerfile 应复制 web/dist 产物');
});

check('nginx：挂载网关配置 + web_dist 静态根 + 80 端口 + 依赖顺序', () => {
  const nginx = expectObject(compose.services.nginx, '缺少 nginx 服务');
  assert(String(nginx.image || '').startsWith('nginx:'), 'nginx 应使用官方 nginx 镜像');
  const ports = expectArray(nginx.ports, 'nginx 缺少 ports');
  assert(ports.some((p) => String(p).endsWith(':80')), 'nginx 应暴露容器 80 端口');
  const vols = listVolumes(nginx).map(String);
  assert(
    vols.some((v) => normalizePath(v.split(':')[0]) === 'deploy/nginx/nginx.conf' || v.includes('deploy/nginx/nginx.conf')),
    'nginx 未挂载 deploy/nginx/nginx.conf',
  );
  assert(vols.some((v) => v.includes('web_dist:/usr/share/nginx/html')), 'nginx 未挂载 web_dist 到 /usr/share/nginx/html');
  const deps = expectObject(nginx.depends_on, 'nginx 缺少 depends_on');
  assert(deps.backend && deps.backend.condition === 'service_healthy', 'nginx 应以 service_healthy 依赖 backend');
  assert(deps.web && deps.web.condition === 'service_completed_successfully', 'nginx 应等待 web 构建产物发布完成');
  const hc = expectObject(nginx.healthcheck, 'nginx 缺少 healthcheck');
  assert(hc.test.join(' ').includes('/healthz'), 'nginx healthcheck 应探测 /healthz');
});

check('命名卷全部在顶层 volumes 声明；绑定挂载的宿主机文件存在', () => {
  const declared = isPlainObject(compose.volumes) ? new Set(Object.keys(compose.volumes)) : new Set();
  const problems = [];
  for (const [svcName, svc] of Object.entries(compose.services)) {
    for (const entry of listVolumes(svc)) {
      const source = String(entry).split(':')[0];
      if (source.startsWith('.') || source.startsWith('/')) {
        const hostPath = resolve(ROOT, source);
        if (!existsSync(hostPath)) problems.push(`${svcName}: 绑定路径不存在 ${source}`);
      } else if (source.startsWith('$')) {
        // 变量形式的卷由运行时解析，此处跳过
      } else if (!declared.has(source)) {
        problems.push(`${svcName}: 命名卷未声明 ${source}`);
      }
    }
  }
  assert(problems.length === 0, problems.join('; '));
  return `已声明卷: ${[...declared].join(', ')}`;
});

check('nginx.conf（Docker）：路由/Upgrade/gzip/10m/SPA fallback/backend:8080', () => {
  const conf = readText(DOCKER_NGINX_CONF);
  const expectations = [
    [/root\s+\/usr\/share\/nginx\/html;/, 'root /usr/share/nginx/html'],
    [/location\s+\/api\//, 'location /api/'],
    [/location\s+\/files\//, 'location /files/'],
    [/location\s+\/ws\b/, 'location /ws'],
    [/proxy_set_header\s+Upgrade\s+\$http_upgrade;/, 'WebSocket Upgrade 头'],
    [/map\s+\$http_upgrade\s+\$connection_upgrade/, 'WebSocket connection_upgrade 映射'],
    [/location\s+\/swagger-ui/, 'location /swagger-ui'],
    [/location\s+\/v3\/api-docs/, 'location /v3/api-docs'],
    [/location\s+=\s+\/actuator\/health/, 'location = /actuator/health'],
    [/location\s+=\s+\/healthz/, 'location = /healthz'],
    [/gzip\s+on;/, 'gzip on'],
    [/client_max_body_size\s+10m;/, 'client_max_body_size 10m'],
    [/try_files\s+\$uri\s+\$uri\/\s+\/index\.html;/, 'SPA history fallback'],
    [/proxy_pass\s+http:\/\/backend:8080;/, 'proxy_pass http://backend:8080'],
    [/listen\s+80;/, 'listen 80'],
  ];
  const missing = expectations.filter(([re]) => !re.test(conf)).map(([, name]) => name);
  assert(missing.length === 0, `缺失: ${missing.join(', ')}`);
  const proxyCount = (conf.match(/proxy_pass\s+http:\/\/backend:8080;/g) || []).length;
  assert(proxyCount >= 6, `proxy_pass 次数过少（${proxyCount}），应覆盖 api/files/ws/swagger/api-docs/actuator`);
  return `proxy_pass 规则 ${proxyCount} 条`;
});

check('nginx.local.conf：监听 127.0.0.1:80 + 反代 127.0.0.1:8080 + web/dist + .runtime/nginx', () => {
  const conf = readText(LOCAL_NGINX_CONF);
  const expectations = [
    [new RegExp(`listen\\s+127\\.0\\.0\\.1:80;`), 'listen 127.0.0.1:80'],
    [/proxy_pass\s+http:\/\/127\.0\.0\.1:8080;/, 'proxy_pass http://127.0.0.1:8080'],
    [new RegExp(`root\\s+${PROJECT_ROOT_POSIX.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\/web\\/dist;`), 'root 指向 web/dist'],
    [new RegExp(`pid\\s+${PROJECT_ROOT_POSIX.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\/\\.runtime\\/nginx\\/nginx\\.pid;`), 'pid 写入 .runtime/nginx'],
    [/include\s+D:\/Code_Environment\/Nginx\/nginx-1\.30\.6\/conf\/mime\.types;/, 'mime.types 绝对路径 include'],
    [/location\s+\/api\//, 'location /api/'],
    [/location\s+\/files\//, 'location /files/'],
    [/location\s+\/ws\b/, 'location /ws'],
    [/location\s+\/swagger-ui/, 'location /swagger-ui'],
    [/location\s+\/v3\/api-docs/, 'location /v3/api-docs'],
    [/location\s+=\s+\/actuator\/health/, 'location = /actuator/health'],
    [/location\s+=\s+\/healthz/, 'location = /healthz'],
  ];
  const missing = expectations.filter(([re]) => !re.test(conf)).map(([, name]) => name);
  assert(missing.length === 0, `缺失: ${missing.join(', ')}`);
});

check('.env.example 覆盖 compose 中全部 ${VAR} 引用且无重复键', () => {
  const composeText = readText(COMPOSE_FILE);
  const referenced = new Set();
  for (const match of composeText.matchAll(/\$\{([A-Za-z_][A-Za-z0-9_]*)/g)) referenced.add(match[1]);
  const envText = readText(ENV_FILE);
  const envKeys = new Set();
  const duplicates = [];
  for (const line of envText.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (trimmed === '' || trimmed.startsWith('#')) continue;
    const match = /^([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$/.exec(trimmed);
    if (!match) continue;
    if (envKeys.has(match[1])) duplicates.push(match[1]);
    envKeys.add(match[1]);
  }
  assert(duplicates.length === 0, `.env.example 重复键: ${duplicates.join(', ')}`);
  const missing = [...referenced].filter((key) => !envKeys.has(key));
  assert(missing.length === 0, `.env.example 缺少 compose 引用的变量: ${missing.join(', ')}`);
  return `compose 引用 ${referenced.size} 个变量，.env.example 定义 ${envKeys.size} 个`;
});

check('nginx / Dockerfile 文件布局完整（含 BuildKit dockerignore）', () => {
  const files = [
    DOCKER_NGINX_CONF,
    LOCAL_NGINX_CONF,
    BACKEND_DOCKERFILE,
    BACKEND_DOCKERIGNORE,
    WEB_DOCKERFILE,
    WEB_DOCKERIGNORE,
    ENV_FILE,
  ];
  const missing = files.filter((file) => !existsSync(file));
  assert(missing.length === 0, `缺少文件: ${missing.join(', ')}`);
});

/* --------------------------------------------------------------------------- */
/* 汇总输出                                                                     */
/* --------------------------------------------------------------------------- */

console.log('============================================================');
console.log(' restaurant-mvp · compose/nginx 部署骨架静态校验');
console.log('============================================================');
console.log(` 项目根目录 : ${ROOT}`);
console.log(` compose    : ${COMPOSE_FILE}`);
console.log('');

let failed = 0;
results.forEach((result, index) => {
  const no = String(index + 1).padStart(2, '0');
  if (result.ok) {
    console.log(`[PASS] ${no}. ${result.description}${result.extra ? `  (${result.extra})` : ''}`);
  } else {
    failed += 1;
    console.log(`[FAIL] ${no}. ${result.description}`);
    console.log(`       原因: ${result.extra}`);
  }
});

console.log('');

const dockerCli = spawnSync('docker', ['--version'], { encoding: 'utf8' });
if (dockerCli.status === 0) {
  const composeCheck = spawnSync('docker', ['compose', '-f', COMPOSE_FILE, 'config', '-q'], {
    cwd: ROOT,
    encoding: 'utf8',
  });
  if (composeCheck.status === 0) {
    console.log('[PASS] 额外: docker compose config -q 通过（Docker CLI 可用）');
  } else {
    failed += 1;
    console.log('[FAIL] 额外: docker compose config -q 未通过');
    console.log(`       原因: ${(composeCheck.stderr || composeCheck.stdout || '').trim()}`);
  }
} else {
  console.log('[SKIP] 额外: 本机无 docker CLI，未执行 docker compose config / up —— 标注「未验证」');
}

console.log('');
if (failed > 0) {
  console.log(`结果: 失败 ${failed} 项 / 共 ${results.length} 项检查`);
  console.log('部署骨架静态校验未通过');
  process.exit(1);
}
console.log(`结果: 全部通过（${results.length} 项检查）`);
console.log('运行态（docker compose up / 双 Nginx 配置 nginx -t）需在对应环境复验');
process.exit(0);
