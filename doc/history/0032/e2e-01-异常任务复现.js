const { chromium } = require('/home/lxb/.nvm/versions/node/v24.13.1/lib/node_modules/playwright');
const BASE = 'http://127.0.0.1:18686';

const nav = (page, path) => page.click(`#menu a[data-path="${path}"]`);

// 采样：各容器内容 + 异常任务列表实际行数
const probe = (page) => page.evaluate(() => {
  const boxes = [];
  document.querySelectorAll('.contentDiv').forEach(el => {
    if (el.innerHTML.trim().length > 0) {
      boxes.push({ id: el.id, hidden: el.classList.contains('hidden') });
    }
  });
  const ab = document.querySelectorAll('#abnormalTasksContainer');
  const target = ab.length ? ab[ab.length - 1] : null;
  const rows = target ? target.querySelectorAll('tbody tr').length : -1;
  return {
    hash: location.hash,
    boxes,
    abnormalNodes: ab.length,
    rows,
    connected: target ? target.isConnected : null,
    // 列表中是否有实际数据行（排除“暂无数据”占位）
    dataRows: target ? target.querySelectorAll('tbody tr[data-id], tbody tr[data-metaid], tbody tr[data-meta-id]').length : -1,
    text: target ? (target.innerText || '').replace(/\s+/g, ' ').trim().substring(0, 90) : null,
  };
});

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  const errors = [];
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));
  page.on('console', m => { if (m.type() === 'error') errors.push('console: ' + m.text().substring(0, 160)); });
  const apiCalls = [];
  page.on('request', r => { if (r.url().includes('/abnormalTasks')) apiCalls.push(r.url().replace(BASE, '')); });

  await page.goto(BASE, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#loginSubmitBtn', { timeout: 20000 });
  await page.fill('input[name="username"]', 'admin');
  await page.fill('input[name="password"]', 'admin');
  await page.click('#loginSubmitBtn');
  await page.waitForSelector('#menu', { timeout: 30000 });
  await page.waitForTimeout(2000);

  console.log('登录成功');

  console.log('\n===== [1] 进入「异常任务」=====');
  await nav(page, '/index/abnormalTasks');
  await page.waitForSelector('#abnormalTasksContainer', { timeout: 20000 });
  await page.waitForTimeout(3000);
  console.log(JSON.stringify(await probe(page)));

  console.log('\n===== [2] 切到「数据源」（不做任何操作）=====');
  await nav(page, '/datasource');
  await page.waitForTimeout(3000);
  console.log(JSON.stringify(await probe(page)));

  console.log('\n===== [3] 切回「异常任务」 ← 用户报告的场景 =====');
  apiCalls.length = 0;
  await nav(page, '/index/abnormalTasks');
  await page.waitForTimeout(4000);
  const s3 = await probe(page);
  console.log(JSON.stringify(s3));
  console.log('→ 本轮 /abnormalTasks 请求:', apiCalls.length ? apiCalls : '（无！说明页面根本没重新加载）');

  console.log('\n===== [4] 快速连续切换 4 轮 =====');
  apiCalls.length = 0;
  for (let i = 0; i < 4; i++) {
    await nav(page, '/datasource');       await page.waitForTimeout(150);
    await nav(page, '/index/abnormalTasks'); await page.waitForTimeout(150);
  }
  await page.waitForTimeout(5000);
  const s4 = await probe(page);
  console.log(JSON.stringify(s4));
  console.log('→ 本轮 /abnormalTasks 请求数:', apiCalls.length);

  console.log('\n===== [5] 静置 15s，检测定时器泄漏 =====');
  apiCalls.length = 0;
  await page.waitForTimeout(15000);
  console.log('→ 静置期 /abnormalTasks 请求数:', apiCalls.length, apiCalls.length ? apiCalls : '');

  console.log('\n===== 结论 =====');
  console.log('步骤3 列表行数:', s3.rows, '| 步骤4 列表行数:', s4.rows);
  console.log('步骤4 容器数(应为1个有内容):', s4.boxes.length, JSON.stringify(s4.boxes));
  console.log('#abnormalTasksContainer 节点数(应为1):', s4.abnormalNodes);
  console.log('JS 错误:', errors.length ? '\n  ' + errors.join('\n  ') : '（无）');

  await browser.close();
})().catch(e => { console.error('E2E 失败:', e.message); process.exit(1); });
