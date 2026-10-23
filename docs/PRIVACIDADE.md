# Dados locais e privacidade

O WinPlay usa informações de Bluetooth e rede para estabelecer a conexão com o iPhone. O vídeo e o áudio recebidos são processados para reprodução no computador. O microfone, quando utilizado pela sessão, fornece áudio ao telefone.

## Armazenamento

O diretório `%LOCALAPPDATA%\DiPlayWindows` é preservado por compatibilidade. Ele armazena a autenticação, a identidade local, pareamentos, cache de bibliotecas e preferências. Não inclua esse diretório no Git.

As preferências da interface guardam o identificador do dispositivo, o endereço IP selecionado e o SSID. A senha do Wi-Fi não é persistida nesse arquivo. Outros componentes precisam manter o material de sessão necessário para a conexão.

## Acesso externo

A preparação inicial de autenticação pode baixar o artefato público descrito nos avisos de procedência, com verificação do hash fixado. A conexão do CarPlay utiliza Bluetooth e rede local. Essas operações devem ser diferenciadas ao investigar tráfego.

## Relatórios

Relatórios podem conter nomes de interfaces, endereços IP, estado do Bluetooth, horários e mensagens da sessão. Revise-os e oculte informações pessoais antes de publicar uma issue ou enviar o arquivo a outra pessoa.

Não publique senhas, identidades, certificados privados, arquivos de pareamento ou cópias completas do diretório local. Para suporte, prefira um trecho mínimo do erro e uma descrição dos passos de reprodução.
