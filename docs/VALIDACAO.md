# Validação

## Registro da versão 0.3.1

| Verificação | Resultado |
| --- | --- |
| Compilação Java/Kotlin | Aprovada no Windows x64 |
| Testes JUnit | 112 testes aprovados |
| Autotestes locais de protocolo | 5 grupos aprovados |
| Executável extraído do ZIP portátil | Relatório e prévia de interface gerados |
| Codecs | H.264 e AAC inicializados; roundtrip local Opus/PCM aprovado |
| Identificação visual | Nome WinPlay, executável WinPlay.exe, versão 0.3.1 |
| Conexão física específica da 0.3.1 | Ainda não validada |

A base de conexão foi confirmada pelo usuário em iPhone 15 Pro Max com iOS 27 antes das mudanças de interface e marca. Os testes locais da nova versão não substituem uma sessão física.

## O que os testes cobrem

- Enquadramento, fragmentação e validação de mensagens.
- Negociação iAP2 entre motores locais.
- Comportamento de componentes de rede e codecs em ambiente local.
- Layout em tamanho normal e compacto.
- Navegação habilitada após indicação de vídeo.
- Descarte de retornos de tentativas antigas.
- Preferências sem armazenamento de senha.
- Limites e acesso concorrente ao histórico.
- Enquadramento da imagem e coordenadas do ponteiro.

## Aceite em dispositivo

Para cada versão candidata, registre Windows, rádio Bluetooth, rede, modelo de iPhone e iOS. Execute uma conexão completa; confirme vídeo, áudio, clique, Início, Voltar e Siri. Alterne tela cheia, encerre e reconecte. Compare a duração e a qualidade em uma sessão com tempo definido e registre qualquer falha.

Considere separadamente: consulta do Bluetooth, enlace RFCOMM, negociação iAP2, autenticação, sessão de rede, primeiro vídeo e mídias contínuas. Uma etapa aprovada não comprova as seguintes.
