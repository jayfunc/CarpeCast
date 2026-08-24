import subprocess
import time
import json
import socket
import os
import sys
import threading
import tkinter as tk
from tkinter import ttk, messagebox

DISCOVERY_PORT = 5001

def get_resource_path(relative_path):
    """获取打包后资源的绝对路径 (支持 PyInstaller)"""
    if hasattr(sys, '_MEIPASS'):
        return os.path.join(sys._MEIPASS, relative_path)
    return os.path.join(os.path.abspath("."), relative_path)

class MacSenderApp:
    def __init__(self, root):
        self.root = root
        self.root.title("CarpeCast Mac Sender")
        self.root.geometry("450x300")
        
        self.discovered_devices = {} # ip -> (name, dataport, type, os)
        self.is_syncing = False
        self.selected_ip = None
        self.selected_port = 5000
        
        self.setup_ui()
        
        # 启动 UDP 发现监听线程
        self.discovery_thread = threading.Thread(target=self.listen_for_discovery, daemon=True)
        self.discovery_thread.start()
        
        # 启动同步发送线程
        self.sync_thread = threading.Thread(target=self.sync_loop, daemon=True)
        self.sync_thread.start()

    def setup_ui(self):
        # 容器
        frame = ttk.Frame(self.root, padding="15")
        frame.pack(fill=tk.BOTH, expand=True)
        
        # 标题与提示
        ttk.Label(frame, text="发现了以下 Windows 接收端：", font=("", 12)).pack(anchor=tk.W, pady=(0, 5))
        
        # 设备列表
        self.device_listbox = tk.Listbox(frame, height=6, font=("", 11))
        self.device_listbox.pack(fill=tk.BOTH, expand=True, pady=5)
        
        # 按钮区
        btn_frame = ttk.Frame(frame)
        btn_frame.pack(fill=tk.X, pady=10)
        
        self.btn_connect = ttk.Button(btn_frame, text="连接并同步", command=self.toggle_sync)
        self.btn_connect.pack(fill=tk.X)
        
        # 状态栏
        self.status_var = tk.StringVar()
        self.status_var.set("状态: 正在监听网络中的接收端...")
        ttk.Label(frame, textvariable=self.status_var, wraplength=400, foreground="gray").pack(anchor=tk.W)

    def update_device_list(self):
        """刷新 UI 中的设备列表"""
        self.device_listbox.delete(0, tk.END)
        for ip, info in self.discovered_devices.items():
            name, port, dtype, os_name = info
            self.device_listbox.insert(tk.END, f"💻 {name} ({ip}) - {os_name}")

    def listen_for_discovery(self):
        """监听 UDP 5001 广播端口，获取 Windows 端信息"""
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        if hasattr(socket, 'SO_REUSEPORT'):
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEPORT, 1)
            
        sock.bind(('', DISCOVERY_PORT))
        
        while True:
            try:
                data, addr = sock.recvfrom(1024)
                ip = addr[0]
                msg = data.decode('utf-8')
                
                # 协议格式: CARPECAST_RECEIVER:{DeviceName}:{DataPort}:{DeviceType}:{OS}
                if msg.startswith("CARPECAST_RECEIVER:"):
                    parts = msg.split(":")
                    if len(parts) >= 5:
                        name = parts[1]
                        dataport = int(parts[2])
                        dtype = parts[3]
                        os_name = parts[4]
                        
                        if ip not in self.discovered_devices:
                            self.discovered_devices[ip] = (name, dataport, dtype, os_name)
                            # 在主线程更新 UI
                            self.root.after(0, self.update_device_list)
            except Exception as e:
                print(f"监听错误: {e}")

    def toggle_sync(self):
        """处理连接与断开"""
        if self.is_syncing:
            self.is_syncing = False
            self.btn_connect.config(text="连接并同步")
            self.status_var.set("状态: 已停止同步")
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

    def get_global_track_info(self):
        """调用 Swift 辅助程序获取 macOS 全局媒体状态"""
        swift_exe = get_resource_path("mac_nowplaying")
        try:
            result = subprocess.run([swift_exe], capture_output=True, text=True)
            output = result.stdout.strip()
            
            if output and "|||" in output:
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
                        "commandPort": 5002 # 占位，当前 Mac 端暂不接收反向控制
                    }
        except Exception as e:
            print(f"调用 Swift 失败: {e}")
            pass
        return None

    def sync_loop(self):
        """每两秒向 Windows 发送 UDP 数据包"""
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        while True:
            if self.is_syncing and self.selected_ip:
                track_info = self.get_global_track_info()
                if track_info:
                    try:
                        # 转为 JSON 并发送 UDP 包
                        data = json.dumps(track_info).encode('utf-8')
                        sock.sendto(data, (self.selected_ip, self.selected_port))
                        
                        # 更新 UI 状态
                        title = track_info['title']
                        artist = track_info['artist']
                        self.root.after(0, lambda t=title, a=artist: self.status_var.set(f"正在同步: {t} - {a}"))
                    except Exception as e:
                        pass
                else:
                    self.root.after(0, lambda: self.status_var.set("状态: 当前没有媒体播放。"))
                    
            time.sleep(2)

if __name__ == "__main__":
    root = tk.Tk()
    # 强制在 macOS 前台展示窗口
    os.system('''/usr/bin/osascript -e 'tell app "Finder" to set frontmost of process "Python" to true' ''')
    app = MacSenderApp(root)
    root.mainloop()
