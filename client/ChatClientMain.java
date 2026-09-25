package client;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
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
 * Interface grafica Swing e cliente RMI do Chat Scott.
 * Suporta execucao em modo grafico (padrao) ou modo terminal CLI (Termux / headless / --cli).
 */
public class ChatClientMain {

    public static final String DEFAULT_SERVICE_NAME = "ChatService";
    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 1099;

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
        // Detecta se esta rodando em ambiente sem interface grafica (ex: Termux) ou com flag --cli
        boolean isCli = GraphicsEnvironment.isHeadless();
        for (String arg : args) {
            if ("--cli".equalsIgnoreCase(arg.trim()) || "-cli".equalsIgnoreCase(arg.trim())) {
                isCli = true;
                break;
            }
        }

        if (!isCli) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
        }

        // Carrega variaveis do arquivo .env se disponivel
        Map<String, String> env = loadEnv();

        int defaultPort = DEFAULT_PORT;
        String envPort = getEnvOrProperty(env, "CHAT_PORT", null);
        if (envPort != null) {
            try {
                defaultPort = Integer.parseInt(envPort);
            } catch (NumberFormatException ignored) {
            }
        }

        String localIp = detectLocalIp();
        String defaultHost = getEnvOrProperty(env, "CHAT_SERVER_IP", null);
        if (defaultHost == null || defaultHost.trim().isEmpty()) {
            defaultHost = getEnvOrProperty(env, "CHAT_HOST", null);
        }
        if (defaultHost == null || defaultHost.trim().isEmpty() || "localhost".equalsIgnoreCase(defaultHost) || "127.0.0.1".equals(defaultHost)) {
            defaultHost = localIp;
        }

        String defaultServiceName = getEnvOrProperty(env, "CHAT_SERVICE_NAME", DEFAULT_SERVICE_NAME);

        String username = null;
        String host = defaultHost;
        int port = defaultPort;
        String serviceName = defaultServiceName;

        // Leitura de argumentos CLI: [apelido] [host] [porta] [nomeServico]
        if (args.length > 0 && !args[0].trim().isEmpty() && !args[0].startsWith("-")) {
            username = args[0].trim();
        }
        if (args.length > 1 && !args[1].trim().isEmpty() && !args[1].startsWith("-")) {
            host = args[1].trim();
        }
        if (args.length > 2 && !args[2].startsWith("-")) {
            try {
                port = Integer.parseInt(args[2].trim());
            } catch (NumberFormatException e) {
                System.err.println("Porta invalida nos argumentos. Usando " + defaultPort);
            }
        }
        if (args.length > 3 && !args[3].startsWith("-") && !args[3].trim().isEmpty()) {
            serviceName = args[3].trim();
        }

        // Execucao em modo CLI (Termux / terminal puro)
        if (isCli) {
            if (username == null || username.isEmpty()) {
                System.out.print("Digite seu apelido (nickname): ");
                try {
                    BufferedReader br = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                    String input = br.readLine();
                    if (input != null && !input.trim().isEmpty()) {
                        username = input.trim();
                    }
                } catch (Exception ignored) {
                }
            }
            if (username == null || username.isEmpty()) {
                username = "UsuarioTermux";
            }
            runCliClient(username, host, port, serviceName);
            return;
        }

        // Execucao em modo Swing GUI (padrao desktop)
        if (username == null || username.isEmpty()) {
            ConnectionConfig config = promptConnectionConfig(host, port);
            if (config == null) {
                System.out.println("Conexao cancelada pelo usuario.");
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
     * Execucao no modo terminal CLI (ideal para Termux / Android sem servidor X11).
     */
    private static void runCliClient(String username, String host, int port, String serviceName) {
        System.out.println("==================================================");
        System.out.println("         CHAT SCOTT (Modo Terminal CLI)           ");
        System.out.println("==================================================");
        System.out.println("Usuario  : " + username);
        System.out.println("Servidor : " + host + ":" + port);
        System.out.println("Servico  : " + serviceName);
        System.out.println("--------------------------------------------------");
        System.out.println("Conectando ao servidor...");

        try {
            configureClientHostname(host, port);
            Registry registry = LocateRegistry.getRegistry(host, port);
            ChatServerInterface server = (ChatServerInterface) registry.lookup(serviceName);

            ChatClientImpl clientCallback = new ChatClientImpl(System.out::print);
            server.registerClient(clientCallback, username);

            System.out.println("Conectado com sucesso! Digite suas mensagens.");
            System.out.println("Para desconectar e sair, digite '/sair' ou 'exit'.");
            System.out.println("==================================================");

            // Shutdown hook para desconectar se o processo for finalizado
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    server.removeClient(username);
                    UnicastRemoteObject.unexportObject(clientCallback, true);
                } catch (Exception ignored) {
                }
            }));

            try (BufferedReader consoleReader = new BufferedReader(
                    new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = consoleReader.readLine()) != null) {
                    line = line.trim();
                    if (line.equalsIgnoreCase("/sair") || line.equalsIgnoreCase("exit")) {
                        break;
                    }
                    if (!line.isEmpty()) {
                        try {
                            server.sendMessage(username, line);
                        } catch (Exception ex) {
                            System.err.println("[Erro] Falha ao enviar mensagem: " + ex.getMessage());
                        }
                    }
                }
            }

            try {
                server.removeClient(username);
                UnicastRemoteObject.unexportObject(clientCallback, true);
            } catch (Exception ignored) {
            }

            System.out.println("Desconectado do chat.");
            System.exit(0);

        } catch (Exception e) {
            System.err.println("Erro ao conectar ao servidor de chat: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Inicializa a interface Swing e estabelece a conexao RMI.
     */
    private void initAndConnect() {
        buildGui();

        // Conecta ao servidor em uma thread separada para nao travar a GUI
        new Thread(() -> {
            try {
                connectToServer();
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(frame,
                            "Falha ao conectar ao servidor:\n" + e.getMessage(),
                            "Erro de Conexao RMI",
                            JOptionPane.ERROR_MESSAGE);
                    frame.dispose();
                    System.exit(1);
                });
            }
        }).start();
    }

    /**
     * Constroi a janela principal do chat Swing.
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

        // Topo: Barra de status informando dados da conexao com IP real
        JPanel topPanel = new JPanel(new BorderLayout());
        statusLabel = new JLabel("Conectando ao servidor em " + host + ":" + port + "...");
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
        messageField.setEnabled(false); // habilitado apos conectar com sucesso

        sendButton = new JButton("Enviar");
        sendButton.setEnabled(false);

        // Acoes de envio (ao teclar Enter ou clicar no botao)
        messageField.addActionListener(this::onSendMessage);
        sendButton.addActionListener(this::onSendMessage);

        bottomPanel.add(messageField, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        frame.setContentPane(mainPanel);
        frame.setVisible(true);
    }

    /**
     * Conecta ao RMI Registry, obtem a referencia remota do servidor e registra o cliente.
     */
    private void connectToServer() throws Exception {
        // Garante configuracao de hostname no cliente para funcionamento dos callbacks
        configureClientHostname(host, port);

        // 1. Obtem o Registry do servidor
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
     * Garante que o IP correto da maquina cliente seja configurado para RMI callbacks.
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
                // Mantem resolucao padrao em caso de falha no socket de teste
            }
        }
    }

    /**
     * Trata o envio de mensagens acionado pelo usuario.
     */
    private void onSendMessage(ActionEvent e) {
        String text = messageField.getText().trim();
        if (text.isEmpty()) {
            return;
        }

        messageField.setText("");
        messageField.requestFocusInWindow();

        // Envia de forma assincrona para manter a interface responsiva
        new Thread(() -> {
            try {
                server.sendMessage(username, text);
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    appendMessage("[Erro] Falha ao enviar mensagem: " + ex.getMessage() + "\n");
                    statusLabel.setText("Erro de comunicacao com o servidor.");
                });
            }
        }).start();
    }

    /**
     * Acrescenta uma mensagem na area de texto e rola ate o final.
     *
     * @param text texto a ser exibido
     */
    private void appendMessage(String text) {
        chatArea.append(text);
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    /**
     * Notifica o servidor da desconexao, cancela o export do callback e fecha a aplicacao.
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
     * Dialogo modal para configuracao de apelido e servidor caso nao seja fornecido por linha de comando.
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
        panel.add(new JLabel("IP / Host do Servidor:"), gbc);
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
            JOptionPane.showMessageDialog(null, "O apelido e obrigatorio.", "Aviso", JOptionPane.WARNING_MESSAGE);
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
            JOptionPane.showMessageDialog(null, "Porta invalida. Utilizando " + defaultPort, "Aviso",
                    JOptionPane.WARNING_MESSAGE);
            port = defaultPort;
        }

        return new ConnectionConfig(username, host, port);
    }

    /**
     * Detecta o endereco IPv4 real da maquina na rede local.
     */
    public static String detectLocalIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        if (!ip.startsWith("127.")) {
                            return ip;
                        }
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    /**
     * Carrega configuracoes de um arquivo .env se presente.
     */
    private static Map<String, String> loadEnv() {
        Map<String, String> env = new HashMap<>();
        File[] candidates = new File[] { new File(".env"), new File("../.env") };
        for (File file : candidates) {
            if (file.exists() && file.isFile()) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        int eqIndex = line.indexOf('=');
                        if (eqIndex > 0) {
                            String key = line.substring(0, eqIndex).trim();
                            String value = line.substring(eqIndex + 1).trim();
                            if ((value.startsWith("\"") && value.endsWith("\""))
                                    || (value.startsWith("'") && value.endsWith("'"))) {
                                value = value.substring(1, value.length() - 1);
                            }
                            env.putIfAbsent(key, value);
                        }
                    }
                } catch (Exception ignored) {
                }
                break;
            }
        }
        return env;
    }

    private static String getEnvOrProperty(Map<String, String> fileEnv, String key, String defaultValue) {
        String val = fileEnv.get(key);
        if (val == null || val.trim().isEmpty()) {
            val = System.getenv(key);
        }
        if (val == null || val.trim().isEmpty()) {
            val = System.getProperty(key);
        }
        return (val != null && !val.trim().isEmpty()) ? val.trim() : defaultValue;
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
