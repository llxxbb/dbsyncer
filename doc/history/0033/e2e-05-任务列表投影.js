// E2E 5：任务列表投影 VO 改造后的渲染验证（ADR-0033）
// 覆盖此前无测试的 /mapping/getRelatedMappings 消费方（数据源关联任务列表），
// 并验证 4 个端点的响应不再含凭据、连接器列仍正常渲染。
const { chromium } = require('playwright');
const BASE = 'http://127.0.0.1:18686';
const CONNECTOR_ID = '1412112027324911616';   // 已知有 20 个关联任务
const GROUP_ID = '1430581533965684736';        // 已知有 4 个任务
const sleep = ms => new Promise(r => setTimeout(r, ms));
const results = [];
const ck = (n, c, d = '') => { results.push({ n, ok: !!c, d }); console.log(`${c ? '✓' : '✗'} ${n} ${d}`); };

(async () => {
    const browser = await chromium.launch();
    const page = await browser.newPage();
    const errs = [];
    page.on('pageerror', e => errs.push(e.message));

    // 抓取 4 个端点的原始响应，检查凭据与体积
    const bodies = {};
    page.on('response', async r => {
        const u = r.url();
        for (const k of ['/index/mappingdata', '/index/searchMapping', '/index/abnormalTasks/api', '/mapping/getRelatedMappings']) {
            if (u.includes(k) && !bodies[k]) {
                try { bodies[k] = await r.text(); } catch (e) { }
            }
        }
    });

    await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('input[name="username"]', { timeout: 20000 });
    await page.fill('input[name="username"]', 'admin');
    await page.fill('input[name="password"]', 'admin');
    await page.click('#loginSubmitBtn');
    await page.waitForSelector('#menu', { timeout: 20000 });

    // ---------- A. 首页任务列表（/index/mappingdata） ----------
    await page.evaluate(g => { window.location.hash = '#/index?projectGroupId=' + g; }, GROUP_ID);
    await page.waitForSelector('#taskListContainer tbody tr', { timeout: 20000 });
    await sleep(1500);
    const idx = await page.evaluate(() => {
        const rows = [...document.querySelectorAll('#taskListContainer tbody tr')];
        const cells = rows[0] ? [...rows[0].querySelectorAll('td')].map(td => td.innerText.trim()) : [];
        return {
            rows: rows.length,
            cells,
            imgs: [...document.querySelectorAll('#taskListContainer tbody img')].map(i => i.getAttribute('src')).filter(Boolean),
            connNames: [...document.querySelectorAll('#taskListContainer tbody td span[title]')].map(s => s.getAttribute('title')).slice(0, 4)
        };
    });
    ck('首页列表渲染出行', idx.rows === 4, `rows=${idx.rows} (期望4)`);
    ck('连接器图标 src 取自 connectorType', idx.imgs.length > 0 && idx.imgs.every(s => /^\/img\/.+\.png$/.test(s)),
        `共${idx.imgs.length}个: ${[...new Set(idx.imgs)].slice(0, 4).join(', ')}`);
    ck('连接器名称已渲染', idx.connNames.some(n => n && n !== '-'), idx.connNames.join(' | '));
    ck('无 default 兜底图标（说明 connectorType 有值）', !idx.imgs.includes('/img/default.png'), '');

    // ---------- B. 异常任务列表（/index/abnormalTasks/api） ----------
    await page.evaluate(() => { window.location.hash = '#/index/abnormalTasks'; });
    await page.waitForSelector('#abnormalTasksContainer tbody tr', { timeout: 20000 });
    await sleep(1500);
    const ab = await page.evaluate(() => ({
        rows: document.querySelectorAll('#abnormalTasksContainer tbody tr').length,
        imgs: [...document.querySelectorAll('#abnormalTasksContainer tbody img')].map(i => i.getAttribute('src')).filter(Boolean)
    }));
    ck('异常任务列表渲染出行', ab.rows > 0, `rows=${ab.rows}`);
    ck('异常任务连接器图标正常', ab.imgs.length > 0 && !ab.imgs.includes('/img/default.png'), `${ab.imgs.length}个图标`);

    // ---------- C. 数据源关联任务列表（/mapping/getRelatedMappings）★此前无覆盖 ----------
    await page.evaluate(() => { window.location.hash = '#/datasource'; });
    await page.waitForSelector('.connector-item', { timeout: 20000 });
    await sleep(1200);
    const clicked = await page.evaluate(id => {
        const el = document.querySelector('.connector-item[data-id="' + id + '"]');
        if (!el) return false;
        el.click();
        return true;
    }, CONNECTOR_ID);
    ck('点击目标连接器', clicked, clicked ? CONNECTOR_ID : '未找到该连接器');
    await page.waitForSelector('#drivers-list tbody tr', { timeout: 20000 });
    await sleep(2000);
    const rel = await page.evaluate(() => ({
        rows: document.querySelectorAll('#drivers-list tbody tr').length,
        count: (document.querySelector('.drivers-count') || {}).innerText,
        imgs: [...document.querySelectorAll('#drivers-list tbody img')].map(i => i.getAttribute('src')).filter(Boolean),
        firstRow: [...(document.querySelectorAll('#drivers-list tbody tr')[0] || { querySelectorAll: () => [] }).querySelectorAll('td')].map(td => td.innerText.trim())
    }));
    ck('关联任务列表渲染出行', rel.rows === 20, `rows=${rel.rows} (期望20) ${rel.count || ''}`);
    ck('关联任务连接器图标正常', rel.imgs.length > 0 && !rel.imgs.includes('/img/default.png'), `${rel.imgs.length}个图标`);
    ck('关联任务行含名称/状态/时间', rel.firstRow.length >= 8 && rel.firstRow.some(c => /\d{4}-\d{2}-\d{2}/.test(c)),
        rel.firstRow.slice(0, 6).join(' | '));

    // ---------- D. 搜索（/index/searchMapping） ----------
    await page.evaluate(g => { window.location.hash = '#/index?projectGroupId=' + g; }, GROUP_ID);
    await page.waitForSelector('#projectGroup', { state: 'attached', timeout: 20000 });
    await sleep(1500);
    const searched = await page.evaluate(() => {
        const input = document.querySelector('#mappingSearchInput');
        const btn = document.querySelector('#mappingSearchBtn');
        if (!input || !btn) return 'none';
        input.value = '增量';                       // 无关键字时 performMappingSearch 会提前返回
        btn.click();
        return 'click:' + input.value;
    });
    await sleep(2500);
    const afterSearch = await page.evaluate(() => document.querySelectorAll('#taskListContainer tbody tr').length);
    ck('搜索后列表仍有数据', searched !== 'none' && afterSearch > 0, `触发方式=${searched} rows=${afterSearch}`);

    // ---------- E. 响应体凭据与体积 ----------
    await sleep(1000);
    for (const [k, body] of Object.entries(bodies)) {
        const leak = ['password', 'username', 'jdbc:'].filter(w => body.includes('"' + w + '"') || body.includes(w + '='));
        const pw = /"password"\s*:/.test(body);
        ck(`${k} 响应无凭据`, !pw && !body.includes('jdbc:'), `字节=${body.length} 命中=${leak.join(',') || '无'}`);
    }
    ck('4 个端点均被捕获', Object.keys(bodies).length === 4, Object.keys(bodies).join(', '));

    console.log(`\nJS 错误 ${errs.length} 个`);
    errs.slice(0, 8).forEach(e => console.log('  ✗', e.slice(0, 200)));
    const passed = results.filter(r => r.ok).length;
    console.log(`\n===== 通过 ${passed}/${results.length} =====`);
    if (passed !== results.length) results.filter(r => !r.ok).forEach(r => console.log('  失败:', r.n, r.d));
    await browser.close();
    process.exit(passed === results.length && errs.length === 0 ? 0 : 1);
})();
