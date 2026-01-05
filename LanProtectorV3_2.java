import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
// ДОБАВЬТЕ ЭТИ ДВЕ СТРОКИ В НАЧАЛО:
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class LanProtectorV3_2 {
    // ===== CONFIGURATION =====
    private static final String MULTICAST_ADDR = "224.0.2.60";
    private static final int PORT = 4445;
    private static final int BUFFER_SIZE = 1024;
    private static final int MIN_PORT = 0;
    private static final int MAX_PORT = 100000; // УМЕНЬШИЛ для теста (было 99999999)
    private static final int THREAD_COUNT = 10; // УМЕНЬШИЛ потоки (было 100)
    private static final Set<Integer> suspiciousPorts = new HashSet<>();
    private static final AtomicInteger blockedPackets = new AtomicInteger(0);
    private static final AtomicInteger scannedPorts = new AtomicInteger(0);
    private static volatile boolean running = true;
    
    // ===== COLORS FOR CONSOLE =====
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String PURPLE = "\u001B[35m";
    private static final String CYAN = "\u001B[36m";
    private static final String WHITE = "\u001B[37m";
    private static final String BOLD = "\u001B[1m";
    private static final String UNDERLINE = "\u001B[4m";
    private static final String BLINK = "\u001B[5m";
    private static final String REVERSE = "\u001B[7m";
    
    public static void main(String[] args) {
        printBanner();
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running = false;
            System.out.println(RED + BOLD + "\n╔══════════════════════════════════════════════════╗");
            System.out.println("║                 SHUTDOWN DETECTED               ║");
            System.out.println("╚══════════════════════════════════════════════════╝" + RESET);
            printStats();
        }));
        
        System.out.println(CYAN + BOLD + "[SYSTEM] Starting LAN Protector v3.2..." + RESET);
        System.out.println(YELLOW + "[INFO] Mega Port Scanner: " + MIN_PORT + " to " + MAX_PORT + RESET);
        System.out.println(YELLOW + "[INFO] Using " + THREAD_COUNT + " threads for maximum speed" + RESET);
        
        // Start all threads
        Thread multicastThread = new Thread(LanProtectorV3_2::monitorMulticast);
        multicastThread.setName("Multicast-Monitor");
        
        Thread scanManagerThread = new Thread(LanProtectorV3_2::startMegaScan);
        scanManagerThread.setName("Mega-Scan-Manager");
        
        Thread statsThread = new Thread(LanProtectorV3_2::showEnhancedStatistics);
        statsThread.setName("Stats-Display");
        
        Thread commandThread = new Thread(LanProtectorV3_2::commandListener);
        commandThread.setName("Command-Listener");
        
        // Start threads
        multicastThread.start();
        scanManagerThread.start();
        statsThread.start();
        commandThread.start();
        
        // Wait for main thread
        try {
            multicastThread.join();
        } catch (InterruptedException e) {
            System.out.println(RED + "[ERROR] Main thread interrupted!" + RESET);
        }
    }
    
    private static void printBanner() {
        System.out.println(BLUE + BOLD + "╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║" + PURPLE + BOLD + "                  LAN PROTECTOR v3.2 - MEGA SCANNER                " + BLUE + "║");
        System.out.println("║" + CYAN + "      ██╗      █████╗ ███╗   ██╗    ██████╗ ██████╗  ██████╗ ████████╗  " + BLUE + "║");
        System.out.println("║" + CYAN + "      ██║     ██╔══██╗████╗  ██║    ██╔══██╗██╔══██╗██╔═══██╗╚══██╔══╝  " + BLUE + "║");
        System.out.println("║" + CYAN + "      ██║     ███████║██╔██╗ ██║    ██████╔╝██████╔╝██║   ██║   ██║     " + BLUE + "║");
        System.out.println("║" + CYAN + "      ██║     ██╔══██║██║╚██╗██║    ██╔═══╝ ██╔══██╗██║   ██║   ██║     " + BLUE + "║");
        System.out.println("║" + CYAN + "      ███████╗██║  ██║██║ ╚████║    ██║     ██║  ██║╚██████╔╝   ██║     " + BLUE + "║");
        System.out.println("║" + CYAN + "      ╚══════╝╚═╝  ╚═╝╚═╝  ╚═══╝    ╚═╝     ╚═╝  ╚═╝ ╚═════╝    ╚═╝     " + BLUE + "║");
        System.out.println("║" + YELLOW + "        Port Range: " + MIN_PORT + " - " + MAX_PORT + " (Fast Test Mode)          " + BLUE + "║");
        System.out.println("╚══════════════════════════════════════════════════════════════════╝" + RESET);
        System.out.println();
    }
    
    private static void monitorMulticast() {
        try {
            MulticastSocket socket = new MulticastSocket(PORT);
            InetAddress group = InetAddress.getByName(MULTICAST_ADDR);
            socket.joinGroup(group);
            socket.setSoTimeout(3000);
            
            System.out.println(GREEN + BOLD + "[✓ MULTICAST] Monitoring: " + MULTICAST_ADDR + ":" + PORT + RESET);
            System.out.println(GREEN + "[✓ MULTICAST] Ready to DESTROY fake Minecraft worlds!" + RESET);
            
            byte[] buffer = new byte[BUFFER_SIZE];
            
            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    
                    String message = new String(packet.getData(), 0, 
                        packet.getLength(), StandardCharsets.UTF_8);
                    InetAddress sender = packet.getAddress();
                    int senderPort = packet.getPort();
                    
                    if (isSuspiciousMessage(message)) {
                        blockedPackets.incrementAndGet();
                        displayBlockedPacketWithStyle(sender, senderPort, message);
                        logBlockedPacket(sender, senderPort, message, "multicast");
                    }
                } catch (SocketTimeoutException e) {
                    // Normal timeout
                } catch (Exception e) {
                    if (running) {
                        System.out.println(RED + "[MULTICAST ERROR] " + e.getMessage() + RESET);
                    }
                }
            }
            
            socket.leaveGroup(group);
            socket.close();
            System.out.println(YELLOW + "[MULTICAST] Stopped" + RESET);
            
        } catch (Exception e) {
            System.out.println(RED + BOLD + "[MULTICAST FATAL ERROR] " + e.getMessage() + RESET);
        }
    }
    
    private static void startMegaScan() {
        System.out.println(PURPLE + BOLD + "[MEGA SCAN] Starting port scan: " + MIN_PORT + " to " + MAX_PORT + RESET);
        System.out.println(YELLOW + "[MEGA SCAN] Fast test mode..." + RESET);
        
        Thread[] scanners = new Thread[THREAD_COUNT];
        int portsPerThread = MAX_PORT / THREAD_COUNT;
        
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            final int startPort = i * portsPerThread;
            final int endPort = (i == THREAD_COUNT - 1) ? MAX_PORT : (i + 1) * portsPerThread - 1;
            
            scanners[i] = new Thread(() -> megaScanRange(threadId, startPort, endPort));
            scanners[i].setName("MegaScanner-" + i);
            scanners[i].start();
            
            // Small delay to not overwhelm system
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {}
        }
        
        // Wait for all scanners
        for (Thread scanner : scanners) {
            try {
                scanner.join();
            } catch (InterruptedException e) {
                System.out.println(RED + "[SCAN] Scanning interrupted!" + RESET);
            }
        }
        
        System.out.println(GREEN + BOLD + "\n[✓ MEGA SCAN] COMPLETED!" + RESET);
        System.out.println(CYAN + "   Total ports scanned: " + String.format("%,d", scannedPorts.get()) + RESET);
        System.out.println(CYAN + "   Suspicious ports found: " + String.format("%,d", suspiciousPorts.size()) + RESET);
    }
    
    private static void megaScanRange(int threadId, int startPort, int endPort) {
        System.out.println(String.format(BLUE + "[SCANNER-%02d] Scanning ports %,d - %,d" + RESET, 
            threadId, startPort, endPort));
        
        for (int port = startPort; port <= endPort && running; port++) {
            scannedPorts.incrementAndGet();
            
            // Progress indicator
            if (scannedPorts.get() % 10000 == 0) {
                double percent = (scannedPorts.get() * 100.0) / MAX_PORT;
                System.out.println(String.format(YELLOW + 
                    "[PROGRESS] %,d/%,d (%.2f%%) - Thread %d active" + RESET,
                    scannedPorts.get(), MAX_PORT, percent, threadId));
            }
            
            if (isSuspiciousPortMega(port)) {
                synchronized(suspiciousPorts) {
                    suspiciousPorts.add(port);
                }
                
                if (isPortOpenEnhanced(port)) {
                    displaySuspiciousPortWithStyle(port);
                    logSuspiciousPort(port, "OPEN");
                } else {
                    logSuspiciousPort(port, "closed");
                }
            }
        }
        
        System.out.println(String.format(GREEN + "[SCANNER-%02d] Finished!" + RESET, threadId));
    }
    
    private static boolean isSuspiciousPortMega(int port) {
        String portStr = String.valueOf(port);
        
        // 🔥 ULTRA SUSPICIOUS PATTERNS 🔥
        
        // 1. Extreme high ports (fake servers love these)
        if (port >= 10000 && port <= 99999) {
            return true;
        }
        
        // 2. Common spam ports
        if (port == 4444 || port == 4445 || port == 4446 || 
            port == 25565 || port == 25566 || port == 25567) {
            return true;
        }
        
        // 3. Minecraft-like ports
        if (port >= 25500 && port <= 25600) {
            return true;
        }
        
        // 4. Repeating digits (44444, 55555, etc.)
        if (portStr.length() >= 4) {
            char first = portStr.charAt(0);
            boolean allSame = true;
            for (int i = 1; i < portStr.length(); i++) {
                if (portStr.charAt(i) != first) {
                    allSame = false;
                    break;
                }
            }
            if (allSame && portStr.length() >= 4) return true;
        }
        
        // 5. Sequential patterns (12345, 23456, etc.)
        if (isSequential(portStr)) {
            return true;
        }
        
        // 6. Palindromes (12321, 45654, etc.)
        if (isPalindrome(portStr)) {
            return true;
        }
        
        // 7. Contains "fake" patterns
        if (portStr.contains("666") || portStr.contains("777") || 
            portStr.contains("888") || portStr.contains("999")) {
            return true;
        }
        
        // 8. Very round numbers (10000, 50000, 100000, etc.)
        if (port % 1000 == 0 && port > 0) {
            return true;
        }
        
        return false;
    }
    
    private static boolean isSequential(String str) {
        if (str.length() < 3) return false;
        
        boolean ascending = true;
        boolean descending = true;
        
        for (int i = 1; i < str.length(); i++) {
            if (str.charAt(i) != str.charAt(i-1) + 1) {
                ascending = false;
            }
            if (str.charAt(i) != str.charAt(i-1) - 1) {
                descending = false;
            }
        }
        
        return ascending || descending;
    }
    
    private static boolean isPalindrome(String str) {
        if (str.length() < 3) return false;
        
        for (int i = 0; i < str.length() / 2; i++) {
            if (str.charAt(i) != str.charAt(str.length() - 1 - i)) {
                return false;
            }
        }
        return true;
    }
    
    private static boolean isPortOpenEnhanced(int port) {
        if (port < 1 || port > 65535) return false;
        
        Socket socket = null;
        try {
            socket = new Socket();
            socket.setReuseAddress(true);
            socket.setSoTimeout(100);
            socket.setTcpNoDelay(true);
            
            // Try localhost
            socket.connect(new InetSocketAddress("127.0.0.1", port), 100);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (Exception e) {}
            }
        }
    }
    
    private static boolean isSuspiciousMessage(String message) {
        if (message == null || message.length() < 10) {
            return false;
        }
        
        // Check for Minecraft LAN advertisement
        if (message.contains("[MOTD]") && message.contains("[/MOTD]") && 
            message.contains("[AD]") && message.contains("[/AD]")) {
            
            // Extract and check MOTD
            try {
                String motd = message.substring(
                    message.indexOf("[MOTD]") + 6,
                    message.indexOf("[/MOTD]")
                ).toLowerCase();
                
                String[] badPatterns = {
                    "cry", "crying", "fake", "spam", "hack", "cheat",
                    "virus", "trojan", "malware", "lag", "crash",
                    "null", "error", "bot", "attack", "exploit",
                    "поплачьтеее", "атака", "взлом", "читы"
                };
                
                for (String pattern : badPatterns) {
                    if (motd.contains(pattern)) {
                        return true;
                    }
                }
            } catch (Exception e) {}
            
            // Extract and check port
            try {
                String portStr = message.substring(
                    message.indexOf("[AD]") + 4,
                    message.indexOf("[/AD]")
                ).trim();
                
                int port = Integer.parseInt(portStr);
                return isSuspiciousPortMega(port);
            } catch (Exception e) {
                return true; // Invalid port format = suspicious
            }
        }
        
        // Direct spam patterns
        if (message.toLowerCase().contains("fake server") ||
            message.toLowerCase().contains("spam world") ||
            message.toLowerCase().contains("cry more") ||
            message.contains("поплачьтеее")) {
            return true;
        }
        
        return false;
    }
    
    private static void displayBlockedPacketWithStyle(InetAddress sender, int port, String message) {
        System.out.println(RED + BOLD + "\n" + repeat("█", 60) + RESET);
        System.out.println(RED + BOLD + "🔥 " + REVERSE + " FAKE WORLD BLOCKED! " + RESET + RED + BOLD + " 🔥");
        System.out.println(RED + "├─ " + UNDERLINE + "BLOCKED PACKET #" + blockedPackets.get() + RESET);
        System.out.println(RED + "├─ From: " + CYAN + BOLD + sender.getHostAddress() + ":" + port + RESET);
        
        try {
            if (message.contains("[MOTD]")) {
                String motd = message.substring(
                    message.indexOf("[MOTD]") + 6,
                    message.indexOf("[/MOTD]")
                );
                System.out.println(RED + "├─ MOTD: " + YELLOW + "\"" + motd + "\"" + RESET);
                
                if (message.contains("[AD]")) {
                    String ad = message.substring(
                        message.indexOf("[AD]") + 4,
                        message.indexOf("[/AD]")
                    );
                    System.out.println(RED + "├─ Port: " + PURPLE + BOLD + ad + RESET);
                }
            }
        } catch (Exception e) {}
        
        System.out.println(RED + "└─ Preview: " + message.substring(0, Math.min(50, message.length())) + "..." + RESET);
        System.out.println(RED + BOLD + repeat("█", 60) + RESET);
    }
    
    private static void displaySuspiciousPortWithStyle(int port) {
        System.out.println(YELLOW + BOLD + "\n" + repeat("⚠", 50) + RESET);
        System.out.println(YELLOW + BOLD + "🚨 SUSPICIOUS PORT DETECTED! 🚨");
        System.out.println(YELLOW + "├─ Port: " + RED + BOLD + String.format("%,d", port) + RESET);
        System.out.println(YELLOW + "├─ Status: " + GREEN + BOLD + "OPEN" + RESET);
        System.out.println(YELLOW + "├─ Type: " + getPortType(port) + RESET);
        System.out.println(YELLOW + "└─ Threat Level: " + getThreatLevel(port) + RESET);
        System.out.println(YELLOW + BOLD + repeat("⚠", 50) + RESET);
    }
    
    private static String getPortType(int port) {
        String portStr = String.valueOf(port);
        
        if (port >= 10000) return PURPLE + "HIGH PORT" + RESET;
        if (portStr.matches("(\\d)\\1{2,}")) return RED + "REPEATING DIGITS" + RESET;
        if (isSequential(portStr)) return CYAN + "SEQUENTIAL PATTERN" + RESET;
        if (isPalindrome(portStr)) return BLUE + "PALINDROME" + RESET;
        if (port % 1000 == 0) return GREEN + "ROUND NUMBER" + RESET;
        
        return YELLOW + "SUSPICIOUS PATTERN" + RESET;
    }
    
    private static String getThreatLevel(int port) {
        String portStr = String.valueOf(port);
        
        if (port == 4444 || port == 4445 || port == 4446) return RED + BOLD + "HIGH" + RESET;
        if (portStr.length() >= 5 && portStr.matches("(\\d)\\1{3,}")) return RED + BOLD + "HIGH" + RESET;
        if (port >= 25500 && port <= 25600) return YELLOW + BOLD + "MEDIUM" + RESET;
        
        return GREEN + "LOW" + RESET;
    }
    
    private static void showEnhancedStatistics() {
        int displayCount = 0;
        
        while (running) {
            try {
                Thread.sleep(10000); // Update every 10 seconds
                
                if (displayCount % 3 == 0) {
                    printStats();
                } else {
                    System.out.println(String.format(CYAN + 
                        "[STATUS] Blocked: %,d | Scanned: %,d/%,d | Suspicious: %,d" + RESET,
                        blockedPackets.get(), scannedPorts.get(), MAX_PORT, suspiciousPorts.size()));
                }
                
                displayCount++;
                
            } catch (InterruptedException e) {
                break;
            }
        }
    }
    
    private static void printStats() {
        System.out.println(BLUE + BOLD + "\n" + repeat("═", 60) + RESET);
        System.out.println(PURPLE + BOLD + "         📊 REAL-TIME STATISTICS 📊" + RESET);
        System.out.println(BLUE + BOLD + repeat("═", 60) + RESET);
        
        System.out.println(String.format(CYAN + "   Packets Blocked: " + GREEN + BOLD + "%,d" + RESET, 
            blockedPackets.get()));
        
        System.out.println(String.format(CYAN + "   Ports Scanned: " + YELLOW + "%,d / %,d" + RESET,
            scannedPorts.get(), MAX_PORT));
        
        double progress = (scannedPorts.get() * 100.0) / MAX_PORT;
        System.out.println(String.format(CYAN + "   Scan Progress: " + getProgressBar(progress) + 
            " %.2f%%" + RESET, progress));
        
        System.out.println(String.format(CYAN + "   Suspicious Ports: " + RED + BOLD + "%,d" + RESET,
            suspiciousPorts.size()));
        
        // Count open suspicious ports
        int openCount = 0;
        for (Integer port : suspiciousPorts) {
            if (isPortOpenEnhanced(port)) {
                openCount++;
            }
        }
        
        System.out.println(String.format(CYAN + "   Open Suspicious Ports: " + 
            (openCount > 0 ? RED + BOLD : GREEN) + "%,d" + RESET, openCount));
        
        if (openCount > 0) {
            System.out.println(YELLOW + BOLD + "\n   🔥 TOP THREATS (OPEN PORTS):" + RESET);
            int count = 0;
            for (Integer port : suspiciousPorts) {
                if (isPortOpenEnhanced(port)) {
                    System.out.println(String.format("      • Port " + RED + BOLD + "%,d" + 
                        RESET + " - " + getThreatLevel(port), port));
                    if (++count >= 5) break;
                }
            }
        }
        
        System.out.println(BLUE + BOLD + repeat("═", 60) + RESET);
    }
    
    private static String getProgressBar(double percent) {
        int bars = (int) (percent / 2);
        StringBuilder bar = new StringBuilder("[");
        
        for (int i = 0; i < 50; i++) {
            if (i < bars) {
                if (percent < 33) bar.append(RED + "█" + RESET);
                else if (percent < 66) bar.append(YELLOW + "█" + RESET);
                else bar.append(GREEN + "█" + RESET);
            } else {
                bar.append("░");
            }
        }
        bar.append("]");
        return bar.toString();
    }
    
    private static void commandListener() {
        System.out.println(GREEN + "\n[COMMANDS] Type 'help', 'stats', 'ports', or 'stop'" + RESET);
        
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            
            while (running) {
                try {
                    if (reader.ready()) {
                        String command = reader.readLine().trim().toLowerCase();
                        
                        switch (command) {
                            case "help":
                                System.out.println(CYAN + "\n[COMMANDS] help, stats, ports, stop, clear" + RESET);
                                break;
                            case "stop":
                                System.out.println(RED + BOLD + "\n[COMMAND] Shutting down..." + RESET);
                                running = false;
                                break;
                            case "stats":
                                printStats();
                                break;
                            case "ports":
                                System.out.println(YELLOW + "\n[SUSPICIOUS PORTS] (first 20):" + RESET);
                                synchronized(suspiciousPorts) {
                                    if (suspiciousPorts.isEmpty()) {
                                        System.out.println(GREEN + "   No suspicious ports found yet" + RESET);
                                    } else {
                                        int count = 0;
                                        for (Integer port : suspiciousPorts) {
                                            System.out.println(String.format("   Port " + 
                                                (isPortOpenEnhanced(port) ? RED + BOLD : CYAN) + 
                                                "%,d" + RESET + " - " + 
                                                (isPortOpenEnhanced(port) ? "OPEN" : "closed"), port));
                                            if (++count >= 20) break;
                                        }
                                    }
                                }
                                break;
                            case "clear":
                                System.out.print("\033[H\033[2J");
                                System.out.flush();
                                printBanner();
                                break;
                            default:
                                System.out.println(YELLOW + "[COMMAND] Unknown. Type 'help'" + RESET);
                        }
                    }
                } catch (Exception e) {}
                
                Thread.sleep(100);
            }
            
            reader.close();
        } catch (Exception e) {
            // No input available
        }
    }
    
    private static void logBlockedPacket(InetAddress address, int port, String message, String type) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String logEntry = String.format("[%s] [%s] BLOCKED %s:%d - %s",
            timestamp, type, address, port,
            message.length() > 100 ? message.substring(0, 100) + "..." : message);
        
        try {
            FileWriter fw = new FileWriter("lan_protector_v3.log", true);
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(logEntry);
            bw.newLine();
            bw.close();
            fw.close();
        } catch (Exception e) {}
    }
    
    private static void logSuspiciousPort(int port, String status) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String logEntry = String.format("[%s] PORT %d - %s", timestamp, port, status);
        
        try {
            FileWriter fw = new FileWriter("suspicious_ports_v3.log", true);
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(logEntry);
            bw.newLine();
            bw.close();
            fw.close();
        } catch (Exception e) {}
    }
    
    private static String repeat(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }
}
// УДАЛИТЬ ВСЕ СТРОКИ ПОСЛЕ ЭТОЙ КОММЕНТАРИИ!
