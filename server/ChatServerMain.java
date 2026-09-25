package server;

import java.net.InetAddress;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Ponto de entrada do servidor de chat.
 * Inicializa o RMI Registry, instancia o ChatServerImpl e publica o serviço.
 */
public class ChatServerMain {

    public static final String DEFAULT_SERVICE_NAME = "ChatService";
    public static final int DEFAULT_PORT = 1099;

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        String serviceName = DEFAULT_SERVICE_NAME;
        String hostname = null;

        // Leitura de argumentos de linha de comando: [porta] [hostname] [nomeServico]
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Aviso: Porta inválida informada ('" + args[0] + "'). Usando porta padrão " + DEFAULT_PORT);
                port = DEFAULT_PORT;
            }
        }

        if (args.length > 1 && !args[1].trim().isEmpty()) {
            hostname = args[1].trim();
            System.setProperty("java.rmi.server.hostname", hostname);
        }

        if (args.length > 2 && !args[2].trim().isEmpty()) {
            serviceName = args[2].trim();
        }

        // Obtém o hostname configurado ou detecta o IP local
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
        System.out.println("Nome do Serviço    : " + serviceName);
        System.out.println("Endereço / Host    : " + currentHostname);
        System.out.println("--------------------------------------------------");

        try {
            Registry registry;
            try {
                registry = LocateRegistry.createRegistry(port);
                System.out.println("RMI Registry criado com sucesso na porta " + port + ".");
            } catch (RemoteException e) {
                System.out.println("RMI Registry já em execução na porta " + port + ". Obtendo registry existente...");
                registry = LocateRegistry.getRegistry(port);
            }

            ChatServerImpl chatServer = new ChatServerImpl();
            registry.rebind(serviceName, chatServer);

            System.out.println("Serviço publicado com sucesso como '" + serviceName + "'.");
            System.out.println("Servidor pronto e aguardando conexões de clientes.");
            System.out.println("==================================================");

        } catch (Exception e) {
            System.err.println("Erro crítico ao inicializar o servidor de chat: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
