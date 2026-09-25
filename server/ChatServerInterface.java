package server;

import java.rmi.Remote;
import java.rmi.RemoteException;
import client.ChatClientInterface;

/**
 * Interface remota do servidor de chat.
 * Define os métodos que os clientes podem invocar remotamente via RMI.
 */
public interface ChatServerInterface extends Remote {

    /**
     * Registra um novo cliente no servidor.
     *
     * @param client   referência remota do cliente (stub) para callbacks
     * @param username apelido único do cliente
     * @throws RemoteException em caso de falha de comunicação RMI ou nome duplicado
     */
    void registerClient(ChatClientInterface client, String username) throws RemoteException;

    /**
     * Remove o cliente do servidor ao desconectar.
     *
     * @param username apelido do cliente a ser removido
     * @throws RemoteException em caso de falha de comunicação RMI
     */
    void removeClient(String username) throws RemoteException;

    /**
     * Envia uma mensagem para o servidor propagar aos demais clientes conectados.
     *
     * @param username apelido do remetente
     * @param message  conteúdo da mensagem
     * @throws RemoteException em caso de falha de comunicação RMI
     */
    void sendMessage(String username, String message) throws RemoteException;
}
