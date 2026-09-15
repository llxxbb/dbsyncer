const { chromium } = require('/home/lxb/.nvm/versions/node/v24.13.1/lib/node_modules/playwright');
const BASE = 'http://127.0.0.1:18686';
let pass = 0, fail = 0;
const check = (name, ok, detail) => { console.log(`${ok ? '✓' : '✗'} ${name}${detail ? '  ' + detail : ''}`); ok ? pass++ : fail++; };

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  const errors = [];
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));

  // 只统计业务接口，排除静态资源
  let api = [];
  page.on('request', r => {
    const u = r.url();
    if (/\.(js|css|png|ico|woff2?|ttf|map)(\?|$)/.test(u)) return;
    if (/\/(mappingdata|searchMapping|abnormalTasks\/api|datasource\/list|monitor\/)/.test(u)) api.push(u.replace(BASE, '').split('?')[0]);
  });
  const resetApi = () => { api = []; };

  await page.goto(BASE, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#loginSubmitBtn', { timeout: 20000 });
  await page.fill('input[name="username"]', 'admin');
  await page.fill('input[name="password"]', 'admin');
  await page.click('#loginSubmitBtn');
  await page.waitForSelector('#menu', { timeout: 30000 });
  await page.waitForTimeout(2500);

  // ========== 问题2：鼠标在分组列表上移动不应发请求 ==========
  console.log('\n===== 问题②：鼠标悬停/移动于分组列表 =====');
  const groups = await page.$$('.group-list-item');
  check('分组项存在', groups.length > 1, `共 ${groups.length} 项`);
  // 先测空闲基线：12s 内自动刷新会发多少个请求
  resetApi();
  await page.waitForTimeout(12000);
  const idleRate = api.length;
  // 再测同等时长内持续悬停移动
  resetApi();
  const t0 = Date.now();
  while (Date.now() - t0 < 12000) {
    for (const g of groups) { await g.hover(); await page.waitForTimeout(40); }
  }
  const hoverRate = api.length;
  console.log(`  空闲 12s 请求数=${idleRate}，悬停移动 12s 请求数=${hoverRate}`);
  check('悬停移动不产生额外请求（与空闲基线一致）',
    hoverRate <= idleRate + 1,
    hoverRate > idleRate + 1 ? `★ 空闲 ${idleRate} vs 悬停 ${hoverRate}` : '');

  // ========== 问题1：列表跟随 projectGroupId ==========
  console.log('\n===== 问题①：点击分组后「列表视图」与「卡片视图」同步 =====');
  const snap = async () => page.evaluate(() => {
    const vis = el => el && el.offsetParent !== null;
    const list = document.querySelector('#listView'), card = document.querySelector('#cardView');
    return {
      hiddenGroup: (document.querySelector('#projectGroup') || {}).value,
      hash: location.hash,
      listVisible: vis(list), cardVisible: vis(card),
      listRows: document.querySelectorAll('#taskListContainer tbody tr').length,
      listText: document.querySelector('#taskListContainer tbody') ?
        document.querySelector('#taskListContainer tbody').innerText.replace(/\s+/g, ' ').trim().substring(0, 60) : null,
      cardCount: card ? card.querySelectorAll('.mapping-card, .panel, .task-card').length : -1,
      cardText: card ? card.innerText.replace(/\s+/g, ' ').trim().substring(0, 60) : null,
    };
  });

  const before = await snap();
  console.log('  初始:', JSON.stringify(before));

  // 切到列表视图
  await page.click('#listViewBtn').catch(() => {});
  await page.waitForTimeout(800);

  // 点击第 2 个分组（非“未分配”）
  const target = groups[1];
  const gid = await target.getAttribute('data-group-id');
  const gname = await target.getAttribute('title');
  resetApi();
  await target.click();
  await page.waitForSelector('#projectGroup', { state: 'attached', timeout: 15000 });
  await page.waitForTimeout(3000);
  const after = await snap();
  console.log(`  点击分组「${gname}」(${gid}) 后:`, JSON.stringify(after));

  check('hash 携带 projectGroupId', after.hash.includes('projectGroupId=' + gid), after.hash);
  check('#projectGroup 隐藏字段已更新', after.hiddenGroup === gid, `实际=${after.hiddenGroup}`);
  check('分组切换只发 1 次列表请求（无重复）',
    api.filter(u => u.includes('mappingdata')).length <= 1,
    api.join(','));
  check('hash 中无 route 参数', !after.hash.includes('route='), after.hash);

  // 列表内容应与卡片一致地反映该分组
  const listFollowsGroup = await page.evaluate((g) => {
    const t = document.querySelector('#taskListContainer tbody');
    return t ? t.innerText.length > 0 && !/暂无任务/.test(t.innerText) : null;
  }, gid);
  console.log('  列表是否有数据:', listFollowsGroup, '| 卡片是否有数据:', after.cardText);

  // 回到“未分配”分组，验证列表也随之变化
  resetApi();
  await (await page.$$('.group-list-item'))[0].click();
  await page.waitForTimeout(3000);
  const back = await snap();
  console.log('  切回「未分配」:', JSON.stringify(back));
  check('切回未分配后隐藏字段为空', back.hiddenGroup === '', `实际=${back.hiddenGroup}`);
  check('列表内容随分组变化（与上一分组不同）',
    back.listText !== after.listText || back.cardText !== after.cardText,
    `\n      分组[${gname}] 列表: ${after.listText}\n      未分配      列表: ${back.listText}`);

  // ========== 数据源页：样式与内容完好 ==========
  console.log('\n===== 数据源页（fragment 化后）=====');
  resetApi();
  await page.click('#menu a[data-path="/datasource"]');
  await page.waitForTimeout(3500);
  const ds = await page.evaluate(() => {
    const p = document.querySelector('#datasourcePage');
    const cs = p ? getComputedStyle(p.querySelector('.container-fluid') || p) : null;
    return {
      exists: !!p,
      hasWrapperLeftover: !!document.querySelector('#initContainer .wrapper > .header #nav'),
      listLoaded: document.querySelectorAll('#connector-list > *').length,
      layoutApplied: cs ? (cs.display || '') : '',
      pluginLoadingT: typeof ($.loadingT),
      pluginSerializeJson: typeof ($.fn.serializeJson),
      pluginTableDnD: typeof ($.fn.tableDnD),
      overlayStuck: !!document.querySelector('.loadingT') && getComputedStyle(document.querySelector('.loadingT')).display !== 'none',
    };
  });
  console.log(' ', JSON.stringify(ds));
  check('数据源页正常渲染', ds.exists && ds.listLoaded > 0, `列表项 ${ds.listLoaded}`);
  check('无残留 .wrapper/#nav 结构', !ds.hasWrapperLeftover);
  check('jQuery 插件未被覆盖 ($.loadingT)', ds.pluginLoadingT === 'function', ds.pluginLoadingT);
  check('jQuery 插件未被覆盖 ($.fn.serializeJson)', ds.pluginSerializeJson === 'function', ds.pluginSerializeJson);
  check('jQuery 插件未被覆盖 ($.fn.tableDnD)', ds.pluginTableDnD === 'function', ds.pluginTableDnD);
  check('遮罩层未卡死', !ds.overlayStuck);

  // ========== 监控页 + 编辑页：定时器守卫 ==========
  console.log('\n===== 监控页定时器守卫 =====');
  await page.click('#menu a[data-path="/monitor"]');
  await page.waitForTimeout(2500);
  check('监控页渲染', await page.evaluate(() => document.querySelector('#initContainer').innerHTML.length > 500));
  resetApi();
  await page.click('#menu a[data-path="/datasource"]');
  await page.waitForTimeout(12000);   // 跨过若干刷新周期
  const monLeak = api.filter(u => /monitor\//.test(u)).length;
  check('离开监控页后轮询已停止', monLeak === 0, monLeak ? `★ 仍有 ${monLeak} 个: ${api.join(',')}` : '');

  console.log('\n===== 结果 =====');
  console.log(`通过 ${pass} / 失败 ${fail}`);
  console.log('JS 错误:', errors.length ? '\n  ' + errors.join('\n  ') : '（无）');
  await browser.close();
  process.exit(fail ? 1 : 0);
})().catch(e => { console.error('E2E 失败:', e.message); process.exit(2); });
