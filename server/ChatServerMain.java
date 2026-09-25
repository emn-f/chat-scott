package server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Ponto de entrada do servidor de chat.
 * Carrega configuracoes do arquivo .env ou argumentos e inicializa o RMI Registry,
 * exibindo o IP real da rede local ao inves de localhost.
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

        // Detecta o IP real da rede para exibicao e configuracao do RMI
        String localIp = detectLocalIp();

        String hostname = getEnvOrProperty(env, "CHAT_SERVER_IP", null);
        if (hostname == null || hostname.trim().isEmpty() || "localhost".equalsIgnoreCase(hostname) || "127.0.0.1".equals(hostname)) {
            String envHost = getEnvOrProperty(env, "CHAT_HOST", null);
            if (envHost != null && !envHost.trim().isEmpty() && !"localhost".equalsIgnoreCase(envHost) && !"127.0.0.1".equals(envHost)) {
                hostname = envHost.trim();
            } else {
                hostname = localIp;
            }
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

        // Configura o IP publicavel no stub RMI (evita que clientes tentem conectar em 127.0.0.1)
        System.setProperty("java.rmi.server.hostname", hostname);

        System.out.println("==================================================");
        System.out.println("            SERVIDOR CHAT SCOTT (RMI)             ");
        System.out.println("==================================================");
        System.out.println("Porta do Registry  : " + port);
        System.out.println("Nome do Servico    : " + serviceName);
        System.out.println("Endereco / Host    : " + hostname);
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
            System.out.println("Para conectar de qualquer PC na rede, use o IP: " + hostname);
            System.out.println("==================================================");

        } catch (Exception e) {
            System.err.println("Erro critico ao inicializar o servidor de chat: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Detecta o endereco IPv4 real da maquina servidora na rede local.
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
