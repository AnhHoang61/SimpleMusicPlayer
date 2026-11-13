const http = require('http');
const fs = require('fs');
const path = require('path');
const os = require('os');
const dgram = require('dgram');
const crypto = require('crypto');
const { exec } = require('child_process');

const HTTP_PORT = 8899;
const UDP_PORT = 8888;
const MUSIC_DIR = path.join(__dirname, 'music');
const PUBLIC_DIR = path.join(__dirname, 'public');

if (!fs.existsSync(MUSIC_DIR)) {
    fs.mkdirSync(MUSIC_DIR, { recursive: true });
}

// Track synced devices and activity log
const syncHistory = [];

function getLocalIpAddresses() {
    const interfaces = os.networkInterfaces();
    const addresses = [];
    for (const name of Object.keys(interfaces)) {
        for (const iface of interfaces[name]) {
            if (iface.family === 'IPv4' && !iface.internal) {
                addresses.push({ iface: name, address: iface.address });
            }
        }
    }
    return addresses;
}

function getMusicFiles() {
    const supportedExts = ['.mp3', '.m4a', '.wav', '.flac', '.ogg', '.aac'];
    if (!fs.existsSync(MUSIC_DIR)) return [];

    const files = fs.readdirSync(MUSIC_DIR);
    const musicList = [];

    for (const file of files) {
        const fullPath = path.join(MUSIC_DIR, file);
        try {
            const stats = fs.statSync(fullPath);
            if (stats.isFile()) {
                const ext = path.extname(file).toLowerCase();
                if (supportedExts.includes(ext)) {
                    // Quick fast hash (file size + first 8KB)
                    const fd = fs.openSync(fullPath, 'r');
                    const buffer = Buffer.alloc(Math.min(8192, stats.size));
                    fs.readSync(fd, buffer, 0, buffer.length, 0);
                    fs.closeSync(fd);

                    const hash = crypto.createHash('md5')
                        .update(file)
                        .update(String(stats.size))
                        .update(buffer)
                        .digest('hex');

                    musicList.push({
                        id: hash,
                        filename: file,
                        title: path.basename(file, ext),
                        size: stats.size,
                        sizeFormatted: (stats.size / (1024 * 1024)).toFixed(2) + ' MB',
                        extension: ext.replace('.', '').toUpperCase(),
                        mtime: stats.mtimeMs,
                        url: `/api/download/${encodeURIComponent(file)}`
                    });
                }
            }
        } catch (e) {
            console.error(`Error reading file ${file}:`, e.message);
        }
    }
    return musicList;
}

// ==========================================
// UDP Auto-Discovery Beacon & Realtime Broadcast
// ==========================================
const udpSocket = dgram.createSocket({ type: 'udp4', reuseAddr: true });

udpSocket.on('error', (err) => {
    console.error(`UDP socket error:\n${err.stack}`);
});

udpSocket.on('listening', () => {
    try {
        udpSocket.setBroadcast(true);
        const address = udpSocket.address();
        console.log(`[UDP Beacon] Listening for phone discovery on port ${address.port}`);
    } catch (e) {
        console.warn(`[UDP Beacon] SetBroadcast warning: ${e.message}`);
    }
});

udpSocket.on('message', (msg, rinfo) => {
    const text = msg.toString().trim();
    if (text.includes('VMUSIC_DISCOVERY_PING')) {
        console.log(`[UDP Beacon] Received discovery ping from ${rinfo.address}:${rinfo.port}`);
        const response = JSON.stringify({
            service: 'vmusic-sync',
            version: '1.0',
            hostname: os.hostname(),
            port: HTTP_PORT,
            timestamp: Date.now()
        });
        udpSocket.send(response, rinfo.port, rinfo.address, (err) => {
            if (err) console.error('[UDP Beacon] Error replying to phone:', err);
        });
    }
});

try {
    udpSocket.bind(UDP_PORT);
} catch (e) {
    console.warn(`[UDP Beacon] Bind warning: ${e.message}`);
}

function broadcastSyncBeacon(customEvent) {
    try {
        const beacon = JSON.stringify({
            service: 'vmusic-beacon',
            event: customEvent || 'periodic_beacon',
            version: '1.0',
            hostname: os.hostname(),
            port: HTTP_PORT,
            timestamp: Date.now()
        });
        udpSocket.send(beacon, UDP_PORT, '255.255.255.255', () => {});
    } catch (ignored) {}
}

// Periodically broadcast beacon every 6 seconds to local network
setInterval(() => {
    broadcastSyncBeacon('periodic');
}, 6000);

// ==========================================
// Real-Time Music Directory Live Watcher & SSE
// ==========================================
let catalogVersion = Date.now();
const sseClients = new Set();

function notifySseClients(event, filename) {
    const payload = JSON.stringify({
        event: event || 'catalog_updated',
        version: catalogVersion,
        filename: filename || '',
        timestamp: Date.now()
    });
    for (const client of sseClients) {
        try {
            client.write(`data: ${payload}\n\n`);
        } catch (e) {
            sseClients.delete(client);
        }
    }
}

// Keep SSE connections active with periodic heartbeat
setInterval(() => {
    for (const client of sseClients) {
        try {
            client.write(': keepalive\n\n');
        } catch (e) {
            sseClients.delete(client);
        }
    }
}, 12000);

let watcherDebounce = null;
try {
    fs.watch(MUSIC_DIR, (eventType, filename) => {
        if (!filename) return;
        if (watcherDebounce) clearTimeout(watcherDebounce);
        watcherDebounce = setTimeout(() => {
            catalogVersion = Date.now();
            console.log(`[Realtime Watcher] Detected library file change (${eventType}): ${filename}`);
            broadcastSyncBeacon('catalog_updated');
            notifySseClients('catalog_updated', filename);
            // Also trigger ADB reverse and intent if adb connected device is present
            exec('adb reverse tcp:8899 tcp:8899 2>nul', () => {});
            exec('adb shell am start -n com.example.simplemusicplayer/.MainActivity -a com.example.simplemusicplayer.ACTION_SYNC 2>nul', () => {});
            exec('adb shell am broadcast -a com.example.simplemusicplayer.ACTION_SYNC 2>nul', () => {});
        }, 350);
    });
    console.log(`[Realtime Watcher] Live file monitoring active on: ${MUSIC_DIR}`);
} catch (err) {
    console.warn(`[Realtime Watcher] Watch warning: ${err.message}`);
}

// ==========================================
// HTTP Sync Server
// ==========================================
const server = http.createServer((req, res) => {
    // Enable CORS for mobile devices
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, DELETE, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, X-Device-Name');

    if (req.method === 'OPTIONS') {
        res.writeHead(204);
        res.end();
        return;
    }

    const parsedUrl = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
    const pathname = parsedUrl.pathname;

    // 1. Static Web GUI & Assets
    if (pathname === '/' || pathname === '/index.html' || pathname === '/favicon.ico' || pathname === '/logo.png') {
        const targetFile = (pathname === '/' || pathname === '/index.html') ? 'index.html' : path.basename(pathname);
        const filePath = path.join(PUBLIC_DIR, targetFile);
        if (fs.existsSync(filePath)) {
            const ext = path.extname(filePath).toLowerCase();
            const mimeMap = {
                '.html': 'text/html; charset=utf-8',
                '.png': 'image/png',
                '.jpg': 'image/jpeg',
                '.ico': 'image/x-icon'
            };
            res.writeHead(200, { 'Content-Type': mimeMap[ext] || 'application/octet-stream' });
            fs.createReadStream(filePath).pipe(res);
            return;
        } else {
            res.writeHead(404);
            res.end('Asset not found');
            return;
        }
    }

    // 2. Real-Time Server-Sent Events (SSE) Stream
    if (pathname === '/api/events' && req.method === 'GET') {
        res.writeHead(200, {
            'Content-Type': 'text/event-stream',
            'Cache-Control': 'no-cache',
            'Connection': 'keep-alive',
            'Access-Control-Allow-Origin': '*'
        });
        res.write('retry: 3000\n\n');
        res.write(`data: ${JSON.stringify({ event: 'connected', version: catalogVersion })}\n\n`);
        sseClients.add(res);
        req.on('close', () => {
            sseClients.delete(res);
        });
        return;
    }

    // Fast Lightweight Version Check
    if (pathname === '/api/version' && req.method === 'GET') {
        const songs = getMusicFiles();
        res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({
            ok: true,
            version: catalogVersion,
            count: songs.length,
            timestamp: Date.now()
        }));
        return;
    }

    // 3. Server Status & Network Info
    if (pathname === '/api/status' && req.method === 'GET') {
        const songs = getMusicFiles();
        const ips = getLocalIpAddresses();
        res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({
            ok: true,
            hostname: os.hostname(),
            port: HTTP_PORT,
            ipAddresses: ips,
            musicDirectory: MUSIC_DIR,
            songsCount: songs.length,
            totalSize: songs.reduce((sum, s) => sum + s.size, 0),
            syncHistory: syncHistory.slice(-10).reverse()
        }));
        return;
    }

    // 3. Songs Catalog for Phone
    if (pathname === '/api/songs' && req.method === 'GET') {
        const songs = getMusicFiles();
        res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({
            ok: true,
            serverName: `V-Music Sync (${os.hostname()})`,
            count: songs.length,
            songs: songs
        }));
        return;
    }

    // 4. Download Song File
    if (pathname.startsWith('/api/download/') && req.method === 'GET') {
        const filename = decodeURIComponent(pathname.replace('/api/download/', ''));
        const filePath = path.join(MUSIC_DIR, path.basename(filename));

        if (!fs.existsSync(filePath)) {
            res.writeHead(404, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'File not found' }));
            return;
        }

        const stat = fs.statSync(filePath);
        const range = req.headers.range;

        const contentTypeMap = {
            '.mp3': 'audio/mpeg',
            '.m4a': 'audio/mp4',
            '.wav': 'audio/wav',
            '.flac': 'audio/flac',
            '.ogg': 'audio/ogg'
        };
        const ext = path.extname(filePath).toLowerCase();
        const contentType = contentTypeMap[ext] || 'application/octet-stream';

        if (range) {
            const parts = range.replace(/bytes=/, "").split("-");
            const start = parseInt(parts[0], 10);
            const end = parts[1] ? parseInt(parts[1], 10) : stat.size - 1;
            const chunksize = (end - start) + 1;
            const file = fs.createReadStream(filePath, { start, end });
            res.writeHead(206, {
                'Content-Range': `bytes ${start}-${end}/${stat.size}`,
                'Accept-Ranges': 'bytes',
                'Content-Length': chunksize,
                'Content-Type': contentType,
            });
            file.pipe(res);
        } else {
            res.writeHead(200, {
                'Content-Length': stat.size,
                'Content-Type': contentType,
                'Content-Disposition': `attachment; filename="${encodeURIComponent(path.basename(filePath))}"`,
                'Accept-Ranges': 'bytes'
            });
            fs.createReadStream(filePath).pipe(res);
        }
        return;
    }

    // 5. Upload File (Stream upload via Drag & Drop or API)
    if (pathname === '/api/upload' && req.method === 'POST') {
        const originalName = parsedUrl.searchParams.get('filename') || `track_${Date.now()}.mp3`;
        const safeName = path.basename(originalName);
        const destPath = path.join(MUSIC_DIR, safeName);

        const writeStream = fs.createWriteStream(destPath);
        req.pipe(writeStream);

        writeStream.on('finish', () => {
            const stat = fs.statSync(destPath);
            console.log(`[Upload] Received: ${safeName} (${(stat.size / 1024 / 1024).toFixed(2)} MB)`);
            broadcastSyncBeacon('file_uploaded');
            res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
            res.end(JSON.stringify({
                ok: true,
                filename: safeName,
                size: stat.size
            }));
        });

        writeStream.on('error', (err) => {
            console.error('[Upload] Error saving file:', err);
            res.writeHead(500, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: err.message }));
        });
        return;
    }

    // 6. Delete Song
    if (pathname.startsWith('/api/songs/') && req.method === 'DELETE') {
        const filename = decodeURIComponent(pathname.replace('/api/songs/', ''));
        const safePath = path.join(MUSIC_DIR, path.basename(filename));

        if (fs.existsSync(safePath)) {
            fs.unlinkSync(safePath);
            broadcastSyncBeacon('file_deleted');
            res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
            res.end(JSON.stringify({ ok: true, deleted: filename }));
        } else {
            res.writeHead(404, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'File not found' }));
        }
        return;
    }

    // 7. Sync Report from Phone
    if (pathname === '/api/sync-report' && req.method === 'POST') {
        let body = '';
        req.on('data', chunk => { body += chunk; });
        req.on('end', () => {
            try {
                const report = JSON.parse(body);
                const entry = {
                    deviceName: report.deviceName || 'Android Device',
                    syncedCount: report.syncedCount || 0,
                    downloadedSongs: report.downloadedSongs || [],
                    time: new Date().toLocaleTimeString('vi-VN')
                };
                syncHistory.push(entry);
                console.log(`[Sync Report] Phone ${entry.deviceName} synchronized ${entry.syncedCount} tracks!`);
                res.writeHead(200, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ ok: true }));
            } catch (e) {
                res.writeHead(400);
                res.end(JSON.stringify({ error: 'Invalid JSON' }));
            }
        });
        return;
    }

    // 8. Open Music Folder in Windows Explorer
    if (pathname === '/api/open-folder' && req.method === 'POST') {
        exec(`explorer.exe "${MUSIC_DIR}"`);
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ ok: true }));
        return;
    }

    // 9. Trigger Active Real-Time Sync to Phone (via UDP + ADB)
    if (pathname === '/api/trigger-sync' && req.method === 'POST') {
        broadcastSyncBeacon('manual_trigger');
        // If ADB is connected to a phone, wake up or trigger sync in app
        exec('adb reverse tcp:8899 tcp:8899; adb shell am start -a com.example.simplemusicplayer.ACTION_SYNC -n com.example.simplemusicplayer/.MainActivity 2>nul', (err, stdout) => {
            console.log('[Trigger Sync] Broadcast sent to all Wi-Fi phones and ADB targets.');
        });
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ ok: true, message: 'Đã gửi lệnh đồng bộ tức thì tới điện thoại!' }));
        return;
    }

    // 404 Fallback
    res.writeHead(404, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'Endpoint not found' }));
});

server.listen(HTTP_PORT, '0.0.0.0', () => {
    const ips = getLocalIpAddresses();
    console.log(`=======================================================`);
    console.log(`  🎵 V-Music Desktop Auto-Sync Server is RUNNING! 🎵   `);
    console.log(`=======================================================`);
    console.log(`* Local Web UI: http://localhost:${HTTP_PORT}`);
    ips.forEach(ip => {
        console.log(`* Network IP:   http://${ip.address}:${HTTP_PORT} (${ip.iface})`);
    });
    console.log(`* Storage path: ${MUSIC_DIR}`);
    console.log(`* UDP Beacon:   Port ${UDP_PORT} (Listening for phones)`);
    console.log(`=======================================================`);
});
