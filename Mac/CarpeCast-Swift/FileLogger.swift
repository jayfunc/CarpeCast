import Foundation

class FileLogger {
    static let shared = FileLogger()
    private var logsDir: URL?
    private let queue = DispatchQueue(label: "com.jayfunc.carpecast.filelogger")
    
    private init() {
        do {
            let fileManager = FileManager.default
            let dir = try fileManager.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
                .appendingPathComponent("CarpeCast/Logs", isDirectory: true)
            
            if !fileManager.fileExists(atPath: dir.path) {
                try fileManager.createDirectory(at: dir, withIntermediateDirectories: true, attributes: nil)
            }
            logsDir = dir
            
            setupUncaughtExceptionHandler()
            log("FileLogger initialized at \(dir.path)")
        } catch {
            print("Failed to initialize FileLogger: \(error)")
        }
    }
    
    private func setupUncaughtExceptionHandler() {
        NSSetUncaughtExceptionHandler { exception in
            cLog("CRASH: Uncaught exception: \(exception.name.rawValue) - \(exception.reason ?? "")")
            cLog("Stack Trace: \(exception.callStackSymbols.joined(separator: "\n"))")
            // Make sure logs are flushed (though it's async, we try to sleep briefly)
            Thread.sleep(forTimeInterval: 0.5)
        }
    }
    
    func log(_ message: String) {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss.SSS"
        let timestamp = formatter.string(from: Date())
        let logMessage = "[\(timestamp)] \(message)\n"
        
        print(message)
        
        queue.async {
            guard let dir = self.logsDir else { return }
            
            let fileFormatter = DateFormatter()
            fileFormatter.dateFormat = "yyyy-MM-dd"
            let dateStr = fileFormatter.string(from: Date())
            let fileURL = dir.appendingPathComponent("log-\(dateStr).txt")
            
            do {
                if !FileManager.default.fileExists(atPath: fileURL.path) {
                    FileManager.default.createFile(atPath: fileURL.path, contents: nil, attributes: nil)
                }
                let fileHandle = try FileHandle(forWritingTo: fileURL)
                fileHandle.seekToEndOfFile()
                if let data = logMessage.data(using: .utf8) {
                    fileHandle.write(data)
                }
                fileHandle.closeFile()
            } catch {
                print("Failed to write to log file: \(error)")
            }
        }
    }
    
    func getLogDirectory() -> URL? {
        return logsDir
    }
}

func cLog(_ message: String) {
    FileLogger.shared.log(message)
}
