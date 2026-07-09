# 启动带调试端口的 Chrome
$chromePath = "C:\Program Files\Google\Chrome\Application\chrome.exe"
$userDataDir = "C:\temp\chrome-debug-profile" # 使用独立用户目录，避免冲突
$appUrl = "http://localhost:18686"             # 你的应用地址

Start-Process $chromePath -ArgumentList "--remote-debugging-port=9222", "--user-data-dir=$userDataDir", $appUrl