using System;
using System.IO;
using System.Drawing;
using System.Diagnostics;
using System.Windows.Forms;
using Microsoft.Win32;
using System.Net;
using System.Threading;

namespace VMusic
{
    public class VMusicTrayContext : ApplicationContext
    {
        private const string AppName = "V-Music Desktop Sync";
        private const string RunRegistryKey = "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run";
        private const int HttpPort = 8899;

        private NotifyIcon trayIcon;
        private ContextMenuStrip trayMenu;
        private ToolStripMenuItem autoStartItem;
        private Process nodeProcess;
        private string appDir;

        public VMusicTrayContext(bool startMinimized)
        {
            appDir = Path.GetDirectoryName(Application.ExecutablePath);

            InitializeTray();
            EnsureServerRunning();
            SetupAdbReverse();

            if (!startMinimized)
            {
                OpenAppWindow();
            }
            else
            {
                trayIcon.ShowBalloonTip(3000, AppName, "V-Music Desktop Sync đang chạy ngầm trên khay hệ thống.", ToolTipIcon.Info);
            }
        }

        private void InitializeTray()
        {
            trayMenu = new ContextMenuStrip();
            trayMenu.Items.Add("🚀 Mở ứng dụng Desktop", null, (s, e) => OpenAppWindow());
            trayMenu.Items.Add("📁 Mở thư mục nhạc", null, (s, e) => OpenMusicFolder());
            trayMenu.Items.Add("📱 Đồng bộ với điện thoại ngay", null, (s, e) => TriggerPhoneSync());
            trayMenu.Items.Add(new ToolStripSeparator());

            autoStartItem = new ToolStripMenuItem("⚙️ Khởi động cùng Windows", null, ToggleAutoStart);
            autoStartItem.Checked = IsAutoStartEnabled();
            trayMenu.Items.Add(autoStartItem);

            trayMenu.Items.Add("🔄 Khởi động lại Server Sync", null, (s, e) => RestartServer());
            trayMenu.Items.Add(new ToolStripSeparator());
            trayMenu.Items.Add("❌ Thoát hoàn toàn", null, (s, e) => ExitApplication());

            Icon icon = CreateAppIcon();

            trayIcon = new NotifyIcon();
            trayIcon.Text = "V-Music Desktop Sync · Đang hoạt động";
            trayIcon.Icon = icon;
            trayIcon.ContextMenuStrip = trayMenu;
            trayIcon.Visible = true;
            trayIcon.DoubleClick += (s, e) => OpenAppWindow();
        }

        private Icon CreateAppIcon()
        {
            string icoPath = Path.Combine(appDir, "app.ico");
            if (File.Exists(icoPath))
            {
                try
                {
                    return new Icon(icoPath);
                }
                catch {}
            }

            Bitmap bmp = new Bitmap(32, 32);
            using (Graphics g = Graphics.FromImage(bmp))
            {
                g.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
                g.Clear(Color.Transparent);

                // Crimson circle with gold rim
                using (Brush b = new SolidBrush(Color.FromArgb(230, 57, 70)))
                {
                    g.FillEllipse(b, 2, 2, 28, 28);
                }
                using (Pen p = new Pen(Color.FromArgb(212, 175, 55), 2))
                {
                    g.DrawEllipse(p, 2, 2, 28, 28);
                }

                // Katana V symbol
                using (Pen p = new Pen(Color.White, 3))
                {
                    p.StartCap = System.Drawing.Drawing2D.LineCap.Round;
                    p.EndCap = System.Drawing.Drawing2D.LineCap.Round;
                    g.DrawLine(p, 8, 8, 16, 23);
                    g.DrawLine(p, 16, 23, 24, 8);
                }
            }
            return Icon.FromHandle(bmp.GetHicon());
        }

        private string FindNodeExecutable()
        {
            if (File.Exists(@"D:\nvidia\node.exe")) return @"D:\nvidia\node.exe";
            if (File.Exists(@"C:\Program Files\nodejs\node.exe")) return @"C:\Program Files\nodejs\node.exe";
            if (File.Exists(@"C:\Program Files (x86)\nodejs\node.exe")) return @"C:\Program Files (x86)\nodejs\node.exe";
            return "node.exe";
        }

        private void EnsureServerRunning()
        {
            if (IsServerAlive())
            {
                return;
            }

            try
            {
                string serverScript = Path.Combine(appDir, "server.js");
                ProcessStartInfo psi = new ProcessStartInfo();
                psi.FileName = FindNodeExecutable();
                psi.Arguments = "\"" + serverScript + "\"";
                psi.WorkingDirectory = appDir;
                psi.UseShellExecute = false;
                psi.CreateNoWindow = true;

                nodeProcess = Process.Start(psi);
                Thread.Sleep(800);
            }
            catch (Exception ex)
            {
                MessageBox.Show("Không thể khởi động V-Music Sync Server: " + ex.Message, AppName, MessageBoxButtons.OK, MessageBoxIcon.Warning);
            }
        }

        private bool IsServerAlive()
        {
            try
            {
                HttpWebRequest req = (HttpWebRequest)WebRequest.Create("http://localhost:" + HttpPort + "/api/status");
                req.Timeout = 1200;
                using (HttpWebResponse resp = (HttpWebResponse)req.GetResponse())
                {
                    return resp.StatusCode == HttpStatusCode.OK;
                }
            }
            catch
            {
                return false;
            }
        }

        private void SetupAdbReverse()
        {
            try
            {
                ProcessStartInfo psi = new ProcessStartInfo();
                psi.FileName = "adb.exe";
                psi.Arguments = "reverse tcp:8899 tcp:8899";
                psi.CreateNoWindow = true;
                psi.UseShellExecute = false;
                Process.Start(psi);
            }
            catch {}
        }

        private void OpenAppWindow()
        {
            EnsureServerRunning();
            string url = "http://localhost:" + HttpPort;

            string edgePath = @"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe";
            if (!File.Exists(edgePath))
            {
                edgePath = @"C:\Program Files\Microsoft\Edge\Application\msedge.exe";
            }
            string chromePath = @"C:\Program Files\Google\Chrome\Application\chrome.exe";

            string browserExe = null;
            if (File.Exists(edgePath)) browserExe = edgePath;
            else if (File.Exists(chromePath)) browserExe = chromePath;

            if (browserExe != null)
            {
                try
                {
                    ProcessStartInfo psi = new ProcessStartInfo();
                    psi.FileName = browserExe;
                    psi.Arguments = "--app=\"" + url + "\" --window-size=1120,760";
                    Process.Start(psi);
                    return;
                }
                catch {}
            }

            Process.Start(url);
        }

        private void OpenMusicFolder()
        {
            string musicDir = Path.Combine(appDir, "music");
            if (!Directory.Exists(musicDir)) Directory.CreateDirectory(musicDir);
            Process.Start("explorer.exe", "\"" + musicDir + "\"");
        }

        private void TriggerPhoneSync()
        {
            try
            {
                HttpWebRequest req = (HttpWebRequest)WebRequest.Create("http://localhost:" + HttpPort + "/api/trigger-sync");
                req.Method = "POST";
                req.Timeout = 2000;
                using (HttpWebResponse resp = (HttpWebResponse)req.GetResponse())
                {
                    trayIcon.ShowBalloonTip(2500, AppName, "Đã gửi lệnh đồng bộ thời gian thực tới điện thoại!", ToolTipIcon.Info);
                }
            }
            catch
            {
                trayIcon.ShowBalloonTip(2500, AppName, "Không thể kết nối tới server đồng bộ.", ToolTipIcon.Warning);
            }
        }

        private bool IsAutoStartEnabled()
        {
            try
            {
                using (RegistryKey key = Registry.CurrentUser.OpenSubKey(RunRegistryKey, false))
                {
                    if (key != null)
                    {
                        object val = key.GetValue("VMusicDesktopSync");
                        return val != null;
                    }
                }
            }
            catch {}
            return false;
        }

        private void ToggleAutoStart(object sender, EventArgs e)
        {
            bool current = IsAutoStartEnabled();
            bool enable = !current;
            try
            {
                using (RegistryKey key = Registry.CurrentUser.OpenSubKey(RunRegistryKey, true))
                {
                    if (key != null)
                    {
                        if (enable)
                        {
                            string exePath = "\"" + Application.ExecutablePath + "\" --tray";
                            key.SetValue("VMusicDesktopSync", exePath);
                        }
                        else
                        {
                            key.DeleteValue("VMusicDesktopSync", false);
                        }
                    }
                }
                autoStartItem.Checked = enable;
                trayIcon.ShowBalloonTip(2000, AppName, enable ? "Đã bật tự khởi động cùng Windows!" : "Đã tắt tự khởi động cùng Windows.", ToolTipIcon.Info);
            }
            catch (Exception ex)
            {
                MessageBox.Show("Lỗi thiết lập khởi động: " + ex.Message, AppName, MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }

        private void RestartServer()
        {
            try
            {
                if (nodeProcess != null && !nodeProcess.HasExited)
                {
                    nodeProcess.Kill();
                }
            }
            catch {}
            Thread.Sleep(500);
            EnsureServerRunning();
            trayIcon.ShowBalloonTip(2000, AppName, "Đã khởi động lại server đồng bộ thành công!", ToolTipIcon.Info);
        }

        private void ExitApplication()
        {
            trayIcon.Visible = false;
            try
            {
                if (nodeProcess != null && !nodeProcess.HasExited)
                {
                    nodeProcess.Kill();
                }
            }
            catch {}
            Application.Exit();
        }

        [STAThread]
        public static void Main(string[] args)
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            bool startMinimized = false;
            foreach (string arg in args)
            {
                if (arg.Equals("--tray", StringComparison.OrdinalIgnoreCase) ||
                    arg.Equals("--background", StringComparison.OrdinalIgnoreCase))
                {
                    startMinimized = true;
                    break;
                }
            }

            Application.Run(new VMusicTrayContext(startMinimized));
        }
    }
}
