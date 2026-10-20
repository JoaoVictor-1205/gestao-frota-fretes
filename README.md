# Gestão de Frota e Fretes

Aplicação **desktop em Java 17 e Swing**, organizada em MVC e DAO, implementada a partir dos oito protótipos de `docs/Atividade_Gestao_Frota_Fretes_Etapa1.pdf`.

É possível trabalhar **sem instalar um banco de dados agora**. O modo padrão grava os cadastros em arquivo local. A mesma aplicação também oferece persistência relacional em **MySQL**, com scripts para criar o banco e as cinco tabelas na máquina de execução.

## Executar agora, sem banco

Requisito: **JDK 17 ou superior**, com `java`, `javac` e `jar`. O modo local não precisa de Maven, internet, MySQL ou bibliotecas adicionais. No Windows, os scripts usam `JAVA_HOME` ou procuram os JDKs 17/21 em `C:\Program Files\Java` antes de consultar o PATH.

Abra o PowerShell na raiz deste repositório (a pasta que contém este README e `src/`):

```powershell
.\scripts\executar.ps1
```

Primeiro acesso de demonstração:

- **Usuário:** `administrador`
- **Senha:** `admin123`

Para trocar a credencial e outras opções:

```powershell
Copy-Item config.example.properties config.properties
```

Edite `admin.usuario` e `admin.senha`. Esse arquivo é ignorado pelo Git. Uma variável de ambiente `FROTA_ADMIN_USUARIO` ou `FROTA_ADMIN_SENHA` prevalece sobre o arquivo. A credencial pertence ao único administrador previsto na atividade; não existe tabela de usuários. A configuração da senha em texto é adequada para a demonstração acadêmica local; mantenha o arquivo privado e troque a senha ao usar dados reais.

Para iniciar com dados fictícios:

```powershell
.\scripts\executar.ps1 -Demo
```

Esse comando usa **`data/demo.dat`**, separado de **`data/frota.dat`**, e cria os exemplos somente quando os cadastros ainda estão vazios. Ele sempre usa arquivo local, mesmo que `app.modo=mysql` esteja configurado. Os exemplos seguem o período do mês atual e contêm documentos fictícios com dígitos verificadores válidos, para passar pelas mesmas validações do formulário.

Se a política de execução do PowerShell bloquear os scripts locais, é possível executar somente esse comando em um processo autorizado:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\executar.ps1 -Demo
```

Linux/macOS com JDK 17+:

```sh
sh scripts/executar.sh --demo
```

## Telas e funcionamento

| Tela | Operações disponíveis |
| --- | --- |
| Login | Autenticação configurável, senha oculta, mensagem de acesso inválido e encerramento do programa. |
| Menu principal | Acessos às seis áreas, indicadores operacionais e navegação por barra de menu ou menu lateral. Sair retorna ao login. |
| Motoristas | Incluir, consultar, buscar, selecionar para editar, excluir quando permitido e inativar. |
| Veículos | Incluir, editar e buscar por placa/modelo; capacidade em kg e situação manual. |
| Clientes | Incluir, editar e buscar por nome/documento; CPF ou CNPJ validado. |
| Viagens / Fretes | Escolher cliente/motorista/veículo, registrar rota, datas, valor e situação; cancelar após confirmação mantendo histórico. |
| Manutenção | Registrar e editar serviços, custo e quilometragem; excluir após confirmação e filtrar o histórico por veículo. |
| Relatório de fretes | Filtrar pela data de saída e opcionalmente por cliente; apresentar quantidade, valores e total sem viagens canceladas. |

Nos cadastros, **Novo** e **Limpar** reiniciam o formulário. O código é gerado ao salvar. Selecionar uma linha carrega seus dados para edição; **Salvar** atualiza esse registro. **Atualizar lista** recarrega dados e vínculos. A busca local é automática durante a digitação e ignora acentos e pontuação, inclusive nos documentos. Apagar a seleção pelo filtro também limpa o formulário para evitar salvar uma cópia indevida de um registro anteriormente selecionado.

Datas usam `dd/mm/aaaa`. Valores aceitam o padrão brasileiro, por exemplo `1.500,00`; quilometragem aceita `85.000`. O sistema usa `BigDecimal`, sem arredondar silenciosamente valores com precisão inválida.

CPF, CPF/CNPJ, telefone, placa e datas recebem máscara durante a digitação e a colagem. O campo de cliente alterna automaticamente entre CPF e CNPJ; telefone aceita fixo ou celular com DDD, e placa aceita os padrões antigo e Mercosul. CNH admite onze dígitos e ano admite quatro. Valores, capacidade e quilometragem são formatados ao sair do campo, preservando o valor informado. Limpar o formulário mantém a chegada opcional vazia. Telefones já cadastrados em outros formatos são preservados ao editar o cadastro.

## Modos de persistência

| `app.modo` / `FROTA_MODO` | Comportamento |
| --- | --- |
| `arquivo` (padrão) | Salva em `data/frota.dat`, preservando dados após fechar o programa. |
| `memoria` | Dados temporários, descartados ao encerrar; útil para experimentos. |
| `mysql` | Salva e consulta as cinco tabelas no MySQL por JDBC. |

O arquivo local é uma alternativa para desenvolver e demonstrar o sistema sem servidor. Ele **não substitui a entrega do banco relacional** quando ela for exigida pelo professor. Para backup local, feche a aplicação e copie o arquivo `data/frota.dat`. A gravação é transacional por substituição atômica; em caso de falha, o estado anterior é preservado. Duas instâncias não podem abrir simultaneamente o mesmo arquivo. O carregamento rejeita arquivos incompatíveis sem apagá-los ou começar do zero silenciosamente.

**Trocar de modo não migra os cadastros.** Arquivo, memória e MySQL são armazenamentos separados. Os scripts SQL criam uma base vazia e não importam `frota.dat` nem adicionam dados de demonstração.

## Criar o MySQL na máquina de execução

O script cria o **banco e suas tabelas**. O servidor MySQL e o cliente `mysql` precisam estar instalados e o serviço precisa estar iniciado; o script não instala o SGBD. Use **MySQL 8.0.16 ou superior**, com tabelas InnoDB e codificação `utf8mb4`.

1. Copie `config.example.properties` para `config.properties`, caso ainda não tenha feito, e configure sua senha local:

   ```properties
   app.modo=mysql
   admin.usuario=administrador
   admin.senha=troque-esta-senha
   db.url=jdbc:mysql://localhost:3306/gestao_frota?connectionTimeZone=LOCAL
   db.usuario=root
   db.senha=sua-senha-do-mysql
   ```

   `config.properties` é ignorado pelo Git. O exemplo versionado mantém `db.senha` vazia; preencha a senha somente na configuração local.

2. Crie o banco e o esquema:

   ```powershell
   .\scripts\criar-banco.ps1
   ```

   O script procura `mysql.exe` no PATH e nas instalações em `C:\Program Files\MySQL`. Se necessário, informe seu caminho real, por exemplo:

   ```powershell
   .\scripts\criar-banco.ps1 -Mysql 'C:\Program Files\MySQL\MySQL Server 9.2\bin\mysql.exe'
   ```

   Servidor/porta/usuário são configuráveis com `-Servidor`, `-Porta` e `-Usuario`, com padrões `localhost`, `3306` e `root`. O usuário precisa de permissão para criar o banco. O script lê a senha de `FROTA_DB_SENHA` ou de `config.properties`, usa `MYSQL_PWD` apenas durante a execução do cliente e solicita a senha se ela estiver ausente.

3. Baixe o driver oficial JDBC (a internet só é necessária para esse download):

   ```powershell
   .\scripts\baixar-driver.ps1
   ```

   O driver é `com.mysql:mysql-connector-j:9.2.0`, salvo em `lib/`. Também é possível colocar manualmente o JAR oficial nessa pasta. Não é necessário baixá-lo para o modo local. Referência: [instalação do MySQL Connector/J](https://dev.mysql.com/doc/connector-j/en/connector-j-installing.html).

4. Inicie sem `-Demo`:

   ```powershell
   .\scripts\executar.ps1
   ```

Alternativa: abra o cliente `mysql` na raiz do projeto, no Windows/Linux/macOS:

```sh
mysql -h localhost -P 3306 -u root -p
```

No prompt do cliente, execute:

```sql
SOURCE database/00-criar-banco.sql;
USE gestao_frota;
SOURCE database/01-schema.sql;
```

`00-criar-banco.sql` usa `CREATE DATABASE IF NOT EXISTS`. Selecione o banco com `USE gestao_frota` antes de executar `01-schema.sql`, que cria as tabelas com `IF NOT EXISTS`. O DDL do MySQL não é transacional: uma falha pode deixar parte das tabelas criada, e os scripts podem ser reexecutados preservando os dados. Eles não atualizam automaticamente esquemas antigos incompatíveis nem importam dados de PostgreSQL.

As tabelas são exatamente **motorista, veiculo, cliente, viagem e manutencao**. Há IDs gerados, `NOT NULL`, restrições de valores/datas/situações, campos únicos, chaves estrangeiras e índices para consulta. Entre os campos de cadastro, somente `viagem.data_chegada` aceita nulo. Colunas geradas auxiliares com índices únicos impedem repetir motorista ou veículo em viagens em andamento; elas ficam nulas para as demais situações. A tabela de viagens reúne o serviço e o valor do frete; o relatório é calculado sem uma sexta tabela. As exclusões vinculadas são bloqueadas com `ON DELETE RESTRICT`. Referência: [CREATE TABLE do MySQL](https://dev.mysql.com/doc/refman/8.0/en/create-table.html).

## Configuração

| Propriedade | Variável de ambiente | Padrão |
| --- | --- | --- |
| `app.modo` | `FROTA_MODO` | `arquivo` |
| `app.arquivo` | `FROTA_ARQUIVO` | `data/frota.dat` |
| `admin.usuario` | `FROTA_ADMIN_USUARIO` | `administrador` |
| `admin.senha` | `FROTA_ADMIN_SENHA` | `admin123` |
| `db.url` | `FROTA_DB_URL` | `jdbc:mysql://localhost:3306/gestao_frota?connectionTimeZone=LOCAL` |
| `db.usuario` | `FROTA_DB_USUARIO` | `root` |
| `db.senha` | `FROTA_DB_SENHA` | Vazia |

O caminho do arquivo é relativo ao diretório atual. Os scripts de execução entram na raiz do projeto para manter a localização previsível. Credenciais e dados locais estão no `.gitignore`.

## Organização e regras

```text
src/app/          Entrada, configuração e exemplos fictícios
src/view/         Painéis Swing, navegação, formulários e formatação
src/controller/   Coordenação e regras de negócio
src/model/        Cinco entidades imutáveis
src/dao/          Contrato, arquivo local/memória e JDBC MySQL
database/         Scripts de criação do banco e esquema
tests/            Testes executáveis sem bibliotecas externas
scripts/          Compilação, execução, testes e preparação do MySQL
```

O fluxo de uma gravação é **View → FrotaController → Repositorio → arquivo/MySQL**. A View converte entradas, o controller normaliza e valida, e o DAO salva dentro de uma transação. No MySQL, `GET_LOCK` serializa as mutações da aplicação no mesmo banco, com liberação após commit ou rollback. Consultas e gravações das telas rodam em `SwingWorker`, mantendo a thread de eventos livre durante operações de disco/rede.

Regras principais:

- CPF/CNPJ com dígitos verificadores; CNH com onze dígitos; documentos e placas únicos, armazenados sem pontuação e formatados para exibição.
- Motorista ativo nas novas viagens; veículo disponível ao iniciar. Não é permitido usar o mesmo motorista ou veículo em duas viagens em andamento.
- O histórico concluído pode ser corrigido preservando o motorista vinculado mesmo se ele foi inativado depois.
- A situação do veículo é **manual**, como no planejamento. Registrar manutenção, iniciar/concluir/cancelar viagem não altera automaticamente o veículo. Ajuste a situação na tela Veículos conforme a operação.
- Valores de frete/capacidade positivos; custo e quilometragem não negativos; valores/capacidade com até duas casas e limite de `99.999.999,99`, alinhado a `DECIMAL(10,2)`.
- Chegada opcional durante a viagem, sempre igual/posterior à saída, obrigatória para concluir.
- Motorista/cliente com viagens e veículo com viagens/manutenções não podem ser excluídos; cancelar uma viagem preserva esses vínculos.
- O relatório inclui as duas datas do período, usa **saída**, pode restringir cliente e exclui somente canceladas; planejadas/em andamento entram quando sua saída pertence ao intervalo. Não representa pagamento recebido ou lucro.

## Compilar, empacotar e testar

Sem Maven:

```powershell
.\scripts\compilar.ps1
.\scripts\testar.ps1
```

O JAR da aplicação é gerado em `build/gestao-frota-fretes.jar`. No modo local, execute na raiz:

```powershell
java -jar build\gestao-frota-fretes.jar
```

Para usar esse JAR com MySQL, inclua o driver no classpath:

```powershell
java -cp 'build\gestao-frota-fretes.jar;lib\*' app.Main
```

Com Maven instalado, `mvn package` também compila e gera `target/gestao-frota-fretes-1.0.0.jar` e `target/lib/`. Os testes deste projeto são programas Java executáveis; rode `scripts/testar.ps1` para executá-los (não dependem do ciclo Surefire/JUnit).

Verificação realizada nesta implementação:

- `RegraNegocioTest`: 62 verificações de validações, unicidade, histórico, conflitos e relatório.
- `PersistenciaTest`: 69 verificações de memória/arquivo, reabertura, exclusão vinculada, bloqueio concorrente, arquivo inválido, rollback e protocolo JDBC com driver simulado.
- `MascarasTest`: 114 verificações de digitação, colagem, seleção, cursor, limpeza, preenchimento dos cadastros, datas do relatório e formatação numérica.
- `InterfaceTest`: 61 verificações de login, formatação pt-BR, tabelas, seleção, filtros por veículo/data, ordenação numérica/cronológica e gravação/edição através de `SwingWorker`.
- `MySqlIntegracaoTest`: 36 verificações aprovadas no MySQL 9.2.0, cobrindo as cinco entidades, persistência, Unicode, restrições, rollback e concorrência entre conexões.
- As oito telas são renderizadas em `.validation/telas/`, inclusive uma janela compacta de viagens, para inspeção visual. Esses testes headless não exercitam o gerenciador de janelas, os diálogos nativos ou uma sessão real com mouse/teclado.

O teste opcional de integração usa um banco temporário isolado no servidor MySQL e o remove ao terminar. Ele precisa de permissão para criar e excluir esse banco; não usa as tabelas de `gestao_frota`:

```powershell
.\scripts\testar-mysql.ps1
```

O teste com driver simulado verifica o protocolo JDBC, mas a execução do SQL e a conectividade reais devem ser verificadas com esse teste de integração.

Para a conferência final na máquina de apresentação: execute os scripts de criação, configure o modo MySQL, cadastre motorista/veículo/cliente, registre uma viagem, reabra o programa para conferir persistência e gere o relatório. Depois cancele a viagem e confirme sua permanência no histórico e sua exclusão do relatório.
