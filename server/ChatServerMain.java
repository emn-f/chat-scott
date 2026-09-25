package server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.HashMap;
import java.util.Map;

/**
 * Ponto de entrada do servidor de chat.
 * Carrega configuracoes do arquivo .env ou argumentos e inicializa o RMI Registry.
 */
public class ChatServerMain {

    public static final String DEFAULT_SERVICE_NAME = "ChatService";
    public static final int DEFAULT_PORT = 1099;

    public static void main(String[] args) {
        // Carrega variaveis do arquivo .env se disponivel
        Map<String, String> env = loadEnv();

        int port = DEFAULT_PORT;
        String envPort = getEnvOrProperty(env, "CHAT_PORT", null);
        if (envPort != null) {
            try {
                port = Integer.parseInt(envPort);
            } catch (NumberFormatException ignored) {
            }
        }

        String serviceName = getEnvOrProperty(env, "CHAT_SERVICE_NAME", DEFAULT_SERVICE_NAME);
        String hostname = getEnvOrProperty(env, "CHAT_SERVER_IP", null);
        if (hostname == null) {
            hostname = getEnvOrProperty(env, "CHAT_HOST", null);
        }

        // Sobrescrita por argumentos de linha de comando: [porta] [hostname] [nomeServico]
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0].trim());
            } catch (NumberFormatException e) {
                System.err.println("Aviso: Porta invalida informada ('" + args[0] + "'). Usando porta " + port);
            }
        }

        if (args.length > 1 && !args[1].trim().isEmpty()) {
            hostname = args[1].trim();
        }

        if (args.length > 2 && !args[2].trim().isEmpty()) {
            serviceName = args[2].trim();
        }

        if (hostname != null && !hostname.trim().isEmpty()) {
            System.setProperty("java.rmi.server.hostname", hostname.trim());
        }

        // Obtem o hostname configurado ou detecta o IP local
        String currentHostname = System.getProperty("java.rmi.server.hostname");
        if (currentHostname == null || currentHostname.isEmpty()) {
            try {
                currentHostname = InetAddress.getLocalHost().getHostAddress();
            } catch (Exception e) {
                currentHostname = "localhost";
            }
        }

        System.out.println("==================================================");
        System.out.println("            SERVIDOR CHAT SCOTT (RMI)             ");
        System.out.println("==================================================");
        System.out.println("Porta do Registry  : " + port);
        System.out.println("Nome do Servico    : " + serviceName);
        System.out.println("Endereco / Host    : " + currentHostname);
        System.out.println("--------------------------------------------------");

        try {
            Registry registry;
            try {
                registry = LocateRegistry.createRegistry(port);
                System.out.println("RMI Registry criado com sucesso na porta " + port + ".");
            } catch (RemoteException e) {
                System.out.println("RMI Registry ja em execucao na porta " + port + ". Obtendo registry existente...");
                registry = LocateRegistry.getRegistry(port);
            }

            ChatServerImpl chatServer = new ChatServerImpl();
            registry.rebind(serviceName, chatServer);

            System.out.println("Servico publicado com sucesso como '" + serviceName + "'.");
            System.out.println("Servidor pronto e aguardando conexoes de clientes.");
            System.out.println("==================================================");

        } catch (Exception e) {
            System.err.println("Erro critico ao inicializar o servidor de chat: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
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
                            String val = line.substring(eqIndex + 1).trim();
                            if ((val.startsWith("\"") && val.endsWith("\""))
                                    || (val.startsWith("'") && val.endsWith("'"))) {
                                val = val.substring(1, val.length() - 1);
                            }
                            env.putIfAbsent(key, val);
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
}
