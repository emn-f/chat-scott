package client;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/**
 * Implementação remota do cliente de chat.
 * Recebe mensagens propagadas pelo servidor e as repassa para a interface gráfica Swing.
 */
public class ChatClientImpl extends UnicastRemoteObject implements ChatClientInterface {

    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final Consumer<String> messageConsumer;

    /**
     * Construtor que recebe uma função callback para atualizar a interface gráfica.
     *
     * @param messageConsumer consumidor chamado com a mensagem formatada para exibição na UI
     * @throws RemoteException se ocorrer erro na exportação remota
     */
    public ChatClientImpl(Consumer<String> messageConsumer) throws RemoteException {
        super();
        this.messageConsumer = messageConsumer;
    }

    /**
     * Construtor permitindo especificar a porta de exportação RMI.
     *
     * @param port            porta TCP para exportar o objeto remoto (0 para anônima)
     * @param messageConsumer consumidor para exibição na UI
     * @throws RemoteException se ocorrer erro na exportação remota
     */
    public ChatClientImpl(int port, Consumer<String> messageConsumer) throws RemoteException {
        super(port);
        this.messageConsumer = messageConsumer;
    }

    @Override
    public void receiveMessage(String username, String message) throws RemoteException {
        String timestamp = LocalTime.now().format(TIME_FORMATTER);
        String formattedMessage;

        // Se for mensagem de sistema (Servidor ou avisos com '***')
        if ("Servidor".equalsIgnoreCase(username) || (message != null && message.startsWith("***"))) {
            formattedMessage = String.format("[%s] %s%n", timestamp, message);
        } else {
            formattedMessage = String.format("[%s] %s: %s%n", timestamp, username, message);
        }

        // Garante que a atualização da interface gráfica ocorra na Event Dispatch Thread (EDT) do Swing
        SwingUtilities.invokeLater(() -> {
            if (messageConsumer != null) {
                messageConsumer.accept(formattedMessage);
            }
        });
    }
}
