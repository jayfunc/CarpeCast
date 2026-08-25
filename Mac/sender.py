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

def get_resource_path(relative_path):
    """获取打包后资源的绝对路径"""
    if hasattr(sys, '_MEIPASS'):
        return os.path.join(sys._MEIPASS, relative_path)
    return os.path.join(os.path.abspath("."), relative_path)

class ConfigManager:
    def __init__(self):
        # macOS 规范的配置文件存放路径 (Application Support)
        self.config_dir = os.path.expanduser("~/Library/Application Support/CarpeCast")
        self.config_path = os.path.join(self.config_dir, "config.json")
        self.config = {
            "device_name": "Mac Sender",
            "discovery_port": 5001,
            "command_port": 5002
        }
        self.load()

    def load(self):
        if os.path.exists(self.config_path):
            try:
                with open(self.config_path, "r", encoding="utf-8") as f:
                    loaded = json.load(f)
                    self.config.update(loaded)
            except Exception:
                pass

    def save(self):
        try:
            os.makedirs(self.config_dir, exist_ok=True)
            with open(self.config_path, "w", encoding="utf-8") as f:
                json.dump(self.config, f, indent=4)
        except Exception:
            pass

    def get(self, key):
        return self.config.get(key)

    def set(self, key, value):
        self.config[key] = value

class MacSenderApp:
    def __init__(self, root):
        self.root = root
        self.root.title("CarpeCast")
        self.root.geometry("650x500")
        
        self.config_mgr = ConfigManager()
        self.discovered_devices = {} 
        self.is_syncing = False
        self.selected_ip = None
        self.selected_port = 5000
        self.logged_errors = set()
        
        self.setup_ui()
        
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(("8.8.8.8", 80))
            local_ip = s.getsockname()[0]
            s.close()
        except Exception:
            local_ip = "127.0.0.1"
            
        self.log(f"程序已启动，本机局域网 IP: {local_ip}")
        
        # 线程启动
        self.discovery_thread = threading.Thread(target=self.listen_for_discovery, daemon=True)
        self.discovery_thread.start()
        
        self.sync_thread = threading.Thread(target=self.sync_loop, daemon=True)
        self.sync_thread.start()

    def setup_ui(self):
        # 使用官方设计语言推荐的 Notebook (选项卡) 结构
        self.notebook = ttk.Notebook(self.root)
        self.notebook.pack(fill=tk.BOTH, expand=True, padx=10, pady=10)
        
        self.tab_player = ttk.Frame(self.notebook, padding=15)
        self.tab_devices = ttk.Frame(self.notebook, padding=15)
        self.tab_settings = ttk.Frame(self.notebook, padding=15)
        
        self.notebook.add(self.tab_player, text="  播放控制  ")
        self.notebook.add(self.tab_devices, text="  设备状态  ")
        self.notebook.add(self.tab_settings, text="  设置  ")
        
        self.build_player_tab()
        self.build_devices_tab()
        self.build_settings_tab()

    def build_player_tab(self):
        # 播放信息展示区
        info_frame = ttk.LabelFrame(self.tab_player, text="当前媒体信息", padding=20)
        info_frame.pack(fill=tk.X, expand=False, pady=10)
        
        self.lbl_title = ttk.Label(info_frame, text="等待同步...", font=("", 18, "bold"))
        self.lbl_title.pack(anchor=tk.W, pady=5)
        
        self.lbl_artist = ttk.Label(info_frame, text="歌手: -", font=("", 13))
        self.lbl_artist.pack(anchor=tk.W, pady=2)
        
        self.lbl_album = ttk.Label(info_frame, text="专辑: -", font=("", 12), foreground="gray")
        self.lbl_album.pack(anchor=tk.W, pady=2)
        
        self.lbl_status = ttk.Label(info_frame, text="状态: -", font=("", 12), foreground="gray")
        self.lbl_status.pack(anchor=tk.W, pady=(10, 2))

        ttk.Label(self.tab_player, text="* 注: Mac 端当前以提取和同步数据为主，若需播放控制请在 Windows 端操作。", 
                  foreground="gray", wraplength=600).pack(side=tk.BOTTOM, pady=10)

    def build_devices_tab(self):
        top_frame = ttk.Frame(self.tab_devices)
        top_frame.pack(fill=tk.X)
        
        ttk.Label(top_frame, text="已发现的 Windows 接收端：", font=("", 12)).pack(side=tk.LEFT)
        
        self.device_listbox = tk.Listbox(self.tab_devices, height=4, font=("", 12))
        self.device_listbox.pack(fill=tk.X, pady=10)
        
        self.btn_connect = ttk.Button(self.tab_devices, text="连接并同步", command=self.toggle_sync)
        self.btn_connect.pack(fill=tk.X)
        
        ttk.Label(self.tab_devices, text="🔧 开发者调试日志 (Debug Log)：", font=("", 11)).pack(anchor=tk.W, pady=(15, 5))
        self.log_area = st.ScrolledText(self.tab_devices, height=10, font=("Menlo", 10), bg="#fcfcfc")
        self.log_area.pack(fill=tk.BOTH, expand=True)

    def build_settings_tab(self):
        form_frame = ttk.Frame(self.tab_settings)
        form_frame.pack(fill=tk.X, pady=10)
        
        # 设备名称
        ttk.Label(form_frame, text="设备名称:").grid(row=0, column=0, sticky=tk.W, pady=10)
        self.ent_dev_name = ttk.Entry(form_frame, width=30)
        self.ent_dev_name.insert(0, self.config_mgr.get("device_name"))
        self.ent_dev_name.grid(row=0, column=1, padx=10, pady=10)
        
        # 发现端口
        ttk.Label(form_frame, text="设备发现端口 (UDP):").grid(row=1, column=0, sticky=tk.W, pady=10)
        self.ent_disc_port = ttk.Entry(form_frame, width=30)
        self.ent_disc_port.insert(0, str(self.config_mgr.get("discovery_port")))
        self.ent_disc_port.grid(row=1, column=1, padx=10, pady=10)

        # 控制端口
        ttk.Label(form_frame, text="播放控制端口 (TCP/UDP):").grid(row=2, column=0, sticky=tk.W, pady=10)
        self.ent_cmd_port = ttk.Entry(form_frame, width=30)
        self.ent_cmd_port.insert(0, str(self.config_mgr.get("command_port")))
        self.ent_cmd_port.grid(row=2, column=1, padx=10, pady=10)
        
        btn_save = ttk.Button(self.tab_settings, text="保存设置", command=self.save_settings)
        btn_save.pack(anchor=tk.W, pady=20)
        
        ttk.Label(self.tab_settings, text="配置文件存储路径:\n" + self.config_mgr.config_path, 
                  foreground="gray", wraplength=600).pack(anchor=tk.W, side=tk.BOTTOM)

    def save_settings(self):
        try:
            self.config_mgr.set("device_name", self.ent_dev_name.get().strip())
            self.config_mgr.set("discovery_port", int(self.ent_disc_port.get().strip()))
            self.config_mgr.set("command_port", int(self.ent_cmd_port.get().strip()))
            self.config_mgr.save()
            messagebox.showinfo("成功", "设置已保存！\n(部分网络端口设置需要重启应用生效)")
        except ValueError:
            messagebox.showerror("错误", "端口号必须是数字！")

    def log_once(self, msg):
        if msg not in self.logged_errors:
            self.log(msg)
            self.logged_errors.add(msg)

    def log(self, msg):
        timestamp = datetime.datetime.now().strftime("%H:%M:%S")
        full_msg = f"[{timestamp}] {msg}\n"
        def append():
            self.log_area.insert(tk.END, full_msg)
            self.log_area.see(tk.END)
        self.root.after(0, append)

    def update_device_list(self):
        self.device_listbox.delete(0, tk.END)
        for ip, info in self.discovered_devices.items():
            name, port, dtype, os_name = info
            self.device_listbox.insert(tk.END, f"💻 {name} ({ip}) - {os_name}")

    def listen_for_discovery(self):
        port = self.config_mgr.get("discovery_port")
        self.log(f"启动 UDP 监听，端口: {port}...")
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        if hasattr(socket, 'SO_REUSEPORT'):
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEPORT, 1)
            
        try:
            sock.bind(('', port))
            self.log(f"✅ 成功绑定发现端口 {port}，等待 Windows 广播包...")
        except Exception as e:
            self.log(f"❌ 绑定端口 {port} 失败: {e}")
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
                pass

    def toggle_sync(self):
        if self.is_syncing:
            self.is_syncing = False
            self.btn_connect.config(text="连接并同步")
            self.log("🛑 用户停止了同步。")
            self.update_player_ui("已断开同步", "-", "-", False, "-")
        else:
            selection = self.device_listbox.curselection()
            if not selection:
                messagebox.showwarning("提示", "请先从列表中选择一台接收端设备。")
                return
            
            index = selection[0]
            ip_list = list(self.discovered_devices.keys())
            self.selected_ip = ip_list[index]
            self.selected_port = self.discovered_devices[self.selected_ip][1]
            
            self.is_syncing = True
            self.btn_connect.config(text="断开连接")
            
            dev_name = self.discovered_devices[self.selected_ip][0]
            self.log(f"▶️ 开始同步至 {dev_name} ({self.selected_ip}:{self.selected_port})")

    def update_player_ui(self, title, artist, album, is_playing, method):
        self.lbl_title.config(text=title)
        self.lbl_artist.config(text=f"歌手: {artist}")
        self.lbl_album.config(text=f"专辑: {album}")
        status_text = "播放中" if is_playing else "已暂停"
        self.lbl_status.config(text=f"状态: {status_text} (来源: {method})")

    def get_applescript_fallback(self):
        # 使用 osascript 直接调用，并避开 System Events，防止额外的权限弹窗
        script = """
        set track_name to ""
        set track_artist to ""
        set track_album to ""
        set is_playing to "false"
        set track_duration to 0.0
        set track_position to 0.0

        if application "Spotify" is running then
            tell application "Spotify"
                if player state is playing then
                    set track_name to name of current track
                    set track_artist to artist of current track
                    set track_album to album of current track
                    set is_playing to "true"
                    set track_duration to (duration of current track) / 1000.0
                    set track_position to player position
                end if
            end tell
        end if

        if track_name is "" and application "Music" is running then
            tell application "Music"
                if player state is playing then
                    set track_name to name of current track
                    set track_artist to artist of current track
                    set track_album to album of current track
                    set is_playing to "true"
                    set track_duration to duration of current track
                    set track_position to player position
                end if
            end tell
        end if

        if track_name is not "" then
            return track_name & "|||" & track_artist & "|||" & track_album & "|||" & is_playing & "|||" & track_position & "|||" & track_duration & "|||AppleScript"
        else
            return ""
        end if
        """
        try:
            result = subprocess.run(["osascript", "-e", script], capture_output=True, text=True)
            if result.stderr:
                err = result.stderr.strip()
                if "-1743" in err or "Not authorized" in err:
                    self.log_once("🍎 系统权限拦截: 请在 Mac [系统设置 -> 隐私与安全性 -> 自动化] 中，允许 CarpeCast 控制 Music/Spotify！")
                else:
                    self.log_once(f"⚠️ AppleScript 错误: {err}")
            return result.stdout.strip()
        except Exception as e:
            return ""

    def get_global_track_info(self):
        output = ""
        swift_exe = get_resource_path("mac_nowplaying")
        
        # 1. 尝试原生 MediaRemote 获取
        if os.path.exists(swift_exe):
            try:
                result = subprocess.run([swift_exe], capture_output=True, text=True)
                output = result.stdout.strip()
            except Exception:
                pass
                
        # 2. 如果原生接口返回空（可能是虚拟机抢占了焦点），触发 AppleScript 降级抓取
        if not output:
            output = self.get_applescript_fallback()
            
        if not output:
            return None
            
        if "|||" in output:
            parts = output.split('|||')
            title = parts[0].strip()
            if title:
                method = parts[6].strip() if len(parts) > 6 else "Unknown"
                return {
                    "title": title,
                    "artist": parts[1].strip() if len(parts) > 1 else "",
                    "album": parts[2].strip() if len(parts) > 2 else "",
                    "isPlaying": (parts[3].strip() == "true") if len(parts) > 3 else True,
                    "position": float(parts[4]) if len(parts) > 4 else 0.0,
                    "duration": float(parts[5]) if len(parts) > 5 else 0.0,
                    "method": method, 
                    "deviceName": self.config_mgr.get("device_name"),
                    "deviceType": "Desktop",
                    "osVersion": "macOS",
                    "commandPort": self.config_mgr.get("command_port")
                }
        return None

    def sync_loop(self):
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        last_log_state = ""
        
        while True:
            if self.is_syncing and self.selected_ip:
                track_info = self.get_global_track_info()
                if track_info:
                    try:
                        # 本地展示所需字段
                        method = track_info.pop("method", "Unknown")
                        title = track_info['title']
                        artist = track_info['artist']
                        album = track_info['album']
                        is_playing = track_info['isPlaying']
                        
                        # 序列化为给 Windows 端的标准 JSON
                        data = json.dumps(track_info).encode('utf-8')
                        sock.sendto(data, (self.selected_ip, self.selected_port))
                        
                        # 按需打印日志，防止每两秒刷屏
                        current_state = f"{title}-{is_playing}"
                        if current_state != last_log_state:
                            self.log(f"🎵 [{method}] 捕获并发送: {title} - {artist}")
                            last_log_state = current_state
                        
                        self.root.after(0, self.update_player_ui, title, artist, album, is_playing, method)
                        
                    except Exception as e:
                        self.log_once(f"❌ UDP 发送失败: {e}")
                else:
                    self.root.after(0, self.update_player_ui, "当前无媒体播放", "-", "-", False, "-")
                    
            time.sleep(2)

if __name__ == "__main__":
    root = tk.Tk()
    app = MacSenderApp(root)
    root.mainloop()
