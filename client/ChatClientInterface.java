package client;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Interface remota do cliente de chat.
 * Define o método de callback que o servidor invoca para entregar mensagens.
 */
public interface ChatClientInterface extends Remote {

    /**
     * Chamado remotamente pelo servidor para entregar uma mensagem ao cliente.
     *
     * @param username apelido do autor da mensagem (ou identificador do sistema)
     * @param message  texto da mensagem recebida
     * @throws RemoteException em caso de falha de comunicação RMI
     */
    void receiveMessage(String username, String message) throws RemoteException;
}
