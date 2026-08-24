import subprocess
import time
import json
import socket
import os
import sys
import threading
import tkinter as tk
from tkinter import ttk, messagebox
import tkinter.scrolledtext as st
import datetime

DISCOVERY_PORT = 5001

def get_resource_path(relative_path):
    """获取打包后资源的绝对路径"""
    if hasattr(sys, '_MEIPASS'):
        return os.path.join(sys._MEIPASS, relative_path)
    return os.path.join(os.path.abspath("."), relative_path)

class MacSenderApp:
    def __init__(self, root):
        self.root = root
        self.root.title("CarpeCast Mac Sender (调试版)")
        self.root.geometry("650x550")
        
        self.discovered_devices = {} 
        self.is_syncing = False
        self.selected_ip = None
        self.selected_port = 5000
        
        self.setup_ui()
        self.log("程序已启动，正在初始化底层服务...")
        
        # 启动 UDP 发现监听线程
        self.discovery_thread = threading.Thread(target=self.listen_for_discovery, daemon=True)
        self.discovery_thread.start()
        
        # 启动同步发送线程
        self.sync_thread = threading.Thread(target=self.sync_loop, daemon=True)
        self.sync_thread.start()

    def log(self, msg):
        """将日志输出到 UI 界面底部的文本框中"""
        timestamp = datetime.datetime.now().strftime("%H:%M:%S")
        full_msg = f"[{timestamp}] {msg}\n"
        def append():
            self.log_area.insert(tk.END, full_msg)
            self.log_area.see(tk.END) # 自动滚动到底部
        self.root.after(0, append)

    def setup_ui(self):
        # 容器
        frame = ttk.Frame(self.root, padding="10")
        frame.pack(fill=tk.BOTH, expand=True)
        
        # 发现列表
        ttk.Label(frame, text="已发现的 Windows 接收端：", font=("", 11)).pack(anchor=tk.W, pady=(0, 2))
        self.device_listbox = tk.Listbox(frame, height=4, font=("", 11))
        self.device_listbox.pack(fill=tk.X, pady=2)
        
        # 连接按钮
        self.btn_connect = ttk.Button(frame, text="连接并同步", command=self.toggle_sync)
        self.btn_connect.pack(fill=tk.X, pady=5)
        
        # 状态栏
        self.status_var = tk.StringVar()
        self.status_var.set("状态: 正在监听...")
        ttk.Label(frame, textvariable=self.status_var, foreground="blue").pack(anchor=tk.W, pady=2)
        
        # 调试日志区
        ttk.Label(frame, text="🔧 详细调试日志 (Debug Log)：", font=("", 11)).pack(anchor=tk.W, pady=(10, 2))
        self.log_area = st.ScrolledText(frame, height=15, font=("Menlo", 10), bg="#f5f5f5")
        self.log_area.pack(fill=tk.BOTH, expand=True)

    def update_device_list(self):
        self.device_listbox.delete(0, tk.END)
        for ip, info in self.discovered_devices.items():
            name, port, dtype, os_name = info
            self.device_listbox.insert(tk.END, f"💻 {name} ({ip}) - {os_name}")

    def listen_for_discovery(self):
        self.log("启动 UDP 监听，端口: 5001...")
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        if hasattr(socket, 'SO_REUSEPORT'):
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEPORT, 1)
            
        try:
            sock.bind(('', DISCOVERY_PORT))
            self.log("✅ 成功绑定端口 5001，等待 Windows 广播包...")
        except Exception as e:
            self.log(f"❌ 绑定端口 5001 失败: {e}")
            return
        
        while True:
            try:
                data, addr = sock.recvfrom(1024)
                ip = addr[0]
                msg = data.decode('utf-8')
                
                if msg.startswith("CARPECAST_RECEIVER:"):
                    parts = msg.split(":")
                    if len(parts) >= 5:
                        name = parts[1]
                        dataport = int(parts[2])
                        dtype = parts[3]
                        os_name = parts[4]
                        
                        if ip not in self.discovered_devices:
                            self.log(f"🔍 新发现设备! IP: {ip}, 名字: {name}, 数据端口: {dataport}")
                            self.discovered_devices[ip] = (name, dataport, dtype, os_name)
                            self.root.after(0, self.update_device_list)
            except Exception as e:
                self.log(f"UDP 接收错误: {e}")

    def toggle_sync(self):
        if self.is_syncing:
            self.is_syncing = False
            self.btn_connect.config(text="连接并同步")
            self.status_var.set("状态: 已停止同步")
            self.log("🛑 用户停止了同步。")
        else:
            selection = self.device_listbox.curselection()
            if not selection:
                messagebox.showwarning("提示", "请先从列表中选择一台设备。")
                return
            
            index = selection[0]
            ip_list = list(self.discovered_devices.keys())
            self.selected_ip = ip_list[index]
            self.selected_port = self.discovered_devices[self.selected_ip][1]
            
            self.is_syncing = True
            self.btn_connect.config(text="断开连接")
            
            dev_name = self.discovered_devices[self.selected_ip][0]
            self.status_var.set(f"状态: 正在向 {dev_name} 同步...")
            self.log(f"▶️ 开始同步至 {dev_name} ({self.selected_ip}:{self.selected_port})")

    def get_global_track_info(self):
        swift_exe = get_resource_path("mac_nowplaying")
        if not os.path.exists(swift_exe):
            self.log(f"❌ 找不到底层程序: {swift_exe}")
            return None
            
        try:
            result = subprocess.run([swift_exe], capture_output=True, text=True)
            output = result.stdout.strip()
            stderr = result.stderr.strip()
            
            if stderr:
                self.log(f"⚠️ Swift 警告/报错: {stderr}")
                
            if not output:
                self.log("⚠️ 原生接口返回为空，当前可能没有媒体播放。")
                return None
                
            self.log(f"🎵 原生捕获数据: {output}")
            
            if "|||" in output:
                parts = output.split('|||')
                title = parts[0].strip()
                if title:
                    return {
                        "title": title,
                        "artist": parts[1].strip() if len(parts) > 1 else "",
                        "album": parts[2].strip() if len(parts) > 2 else "",
                        "isPlaying": (parts[3].strip() == "true") if len(parts) > 3 else True,
                        "position": float(parts[4]) if len(parts) > 4 else 0.0,
                        "duration": float(parts[5]) if len(parts) > 5 else 0.0,
                        "deviceName": "Mac Sender",
                        "deviceType": "Desktop",
                        "osVersion": "macOS",
                        "commandPort": 5002
                    }
        except Exception as e:
            self.log(f"❌ 调用 Swift 异常: {e}")
        return None

    def sync_loop(self):
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        while True:
            if self.is_syncing and self.selected_ip:
                track_info = self.get_global_track_info()
                if track_info:
                    try:
                        data = json.dumps(track_info).encode('utf-8')
                        sock.sendto(data, (self.selected_ip, self.selected_port))
                        self.log(f"📤 已发送 JSON 包 -> {data.decode('utf-8')}")
                        
                        title = track_info['title']
                        artist = track_info['artist']
                        self.root.after(0, lambda t=title, a=artist: self.status_var.set(f"正在同步: {t} - {a}"))
                    except Exception as e:
                        self.log(f"❌ UDP 发送失败: {e}")
                else:
                    self.root.after(0, lambda: self.status_var.set("状态: 当前没有媒体播放。"))
                    
            time.sleep(2)

if __name__ == "__main__":
    root = tk.Tk()
    app = MacSenderApp(root)
    root.mainloop()
