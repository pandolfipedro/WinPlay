# Solução de problemas

## O iPhone não aparece

Confirme o pareamento nas configurações de Bluetooth do Windows. Volte ao WinPlay e clique em **Atualizar**. Se houver vários rádios Bluetooth ativos, mantenha apenas o rádio destinado à conexão: a implementação atual exige um único endereço de rádio local para iniciar a sessão.

## Falha ao abrir RFCOMM

Confirme o pareamento, o Bluetooth ativo e a disponibilidade do telefone. Encerre outras instâncias do receptor antes de uma nova tentativa. Abra **Ferramentas** para consultar o estado do Bluetooth e executar o teste de enlace fora de uma sessão ativa.

O código de erro registrado no histórico ajuda a distinguir falha de socket de uma etapa posterior de autenticação. Não remova os dados locais como primeira medida.

## RFCOMM e iAP2 concluídos, mas sem CarPlay

Essa mensagem confirma apenas as etapas iniciais. Confira se a autenticação avançou, se o telefone mostrou algum pedido e se o nome e a senha do Wi-Fi correspondem à rede atual. Confirme a seleção do IPv4 do PC e a comunicação entre os dois dispositivos.

Redes de convidados, isolamento de clientes e VPNs podem impedir o tráfego. Consulte o histórico para verificar se houve abertura da sessão de rede ou falha anterior. Salve o relatório antes de encerrar a tentativa.

## A sessão abre, mas o vídeo não chega

Observe a diferença entre sessão de rede ativa e recebimento do primeiro quadro. Verifique a qualidade do Wi-Fi e eventuais bloqueios de rede. Encerre a sessão, aguarde a liberação dos controles e tente novamente com o telefone próximo ao PC.

## Vídeo lento ou áudio interrompido

Confira o sinal do Wi-Fi, a carga de CPU e a quantidade de receptores abertos. Teste com uma única instância do WinPlay. Compare o comportamento na mesma rede e com os mesmos aplicativos do telefone para evitar conclusões causadas por condições diferentes.

## Siri não recebe voz

Confira o microfone padrão do Windows, seu nível de entrada e o acesso para aplicativos desktop. A inicialização local do codec não comprova captura física do microfone nem reconhecimento de voz pelo telefone.

## O aplicativo não abre

Confira se o ZIP foi extraído por inteiro e se `app/` e `runtime/` estão ao lado de `WinPlay.exe`. O pacote requer Windows x64. Uma atualização deve usar o conjunto completo de arquivos da versão.

## Relatar um problema

Informe versão do WinPlay, Windows, modelo do iPhone, iOS, etapa da conexão e passos de reprodução. Use **Salvar relatório** ou as opções de **Ferramentas**. Antes de compartilhar, remova nomes de dispositivos, SSIDs, endereços de rede e qualquer outro dado pessoal. Nunca envie identidades, certificados privados, senhas ou o diretório completo de dados locais.
