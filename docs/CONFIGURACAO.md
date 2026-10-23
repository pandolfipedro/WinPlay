# Configuração

## Bluetooth e Wi-Fi

O Bluetooth inicia o diálogo com o iPhone. A sessão de controle e as mídias utilizam a rede IP. Escolha um endereço IPv4 da interface que alcança o telefone. Adaptadores virtuais de VPN, máquinas virtuais e túneis podem aparecer na lista, mas nem sempre oferecem esse acesso.

O nome da rede é o SSID exato, com espaços e diferenças de maiúsculas preservados. Para WPA2, o campo de senha aceita de 8 a 63 caracteres ou uma chave de 64 dígitos hexadecimais. Campo vazio informa rede aberta; isso não equivale a descobrir automaticamente a configuração da rede.

O receptor tenta abrir a porta de controle 7000 e utiliza outra porta livre quando ela já está ocupada. Há portas adicionais negociadas para mídia. Não há criação automática de hotspot ou implementação de Wi-Fi Direct.

## Dados persistentes

Por compatibilidade, o diretório é `%LOCALAPPDATA%\DiPlayWindows`. Ele contém os arquivos locais de autenticação, identidade do receptor, pareamentos, cache de bibliotecas nativas e `connection-ui.properties`.

As preferências de interface armazenam apenas identificador do dispositivo, endereço de rede escolhido e SSID. A senha do Wi-Fi permanece na memória da janela e precisa ser informada nas próximas execuções.

Para testes isolados, a propriedade Java `diplay.data.dir` permite apontar para outro diretório. É uma opção de desenvolvimento; não é necessária no uso normal.

## Autenticação

Na primeira conexão, se não houver identidade válida no diretório local, o aplicativo baixa um artefato de origem pública, confere seu SHA-256 e importa apenas os arquivos necessários à autenticação. A origem e o hash estão documentados em [UPSTREAM.txt](../UPSTREAM.txt).

Esse material não acompanha o repositório nem deve ser incluído em relatórios ou commits. A autenticação é experimental e a aceitação por versões futuras do iOS depende de validação. Não há certificação Apple para este aplicativo.

## Áudio e microfone

A reprodução utiliza a saída de áudio disponível no Windows. Os comandos de voz precisam de um microfone utilizável pelo aplicativo. Se houver falha, confira o dispositivo padrão e as permissões de microfone para aplicativos desktop nas configurações do Windows.

## Linha de comando

```powershell
.\WinPlay.exe --diagnostics 'C:\pasta\diagnostico.txt'
.\WinPlay.exe --prepare-auth 'C:\pasta\autenticacao.txt'
```

Use uma pasta existente com permissão de escrita. O executável não abre console; leia o arquivo solicitado. `--prepare-auth` pode acessar a internet e alterar o diretório de autenticação. `--diagnostics` executa verificações locais e consulta rede e Bluetooth; não inicia uma sessão física de CarPlay.
