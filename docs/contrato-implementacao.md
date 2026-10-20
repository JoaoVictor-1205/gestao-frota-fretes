# Contrato interno da implementação

Aplicação desktop Java 17 / Swing, sem servidor HTTP. O documento da etapa 1 em `docs/` é a fonte dos requisitos. Os pacotes seguem `app`, `model`, `dao`, `controller`, `view` (sem prefixo adicional).

## Modelos imutáveis

`Entidade extends java.io.Serializable`: `int id()` e `Entidade comId(int id)` (cada record retorna o próprio tipo covariantemente).

```java
Motorista(int id, String nome, String cpf, String cnh, String categoriaCnh, String telefone, boolean ativo)
Veiculo(int id, String placa, String modelo, int ano, BigDecimal capacidadeKg, String status)
Cliente(int id, String nome, String cpfCnpj, String telefone, String endereco)
Viagem(int id, int motoristaId, int veiculoId, int clienteId, String origem, String destino,
       LocalDate dataSaida, LocalDate dataChegada, BigDecimal valorFrete, String status)
Manutencao(int id, int veiculoId, LocalDate data, String tipo, String descricao, BigDecimal custo, int quilometragem)
```

Valores de situação usados tanto na View como na persistência:

- Veículo: `Disponível`, `Em viagem`, `Em manutenção`.
- Viagem: `Planejada`, `Em andamento`, `Concluída`, `Cancelada`.
- Manutenção: `Preventiva`, `Corretiva`.
- CNH: `A`, `B`, `AB`, `C`, `D`, `E`, `AC`, `AD`, `AE`.

## Persistência

`dao.Repositorio extends AutoCloseable`:

```java
<T extends Entidade> List<T> listar(Class<T> tipo);
<T extends Entidade> T salvar(T entidade);
void excluir(Class<? extends Entidade> tipo, int id);
<T> T transacao(java.util.concurrent.Callable<T> operacao);
String descricao();
void close(); // sem checked exception
```

- `ArquivoRepositorio(Path arquivo)` salva estado em arquivo local de forma atômica e transacional; `ArquivoRepositorio()` opera somente em memória para testes. Falha de leitura não pode apagar arquivo nem começar vazio silenciosamente.
- `JdbcRepositorio(String url, String usuario, String senha)` usa MySQL por Connector/J. Erros viram `PersistenciaException extends RuntimeException`. Não criar esquema automaticamente no início.
- Toda mutação de negócio ocorre dentro de `transacao`; rollback restaura estado em arquivo/memória e desfaz transação JDBC.
- No MySQL, as mutações usam `GET_LOCK` identificado pelo banco para serializar a validação e a gravação entre instâncias da aplicação. O bloqueio é liberado após commit ou rollback.
- SQL requer MySQL 8.0.16+, tabelas InnoDB e `utf8mb4`. Colunas geradas com índices únicos impedem viagens em andamento com motorista ou veículo repetidos. O DDL não é transacional; scripts idempotentes preservam dados e não convertem esquemas antigos.

## Controller

`controller.FrotaController(Repositorio repositorio)`:

```java
<T extends Entidade> List<T> listar(Class<T> tipo);
<T extends Entidade> T salvar(T entidade);
void excluir(Class<? extends Entidade> tipo, int id);
void cancelarViagem(int id);
List<Viagem> relatorio(LocalDate inicio, LocalDate fim, Integer clienteId);
String descricaoPersistencia();
void close();
```

Validação em `RegraNegocioException extends RuntimeException`. Campos obrigatórios, limites de tamanho do esquema, normalização de documentos/placas, dígitos de CPF/CNPJ, CNH de 11 dígitos, duplicidade, vínculos, valores, datas e regras da etapa 1. `BigDecimal` com no máximo duas casas e limite SQL. A situação do veículo é manual, conforme o PDF, mas iniciar uma viagem requer disponibilidade (edição da própria viagem em andamento continua permitida) e não pode conflitar com outra em andamento.

## Divisão de arquivos

- Modelos e controller + testes de negócio: agente de domínio.
- DAO, SQL e testes de persistência: agente de persistência.
- `app/`, `view/`, build/configuração e manual: agente principal.

Modo padrão: arquivo `data/frota.dat`. Modo MySQL: propriedade `app.modo=mysql` / variável `FROTA_MODO`. A URL padrão é `jdbc:mysql://localhost:3306/gestao_frota?connectionTimeZone=LOCAL`, com usuário `root` e senha vazia; credenciais locais ficam em `config.properties`, ignorado pelo Git. Credencial do administrador é configuração, sem tabela de usuários. Os scripts SQL criam exatamente cinco tabelas e preservam o histórico. `scripts/testar-mysql.ps1` executa a integração opcional em banco temporário isolado.
