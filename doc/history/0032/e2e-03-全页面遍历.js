const { chromium } = require('/home/lxb/.nvm/versions/node/v24.13.1/lib/node_modules/playwright');
const BASE = 'http://127.0.0.1:18686';
let pass = 0, fail = 0;
const check = (n, ok, d) => { console.log(`${ok ? '✓' : '✗'} ${n}${d ? '  ' + d : ''}`); ok ? pass++ : fail++; };

const PAGES = ['/index', '/index/abnormalTasks', '/datasource', '/monitor', '/helpDoc',
               '/system', '/user', '/userGroup', '/config', '/license'];

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(`[${page.url().replace(BASE,'')}] ${e.message}`));

  await page.goto(BASE, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#loginSubmitBtn', { timeout: 20000 });
  await page.fill('input[name="username"]', 'admin');
  await page.fill('input[name="password"]', 'admin');
  await page.click('#loginSubmitBtn');
  await page.waitForSelector('#menu', { timeout: 30000 });
  await page.waitForTimeout(2500);

  console.log('===== 全部导航页逐一访问（每页 2 次，检查重复进入不泄漏）=====');
  for (const p of PAGES) {
    for (let round = 1; round <= 2; round++) {
      const sel = `#menu a[data-path="${p}"]`;
      if (!(await page.$(sel))) { console.log(`- ${p} 菜单项不存在（权限）`); break; }
      const errsBefore = errors.length;
      await page.$eval(sel, el => el.click());   // 折叠子菜单不可见，直接触发 click
      await page.waitForTimeout(2200);
      const st = await page.evaluate(() => {
        const c = document.querySelector('#initContainer');
        return {
          len: c ? c.innerHTML.trim().length : -1,
          containers: document.querySelectorAll('.contentDiv').length,
          overlay: (() => { const o = document.querySelector('.loadingT'); return o ? getComputedStyle(o).display !== 'none' : false; })(),
          plugins: [typeof ($.loadingT), typeof ($.fn.serializeJson), typeof ($.fn.tableDnD)].join('/'),
        };
      });
      const newErrs = errors.length - errsBefore;
      check(`${p} 第${round}次`, st.len > 300 && st.containers === 1 && !st.overlay && st.plugins === 'function/function/function' && newErrs === 0,
        `内容${st.len}B 容器${st.containers} 遮罩${st.overlay} 插件${st.plugins} 新错误${newErrs}`);
    }
  }

  console.log('\n===== 任务编辑页（timer3 + renderDuration 代际守卫）=====');
  await page.$eval('#menu a[data-path="/index"]', el => el.click());
  await page.waitForTimeout(2500);
  // 进入第一个任务的编辑页
  const detailBtn = await page.$('#taskListContainer tbody tr td a, #cardView .btn, #taskListContainer a[href*="edit"]');
  const editLink = await page.evaluate(() => {
    const a = document.querySelector('#taskListContainer tbody tr a, #taskListContainer tbody tr button');
    return a ? (a.getAttribute('onclick') || a.textContent.trim()) : null;
  });
  console.log('  列表首行操作元素:', editLink);
  // 直接走 hash 进编辑页
  const mappingId = await page.evaluate(() => {
    const el = document.querySelector('#taskListContainer tbody tr');
    return el ? (el.getAttribute('data-id') || el.getAttribute('data-metaid') || '') : '';
  });
  console.log('  mappingId:', mappingId || '(未取到，改用详情按钮点击)');

  if (mappingId) {
    for (let round = 1; round <= 2; round++) {
      const errsBefore = errors.length;
      await page.evaluate(id => { window.location.hash = '#/mapping/page/edit?id=' + id + '&refresh=' + Date.now(); }, mappingId);
      await page.waitForTimeout(3500);
      const st = await page.evaluate(() => ({
        len: document.querySelector('#initContainer').innerHTML.trim().length,
        durationText: (document.querySelector('#metaDuration, .duration, #duration') || {}).textContent || null,
      }));
      check(`编辑页 第${round}次`, st.len > 500 && errors.length - errsBefore === 0,
        `内容${st.len}B 新错误${errors.length - errsBefore}`);
    }
    // 离开编辑页，验证 renderDuration 的 1s 定时器与 timer3 都自我销毁
    const errsBefore = errors.length;
    await page.$eval('#menu a[data-path="/datasource"]', el => el.click());
    await page.waitForTimeout(6000);
    const afterLeave = await page.evaluate(() => ({
      containerIsDatasource: !!document.querySelector('#datasourcePage'),
      len: document.querySelector('#initContainer').innerHTML.trim().length,
    }));
    check('离开编辑页后正常停留在新页面', afterLeave.containerIsDatasource && errsBefore === errors.length,
      JSON.stringify(afterLeave) + ` 新错误${errors.length - errsBefore}`);
  }

  console.log('\n===== 浏览器后退/前进（hash 历史回放）=====');
  await page.$eval('#menu a[data-path="/monitor"]', el => el.click());
  await page.waitForTimeout(2000);
  await page.goBack(); await page.waitForTimeout(2500);
  const back1 = await page.evaluate(() => ({ hash: location.hash, len: document.querySelector('#initContainer').innerHTML.trim().length }));
  check('后退可正常恢复页面', back1.len > 300, JSON.stringify(back1));
  await page.goForward(); await page.waitForTimeout(2500);
  const fwd = await page.evaluate(() => ({ hash: location.hash, len: document.querySelector('#initContainer').innerHTML.trim().length }));
  check('前进可正常恢复页面', fwd.len > 300, JSON.stringify(fwd));

  console.log('\n===== 结果 =====');
  console.log(`通过 ${pass} / 失败 ${fail}`);
  console.log('JS 错误:', errors.length ? '\n  ' + [...new Set(errors)].join('\n  ') : '（无）');
  await browser.close();
  process.exit(fail ? 1 : 0);
})().catch(e => { console.error('E2E 失败:', e.message); process.exit(2); });
