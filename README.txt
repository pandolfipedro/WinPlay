WinPlay 0.3.1
Nova interface, com o motor de conexão da versão 0.2.0 preservado.

O QUE MUDOU
- Novo nome: WinPlay. Interface, executavel e nome exibido ao iPhone atualizados.
- Pasta de dados anterior mantida para reaproveitar autenticacao e preferencias.
- Tema escuro com detalhes em verde-água, controles consistentes e ícone próprio.
- Configuração lateral recolhível. Quando o vídeo começa, o CarPlay ganha espaço.
- Etapas de Bluetooth, autenticação e CarPlay; tempo de sessão contado a partir
  do primeiro quadro de vídeo recebido.
- Encerrar fica no cabeçalho durante a sessão, inclusive com a configuração oculta.
- Tela cheia com controles; F11 alterna e Esc retorna à janela.
- Histórico técnico recolhível, atualizado em lotes e com tamanho limitado.
- Movimentos do mouse agrupados para evitar fila de eventos; cliques nas barras
  externas da imagem são ignorados e a soltura é preservada fora da área de vídeo.
- Tentativas antigas não atualizam nem enviam controles para uma conexão nova.
- Encerramento em segundo plano; nova tentativa liberada após o trabalho de fechamento.
- Lembra iPhone, interface IP e nome do Wi-Fi. A senha não é gravada em preferências.

ABRIR
Feche a versão anterior, extraia o ZIP inteiro em uma pasta nova e execute
WinPlay.exe. Mantenha app/ e runtime/ ao lado do executável.
Não precisa instalar Java, FFmpeg ou bibliotecas adicionais.
A autenticação já preparada em %LOCALAPPDATA%\DiPlayWindows é reaproveitada.

CONECTAR
1. Coloque PC e iPhone na mesma rede Wi-Fi, com Bluetooth ligado e pareado.
2. No painel Vamos conectar, selecione seu iPhone e a Rede do PC correspondente
   à interface Wi-Fi utilizada. Evite escolher uma VPN ou rede de outro adaptador.
3. Informe NOME DO WI-FI e SENHA DO WI-FI. Para rede aberta, deixe a senha vazia.
   Mostrar senha é apenas uma opção de visualização; não grava a senha.
4. Clique em Conectar CarPlay ou use Ctrl+Enter.
5. Aceite as solicitações de CarPlay no iPhone. Se o Windows solicitar permissão
   no firewall, permita a comunicação na rede privada utilizada.
6. O campo de estado mostra a etapa real. A indicação Conectado aparece quando
   o receptor entrega o primeiro quadro de vídeo, não apenas ao abrir o Bluetooth.
7. Use o mouse para tocar e arrastar na imagem. Início, Voltar e Siri ficam abaixo
   do vídeo e são habilitados quando há vídeo recebido.
8. Quando o vídeo começar, o painel de conexão se recolhe. Conexão, no cabeçalho,
   abre ou fecha esse painel sem interromper a sessão.
9. Encerrar, no cabeçalho, fecha a sessão. Aguarde o encerramento antes de reconectar.
   Ctrl+Enter também inicia ou encerra quando a configuração não está ocupada.
10. Tela cheia fica no botão com quatro cantos abaixo da imagem; F11 faz o mesmo.
    Em tela cheia, Esc ou F11 retorna. Os controles continuam acessíveis.

RELATÓRIOS E FERRAMENTAS
O botão com ícone de documento abaixo do vídeo mostra ou oculta o histórico.
Salvar relatório exporta o histórico da tentativa, inclusive enquanto está oculto.
Ferramentas abre uma janela com diagnóstico do ambiente, preparação de identidade
ou teste isolado do enlace Bluetooth. Encerre o CarPlay antes de executar os testes.
Nenhum teste de enlace deve ser interpretado como prova de uma sessão completa.

FUNCIONAMENTO E VALIDAÇÃO
O usuário confirmou o funcionamento da versão 0.2.0 em iPhone 15 Pro Max / iOS 27.
Nesta versão, o protocolo, Bluetooth, sessão de rede e codecs foram mantidos.
A interface e seu gerenciamento de tentativas foram testados localmente; é necessário
conferir o uso da nova interface com o telefone real.
Inclui o fluxo sem fio por Same LAN, H.264, AAC/Opus/PCM, mouse e microfone/Siri.
USB, Wi-Fi Direct e criação automática de hotspot não fazem parte desta versão.

Rede com isolamento de clientes, bloqueio de multicast, VPN, WPA3-only ou captive
portal pode impedir a conexão. A interface utiliza IPv4, controle em 7000 com fallback
para porta livre, além das portas de mídia negociadas pela sessão.
Se o microfone não abrir, confira o acesso ao microfone para apps desktop no Windows.

AUTENTICAÇÃO
A preparação automática usa o APK público oficial DiPlay 0.2.21, conferido pelo hash:
916c0708beb1022d4ea9678ff6a46dfb6d234f707837a2cbd80d615e8e2bb2e3
https://github.com/shihabal3amri/DiPlay/releases/tag/v0.2.21
Nenhuma chave precisa ser fornecida pelo usuário. Se ainda não houver uma identidade
válida na pasta do app, a primeira conexão baixa e verifica o APK original.
A identidade é experimental e não foi provisionada pela Apple para este port;
a aceitação em futuras versões do iOS continua sujeita a testes.

DADOS LOCAIS
%LOCALAPPDATA%\DiPlayWindows armazena a identidade, o cache dos codecs e as preferências.
connection-ui.properties grava apenas identificador do dispositivo, IP escolhido e SSID.
A senha é mantida somente na memória da janela. Não apague a pasta de dados para atualizar.

BUILD
JDK 21 x64 e Kotlin 2.2.10, usando as bibliotecas fornecidas no ZIP de fonte:
  .\build.ps1 -Jdk 'C:\caminho\jdk-21' -Kotlin 'C:\caminho\kotlinc' -Package
O script compila Java/Kotlin, executa os testes, gera o ícone e empacota o aplicativo.
DIPLAY_TEST_PUBLIC_APK pode apontar para o APK oficial já baixado para executar
explicitamente o teste opcional de importação, que não faz download por conta própria.

LINHA DE COMANDO
  WinPlay.exe --diagnostics C:\caminho\diagnostico.txt
  WinPlay.exe --prepare-auth C:\caminho\autenticacao.txt
O executável não abre console; confira o arquivo solicitado.

Aplicativo independente, sem afiliação ou certificação Apple/BYD.
Consulte UPSTREAM.txt, licenses/ e os manifestos de hash para origem e licenças.