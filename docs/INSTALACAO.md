# Instalação e atualização

## Pacote portátil

1. Baixe o ZIP portátil da versão desejada.
2. Extraia todo o conteúdo. Executar diretamente de dentro do ZIP não é suportado.
3. Abra a pasta extraída e confira a presença de `WinPlay.exe`, `app/`, `runtime/` e `README.txt`.
4. Execute `WinPlay.exe`. A janela deve mostrar o painel **Vamos conectar**.

O runtime Java e as bibliotecas de mídia acompanham o pacote. O executável é destinado ao Windows x64.

## Preparar o iPhone e a rede

1. Ative o Bluetooth no PC e no iPhone.
2. Nas configurações de Bluetooth do Windows, adicione e pareie o iPhone. Confirme os códigos solicitados pelos dispositivos.
3. Conecte ambos à mesma rede Wi-Fi. Evite rede de convidados com isolamento entre clientes.
4. Abra o WinPlay e clique em **Atualizar** se o pareamento tiver sido realizado depois da abertura.
5. Selecione o iPhone e a interface IPv4 correspondente à rede Wi-Fi.
6. Preencha o nome exato da rede e sua senha; clique em **Conectar CarPlay**.
7. Aceite os pedidos de conexão e CarPlay no telefone. Aguarde a chegada do vídeo.

As etapas Bluetooth, Autenticação e CarPlay ajudam a identificar o andamento. O resultado esperado é o vídeo do telefone e os controles de navegação habilitados.

## Atualizar

Encerre a sessão e feche o aplicativo. Extraia a versão nova em outra pasta e abra seu executável. Preserve os dados em `%LOCALAPPDATA%\DiPlayWindows`, usados por compatibilidade para autenticação, identidade local, pareamentos e preferências.

Não copie apenas o EXE entre versões: o diretório `app/` precisa corresponder ao executável entregue. Guarde a versão anterior caso precise comparar o comportamento.

## Remover

Feche o aplicativo e remova a pasta do pacote portátil. Para uma remoção completa, os dados locais podem ser excluídos manualmente depois de um backup. Essa exclusão remove preferências e material de autenticação e pode exigir nova preparação na próxima execução.

## Próximos passos

Consulte [Uso](USO.md) para os controles ou [Solução de problemas](SOLUCAO-DE-PROBLEMAS.md) se uma etapa não avançar.
