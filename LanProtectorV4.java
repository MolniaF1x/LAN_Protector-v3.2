import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class LanProtectorV4 {
    // ===== CONFIGURATION =====
    private static final String MULTICAST_ADDR = "224.0.2.60";
    private static final int PORT = 4445;
    private static final int BUFFER_SIZE = 1024;
    private static final int MIN_PORT = 0;
    private static final int MAX_PORT = 50000; // Быстрое сканирование
    private static final int THREAD_COUNT = 8;
    private static final Set<Integer> suspiciousPorts = new HashSet<>();
    private static final AtomicInteger blockedPackets = new AtomicInteger(0);
    private static final AtomicInteger scannedPorts = new AtomicInteger(0);
    private static volatile boolean running = true;
    
    // ===== WORDS TO BLOCK IN MOTD =====
    private static final String[] BLOCKED_WORDS = {
        // Основные слова для блокировки
        "Real", "real", "REAL",
        "Mine", "mine", "MINE",
        "Real_Mine", "real_mine", "REAL_MINE",
        "Real ", "real ", "REAL ",  // С пробелом
        "Лучший", "лучший", "ЛУЧШИЙ",
        "Сервер", "сервер", "СЕРВЕР",
        "Server", "server", "SERVER",
        
        // Спам слова
        "поплачьтеее", "поплачь", "плачь", "реви",
        "cry", "crying", "fake", "spam",
        "атака", "взлом", "читы", "чит",
        "virus", "hack", "crash", "lag",
        
        // Специальные паттерны
        "||||", "____", "!!!!", "????",
        "....", "----", "@@@@", "####"
    };
    
    // ===== ALLOWED WORDS (не блокировать) =====
    private static final String[] ALLOWED_WORDS = {
        "Survival", "Creative", "Hardcore",
        "Adventure", "Skyblock", "BedWars",
        "SkyWars", "PvP", "PvE", "Vanilla",
        "Modded", "Economy", "RolePlay"
    };
    
    // ===== ЯРКИЕ ЦВЕТА =====
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[91m";     // Ярко-красный
    private static final String GREEN = "\u001B[92m";   // Ярко-зеленый
    private static final String YELLOW = "\u001B[93m";  // Ярко-желтый
    private static final String BLUE = "\u001B[94m";    // Ярко-синий
    private static final String PURPLE = "\u001B[95m";  // Ярко-пурпурный
    private static final String CYAN = "\u001B[96m";    // Ярко-голубой
    private static final String WHITE = "\u001B[97m";   // Ярко-белый
    
    // ===== СТИЛИ =====
    private static final String BOLD = "\u001B[1m";
    private static final String UNDERLINE = "\u001B[4m";
    private static final String BLINK = "\u001B[5m";
    private static final String REVERSE = "\u001B[7m";
    
    public static void main(String[] args) {
        printAwesomeBanner();
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running = false;
            System.out.println(RED + BOLD + "\n🔥 " + BLINK + "SHUTTING DOWN..." + RESET);
            printStats();
        }));
        
        System.out.println(CYAN + BOLD + "🚀 " + BLINK + "LAN PROTECTOR v4.0 STARTING..." + RESET);
        System.out.println(YELLOW + "🎯 Blocking: Real, Mine, Real_Mine, Лучший, Сервер" + RESET);
        System.out.println(PURPLE + "✨ Colorful interface | Smart detection | Fast scan" + RESET);
        System.out.println();
        
        Thread multicastThread = new Thread(LanProtectorV4::monitorMulticastV4);
        Thread scanThread = new Thread(LanProtectorV4::startColorfulScan);
        Thread statsThread = new Thread(LanProtectorV4::showColorfulStatistics);
        Thread commandThread = new Thread(LanProtectorV4::commandListener);
        
        multicastThread.start();
        scanThread.start();
        statsThread.start();
        commandThread.start();
        
        try {
            multicastThread.join();
        } catch (InterruptedException e) {
            System.out.println(RED + BOLD + "💥 INTERRUPTED!" + RESET);
        }
    }
    
    private static void printAwesomeBanner() {
        System.out.println();
        System.out.println(PURPLE + BOLD + "███████╗██╗      █████╗ ███╗   ██╗    ██████╗ ██████╗  ██████╗ ████████╗" + RESET);
        System.out.println(CYAN + BOLD + "██╔════╝██║     ██╔══██╗████╗  ██║    ██╔══██╗██╔══██╗██╔═══██╗╚══██╔══╝" + RESET);
        System.out.println(GREEN + BOLD + "███████╗██║     ███████║██╔██╗ ██║    ██████╔╝██████╔╝██║   ██║   ██║   " + RESET);
        System.out.println(YELLOW + BOLD + "╚════██║██║     ██╔══██║██║╚██╗██║    ██╔═══╝ ██╔══██╗██║   ██║   ██║   " + RESET);
        System.out.println(RED + BOLD + "███████║███████╗██║  ██║██║ ╚████║    ██║     ██║  ██║╚██████╔╝   ██║   " + RESET);
        System.out.println(BLUE + BOLD + "╚══════╝╚══════╝╚═╝  ╚═╝╚═╝  ╚═══╝    ╚═╝     ╚═╝  ╚═╝ ╚═════╝    ╚═╝   " + RESET);
        System.out.println();
        System.out.println(WHITE + BOLD + "                    ═══════════════════════════" + RESET);
        System.out.println(PURPLE + BOLD + BLINK + "                     VERSION 4.0 - REAL BLOCKER" + RESET);
        System.out.println(WHITE + BOLD + "                    ═══════════════════════════" + RESET);
        System.out.println();
    }
    
    private static void monitorMulticastV4() {
        try {
            MulticastSocket socket = new MulticastSocket(PORT);
            InetAddress group = InetAddress.getByName(MULTICAST_ADDR);
            socket.joinGroup(group);
            socket.setSoTimeout(2000);
            
            System.out.println(GREEN + BOLD + "✅ " + BLINK + "MULTICAST ACTIVE: " + CYAN + MULTICAST_ADDR + ":" + PORT + RESET);
            System.out.println(YELLOW + "🎯 Blocking words: " + RED + "Real, Mine, Real_Mine, Лучший, Сервер" + RESET);
            System.out.println();
            
            byte[] buffer = new byte[BUFFER_SIZE];
            
            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    
                    String message = new String(packet.getData(), 0, 
                        packet.getLength(), StandardCharsets.UTF_8);
                    InetAddress sender = packet.getAddress();
                    int senderPort = packet.getPort();
                    
                    if (message.contains("[MOTD]") && message.contains("[AD]")) {
                        String motd = extractMOTD(message);
                        String blockedWord = containsBlockedWord(motd);
                        
                        if (blockedWord != null) {
                            blockedPackets.incrementAndGet();
                            displayColorfulBlock(sender, senderPort, motd, blockedWord);
                            logBlockedPacket(sender, senderPort, message, blockedWord);
                        } else {
                            displayNormalServer(sender, senderPort, motd);
                        }
                    }
                } catch (SocketTimeoutException e) {
                    // Таймаут - нормально
                } catch (Exception e) {
                    if (running) {
                        System.out.println(RED + "⚠️  Error: " + e.getMessage() + RESET);
                    }
                }
            }
            
            socket.leaveGroup(group);
            socket.close();
            System.out.println(YELLOW + "🛑 Multicast stopped" + RESET);
            
        } catch (Exception e) {
            System.out.println(RED + BOLD + "💥 FATAL ERROR: " + e.getMessage() + RESET);
        }
    }
    
    private static String extractMOTD(String message) {
        try {
            int start = message.indexOf("[MOTD]") + 6;
            int end = message.indexOf("[/MOTD]");
            if (start < end) {
                return message.substring(start, end).trim();
            }
        } catch (Exception e) {}
        return "";
    }
    
    private static String containsBlockedWord(String motd) {
        String lowerMotd = motd.toLowerCase();
        
        for (String word : BLOCKED_WORDS) {
            if (lowerMotd.contains(word.toLowerCase())) {
                return word;
            }
        }
        return null;
    }
    
    private static void displayColorfulBlock(InetAddress sender, int port, String motd, String blockedWord) {
        System.out.println();
        System.out.println(RED + BOLD + BLINK + "🔥🔥🔥 ФЕЙК ОБНАРУЖЕН! 🔥🔥🔥" + RESET);
        System.out.println(RED + BOLD + "┌──────────────────────────────────────────┐" + RESET);
        System.out.println(RED + "│ " + YELLOW + BOLD + "Заблокированное слово: " + RED + BOLD + blockedWord + RESET);
        System.out.println(RED + "│ " + CYAN + "IP отправителя: " + WHITE + sender.getHostAddress() + ":" + port + RESET);
        System.out.println(RED + "│ " + PURPLE + "MOTD сообщение: " + YELLOW + motd + RESET);
        System.out.println(RED + "│ " + GREEN + "Всего заблокировано: " + RED + BOLD + blockedPackets.get() + RESET);
        System.out.println(RED + BOLD + "└──────────────────────────────────────────┘" + RESET);
        System.out.println();
    }
    
    private static void displayNormalServer(InetAddress sender, int port, String motd) {
        System.out.println(GREEN + "✅ Нормальный сервер: " + CYAN + motd + 
                          " (" + sender.getHostAddress() + ":" + port + ")" + RESET);
    }
    
    private static void startColorfulScan() {
        System.out.println(BLUE + BOLD + "🔍 Запуск цветного сканирования портов..." + RESET);
        System.out.println(PURPLE + "📊 Диапазон: " + MIN_PORT + " - " + MAX_PORT + RESET);
        
        Thread[] scanners = new Thread[THREAD_COUNT];
        int portsPerThread = MAX_PORT / THREAD_COUNT;
        
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            final int startPort = i * portsPerThread;
            final int endPort = (i == THREAD_COUNT - 1) ? MAX_PORT : (i + 1) * portsPerThread - 1;
            
            scanners[i] = new Thread(() -> scanWithColors(threadId, startPort, endPort));
            scanners[i].setName("ColorScanner-" + i);
            scanners[i].start();
            
            try { Thread.sleep(100); } catch (InterruptedException e) {}
        }
        
        for (Thread scanner : scanners) {
            try { scanner.join(); } catch (InterruptedException e) {}
        }
        
        System.out.println(GREEN + BOLD + "✅ Сканирование завершено!" + RESET);
        System.out.println(CYAN + "📈 Проверено портов: " + scannedPorts.get() + RESET);
        System.out.println(YELLOW + "⚠️  Найдено подозрительных: " + suspiciousPorts.size() + RESET);
    }
    
    private static void scanWithColors(int threadId, int startPort, int endPort) {
        String[] colors = {CYAN, PURPLE, YELLOW, GREEN, BLUE};
        String color = colors[threadId % colors.length];
        
        System.out.println(color + "📡 Сканер-" + threadId + ": порты " + startPort + "-" + endPort + RESET);
        
        for (int port = startPort; port <= endPort && running; port++) {
            scannedPorts.incrementAndGet();
            
            if (scannedPorts.get() % 5000 == 0) {
                double percent = (scannedPorts.get() * 100.0) / MAX_PORT;
                System.out.println(YELLOW + "📊 Прогресс: " + String.format("%.1f", percent) + 
                                  "% (" + scannedPorts.get() + "/" + MAX_PORT + ")" + RESET);
            }
            
            if (isSuspiciousPortV4(port)) {
                suspiciousPorts.add(port);
                
                if (isPortOpen(port)) {
                    System.out.println(RED + BOLD + "🚨 Открыт подозрительный порт: " + port + RESET);
                    logSuspiciousPort(port, "OPEN");
                }
            }
        }
        
        System.out.println(color + "✅ Сканер-" + threadId + " завершил работу" + RESET);
    }
    
    private static boolean isSuspiciousPortV4(int port) {
        // Порты с подозрительными паттернами
        String portStr = String.valueOf(port);
        
        // Блокируем порты с "опасными" номерами
        if (port == 4444 || port == 4445 || port == 4446) return true;
        if (port >= 10000 && port <= 20000 && port % 1111 == 0) return true;
        
        // Повторяющиеся цифры
        if (portStr.matches(".*(\\d)\\1{3,}.*")) return true;
        
        // Последовательности
        if (isSequential(portStr)) return true;
        
        return false;
    }
    
    private static boolean isSequential(String str) {
        if (str.length() < 3) return false;
        
        boolean ascending = true;
        boolean descending = true;
        
        for (int i = 1; i < str.length(); i++) {
            if (str.charAt(i) != str.charAt(i-1) + 1) ascending = false;
            if (str.charAt(i) != str.charAt(i-1) - 1) descending = false;
        }
        
        return ascending || descending;
    }
    
    private static boolean isPortOpen(int port) {
        if (port < 1 || port > 65535) return false;
        
        Socket socket = null;
        try {
            socket = new Socket();
            socket.setReuseAddress(true);
            socket.setSoTimeout(100);
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
    
    private static void showColorfulStatistics() {
        int updateCount = 0;
        
        while (running) {
            try {
                Thread.sleep(15000); // Каждые 15 секунд
                
                if (updateCount % 2 == 0) {
                    printStats();
                } else {
                    System.out.println(CYAN + "📈 Статистика: " + 
                                      RED + "Заблокировано: " + blockedPackets.get() + " | " +
                                      YELLOW + "Проверено портов: " + scannedPorts.get() + RESET);
                }
                
                updateCount++;
                
            } catch (InterruptedException e) {
                break;
            }
        }
    }
    
    private static void printStats() {
        System.out.println();
        System.out.println(PURPLE + BOLD + "════════════════════════════════════════════" + RESET);
        System.out.println(CYAN + BOLD + "           📊 РЕАЛЬНАЯ СТАТИСТИКА v4.0" + RESET);
        System.out.println(PURPLE + BOLD + "════════════════════════════════════════════" + RESET);
        
        System.out.println(YELLOW + "   🛡️  Заблокировано фейков: " + RED + BOLD + blockedPackets.get() + RESET);
        System.out.println(BLUE + "   🔍 Проверено портов: " + CYAN + scannedPorts.get() + "/" + MAX_PORT + RESET);
        
        double progress = (scannedPorts.get() * 100.0) / MAX_PORT;
        String progressBar = getColorfulProgressBar(progress);
        System.out.println(GREEN + "   📊 Прогресс сканирования: " + progressBar + 
                          String.format(" %.1f%%", progress) + RESET);
        
        System.out.println(PURPLE + "   ⚠️  Подозрительных портов: " + 
                          (suspiciousPorts.size() > 0 ? RED + BOLD : GREEN) + 
                          suspiciousPorts.size() + RESET);
        
        // Показываем топ угроз
        if (suspiciousPorts.size() > 0) {
            System.out.println(YELLOW + BOLD + "\n   🔥 ТОП УГРОЗ:" + RESET);
            int count = 0;
            for (Integer port : suspiciousPorts) {
                if (isPortOpen(port)) {
                    System.out.println("      • Порт " + RED + BOLD + port + RESET + " - ОТКРЫТ");
                    if (++count >= 3) break;
                }
            }
        }
        
        System.out.println(PURPLE + BOLD + "════════════════════════════════════════════" + RESET);
        System.out.println();
    }
    
    private static String getColorfulProgressBar(double percent) {
        int bars = (int) (percent / 2);
        StringBuilder bar = new StringBuilder("[");
        
        for (int i = 0; i < 50; i++) {
            if (i < bars) {
                if (percent < 25) bar.append(RED + "█" + RESET);
                else if (percent < 50) bar.append(YELLOW + "█" + RESET);
                else if (percent < 75) bar.append(GREEN + "█" + RESET);
                else bar.append(CYAN + "█" + RESET);
            } else {
                bar.append("░");
            }
        }
        bar.append("]");
        return bar.toString();
    }
    
    private static void commandListener() {
        System.out.println(YELLOW + "\n💬 Доступные команды: " + 
                          CYAN + "'help', 'stats', 'ports', 'stop', 'clear'" + RESET);
        
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            
            while (running) {
                try {
                    if (reader.ready()) {
                        String command = reader.readLine().trim().toLowerCase();
                        
                        switch (command) {
                            case "help":
                                showColorfulHelp();
                                break;
                            case "stop":
                                System.out.println(RED + BOLD + "\n🛑 Остановка программы..." + RESET);
                                running = false;
                                break;
                            case "stats":
                                printStats();
                                break;
                            case "ports":
                                showPortsList();
                                break;
                            case "clear":
                                System.out.print("\033[H\033[2J");
                                System.out.flush();
                                printAwesomeBanner();
                                break;
                            default:
                                System.out.println(YELLOW + "❓ Неизвестная команда. Напишите 'help'" + RESET);
                        }
                    }
                } catch (Exception e) {}
                
                Thread.sleep(100);
            }
            
            reader.close();
        } catch (Exception e) {
            // Нет ввода
        }
    }
    
    private static void showColorfulHelp() {
        System.out.println();
        System.out.println(CYAN + BOLD + "════════════════ КОМАНДЫ ════════════════" + RESET);
        System.out.println(GREEN + "   help   " + WHITE + " - Показать эту справку" + RESET);
        System.out.println(YELLOW + "   stats  " + WHITE + " - Показать статистику" + RESET);
        System.out.println(PURPLE + "   ports  " + WHITE + " - Список подозрительных портов" + RESET);
        System.out.println(RED + "   stop   " + WHITE + " - Остановить программу" + RESET);
        System.out.println(BLUE + "   clear  " + WHITE + " - Очистить экран" + RESET);
        System.out.println(CYAN + BOLD + "══════════════════════════════════════════" + RESET);
        System.out.println();
    }
    
    private static void showPortsList() {
        System.out.println();
        System.out.println(YELLOW + BOLD + "📋 ПОДОЗРИТЕЛЬНЫЕ ПОРТЫ:" + RESET);
        
        synchronized(suspiciousPorts) {
            if (suspiciousPorts.isEmpty()) {
                System.out.println(GREEN + "   ✅ Нет подозрительных портов!" + RESET);
            } else {
                int count = 0;
                for (Integer port : suspiciousPorts) {
                    String status = isPortOpen(port) ? 
                                   RED + BOLD + "ОТКРЫТ" + RESET : 
                                   CYAN + "закрыт" + RESET;
                    System.out.println("   • Порт " + PURPLE + port + RESET + " - " + status);
                    if (++count >= 15) {
                        System.out.println(YELLOW + "   ... и ещё " + (suspiciousPorts.size() - 15) + " портов" + RESET);
                        break;
                    }
                }
            }
        }
        System.out.println();
    }
    
    private static void logBlockedPacket(InetAddress address, int port, String message, String blockedWord) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String logEntry = String.format("[%s] BLOCKED [%s] %s:%d - %s",
            timestamp, blockedWord, address, port,
            message.length() > 80 ? message.substring(0, 80) + "..." : message);
        
        try {
            FileWriter fw = new FileWriter("lan_protector_v4.log", true);
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(logEntry);
            bw.newLine();
            bw.close();
            fw.close();
        } catch (Exception e) {}
    }
    
    private static void logSuspiciousPort(int port, String status) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String logEntry = String.format("[%s] SUSPICIOUS PORT %d - %s", timestamp, port, status);
        
        try {
            FileWriter fw = new FileWriter("suspicious_ports_v4.log", true);
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(logEntry);
            bw.newLine();
            bw.close();
            fw.close();
        } catch (Exception e) {}
    }
}