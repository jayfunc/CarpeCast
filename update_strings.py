import xml.etree.ElementTree as ET
import os

android_res_dir = r"d:\Workspace\CarpeCast\Android\app\src\main\res"

android_strings = {
    "values": {
        "settings_troubleshooting": "Troubleshooting",
        "settings_open_log_folder": "View Log File",
        "settings_open_log_folder_desc": "View application log files for troubleshooting.",
        "settings_clear_logs": "Clear Logs",
        "settings_clear_logs_desc": "Delete all stored log files.",
        "logs_cleared": "Logs cleared."
    },
    "values-zh": {
        "settings_troubleshooting": "疑难解答",
        "settings_open_log_folder": "查看日志文件",
        "settings_open_log_folder_desc": "查看应用程序日志文件以排查问题。",
        "settings_clear_logs": "清空日志",
        "settings_clear_logs_desc": "删除所有已保存的日志文件。",
        "logs_cleared": "日志已清空。"
    },
    "values-zh-rTW": {
        "settings_troubleshooting": "疑難排解",
        "settings_open_log_folder": "檢視記錄檔",
        "settings_open_log_folder_desc": "檢視應用程式記錄檔以排查問題。",
        "settings_clear_logs": "清除記錄",
        "settings_clear_logs_desc": "刪除所有已儲存的記錄檔。",
        "logs_cleared": "記錄已清除。"
    },
    "values-ja": {
        "settings_troubleshooting": "トラブルシューティング",
        "settings_open_log_folder": "ログファイルを表示",
        "settings_open_log_folder_desc": "トラブルシューティングのためにアプリケーションのログファイルを表示します。",
        "settings_clear_logs": "ログをクリア",
        "settings_clear_logs_desc": "保存されているすべてのログファイルを削除します。",
        "logs_cleared": "ログがクリアされました。"
    }
}

for folder, strings in android_strings.items():
    file_path = os.path.join(android_res_dir, folder, "strings.xml")
    if not os.path.exists(file_path):
        continue
    ET.register_namespace('', 'http://schemas.android.com/apk/res/android')
    tree = ET.parse(file_path)
    root = tree.getroot()
    
    for key, value in strings.items():
        # Check if exists
        elem = root.find(f"./string[@name='{key}']")
        if elem is not None:
            elem.text = value
        else:
            new_elem = ET.Element("string", name=key)
            new_elem.text = value
            # Insert before Foreground Service Notification if possible
            root.append(new_elem)
            
    with open(file_path, "wb") as f:
        f.write(b"<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        tree.write(f, encoding="utf-8")
        print(f"Updated {file_path}")

windows_res_dir = r"d:\Workspace\CarpeCast\Windows\Strings"
windows_strings = {
    "en": {
        "SettingsGroupGeneral.Text": "General",
        "SettingsGroupStartup.Text": "Startup",
        "SettingsGroupNetwork.Text": "Network",
        "SettingsGroupTroubleshooting.Text": "Troubleshooting",
        "SettingsGroupAbout.Text": "About"
    },
    "zh-Hans": {
        "SettingsGroupGeneral.Text": "通用",
        "SettingsGroupStartup.Text": "启动",
        "SettingsGroupNetwork.Text": "网络",
        "SettingsGroupTroubleshooting.Text": "疑难解答",
        "SettingsGroupAbout.Text": "关于"
    },
    "zh-Hant": {
        "SettingsGroupGeneral.Text": "一般",
        "SettingsGroupStartup.Text": "啟動",
        "SettingsGroupNetwork.Text": "網路",
        "SettingsGroupTroubleshooting.Text": "疑難排解",
        "SettingsGroupAbout.Text": "關於"
    },
    "ja": {
        "SettingsGroupGeneral.Text": "一般",
        "SettingsGroupStartup.Text": "スタートアップ",
        "SettingsGroupNetwork.Text": "ネットワーク",
        "SettingsGroupTroubleshooting.Text": "トラブルシューティング",
        "SettingsGroupAbout.Text": "情報"
    }
}

for folder, strings in windows_strings.items():
    file_path = os.path.join(windows_res_dir, folder, "Resources.resw")
    if not os.path.exists(file_path):
        continue
    
    # Parse xml, append missing
    tree = ET.parse(file_path)
    root = tree.getroot()
    
    for key, value in strings.items():
        # check if data name=key exists
        elem = root.find(f"./data[@name='{key}']")
        if elem is not None:
            val_elem = elem.find("value")
            if val_elem is not None:
                val_elem.text = value
        else:
            data_elem = ET.Element("data", name=key)
            data_elem.set("xml:space", "preserve")
            val_elem = ET.Element("value")
            val_elem.text = value
            data_elem.append(val_elem)
            root.append(data_elem)
            
    with open(file_path, "wb") as f:
        f.write(b"<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        tree.write(f, encoding="utf-8")
        print(f"Updated {file_path}")
