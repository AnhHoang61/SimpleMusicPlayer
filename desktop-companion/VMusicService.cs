using System;
using System.IO;
using System.Diagnostics;
using System.ServiceProcess;
using System.Threading;

namespace VMusic
{
    public class VMusicService : ServiceBase
    {
        public const string MyServiceName = "VMusicSyncService";
        private Process nodeProcess;
        private string appDir;

        public VMusicService()
        {
            this.ServiceName = MyServiceName;
            this.CanStop = true;
            this.CanPauseAndContinue = false;
            this.AutoLog = true;
        }

        protected override void OnStart(string[] args)
        {
            appDir = Path.GetDirectoryName(System.Reflection.Assembly.GetExecutingAssembly().Location);
            Log("VMusicService starting in: " + appDir);
            StartNode();
        }

        private string FindNodeExecutable()
        {
            if (File.Exists(@"D:\nvidia\node.exe")) return @"D:\nvidia\node.exe";
            if (File.Exists(@"C:\Program Files\nodejs\node.exe")) return @"C:\Program Files\nodejs\node.exe";
            if (File.Exists(@"C:\Program Files (x86)\nodejs\node.exe")) return @"C:\Program Files (x86)\nodejs\node.exe";
            return "node.exe";
        }

        private void StartNode()
        {
            try
            {
                string serverScript = Path.Combine(appDir, "server.js");
                if (!File.Exists(serverScript))
                {
                    Log("Error: server.js not found at " + serverScript);
                    return;
                }

                string nodeExe = FindNodeExecutable();
                Log("Using node binary: " + nodeExe);

                ProcessStartInfo psi = new ProcessStartInfo();
                psi.FileName = nodeExe;
                psi.Arguments = "\"" + serverScript + "\"";
                psi.WorkingDirectory = appDir;
                psi.UseShellExecute = false;
                psi.CreateNoWindow = true;
                psi.RedirectStandardOutput = true;
                psi.RedirectStandardError = true;

                nodeProcess = new Process();
                nodeProcess.StartInfo = psi;
                nodeProcess.EnableRaisingEvents = true;
                nodeProcess.OutputDataReceived += (s, e) => { if (e.Data != null) Log("[Node] " + e.Data); };
                nodeProcess.ErrorDataReceived += (s, e) => { if (e.Data != null) Log("[Node-Err] " + e.Data); };
                nodeProcess.Exited += (s, e) => {
                    Log("Node process exited.");
                };

                nodeProcess.Start();
                nodeProcess.BeginOutputReadLine();
                nodeProcess.BeginErrorReadLine();
                Log("Node process started successfully with PID: " + nodeProcess.Id);
            }
            catch (Exception ex)
            {
                Log("Failed to start Node process: " + ex.ToString());
            }
        }

        protected override void OnStop()
        {
            Log("VMusicService stopping...");
            try
            {
                if (nodeProcess != null && !nodeProcess.HasExited)
                {
                    nodeProcess.Kill();
                    nodeProcess.WaitForExit(3000);
                }
            }
            catch (Exception ex)
            {
                Log("Error killing node process: " + ex.Message);
            }
        }

        private void Log(string message)
        {
            try
            {
                string logFile = Path.Combine(appDir ?? AppDomain.CurrentDomain.BaseDirectory, "service.log");
                File.AppendAllText(logFile, "[" + DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss") + "] " + message + Environment.NewLine);
            }
            catch {}
        }

        public static void Main(string[] args)
        {
            if (Environment.UserInteractive)
            {
                Console.WriteLine("Running VMusicService in console debug mode...");
                VMusicService s = new VMusicService();
                s.OnStart(args);
                Console.WriteLine("Press Enter to stop...");
                Console.ReadLine();
                s.OnStop();
            }
            else
            {
                ServiceBase.Run(new VMusicService());
            }
        }
    }
}
