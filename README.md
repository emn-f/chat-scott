# Chat Scott — Sistemas Distribuídos (Java RMI)

Projeto de chat cliente-servidor distribuído implementado com **Java RMI** (Remote Method Invocation), desenvolvido para a disciplina de **Sistemas Distribuídos**.

---

## 📌 Visão Geral da Arquitetura

O sistema adota o padrão cliente-servidor com **callbacks remotos RMI**:

- **Comunicação Ativa (sem Polling):** Os clientes não fazem requisições periódicas para buscar novas mensagens. Quando qualquer cliente envia uma mensagem via `server.sendMessage(username, message)`, o servidor itera sobre os clientes conectados e invoca remotamente o método de callback `receiveMessage(username, message)` diretamente em cada stub de cliente.
- **Transparência de Distribuição:** Ambos os lados (servidor e clientes) atuam como objetos remotos exportados (`UnicastRemoteObject`).
- **RMI Puro:** Desenvolvido exclusivamente com classes padrão do Java SE (`java.rmi.*`, `javax.swing.*`), sem bibliotecas ou frameworks externos.

```
       [ Cliente 1 (Swing) ]                  [ Servidor RMI ]                  [ Cliente 2 (Swing) ]
                 |                                   |                                   |
                 | ----- registerClient(stub) -----> |                                   |
                 |                                   | <----- registerClient(stub) ----- |
                 |                                   |                                   |
                 | ----- sendMessage("Olá!") ------> |                                   |
                 |                                   | ===== receiveMessage("Olá!") ===> |
                 | <==== receiveMessage("Olá!") ==== |                                   |
```

---

## 📂 Estrutura do Repositório

```
chat-scott/
├── server/
│   ├── ChatServerInterface.java  # Interface remota do servidor
│   ├── ChatServerImpl.java       # Implementação remota e controle de conexões
│   └── ChatServerMain.java       # Criação do Registry (1099) e bind do serviço
├── client/
│   ├── ChatClientInterface.java  # Interface remota do callback do cliente
│   ├── ChatClientImpl.java       # Implementação remota do callback (EDT Swing)
│   └── ChatClientMain.java       # Interface gráfica Swing e fluxo de conexão
└── README.md                     # Documentação completa de uso e testes
```

---

## ⚙️ Pré-requisitos

- **Java Development Kit (JDK) 11 ou superior** (desenvolvido e testado na JDK 17).
- Terminal de linha de comando (`PowerShell`, `cmd` ou `bash`).

---

## 🔨 Como Compilar

Na raiz do projeto (`chat-scott`), execute o comando abaixo para compilar todos os módulos no diretório `bin/`:

### No Windows (PowerShell ou Prompt de Comando):
```powershell
javac -d bin server/*.java client/*.java
```

### No Linux / macOS:
```bash
javac -d bin server/*.java client/*.java
```

---

## 🚀 Execução Local (Mesma Máquina - localhost)

### 1. Iniciar o Servidor
Abra um terminal na pasta do projeto e inicie o servidor:

```bash
java -cp bin server.ChatServerMain
```

Por padrão:
- O **RMI Registry** é iniciado na porta **1099**.
- O serviço é registrado com o nome **`ChatService`**.
- Argumentos opcionais: `java -cp bin server.ChatServerMain [porta] [hostname] [nomeServico]`

### 2. Iniciar o Primeiro Cliente
Abra outro terminal e inicie o cliente:

```bash
java -cp bin client.ChatClientMain
```

Uma janela de diálogo será exibida para informar:
- **Apelido (Nickname):** ex. `Alice`
- **Host do Servidor:** `localhost`
- **Porta do Registry:** `1099`

*(Dica: você também pode passar os dados diretamente via linha de comando: `java -cp bin client.ChatClientMain Alice`)*

### 3. Iniciar Mais Clientes
Abra novos terminais para instanciar outros clientes (ex: `Bob`, `Carlos`):

```bash
java -cp bin client.ChatClientMain Bob
```

Envie mensagens pela interface gráfica e observe a entrega instantânea em todos os clientes conectados.

---

## 🌐 Teste em Rede (Duas Máquinas Diferentes)

Para testar entre computadores distintos conectados na mesma rede local (Wi-Fi ou Ethernet):

### 1. Identificar o IP do Servidor
Na máquina que executará o servidor, descubra o IP local:
- **Windows:** execute `ipconfig` (procure pelo *Endereço IPv4*, ex: `192.168.1.50`).
- **Linux/macOS:** execute `ip a` ou `ifconfig`.

### 2. Configurar o Firewall no Servidor
Certifique-se de que a porta **1099 TCP** esteja liberada no firewall do sistema operacional da máquina do servidor, ou permita a comunicação da JVM quando a caixa de diálogo do firewall solicitar.

### 3. Iniciar o Servidor com o IP da Rede
O Java RMI incorpora o IP do servidor nos stubs remotos enviados aos clientes. Se não for especificado, pode adotar `127.0.0.1`, impedindo conexões externas.

Inicie o servidor informando o IP real da máquina:

```bash
java -cp bin server.ChatServerMain 1099 192.168.1.50
```

*(Ou via propriedade do sistema: `java -Djava.rmi.server.hostname=192.168.1.50 -cp bin server.ChatServerMain`)*

### 4. Conectar o Cliente na Outra Máquina
Na máquina cliente:
1. Copie o projeto compilado (ou compile o repositório clonado).
2. Execute o cliente informando o IP do servidor:
   ```bash
   java -cp bin client.ChatClientMain Bob 192.168.1.50 1099
   ```
   *Ou execute sem argumentos e digite o IP `192.168.1.50` no diálogo de conexão.*

> **Nota sobre Callbacks em Rede:**
> O `ChatClientMain` detecta automaticamente a interface de rede ativa que se comunica com o servidor e define a propriedade `java.rmi.server.hostname` da máquina cliente, permitindo que o servidor alcance o cliente de volta via callback sem necessidade de configurações manuais adicionais.

---

## 💬 Recursos e Comportamento Implementado

1. **Formato das Mensagens:**
   - Mensagens de usuários: `[HH:mm:ss] Apelido: Mensagem`
   - Avisos do sistema: `[HH:mm:ss] *** Apelido entrou no chat ***` e `[HH:mm:ss] *** Apelido saiu do chat ***`
2. **Controle de Conexão e Saída:**
   - Ao fechar a janela do chat ou clicar no botão **Sair**, o cliente aciona `server.removeClient(username)`, que desregistra o usuário e avisa aos demais.
3. **Prevenção de Nomes Duplicados:**
   - Se um usuário tentar se registrar com um apelido já ativo na sessão, o servidor recusa via `RemoteException` com mensagem clara na tela.
4. **Tratamento de Desconexões Abruptas:**
   - Se um cliente fechar de forma anormal ou perder o sinal de rede, o servidor detecta a `RemoteException` durante o broadcast e remove o cliente inativo do registro com segurança.
5. **Thread Safety:**
   - Registro de clientes gerenciado com `ConcurrentHashMap` e sincronização no broadcast.
   - Atualizações da interface gráfica Swing despachadas na Event Dispatch Thread (`SwingUtilities.invokeLater`).

---

## 📋 Resumo dos Métodos RMI

### `ChatServerInterface`
- `void registerClient(ChatClientInterface client, String username) throws RemoteException`
- `void removeClient(String username) throws RemoteException`
- `void sendMessage(String username, String message) throws RemoteException`

### `ChatClientInterface`
- `void receiveMessage(String username, String message) throws RemoteException`

---

## 🔍 Linting e Análise Estática

O projeto está configurado para análise estática rigorosa:

- **Configuração no VS Code (`.vscode/settings.json`):** Formatação automática ao salvar, organização de imports e linting integrado do compilador Java ativado.
- **Compilação com Verificação de Lint Completa (`-Xlint:all`):**
  ```powershell
  javac -Xlint:all -d bin server/*.java client/*.java
  ```
  O código compila com **0 warnings e 0 erros**.

