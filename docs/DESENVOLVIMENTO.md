# Desenvolvimento

## Ferramentas

- Windows x64 para compilação e execução do pacote Windows.
- JDK 21 x64, incluindo `javac`, `jar` e `jpackage`.
- Kotlin 2.2.10, distribuição com diretório `lib` do compilador.
- PowerShell.

As dependências Java estão em `lib/`. A procedência do toolchain está em `TOOLCHAIN.json`; os manifestos registram hashes das dependências e dos fontes de terceiros.

## Compilar e testar

Na raiz do repositório:

```powershell
.\build.ps1 -Jdk 'C:\ferramentas\jdk-21' -Kotlin 'C:\ferramentas\kotlinc'
```

O script compila a integração Java dos codecs, compila Kotlin, empacota o JAR e executa a descoberta dos testes JUnit, seguida dos autotestes locais do protocolo. Uma falha em qualquer etapa interrompe o build.

O APK usado no teste opcional de importação não é versionado. Para habilitar esse teste explicitamente, configure `DIPLAY_TEST_PUBLIC_APK` com um arquivo local válido da versão e hash especificados em `UPSTREAM.txt`. Os testes não fazem esse download automaticamente.

## Gerar o aplicativo portátil

```powershell
.\build.ps1 -Jdk 'C:\ferramentas\jdk-21' -Kotlin 'C:\ferramentas\kotlinc' -Package
```

O script gera o ícone e executa `jpackage --type app-image`. A saída fica em `build/package-<data-hora>/WinPlay/`. Esse diretório contém o executável, runtime, bibliotecas e avisos de licença. Distribua a pasta completa.

## Executar a partir das classes empacotadas

```powershell
& 'C:\ferramentas\jdk-21\bin\java.exe' -cp 'build/app/*' diplay.desktop.Desktop
```

O namespace interno e o nome do JAR são identificadores de implementação preservados. A apresentação do produto e o executável usam WinPlay.

## Gerar prévia e relatório

```powershell
.\WinPlay.exe --render-preview 'C:\pasta\winplay.png'
.\WinPlay.exe --diagnostics 'C:\pasta\diagnostico.txt'
```

A prévia renderiza a interface em estado inicial e encerra. Ela não representa uma sessão com iPhone. O relatório contém dados de ambiente; revise antes de compartilhar.

## Preparar uma entrega

1. Execute os testes e o empacotamento.
2. Copie a pasta completa do aplicativo para um diretório de entrega.
3. Compacte, extraia em uma pasta limpa e execute o EXE extraído.
4. Confira versão, títulos, ícone, layout, relatório e inicialização dos codecs.
5. Verifique a presença das licenças e a ausência de material privado.
6. Valide uma conexão física com o iPhone antes de anunciar compatibilidade da nova versão.
7. Gere hashes dos arquivos distribuídos e registre as condições de teste.

Consulte [Contribuição](../CONTRIBUTING.md) antes de abrir uma alteração.
