import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class ChatServerGUI extends JFrame {
    private static final String QUIT_COMMAND = "/quit";

    private final JTextArea logArea = new JTextArea();
    private final JTextField portField = new JTextField("5000", 8);
    private final JButton startButton = new JButton("启动服务器");
    private final JButton stopButton = new JButton("停止服务器");

    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();
    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread acceptThread;

    public ChatServerGUI() {
        setTitle("多人聊天室服务器");
        setSize(700, 500);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        buildUI();
        bindEvents();
    }

    private void buildUI() {
        logArea.setEditable(false);

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("端口:"));
        topPanel.add(portField);
        topPanel.add(startButton);
        topPanel.add(stopButton);

        stopButton.setEnabled(false);

        add(topPanel, BorderLayout.NORTH);
        add(new JScrollPane(logArea), BorderLayout.CENTER);
    }

    private void bindEvents() {
        startButton.addActionListener(e -> startServer());
        stopButton.addActionListener(e -> stopServer());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopServer();
            }
        });
    }

    private void startServer() {
        if (running) {
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "端口号必须是整数。");
            return;
        }

        try {
            serverSocket = new ServerSocket(port);
            running = true;

            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            portField.setEditable(false);

            appendLog("服务器已启动，监听端口: " + port);

            acceptThread = new Thread(() -> acceptClients(serverSocket), "chat-server-accept");
            acceptThread.start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "启动失败: " + e.getMessage());
        }
    }

    private void acceptClients(ServerSocket socket) {
        while (running) {
            try {
                Socket clientSocket = socket.accept();
                ClientHandler handler = new ClientHandler(clientSocket);
                clients.add(handler);
                handler.start();
                appendLog("新客户端连接: " + clientSocket.getRemoteSocketAddress());
            } catch (IOException e) {
                if (running) {
                    appendLog("接受客户端连接失败: " + e.getMessage());
                }
                break;
            }
        }
    }

    private void stopServer() {
        running = false;

        if (acceptThread != null) {
            acceptThread.interrupt();
        }

        for (ClientHandler client : clients.toArray(new ClientHandler[0])) {
            client.closeConnection();
        }
        clients.clear();

        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                appendLog("关闭服务器时发生异常: " + e.getMessage());
            }
        }

        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        portField.setEditable(true);
        appendLog("服务器已停止。");
    }

    private void broadcast(String message, ClientHandler excludeClient) {
        appendLog(message);
        for (ClientHandler client : clients) {
            if (client != excludeClient) {
                client.send(message);
            }
        }
    }

    private void appendLog(String message) {
        SwingUtilities.invokeLater(() -> logArea.append(message + System.lineSeparator()));
    }

    private class ClientHandler extends Thread {
        private final Socket socket;
        private BufferedReader reader;
        private BufferedWriter writer;
        private String clientName = "匿名用户";

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
                );
                writer = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)
                );

                String requestedName = reader.readLine();
                if (requestedName != null && !requestedName.trim().isEmpty()) {
                    clientName = requestedName.trim();
                }

                send("系统消息: 已成功连接到聊天室。");
                broadcast(clientName + " 进入了聊天室。", this);

                String message;
                while ((message = reader.readLine()) != null) {
                    if (message.trim().isEmpty()) {
                        continue;
                    }

                    if (QUIT_COMMAND.equals(message.trim())) {
                        appendLog(clientName + " 主动断开连接。");
                        break;
                    }

                    broadcast(clientName + ": " + message, this);
                    appendLog("收到消息 - " + clientName + ": " + message);
                }
            } catch (IOException e) {
                appendLog(clientName + " 连接异常断开: " + e.getMessage());
            } finally {
                clients.remove(this);
                broadcast(clientName + " 离开了聊天室。", this);
                closeConnection();
            }
        }

        private void send(String message) {
            try {
                if (writer == null) {
                    return;
                }
                writer.write(message);
                writer.newLine();
                writer.flush();
            } catch (IOException e) {
                appendLog("发送消息失败: " + e.getMessage());
            }
        }

        private void closeConnection() {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                appendLog("关闭客户端连接失败: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ChatServerGUI serverGUI = new ChatServerGUI();
            serverGUI.setVisible(true);
        });
    }
}
