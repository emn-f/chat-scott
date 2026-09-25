@echo off
cd /d "%~dp0"

if not exist bin (
    echo [Chat Scott] Criando pasta bin e compilando...
    mkdir bin
    javac -d bin server\*.java client\*.java
)

echo [Chat Scott] Iniciando Cliente...
java -cp bin client.ChatClientMain %*
