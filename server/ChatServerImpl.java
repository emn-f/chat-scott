package server;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import client.ChatClientInterface;

/**
 * Implementação remota do servidor de chat.
 * Mantém o registro dos clientes conectados e propaga mensagens via callback RMI.
 */
public class ChatServerImpl extends UnicastRemoteObject implements ChatServerInterface {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Registro concorrente dos clientes conectados: Nome de Usuário -> Stub Remoto
    private final Map<String, ChatClientInterface> clients = new ConcurrentHashMap<>();

    /**
     * Construtor padrão exportando o objeto remoto RMI.
     *
     * @throws RemoteException se ocorrer erro na exportação remota
     */
    public ChatServerImpl() throws RemoteException {
        super();
    }

    /**
     * Construtor permitindo especificar a porta de exportação RMI.
     *
     * @param port porta TCP para exportar o objeto remoto (0 para porta anônima)
     * @throws RemoteException se ocorrer erro na exportação remota
     */
    public ChatServerImpl(int port) throws RemoteException {
        super(port);
    }

    @Override
    public void registerClient(ChatClientInterface client, String username) throws RemoteException {
        if (username == null || username.trim().isEmpty()) {
            throw new RemoteException("O apelido não pode ser vazio.");
        }
        if (client == null) {
            throw new RemoteException("A referência remota do cliente não pode ser nula.");
        }

        String trimmedName = username.trim();

        // Evita duplicidade de nomes
        if (clients.putIfAbsent(trimmedName, client) != null) {
            throw new RemoteException("O apelido '" + trimmedName + "' já está em uso.");
        }

        System.out.println(String.format("[%s] Cliente conectado: %s (Total online: %d)",
                LocalTime.now().format(TIME_FORMATTER), trimmedName, clients.size()));

        // Notifica todos os participantes (incluindo o novo cliente)
        broadcast("Servidor", "*** " + trimmedName + " entrou no chat ***");
    }

    @Override
    public void removeClient(String username) throws RemoteException {
        if (username == null || username.trim().isEmpty()) {
            return;
        }

        String trimmedName = username.trim();
        ChatClientInterface removed = clients.remove(trimmedName);

        if (removed != null) {
            System.out.println(String.format("[%s] Cliente desconectado: %s (Restantes online: %d)",
                    LocalTime.now().format(TIME_FORMATTER), trimmedName, clients.size()));

            // Notifica os demais participantes
            broadcast("Servidor", "*** " + trimmedName + " saiu do chat ***");
        }
    }

    @Override
    public void sendMessage(String username, String message) throws RemoteException {
        if (username == null || message == null) {
            return;
        }
        broadcast(username.trim(), message.trim());
    }

    /**
     * Propaga a mensagem para todos os clientes conectados.
     * Caso algum cliente lance RemoteException, é considerado desconectado e removido.
     *
     * @param sender remetente da mensagem
     * @param text   conteúdo da mensagem
     */
    private synchronized void broadcast(String sender, String text) {
        System.out.println(String.format("[%s] %s: %s",
                LocalTime.now().format(TIME_FORMATTER), sender, text));

        List<String> disconnectedUsers = new ArrayList<>();

        for (Map.Entry<String, ChatClientInterface> entry : clients.entrySet()) {
            String clientName = entry.getKey();
            ChatClientInterface clientStub = entry.getValue();

            try {
                clientStub.receiveMessage(sender, text);
            } catch (RemoteException e) {
                System.err.println(String.format("[%s] Falha ao comunicar com o cliente '%s': %s",
                        LocalTime.now().format(TIME_FORMATTER), clientName, e.getMessage()));
                disconnectedUsers.add(clientName);
            }
        }

        // Limpeza de clientes desconectados inesperadamente
        for (String deadUser : disconnectedUsers) {
            clients.remove(deadUser);
            System.out.println(String.format("[%s] Cliente '%s' removido devido a falha de comunicação.",
                    LocalTime.now().format(TIME_FORMATTER), deadUser));
        }
    }

    /**
     * Retorna a quantidade atual de clientes conectados.
     *
     * @return número de clientes ativos
     */
    public int getConnectedClientsCount() {
        return clients.size();
    }
}
