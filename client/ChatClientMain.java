package client;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import server.ChatServerInterface;

/**
 * Interface gráfica Swing e cliente RMI do Chat Scott.
 */
public class ChatClientMain {

    private static final String DEFAULT_SERVICE_NAME = "ChatService";
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 1099;

    private String username;
    private String host;
    private int port;
    private String serviceName;

    private ChatServerInterface server;
    private ChatClientImpl clientCallback;

    private JFrame frame;
    private JTextArea chatArea;
    private JTextField messageField;
    private JButton sendButton;
    private JLabel statusLabel;

    public ChatClientMain(String username, String host, int port, String serviceName) {
        this.username = username;
        this.host = host;
        this.port = port;
        this.serviceName = serviceName;
    }

    public static void main(String[] args) {
        // Ajusta Look and Feel para o padrão do sistema operacional
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        String username = null;
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;
        String serviceName = DEFAULT_SERVICE_NAME;

        // Se passado via linha de comando: [apelido] [host] [porta] [nomeServico]
        if (args.length > 0 && !args[0].trim().isEmpty()) {
            username = args[0].trim();
        }
        if (args.length > 1 && !args[1].trim().isEmpty()) {
            host = args[1].trim();
        }
        if (args.length > 2) {
            try {
                port = Integer.parseInt(args[2].trim());
            } catch (NumberFormatException e) {
                System.err.println("Porta inválida nos argumentos. Usando padrão " + DEFAULT_PORT);
            }
        }
        if (args.length > 3 && !args[3].trim().isEmpty()) {
            serviceName = args[3].trim();
        }

        // Se o apelido não foi informado via CLI, exibe diálogo de conexão
        if (username == null || username.isEmpty()) {
            ConnectionConfig config = promptConnectionConfig(host, port);
            if (config == null) {
                System.out.println("Conexão cancelada pelo usuário.");
                System.exit(0);
            }
            username = config.username;
            host = config.host;
            port = config.port;
        }

        ChatClientMain clientApp = new ChatClientMain(username, host, port, serviceName);
        SwingUtilities.invokeLater(clientApp::initAndConnect);
    }

    /**
     * Inicializa a interface Swing e estabelece a conexão RMI.
     */
    private void initAndConnect() {
        buildGui();

        // Conecta ao servidor em uma thread separada para não travar a GUI
        new Thread(() -> {
            try {
                connectToServer();
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(frame,
                            "Falha ao conectar ao servidor:\n" + e.getMessage(),
                            "Erro de Conexão RMI",
                            JOptionPane.ERROR_MESSAGE);
                    frame.dispose();
                    System.exit(1);
                });
            }
        }).start();
    }

    /**
     * Constrói a janela principal do chat Swing.
     */
    private void buildGui() {
        frame = new JFrame("Chat Scott - " + username + " (" + host + ":" + port + ")");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setSize(550, 450);
        frame.setMinimumSize(new Dimension(400, 300));
        frame.setLocationRelativeTo(null);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnectAndExit();
            }
        });

        JPanel mainPanel = new JPanel(new BorderLayout(8, 8));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Topo: Barra de status informando dados da conexão
        JPanel topPanel = new JPanel(new BorderLayout());
        statusLabel = new JLabel("Conectando a " + host + ":" + port + "...");
        statusLabel.setFont(new Font("SansSerif", Font.ITALIC, 12));
        topPanel.add(statusLabel, BorderLayout.WEST);

        JButton disconnectButton = new JButton("Sair");
        disconnectButton.setFocusable(false);
        disconnectButton.addActionListener(e -> disconnectAndExit());
        topPanel.add(disconnectButton, BorderLayout.EAST);

        mainPanel.add(topPanel, BorderLayout.NORTH);

        // Centro: Area de exibicao de mensagens
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font("SansSerif", Font.PLAIN, 13));

        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Rodape: Campo de entrada e botao de envio
        JPanel bottomPanel = new JPanel(new BorderLayout(8, 0));
        messageField = new JTextField();
        messageField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        messageField.setEnabled(false); // habilitado após conectar com sucesso

        sendButton = new JButton("Enviar");
        sendButton.setEnabled(false);

        // Ações de envio (ao teclar Enter ou clicar no botão)
        messageField.addActionListener(this::onSendMessage);
        sendButton.addActionListener(this::onSendMessage);

        bottomPanel.add(messageField, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        frame.setContentPane(mainPanel);
        frame.setVisible(true);
    }

    /**
     * Conecta ao RMI Registry, obtém a referência remota do servidor e registra o cliente.
     */
    private void connectToServer() throws Exception {
        // Se estiver conectando a um host remoto e java.rmi.server.hostname não estiver definido,
        // detecta o IP local da interface de rede que alcança o servidor para que os callbacks funcionem.
        configureClientHostname(host, port);

        // 1. Obtém o Registry do servidor
        Registry registry = LocateRegistry.getRegistry(host, port);

        // 2. Faz o lookup da interface remota do servidor
        server = (ChatServerInterface) registry.lookup(serviceName);

        // 3. Instancia o objeto remoto de callback do cliente
        clientCallback = new ChatClientImpl(this::appendMessage);

        // 4. Registra o cliente no servidor
        server.registerClient(clientCallback, username);

        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Conectado como: " + username + " | Servidor: " + host + ":" + port);
            messageField.setEnabled(true);
            sendButton.setEnabled(true);
            messageField.requestFocusInWindow();
        });
    }

    /**
     * Garante que o IP correto da máquina cliente seja configurado para RMI callbacks.
     */
    private static void configureClientHostname(String targetHost, int targetPort) {
        if (System.getProperty("java.rmi.server.hostname") == null) {
            try {
                if (!"localhost".equalsIgnoreCase(targetHost) && !"127.0.0.1".equals(targetHost)) {
                    try (Socket socket = new Socket()) {
                        socket.connect(new InetSocketAddress(targetHost, targetPort), 2000);
                        String localIp = socket.getLocalAddress().getHostAddress();
                        if (localIp != null && !localIp.startsWith("127.")) {
                            System.setProperty("java.rmi.server.hostname", localIp);
                        }
                    }
                }
            } catch (Exception ignored) {
                // Deixa o RMI utilizar a resolução padrão caso o socket de teste falhe
            }
        }
    }

    /**
     * Trata o envio de mensagens acionado pelo usuário.
     */
    private void onSendMessage(ActionEvent e) {
        String text = messageField.getText().trim();
        if (text.isEmpty()) {
            return;
        }

        messageField.setText("");
        messageField.requestFocusInWindow();

        // Envia de forma assíncrona para manter a interface responsiva
        new Thread(() -> {
            try {
                server.sendMessage(username, text);
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    appendMessage("[Erro] Falha ao enviar mensagem: " + ex.getMessage() + "\n");
                    statusLabel.setText("Erro de comunicação com o servidor.");
                });
            }
        }).start();
    }

    /**
     * Acrescenta uma mensagem na área de texto e rola até o final.
     *
     * @param text texto a ser exibido
     */
    private void appendMessage(String text) {
        chatArea.append(text);
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    /**
     * Notifica o servidor da desconexão, cancela o export do callback e fecha a aplicação.
     */
    private void disconnectAndExit() {
        if (server != null && username != null) {
            try {
                server.removeClient(username);
            } catch (Exception e) {
                System.err.println("Erro ao desregistrar cliente: " + e.getMessage());
            }
        }

        if (clientCallback != null) {
            try {
                UnicastRemoteObject.unexportObject(clientCallback, true);
            } catch (Exception ignored) {
            }
        }

        if (frame != null) {
            frame.dispose();
        }
        System.exit(0);
    }

    /**
     * Diálogo modal para configuração de apelido e servidor caso não seja fornecido por linha de comando.
     */
    private static ConnectionConfig promptConnectionConfig(String defaultHost, int defaultPort) {
        JTextField nameField = new JTextField(15);
        JTextField hostField = new JTextField(defaultHost, 15);
        JTextField portField = new JTextField(String.valueOf(defaultPort), 6);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Apelido (Nickname):"), gbc);
        gbc.gridx = 1;
        panel.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Host do Servidor:"), gbc);
        gbc.gridx = 1;
        panel.add(hostField, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("Porta do Registry:"), gbc);
        gbc.gridx = 1;
        panel.add(portField, gbc);

        int result = JOptionPane.showConfirmDialog(
                null,
                panel,
                "Conectar ao Chat Scott",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (result != JOptionPane.OK_OPTION) {
            return null;
        }

        String username = nameField.getText().trim();
        if (username.isEmpty()) {
            JOptionPane.showMessageDialog(null, "O apelido é obrigatório.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return promptConnectionConfig(defaultHost, defaultPort);
        }

        String host = hostField.getText().trim();
        if (host.isEmpty()) {
            host = defaultHost;
        }

        int port = defaultPort;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(null, "Porta inválida. Utilizando " + defaultPort, "Aviso", JOptionPane.WARNING_MESSAGE);
            port = defaultPort;
        }

        return new ConnectionConfig(username, host, port);
    }

    private static class ConnectionConfig {
        final String username;
        final String host;
        final int port;

        ConnectionConfig(String username, String host, int port) {
            this.username = username;
            this.host = host;
            this.port = port;
        }
    }
}
