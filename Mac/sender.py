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

def get_version_info():
    version = "1.0.4"
    commit_hash = "dev"
    try:
        build_info_path = get_resource_path("build_info.json")
        if os.path.exists(build_info_path):
            with open(build_info_path, "r", encoding="utf-8") as f:
                info = json.load(f)
                return info.get("version", version), info.get("commit", commit_hash)
    except Exception:
        pass
        
    try:
        result = subprocess.run(["git", "rev-parse", "--short", "HEAD"], capture_output=True, text=True, cwd=os.path.abspath("."))
        if result.returncode == 0:
            commit_hash = result.stdout.strip()
    except Exception:
        pass
        
    return version, commit_hash


I18N = {
    "zh": {
        "tab_player": "  播放控制  ",
        "tab_devices": "  设备状态  ",
        "tab_settings": "  设置  ",
        "current_media": "当前媒体信息",
        "waiting_sync": "等待同步...",
        "artist": "歌手: ",
        "album": "专辑: ",
        "status": "状态: ",
        "playing": "播放中",
        "paused": "已暂停",
        "no_media": "当前无媒体播放",
        "source": "来源: ",
        "mac_note": "* 注: Mac 端当前以提取和同步数据为主，若需播放控制请在 Windows 端操作。",
        "discovered_devices": "已发现的 Windows 接收端：",
        "connect_sync": "连接并同步",
        "disconnect": "断开连接",
        "debug_log": "🔧 开发者调试日志 (Debug Log)：",
        "dev_name": "设备名称:",
        "disc_port": "设备发现端口 (UDP):",
        "cmd_port": "播放控制端口 (TCP/UDP):",
        "theme": "主题外观:",
        "theme_sys": "跟随系统",
        "theme_light": "浅色 (Light)",
        "theme_dark": "深色 (Dark)",
        "lang": "应用语言:",
        "lang_sys": "跟随系统",
        "lang_zh": "简体中文",
        "lang_hant": "繁體中文",
        "lang_en": "English",
        "lang_ja": "日本語",
        "save_settings": "保存设置",
        "config_path": "配置文件存储路径:\n",
        "get_receiver": "获取接收端 / 访问官网",
        "about_lyrics": "了解 BetterLyrics",
        "success": "成功",
        "success_msg": "设置已保存！\n(部分网络及界面设置需要重启应用生效)",
        "error": "错误",
        "error_port": "端口号必须是数字！",
        "tip": "提示",
        "tip_select": "请先从列表中选择一台接收端设备。",
        "prog_started": "程序已启动，本机局域网 IP: {}",
        "start_listen": "启动 UDP 监听，端口: {}...",
        "bind_success": "✅ 成功绑定发现端口 {}，等待 Windows 广播包...",
        "bind_fail": "❌ 绑定端口 {} 失败: {}",
        "new_device": "🔍 新发现设备! IP: {}, 名字: {}, 数据端口: {}",
        "stop_sync": "🛑 用户停止了同步。",
        "disconnected": "已断开同步",
        "start_sync": "▶️ 开始同步至 {} ({}:{})",
        "perm_error": "🍎 系统权限拦截: 请在 Mac [系统设置 -> 隐私与安全性 -> 自动化] 中，允许 CarpeCast 控制 Music/Spotify！",
        "as_error": "⚠️ AppleScript 错误: {}",
        "send_fail": "❌ UDP 发送失败: {}"
    },
    "zh-Hant": {
        "tab_player": "  播放控制  ",
        "tab_devices": "  設備狀態  ",
        "tab_settings": "  設定  ",
        "current_media": "目前媒體資訊",
        "waiting_sync": "等待同步...",
        "artist": "歌手: ",
        "album": "專輯: ",
        "status": "狀態: ",
        "playing": "播放中",
        "paused": "已暫停",
        "no_media": "目前無媒體播放",
        "source": "來源: ",
        "mac_note": "* 註: Mac 端目前以擷取和同步資料為主，若需播放控制請在 Windows 端操作。",
        "discovered_devices": "已發現的 Windows 接收端：",
        "connect_sync": "連線並同步",
        "disconnect": "斷開連線",
        "debug_log": "🔧 開發者除錯日誌 (Debug Log)：",
        "dev_name": "裝置名稱:",
        "disc_port": "發現連接埠 (UDP):",
        "cmd_port": "播放控制連接埠 (TCP/UDP):",
        "theme": "外觀主題:",
        "theme_sys": "跟隨系統",
        "theme_light": "淺色 (Light)",
        "theme_dark": "深色 (Dark)",
        "lang": "應用語言:",
        "lang_sys": "跟隨系統",
        "lang_zh": "简体中文",
        "lang_hant": "繁體中文",
        "lang_en": "English",
        "lang_ja": "日本語",
        "save_settings": "儲存設定",
        "config_path": "設定檔儲存路徑:\n",
        "get_receiver": "取得接收端 / 瀏覽官網",
        "about_lyrics": "了解 BetterLyrics",
        "success": "成功",
        "success_msg": "設定已儲存！\n(部分網路及介面設定需要重啟應用程式生效)",
        "error": "錯誤",
        "error_port": "連接埠必須是數字！",
        "tip": "提示",
        "tip_select": "請先從列表中選擇一台接收端設備。",
        "prog_started": "程式已啟動，本機區域網路 IP: {}",
        "start_listen": "啟動 UDP 監聽，連接埠: {}...",
        "bind_success": "✅ 成功綁定發現連接埠 {}，等待 Windows 廣播封包...",
        "bind_fail": "❌ 綁定連接埠 {} 失敗: {}",
        "new_device": "🔍 新發現設備! IP: {}, 名字: {}, 資料連接埠: {}",
        "stop_sync": "🛑 使用者停止了同步。",
        "disconnected": "已斷開同步",
        "start_sync": "▶️ 開始同步至 {} ({}:{})",
        "perm_error": "🍎 系統權限攔截: 請在 Mac [系統設定 -> 隱私權與安全性 -> 自動化] 中，允許 CarpeCast 控制 Music/Spotify！",
        "as_error": "⚠️ AppleScript 錯誤: {}",
        "send_fail": "❌ UDP 發送失敗: {}"
    },
    "ja": {
        "tab_player": "  プレーヤー  ",
        "tab_devices": "  デバイス  ",
        "tab_settings": "  設定  ",
        "current_media": "現在のメディア情報",
        "waiting_sync": "同期を待機中...",
        "artist": "アーティスト: ",
        "album": "アルバム: ",
        "status": "ステータス: ",
        "playing": "再生中",
        "paused": "一時停止",
        "no_media": "メディアが再生されていません",
        "source": "ソース: ",
        "mac_note": "* 注: Mac版は現在データの抽出と同期を主としています。再生制御はWindowsで行ってください。",
        "discovered_devices": "発見されたWindowsデバイス:",
        "connect_sync": "接続して同期",
        "disconnect": "切断",
        "debug_log": "🔧 デバッグログ (Debug Log):",
        "dev_name": "デバイス名:",
        "disc_port": "探索ポート (UDP):",
        "cmd_port": "コマンドポート (TCP/UDP):",
        "theme": "テーマ:",
        "theme_sys": "システムに従う",
        "theme_light": "ライト",
        "theme_dark": "ダーク",
        "lang": "言語:",
        "lang_sys": "システムに従う",
        "lang_zh": "简体中文",
        "lang_hant": "繁體中文",
        "lang_en": "English",
        "lang_ja": "日本語",
        "save_settings": "設定を保存",
        "config_path": "設定ファイルのパス:\n",
        "get_receiver": "受信アプリを取得 / 公式サイトへ",
        "about_lyrics": "BetterLyricsについて",
        "success": "成功",
        "success_msg": "設定を保存しました！\n(一部の設定はアプリの再起動後に適用されます)",
        "error": "エラー",
        "error_port": "ポート番号は数字である必要があります！",
        "tip": "ヒント",
        "tip_select": "リストから受信デバイスを選択してください。",
        "prog_started": "起動しました。ローカルIP: {}",
        "start_listen": "UDPリッスンを開始。ポート: {}...",
        "bind_success": "✅ 探索ポート {} のバインドに成功。Windowsからのブロードキャストを待機中...",
        "bind_fail": "❌ ポート {} のバインドに失敗: {}",
        "new_device": "🔍 新しいデバイスを発見! IP: {}, 名前: {}, データポート: {}",
        "stop_sync": "🛑 同期を停止しました。",
        "disconnected": "同期が切断されました",
        "start_sync": "▶️ 同期を開始: {} ({}:{})",
        "perm_error": "🍎 権限エラー: Macの [システム設定 -> プライバシーとセキュリティ -> オートメーション] で、Music/Spotifyの制御を許可してください！",
        "as_error": "⚠️ AppleScriptエラー: {}",
        "send_fail": "❌ UDP送信失敗: {}"
    },
    "en": {
        "tab_player": "  Player  ",
        "tab_devices": "  Devices  ",
        "tab_settings": "  Settings  ",
        "current_media": "Current Media",
        "waiting_sync": "Waiting for sync...",
        "artist": "Artist: ",
        "album": "Album: ",
        "status": "Status: ",
        "playing": "Playing",
        "paused": "Paused",
        "no_media": "No Media Playing",
        "source": "Source: ",
        "mac_note": "* Note: The Mac version currently focuses on data extraction and sync. Use Windows for playback control.",
        "discovered_devices": "Discovered Windows Receivers:",
        "connect_sync": "Connect & Sync",
        "disconnect": "Disconnect",
        "debug_log": "🔧 Debug Log:",
        "dev_name": "Device Name:",
        "disc_port": "Discovery Port (UDP):",
        "cmd_port": "Command Port (TCP/UDP):",
        "theme": "Theme:",
        "theme_sys": "System Default",
        "theme_light": "Light",
        "theme_dark": "Dark",
        "lang": "Language:",
        "lang_sys": "System Default",
        "lang_zh": "简体中文",
        "lang_hant": "繁體中文",
        "lang_en": "English",
        "lang_ja": "日本語",
        "save_settings": "Save Settings",
        "config_path": "Config Path:\n",
        "get_receiver": "Get Receiver App / Visit Website",
        "about_lyrics": "About BetterLyrics",
        "success": "Success",
        "success_msg": "Settings saved!\n(Some network & UI settings require app restart)",
        "error": "Error",
        "error_port": "Port must be a number!",
        "tip": "Tip",
        "tip_select": "Please select a receiver from the list first.",
        "prog_started": "App started, Local IP: {}",
        "start_listen": "Starting UDP listener on port {}...",
        "bind_success": "✅ Bound discovery port {}. Waiting for Windows broadcast...",
        "bind_fail": "❌ Failed to bind port {}: {}",
        "new_device": "🔍 Discovered new device! IP: {}, Name: {}, Data Port: {}",
        "stop_sync": "🛑 User stopped sync.",
        "disconnected": "Disconnected",
        "start_sync": "▶️ Starting sync to {} ({}:{})",
        "perm_error": "🍎 Permission Error: Please allow CarpeCast to control Music/Spotify in Mac [System Settings -> Privacy & Security -> Automation]!",
        "as_error": "⚠️ AppleScript Error: {}",
        "send_fail": "❌ UDP Send Failed: {}"
    }
}

def get_system_language():
    try:
        import locale
        lang, _ = locale.getdefaultlocale()
        if lang:
            if lang.startswith('zh_TW') or lang.startswith('zh_HK') or lang.startswith('zh_Hant'):
                return 'zh-Hant'
            elif lang.startswith('zh'):
                return 'zh'
            elif lang.startswith('ja'):
                return 'ja'
    except:
        pass
    return 'en'

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
        self.root.geometry("650x650")
        
        self.config_mgr = ConfigManager()
        
        self.discovered_devices = {} 
        self.is_syncing = False
        self.selected_ip = None
        self.selected_port = 5000
        self.logged_errors = set()
        
        self.lang_setting = self.config_mgr.get("language") or "sys"
        if self.lang_setting == "zh":
            self.lang_code = "zh"
        elif self.lang_setting == "zh-Hant":
            self.lang_code = "zh-Hant"
        elif self.lang_setting == "en":
            self.lang_code = "en"
        elif self.lang_setting == "ja":
            self.lang_code = "ja"
        else:
            self.lang_code = get_system_language()
            
        self.setup_ui()
        self.setup_macos_dock_behavior()
        
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(("8.8.8.8", 80))
            local_ip = s.getsockname()[0]
            s.close()
        except Exception:
            local_ip = "127.0.0.1"
            
        self.log(self._("prog_started").format(local_ip))
        
        # 线程启动
        self.discovery_thread = threading.Thread(target=self.listen_for_discovery, daemon=True)
        self.discovery_thread.start()
        
        self.sync_thread = threading.Thread(target=self.sync_loop, daemon=True)
        self.sync_thread.start()
        
        # 强制刷新解决部分 Mac 机器首次白屏的问题
        self.root.update_idletasks()

    def _(self, key):
        return I18N.get(self.lang_code, I18N["en"]).get(key, I18N["en"].get(key, key))

        
    def setup_macos_dock_behavior(self):
        # 拦截点击红叉事件（将其改为隐藏窗口而不是直接退出）
        self.root.protocol("WM_DELETE_WINDOW", self.hide_window)
        # 拦截 macOS 程序坞 (Dock) 点击事件，实现再次唤醒
        try:
            self.root.createcommand("::tk::mac::ReopenApplication", self.show_window)
        except Exception:
            pass
            
    def hide_window(self):
        self.root.withdraw()
        
    def show_window(self, *args):
        self.root.deiconify()
        self.root.update_idletasks()

    def setup_ui(self):
        # 使用官方设计语言推荐的 Notebook (选项卡) 结构
        self.notebook = ttk.Notebook(self.root)
        self.notebook.pack(fill=tk.BOTH, expand=True, padx=10, pady=10)
        
        self.tab_player = ttk.Frame(self.notebook, padding=15)
        self.tab_devices = ttk.Frame(self.notebook, padding=15)
        self.tab_settings = ttk.Frame(self.notebook, padding=15)
        
        self.notebook.add(self.tab_player, text=self._("tab_player"))
        self.notebook.add(self.tab_devices, text=self._("tab_devices"))
        self.notebook.add(self.tab_settings, text=self._("tab_settings"))
        
        self.build_player_tab()
        self.build_devices_tab()
        self.build_settings_tab()

    def build_player_tab(self):
        # 播放信息展示区
        info_frame = ttk.LabelFrame(self.tab_player, text=self._("current_media"), padding=20)
        info_frame.pack(fill=tk.X, expand=False, pady=10)
        
        self.lbl_title = ttk.Label(info_frame, text=self._("waiting_sync"), font=("", 18, "bold"))
        self.lbl_title.pack(anchor=tk.W, pady=5)
        
        self.lbl_artist = ttk.Label(info_frame, text=self._("artist") + "-", font=("", 13))
        self.lbl_artist.pack(anchor=tk.W, pady=2)
        
        self.lbl_album = ttk.Label(info_frame, text=self._("album") + "-", font=("", 12), foreground="gray")
        self.lbl_album.pack(anchor=tk.W, pady=2)
        
        self.lbl_status = ttk.Label(info_frame, text=self._("status") + "-", font=("", 12), foreground="gray")
        self.lbl_status.pack(anchor=tk.W, pady=(10, 2))

        ttk.Label(self.tab_player, text=self._("mac_note"), 
                  foreground="gray", wraplength=600).pack(side=tk.BOTTOM, pady=10)

    def build_devices_tab(self):
        top_frame = ttk.Frame(self.tab_devices)
        top_frame.pack(fill=tk.X)
        
        ttk.Label(top_frame, text=self._("discovered_devices"), font=("", 12)).pack(side=tk.LEFT)
        
        self.device_listbox = tk.Listbox(self.tab_devices, height=4, font=("", 12))
        self.device_listbox.pack(fill=tk.X, pady=10)
        
        self.btn_connect = ttk.Button(self.tab_devices, text=self._("connect_sync"), command=self.toggle_sync)
        self.btn_connect.pack(fill=tk.X)
        
        ttk.Label(self.tab_devices, text=self._("debug_log"), font=("", 11)).pack(anchor=tk.W, pady=(15, 5))
        self.log_area = st.ScrolledText(self.tab_devices, height=10, font=("Menlo", 10))
        self.log_area.pack(fill=tk.BOTH, expand=True)

    def get_lang_map(self):
        return {
            self._("lang_sys"): "sys",
            self._("lang_zh"): "zh",
            self._("lang_hant"): "zh-Hant",
            self._("lang_en"): "en",
            self._("lang_ja"): "ja"
        }

    def get_theme_map(self):
        return {
            self._("theme_sys"): "sys",
            self._("theme_light"): "light",
            self._("theme_dark"): "dark"
        }

    def build_settings_tab(self):
        # 创建一个支持滚动的 Canvas 容器
        canvas = tk.Canvas(self.tab_settings, highlightthickness=0)
        scrollbar = ttk.Scrollbar(self.tab_settings, orient="vertical", command=canvas.yview)
        
        main_frame = ttk.Frame(canvas)
        canvas_window = canvas.create_window((0, 0), window=main_frame, anchor="nw")
        
        def on_canvas_configure(event):
            canvas.itemconfig(canvas_window, width=event.width)
            
        canvas.bind("<Configure>", on_canvas_configure)
        
        main_frame.bind(
            "<Configure>",
            lambda e: canvas.configure(
                scrollregion=canvas.bbox("all")
            )
        )
        
        # 绑定 macOS 鼠标滚轮
        def _on_mousewheel(event):
            canvas.yview_scroll(int(-1 * event.delta), "units")
            
        def _bind_mouse(event):
            canvas.bind_all("<MouseWheel>", _on_mousewheel)
            
        def _unbind_mouse(event):
            canvas.unbind_all("<MouseWheel>")
            
        canvas.bind('<Enter>', _bind_mouse)
        canvas.bind('<Leave>', _unbind_mouse)

        canvas.pack(side="left", fill="both", expand=True)
        scrollbar.pack(side="right", fill="y")
        canvas.configure(yscrollcommand=scrollbar.set)

        # 1. 顶部：Logo 与版本号
        top_frame = ttk.Frame(main_frame)
        top_frame.pack(fill=tk.X, pady=(15, 20))
        
        logo_path = get_resource_path("CarpeCast-logo.png")
        if os.path.exists(logo_path):
            try:
                self.logo_img = tk.PhotoImage(file=logo_path)
                # 原图 2048x2048，缩小到 1/32 以适应界面 (64x64)
                self.logo_img = self.logo_img.subsample(32, 32) 
                ttk.Label(top_frame, image=self.logo_img).pack()
            except Exception:
                ttk.Label(top_frame, text="🎵", font=("", 48)).pack()
        else:
            ttk.Label(top_frame, text="🎵", font=("", 48)).pack()
            
        ttk.Label(top_frame, text="CarpeCast", font=("", 18, "bold")).pack()
        version, commit_hash = get_version_info()
        version_text = f"v{version} - {commit_hash}" if commit_hash else f"v{version}"
        ttk.Label(top_frame, text=version_text, font=("", 12), foreground="gray").pack()

        # 2. 中间：表单设置
        form_frame = ttk.Frame(main_frame)
        form_frame.pack(fill=tk.X, padx=20)
        
        # 设备名称
        ttk.Label(form_frame, text=self._("dev_name")).grid(row=0, column=0, sticky=tk.W, pady=8)
        self.ent_dev_name = ttk.Entry(form_frame, width=25)
        self.ent_dev_name.insert(0, self.config_mgr.get("device_name") or "")
        self.ent_dev_name.grid(row=0, column=1, padx=10, pady=8, sticky=tk.EW)
        
        # 发现端口
        ttk.Label(form_frame, text=self._("disc_port")).grid(row=1, column=0, sticky=tk.W, pady=8)
        self.ent_disc_port = ttk.Entry(form_frame, width=25)
        self.ent_disc_port.insert(0, str(self.config_mgr.get("discovery_port") or ""))
        self.ent_disc_port.grid(row=1, column=1, padx=10, pady=8, sticky=tk.EW)

        # 控制端口
        ttk.Label(form_frame, text=self._("cmd_port")).grid(row=2, column=0, sticky=tk.W, pady=8)
        self.ent_cmd_port = ttk.Entry(form_frame, width=25)
        self.ent_cmd_port.insert(0, str(self.config_mgr.get("command_port") or ""))
        self.ent_cmd_port.grid(row=2, column=1, padx=10, pady=8, sticky=tk.EW)

        # 主题
        ttk.Label(form_frame, text=self._("theme")).grid(row=3, column=0, sticky=tk.W, pady=8)
        self.theme_var = tk.StringVar()
        theme_map = self.get_theme_map()
        rev_theme = {v: k for k, v in theme_map.items()}
        current_theme_code = self.config_mgr.get("theme") or "sys"
        self.cb_theme = ttk.Combobox(form_frame, textvariable=self.theme_var, values=list(theme_map.keys()), state="readonly", width=23)
        self.cb_theme.set(rev_theme.get(current_theme_code, rev_theme["sys"]))
        self.cb_theme.grid(row=3, column=1, padx=10, pady=8, sticky=tk.EW)

        # 语言
        ttk.Label(form_frame, text=self._("lang")).grid(row=4, column=0, sticky=tk.W, pady=8)
        self.lang_var = tk.StringVar()
        lang_map = self.get_lang_map()
        rev_lang = {v: k for k, v in lang_map.items()}
        current_lang_code = self.config_mgr.get("language") or "sys"
        self.cb_lang = ttk.Combobox(form_frame, textvariable=self.lang_var, values=list(lang_map.keys()), state="readonly", width=23)
        self.cb_lang.set(rev_lang.get(current_lang_code, rev_lang["sys"]))
        self.cb_lang.grid(row=4, column=1, padx=10, pady=8, sticky=tk.EW)
        
        form_frame.columnconfigure(1, weight=1)

        # 3. 保存按钮
        btn_save = ttk.Button(form_frame, text=self._("save_settings"), command=self.save_settings)
        btn_save.grid(row=5, column=0, columnspan=2, pady=(15, 5), sticky=tk.EW)

        # 保存状态提示标签
        self.lbl_save_status = ttk.Label(form_frame, text="", foreground="green")
        self.lbl_save_status.grid(row=6, column=0, columnspan=2, pady=(0, 5))

        # 4. 底部外部链接按钮
        bottom_frame = ttk.Frame(main_frame)
        bottom_frame.pack(fill=tk.X, padx=20, pady=(10, 0))
        
        import webbrowser
        btn_github = ttk.Button(bottom_frame, text=self._("get_receiver"), command=lambda: webbrowser.open("https://github.com/jayfunc/CarpeCast"))
        btn_github.pack(fill=tk.X, pady=4)

        btn_lyrics = ttk.Button(bottom_frame, text=self._("about_lyrics"), command=lambda: webbrowser.open("https://github.com/jayfunc/BetterLyrics"))
        btn_lyrics.pack(fill=tk.X, pady=4)

        # 配置文件路径显示
        ttk.Label(main_frame, text=self._("config_path") + self.config_mgr.config_path, 
                  foreground="gray", justify=tk.CENTER, wraplength=400).pack(side=tk.BOTTOM, pady=10)

    def save_settings(self):
        try:
            self.config_mgr.set("device_name", self.ent_dev_name.get().strip())
            self.config_mgr.set("discovery_port", int(self.ent_disc_port.get().strip()))
            self.config_mgr.set("command_port", int(self.ent_cmd_port.get().strip()))
            
            theme_map = self.get_theme_map()
            self.config_mgr.set("theme", theme_map.get(self.theme_var.get(), "sys"))
            
            lang_map = self.get_lang_map()
            self.config_mgr.set("language", lang_map.get(self.lang_var.get(), "sys"))
            
            self.config_mgr.save()
            self.lbl_save_status.config(text=self._("success_msg").replace('\n', ' '), foreground="green")
            self.root.after(3000, lambda: self.lbl_save_status.config(text=""))
        except ValueError:
            self.lbl_save_status.config(text=self._("error_port"), foreground="red")
            self.root.after(3000, lambda: self.lbl_save_status.config(text=""))

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
        self.log(self._("start_listen").format(port))
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        if hasattr(socket, 'SO_REUSEPORT'):
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEPORT, 1)
            
        try:
            sock.bind(('', port))
            self.log(self._("bind_success").format(port))
        except Exception as e:
            self.log(self._("bind_fail").format(port, e))
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
                            self.log(self._("new_device").format(ip, name, dataport))
                            self.discovered_devices[ip] = (name, dataport, dtype, os_name)
                            self.root.after(0, self.update_device_list)
            except Exception as e:
                pass

    def toggle_sync(self):
        if self.is_syncing:
            self.is_syncing = False
            self.btn_connect.config(text=self._("connect_sync"))
            self.log(self._("stop_sync"))
            self.update_player_ui(self._("disconnected"), "-", "-", False, "-")
        else:
            selection = self.device_listbox.curselection()
            if not selection:
                messagebox.showwarning(self._("tip"), self._("tip_select"))
                return
            
            index = selection[0]
            ip_list = list(self.discovered_devices.keys())
            self.selected_ip = ip_list[index]
            self.selected_port = self.discovered_devices[self.selected_ip][1]
            
            self.is_syncing = True
            self.btn_connect.config(text=self._("disconnect"))
            
            dev_name = self.discovered_devices[self.selected_ip][0]
            self.log(self._("start_sync").format(dev_name, self.selected_ip, self.selected_port))

    def update_player_ui(self, title, artist, album, is_playing, method):
        self.lbl_title.config(text=title)
        self.lbl_artist.config(text=f"{self._('artist')}{artist}")
        self.lbl_album.config(text=f"{self._('album')}{album}")
        status_text = self._("playing") if is_playing else self._("paused")
        self.lbl_status.config(text=f"{self._('status')}{status_text} ({self._('source')}{method})")

    def get_applescript_fallback(self):
        # 使用 osascript 直接调用，并避开 System Events，防止额外的权限弹窗
        script = """
        set track_name to ""
        set track_artist to ""
        set track_album to ""
        set is_playing to "false"
        set track_duration to 0.0
        set track_position to 0.0

        try
            if application "Spotify" is running then
                tell application "Spotify"
                    if player state is playing or player state is paused then
                        set track_name to name of current track
                        set track_artist to artist of current track
                        set track_album to album of current track
                        if player state is playing then
                            set is_playing to "true"
                        end if
                        set track_duration to (duration of current track) / 1000.0
                        set track_position to player position
                    end if
                end tell
            end if

            if track_name is "" and application "Music" is running then
                tell application "Music"
                    if player state is playing or player state is paused then
                        set track_name to name of current track
                        set track_artist to artist of current track
                        set track_album to album of current track
                        if player state is playing then
                            set is_playing to "true"
                        end if
                        set track_duration to duration of current track
                        set track_position to player position
                    end if
                end tell
            end if
        on error
            -- 忽略获取不到时的错误
        end try

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
                    self.log_once(self._("perm_error"))
                else:
                    self.log_once(self._("as_error").format(err))
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
                # The length might be 8 if MediaRemote (with artwork) or 7 if AppleScript (no artwork)
                method = parts[-1].strip()
                albumArtBase64 = parts[6].strip() if len(parts) > 7 else ""
                
                return {
                    "title": title,
                    "artist": parts[1].strip() if len(parts) > 1 else "",
                    "album": parts[2].strip() if len(parts) > 2 else "",
                    "isPlaying": (parts[3].strip() == "true") if len(parts) > 3 else True,
                    # Windows 端（同 Android 端）预期的是毫秒(ms)，所以需要乘以 1000
                    "position": float(parts[4]) * 1000.0 if len(parts) > 4 else 0.0,
                    "duration": float(parts[5]) * 1000.0 if len(parts) > 5 else 0.0,
                    "albumArt": albumArtBase64,
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
                        self.log_once(self._("send_fail").format(e))
                else:
                    self.root.after(0, self.update_player_ui, "当前无媒体播放", "-", "-", False, "-")
                    try:
                        empty_info = {
                            "title": "",
                            "artist": "",
                            "album": "",
                            "isPlaying": False,
                            "position": 0.0,
                            "duration": 0.0,
                            "albumArt": "",
                            "deviceName": self.config_mgr.get("device_name"),
                            "deviceType": "Desktop",
                            "osVersion": "macOS",
                            "commandPort": self.config_mgr.get("command_port")
                        }
                        data = json.dumps(empty_info).encode('utf-8')
                        sock.sendto(data, (self.selected_ip, self.selected_port))
                        
                        current_state = "empty"
                        if current_state != last_log_state:
                            self.log("🎵 发送空状态 (保持连接)")
                            last_log_state = current_state
                    except Exception as e:
                        self.log_once(self._("send_fail").format(e))
                    
            time.sleep(2)

if __name__ == "__main__":
    root = tk.Tk()
    app = MacSenderApp(root)
    root.mainloop()
