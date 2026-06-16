import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ChatClientGUI extends JFrame {
    private static final String QUIT_COMMAND = "/quit";

    private final JTextField hostField = new JTextField("127.0.0.1", 10);
    private final JTextField portField = new JTextField("5000", 6);
    private final JTextField nameField = new JTextField(10);
    private final JButton connectButton = new JButton("连接");
    private final JButton disconnectButton = new JButton("断开");

    private final JTextArea chatArea = new JTextArea();
    private final JTextField messageField = new JTextField();
    private final JButton sendButton = new JButton("发送");

    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private Thread receiveThread;
    private volatile boolean connected;

    public ChatClientGUI() {
        setTitle("多人聊天室客户端");
        setSize(700, 500);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        buildUI();
        bindEvents();
        setConnectedState(false);
    }

    private void buildUI() {
        JPanel connectionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        connectionPanel.add(new JLabel("主机:"));
        connectionPanel.add(hostField);
        connectionPanel.add(new JLabel("端口:"));
        connectionPanel.add(portField);
        connectionPanel.add(new JLabel("昵称:"));
        connectionPanel.add(nameField);
        connectionPanel.add(connectButton);
        connectionPanel.add(disconnectButton);

        chatArea.setEditable(false);
        chatArea.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new BoxLayout(inputPanel, BoxLayout.X_AXIS));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        inputPanel.add(messageField);
        inputPanel.add(sendButton);

        add(connectionPanel, BorderLayout.NORTH);
        add(new JScrollPane(chatArea), BorderLayout.CENTER);
        add(inputPanel, BorderLayout.SOUTH);
    }

    private void bindEvents() {
        connectButton.addActionListener(e -> connectToServer());
        disconnectButton.addActionListener(e -> disconnectFromServer(true));
        sendButton.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnectFromServer(true);
            }
        });
    }

    private void connectToServer() {
        if (connected) {
            return;
        }

        String host = hostField.getText().trim();
        String name = nameField.getText().trim();
        int port;

        if (host.isEmpty()) {
            JOptionPane.showMessageDialog(this, "主机地址不能为空。");
            return;
        }

        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "昵称不能为空。");
            return;
        }

        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "端口号必须是整数。");
            return;
        }

        try {
            socket = new Socket(host, port);
            reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
            );
            writer = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)
            );

            writer.write(name);
            writer.newLine();
            writer.flush();

            connected = true;
            setConnectedState(true);
            appendMessage("已连接到服务器 " + host + ":" + port);

            receiveThread = new Thread(this::receiveMessages, "chat-client-receive");
            receiveThread.start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "连接失败: " + e.getMessage());
            disconnectFromServer(false);
        }
    }

    private void receiveMessages() {
        try {
            String message;
            while (connected && (message = reader.readLine()) != null) {
                appendMessage(message);
            }
        } catch (IOException e) {
            if (connected) {
                appendMessage("连接中断: " + e.getMessage());
            }
        } finally {
            if (connected) {
                appendMessage("已与服务器断开连接。");
            }
            disconnectFromServer(false);
        }
    }

    private void sendMessage() {
        if (!connected) {
            JOptionPane.showMessageDialog(this, "请先连接服务器。");
            return;
        }

        String message = messageField.getText().trim();
        if (message.isEmpty()) {
            return;
        }

        try {
            writer.write(message);
            writer.newLine();
            writer.flush();
            appendMessage("我: " + message);
            messageField.setText("");
        } catch (IOException e) {
            appendMessage("发送失败: " + e.getMessage());
        }
    }

    private void disconnectFromServer(boolean notifyServer) {
        if (!connected && socket == null && reader == null && writer == null) {
            setConnectedState(false);
            return;
        }

        if (notifyServer) {
            sendQuitCommand();
        }

        connected = false;

        if (receiveThread != null) {
            receiveThread.interrupt();
            receiveThread = null;
        }

        closeQuietly();
        setConnectedState(false);
    }

    private void sendQuitCommand() {
        try {
            if (writer != null) {
                writer.write(QUIT_COMMAND);
                writer.newLine();
                writer.flush();
            }
        } catch (IOException ignored) {
        }
    }

    private void closeQuietly() {
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException ignored) {
        }

        try {
            if (writer != null) {
                writer.close();
            }
        } catch (IOException ignored) {
        }

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }

        reader = null;
        writer = null;
        socket = null;
    }

    private void setConnectedState(boolean isConnected) {
        connectButton.setEnabled(!isConnected);
        disconnectButton.setEnabled(isConnected);
        hostField.setEditable(!isConnected);
        portField.setEditable(!isConnected);
        nameField.setEditable(!isConnected);
        messageField.setEnabled(isConnected);
        sendButton.setEnabled(isConnected);
    }

    private void appendMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append(message + System.lineSeparator());
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ChatClientGUI clientGUI = new ChatClientGUI();
            clientGUI.setVisible(true);
        });
    }
}
