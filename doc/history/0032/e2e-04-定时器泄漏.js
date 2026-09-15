const { chromium } = require('/home/lxb/.nvm/versions/node/v24.13.1/lib/node_modules/playwright');
const BASE = 'http://127.0.0.1:18686';
let pass = 0, fail = 0;
const check = (n, ok, d) => { console.log(`${ok ? '✓' : '✗'} ${n}${d ? '  ' + d : ''}`); ok ? pass++ : fail++; };

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));

  // 请求计数器：按 URL 前缀统计，用于观察离开页面后是否仍有轮询
  const hits = {};
  page.on('request', r => {
    const u = r.url().replace(BASE, '');
    if (!/\/(api|mappingdata|searchMapping|getRefreshIntervalSeconds|list|monitor|mapping)/.test(u)) return;
    const key = u.split('?')[0];
    hits[key] = (hits[key] || 0) + 1;
  });
  const snap = () => JSON.parse(JSON.stringify(hits));
  const delta = (a, b) => Object.keys(b).filter(k => (b[k] - (a[k] || 0)) > 0)
    .map(k => `${k.replace(BASE,'')}×${b[k] - (a[k] || 0)}`);

  await page.goto(BASE, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#loginSubmitBtn', { timeout: 20000 });
  await page.fill('input[name="username"]', 'admin');
  await page.fill('input[name="password"]', 'admin');
  await page.click('#loginSubmitBtn');
  await page.waitForSelector('#menu', { timeout: 30000 });
  await page.waitForTimeout(2500);

  const go = async (p, wait = 3000) => {
    await page.$eval(`#menu a[data-path="${p}"]`, el => el.click());
    await page.waitForTimeout(wait);
  };

  // ---------- 1. 编辑页 timer3 轮询 ----------
  console.log('===== 编辑页 timer3（轮询 /mapping/get/... ）=====');
  await go('/index');
  const mappingId = await page.evaluate(() => {
    const el = document.querySelector('#taskListContainer tbody tr');
    return el ? (el.getAttribute('data-id') || '') : '';
  });
  check('取到 mappingId', !!mappingId, mappingId);

  const toEdit = async () => {
    await page.evaluate(id => { window.location.hash = '#/mapping/page/edit?id=' + id + '&refresh=' + Date.now(); }, mappingId);
    await page.waitForTimeout(4000);
  };
  await toEdit();
  await go('/datasource', 2000); await toEdit();   // 再进一次，暴露处理器累积
  // 在编辑页静置，确认 timer3 确实在轮询（建立基线）
  let base = snap(); await page.waitForTimeout(12000); let after = snap();
  const editPolling = delta(base, after);
  console.log('  编辑页内静置 12s 的轮询:', editPolling.join(' ') || '(无)');

  await go('/datasource', 2500);
  base = snap(); await page.waitForTimeout(15000); after = snap();
  const leaked = delta(base, after).filter(d => /edit|get\/|mapping\/get/.test(d));
  check('离开编辑页后 timer3 自我销毁', leaked.length === 0,
    leaked.length ? '仍在轮询: ' + leaked.join(' ') : '编辑页相关请求 0 个');

  // ---------- 2. 监控页定时器 ----------
  console.log('\n===== 监控页定时器（重复进入 3 次，观察是否叠加）=====');
  for (let i = 0; i < 3; i++) { await go('/monitor', 3500); await go('/datasource', 2000); }
  // 先停在无关页并静置，让前 3 次访问遗留的定时器各自触发一次后自毁，排除测量噪声
  await go('/index', 2500); await page.waitForTimeout(9000);
  await go('/monitor', 4000);
  base = snap(); await page.waitForTimeout(12000); after = snap();
  const monReq = delta(base, after);
  console.log('  监控页内静置 12s:', monReq.join(' ') || '(无)');
  // 单一监控定时器 → 每类请求次数应 ≤ 12s/间隔 + 1，不应成倍
  const monTotal = monReq.reduce((a, s) => a + parseInt(s.match(/×(\d+)$/)[1], 10), 0);
  check('监控页定时器未叠加（12s 内总请求 ≤8）', monTotal <= 8, `总计 ${monTotal} 次`);

  await go('/index', 2500);
  base = snap(); await page.waitForTimeout(12000); after = snap();
  const monLeak = delta(base, after).filter(d => /monitor/.test(d));
  check('离开监控页后其定时器自我销毁', monLeak.length === 0, monLeak.join(' ') || 'monitor 相关请求 0 个');

  // ---------- 3. window 上残留的处理器/全局 ----------
  console.log('\n===== 全局残留检查 =====');
  const globals = await page.evaluate(() => ({
    cleanups: typeof window.__pageCleanups,
    registerFn: typeof window.registerPageCleanup,
    containerSeq: typeof window.containerLoadSeq,
    forceClose: typeof $.loadingTForceClose,
    loadingCount: typeof window.__loadingCount,
    epoch: window.__pageEpoch,
    epochType: typeof window.__pageEpoch,
    handlers: (() => { const ev = (window.jQuery && $._data(window, 'events')) || {}; return Object.keys(ev).map(k => k + '×' + ev[k].length).join(', ') || '(无)'; })(),
    hash: location.hash,
    containers: document.querySelectorAll('.contentDiv').length,
    plugins: [typeof $.loadingT, typeof $.fn.serializeJson, typeof $.fn.tableDnD].join('/'),
  }));
  check('旧的补偿机制已彻底移除',
    globals.cleanups === 'undefined' && globals.registerFn === 'undefined' &&
    globals.containerSeq === 'undefined' && globals.forceClose === 'undefined',
    `__pageCleanups=${globals.cleanups} registerPageCleanup=${globals.registerFn} containerLoadSeq=${globals.containerSeq} loadingTForceClose=${globals.forceClose}`);
  check('代际计数器已就位', globals.epochType === 'number' && globals.epoch > 5, `__pageEpoch=${globals.epoch}`);
  check('单容器 + 插件完好', globals.containers === 1 && globals.plugins === 'function/function/function',
    `容器${globals.containers} 插件${globals.plugins}`);
  console.log('  window 事件处理器:', globals.handlers);
  // beforeunload/hashchange 处理器不应随访问次数累积（本页共访问约 15 次）
  const cnt = n => { const m = globals.handlers.match(new RegExp('(?:^|, )' + n + '×(\\d+)')); return m ? parseInt(m[1], 10) : 0; };
  check('window 上无 beforeunload/pagehide 累积', cnt('beforeunload') === 0 && cnt('pagehide') === 0,
    `beforeunload×${cnt('beforeunload')} pagehide×${cnt('pagehide')}`);

  console.log('\n===== 结果 =====');
  console.log(`通过 ${pass} / 失败 ${fail}`);
  console.log('JS 错误:', errors.length ? '\n  ' + [...new Set(errors)].join('\n  ') : '（无）');
  await browser.close();
  process.exit(fail ? 1 : 0);
})().catch(e => { console.error('E2E 失败:', e.message); process.exit(2); });
