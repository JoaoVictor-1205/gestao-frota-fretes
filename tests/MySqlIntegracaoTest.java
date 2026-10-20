package tests;

import app.Configuracao;
import dao.JdbcRepositorio;
import dao.PersistenciaException;
import dao.Repositorio;
import java.net.URI;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import model.Cliente;
import model.Manutencao;
import model.Motorista;
import model.Veiculo;
import model.Viagem;

/** Opt-in: usa credenciais locais e cria/remove somente um banco temporário exclusivo. */
public final class MySqlIntegracaoTest {
    private static final LocalDate DIA = LocalDate.of(2026, 10, 20);
    private static int verificacoes;
    private MySqlIntegracaoTest() { }

    public static void main(String[] argumentos) throws Exception {
        if (!Arrays.asList(argumentos).contains("--executar")) {
            System.out.println("MySqlIntegracaoTest: ignorado; execute scripts/testar-mysql.ps1 para testar um MySQL real.");
            return;
        }
        Configuracao configuracao = new Configuracao();
        String nome = "gestao_frota_test_" + UUID.randomUUID().toString().replace("-", "");
        verificar(nome.matches("gestao_frota_test_[a-f0-9]{32}"), "Nome temporário seguro");
        URI endereco = URI.create(configuracao.dbUrl().substring("jdbc:".length()));
        if (!"mysql".equals(endereco.getScheme()) || endereco.getRawAuthority() == null || endereco.getRawUserInfo() != null) {
            throw new IllegalArgumentException("Configure uma URL jdbc:mysql://servidor:porta/banco para a integração.");
        }
        String servidor = "jdbc:mysql://" + endereco.getRawAuthority() + "/";
        String parametros = endereco.getRawQuery() == null ? "" : "?" + endereco.getRawQuery();
        String url = servidor + nome + parametros;
        Properties propriedades = new Properties();
        propriedades.setProperty("user", configuracao.dbUsuario());
        propriedades.setProperty("password", configuracao.dbSenha());
        propriedades.setProperty("connectTimeout", "10000");
        propriedades.setProperty("socketTimeout", "30000");
        try (Connection administracao = DriverManager.getConnection(servidor + parametros, propriedades)) {
            boolean criado = false;
            try {
                try (Statement comando = administracao.createStatement()) {
                    comando.executeUpdate("CREATE DATABASE `" + nome + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_bin");
                    criado = true;
                }
                administracao.setCatalog(nome);
                aplicarEsquema(administracao);
                testarDados(url, configuracao, administracao);
                testarMutex(url, configuracao);
                testarExclusoes(url, configuracao);
                System.out.println("MySqlIntegracaoTest: " + verificacoes + " verificações passaram em um banco temporário isolado.");
            } finally {
                if (criado) {
                    // O nome validado foi gerado nesta execução; nunca usa o banco de config.properties.
                    try (Statement comando = administracao.createStatement()) {
                        comando.executeUpdate("DROP DATABASE `" + nome + "`");
                    }
                }
            }
        }
    }

    private static void aplicarEsquema(Connection conexao) throws Exception {
        String esquema = Files.readString(Path.of("database", "01-schema.sql"), StandardCharsets.UTF_8)
                .lines().filter(linha -> !linha.stripLeading().startsWith("--")).collect(Collectors.joining("\n"));
        try (Statement comando = conexao.createStatement()) {
            for (String trecho : esquema.split(";")) {
                String sql = trecho.trim();
                if (sql.isEmpty()) continue;
                if (!sql.matches("(?is)^(SET\\s+NAMES\\b.*|CREATE\\s+TABLE\\s+IF\\s+NOT\\s+EXISTS\\b.*)$")) {
                    throw new IllegalStateException("O teste aceita somente SET NAMES e CREATE TABLE IF NOT EXISTS no esquema.");
                }
                comando.execute(sql);
            }
        }
    }

    private static JdbcRepositorio abrir(String url, Configuracao configuracao) {
        return new JdbcRepositorio(url, configuracao.dbUsuario(), configuracao.dbSenha());
    }

    private static void testarDados(String url, Configuracao configuracao, Connection esquema) throws Exception {
        Motorista motorista;
        Veiculo veiculo;
        Cliente cliente;
        Viagem viagem;
        Manutencao manutencao;
        try (Repositorio repositorio = abrir(url, configuracao)) {
            motorista = repositorio.salvar(motorista("12345678901", "12345678901"));
            veiculo = repositorio.salvar(veiculo("ABC1D23"));
            cliente = repositorio.salvar(cliente("12345678901"));
            viagem = repositorio.salvar(viagem(motorista.id(), veiculo.id(), cliente.id(), "Planejada"));
            manutencao = repositorio.salvar(new Manutencao(0, veiculo.id(), DIA, "Preventiva", "Troca de óleo", new BigDecimal("250.50"), 1000));
            verificar(motorista.id() > 0 && veiculo.id() > 0 && cliente.id() > 0 && viagem.id() > 0 && manutencao.id() > 0,
                    "IDs gerados nas cinco tabelas");
            motorista = repositorio.salvar(new Motorista(motorista.id(), "José d'Ávila 🚚", motorista.cpf(), motorista.cnh(),
                    motorista.categoriaCnh(), motorista.telefone(), false));
            verificar(repositorio.salvar(motorista).equals(motorista), "Update sem mudanças mantém o registro");
            final Motorista motoristaSalvo = motorista;
            final Veiculo veiculoSalvo = veiculo;
            final Cliente clienteSalvo = cliente;
            esperarSql(1062, () -> repositorio.salvar(motorista(motoristaSalvo.cpf(), "99999999999")));
            esperarSql(1062, () -> repositorio.salvar(motorista("99999999999", motoristaSalvo.cnh())));
            esperarSql(1062, () -> repositorio.salvar(veiculo(veiculoSalvo.placa())));
            esperarSql(1062, () -> repositorio.salvar(cliente(clienteSalvo.cpfCnpj())));
            esperarSql(1451, () -> repositorio.excluir(Motorista.class, motoristaSalvo.id()));
            esperarSql(1451, () -> repositorio.excluir(Veiculo.class, veiculoSalvo.id()));
            esperarSql(1451, () -> repositorio.excluir(Cliente.class, clienteSalvo.id()));
            esperarSql(1452, () -> repositorio.salvar(viagem(Integer.MAX_VALUE, veiculoSalvo.id(), clienteSalvo.id(), "Planejada")));
            esperarSql(3819, () -> repositorio.salvar(new Veiculo(0, "DEF4G56", "Inválido", 2026, BigDecimal.ZERO, "Disponível")));
            esperarSql(3819, () -> repositorio.salvar(viagem(motoristaSalvo.id(), veiculoSalvo.id(), clienteSalvo.id(), "Concluída")));
            esperar(PersistenciaException.class, () -> repositorio.salvar(new Cliente(Integer.MAX_VALUE, "Ausente", "99999999999", "11999999999", "Rua")));
            esperar(IllegalStateException.class, () -> repositorio.transacao(() -> {
                repositorio.salvar(cliente("99999999999"));
                throw new IllegalStateException("rollback simulado");
            }));
            verificar(repositorio.listar(Cliente.class).equals(List.of(cliente)), "Rollback remove inclusão intermediária");
            esperar(PersistenciaException.class, () -> repositorio.transacao(() -> {
                repositorio.salvar(cliente("99999999999"));
                try { repositorio.salvar(cliente(clienteSalvo.cpfCnpj())); }
                catch (PersistenciaException esperada) { /* Não pode confirmar alterações anteriores. */ }
                return null;
            }));
            verificar(repositorio.listar(Cliente.class).equals(List.of(cliente)), "Constraint capturada também força rollback integral");
            esperar(PersistenciaException.class, () -> repositorio.transacao(() -> {
                repositorio.salvar(cliente("99999999999"));
                try { repositorio.transacao(() -> { throw new IllegalStateException("falha aninhada"); }); }
                catch (IllegalStateException esperada) { /* A transação externa continua marcada. */ }
                return null;
            }));
            verificar(repositorio.listar(Cliente.class).equals(List.of(cliente)), "Rollback de transação aninhada");
            aplicarEsquema(esquema);
            verificar(repositorio.listar(Cliente.class).equals(List.of(cliente)), "Reexecutar esquema preserva dados");
        }
        try (Repositorio reaberto = abrir(url, configuracao)) {
            verificar(reaberto.listar(Motorista.class).equals(List.of(motorista)), "Reabre motorista com acentos, emoji e boolean");
            verificar(reaberto.listar(Veiculo.class).equals(List.of(veiculo)), "Reabre veículo e BigDecimal");
            verificar(reaberto.listar(Cliente.class).equals(List.of(cliente)), "Reabre cliente");
            verificar(reaberto.listar(Viagem.class).equals(List.of(viagem)), "Reabre viagem e LocalDate com chegada nula");
            verificar(reaberto.listar(Manutencao.class).equals(List.of(manutencao)), "Reabre manutenção");
            Motorista outroMotorista = reaberto.salvar(motorista("99999999999", "99999999999"));
            Veiculo outroVeiculo = reaberto.salvar(veiculo("DEF4G56"));
            Viagem andamento = reaberto.salvar(viagem(motorista.id(), veiculo.id(), cliente.id(), "Em andamento"));
            final Motorista primeiroMotorista = motorista;
            final Veiculo primeiroVeiculo = veiculo;
            final Cliente primeiroCliente = cliente;
            esperarSql(1062, () -> reaberto.salvar(viagem(primeiroMotorista.id(), outroVeiculo.id(), primeiroCliente.id(), "Em andamento")));
            esperarSql(1062, () -> reaberto.salvar(viagem(outroMotorista.id(), primeiroVeiculo.id(), primeiroCliente.id(), "Em andamento")));
            reaberto.salvar(new Viagem(andamento.id(), andamento.motoristaId(), andamento.veiculoId(), andamento.clienteId(),
                    andamento.origem(), andamento.destino(), andamento.dataSaida(), DIA.plusDays(1), andamento.valorFrete(), "Concluída"));
            verificar(reaberto.salvar(viagem(motorista.id(), veiculo.id(), cliente.id(), "Em andamento")).id() > andamento.id(),
                    "Concluir viagem libera motorista e veículo para outra viagem");
            verificar(reaberto.salvar(viagem(motorista.id(), veiculo.id(), cliente.id(), "Planejada")).id() > 0,
                    "Índices condicionais permitem várias viagens fora de andamento");
        }
        // Força o modo que contabiliza apenas linhas alteradas para cobrir o fallback de existência.
        String urlAlteradas = url + (url.contains("?") ? "&" : "?") + "useAffectedRows=true";
        try (Repositorio alteradas = abrir(urlAlteradas, configuracao)) {
            verificar(alteradas.salvar(cliente).equals(cliente), "Update idêntico funciona com useAffectedRows=true");
        }
    }

    private static void testarMutex(String url, Configuracao configuracao) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch primeiraDentro = new CountDownLatch(1);
        CountDownLatch liberarPrimeira = new CountDownLatch(1);
        CountDownLatch segundaSolicitada = new CountDownLatch(1);
        CountDownLatch segundaDentro = new CountDownLatch(1);
        try (Repositorio primeira = abrir(url, configuracao); Repositorio segunda = abrir(url, configuracao)) {
            try {
                var primeiraOperacao = executor.submit(() -> primeira.transacao(() -> {
                    primeiraDentro.countDown();
                    if (!liberarPrimeira.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Timeout no teste da primeira transação.");
                    return null;
                }));
                verificar(primeiraDentro.await(5, TimeUnit.SECONDS), "Primeira conexão adquire trava");
                var segundaOperacao = executor.submit(() -> {
                    segundaSolicitada.countDown();
                    return segunda.transacao(() -> { segundaDentro.countDown(); return null; });
                });
                verificar(segundaSolicitada.await(5, TimeUnit.SECONDS), "Segunda conexão solicita transação");
                verificar(!segundaDentro.await(300, TimeUnit.MILLISECONDS), "Trava impede validações simultâneas entre conexões");
                liberarPrimeira.countDown();
                primeiraOperacao.get(5, TimeUnit.SECONDS);
                segundaOperacao.get(5, TimeUnit.SECONDS);
                verificar(segundaDentro.getCount() == 0, "Commit libera a trava para a segunda conexão");
            } finally {
                liberarPrimeira.countDown();
                executor.shutdownNow();
                executor.awaitTermination(5, TimeUnit.SECONDS);
            }
        } finally { executor.shutdownNow(); }
    }

    private static void testarExclusoes(String url, Configuracao configuracao) {
        try (Repositorio repositorio = abrir(url, configuracao)) {
            repositorio.transacao(() -> {
                for (Viagem registro : repositorio.listar(Viagem.class)) repositorio.excluir(Viagem.class, registro.id());
                for (Manutencao registro : repositorio.listar(Manutencao.class)) repositorio.excluir(Manutencao.class, registro.id());
                for (Motorista registro : repositorio.listar(Motorista.class)) repositorio.excluir(Motorista.class, registro.id());
                for (Veiculo registro : repositorio.listar(Veiculo.class)) repositorio.excluir(Veiculo.class, registro.id());
                for (Cliente registro : repositorio.listar(Cliente.class)) repositorio.excluir(Cliente.class, registro.id());
                return null;
            });
            verificar(repositorio.listar(Motorista.class).isEmpty() && repositorio.listar(Veiculo.class).isEmpty()
                    && repositorio.listar(Cliente.class).isEmpty() && repositorio.listar(Viagem.class).isEmpty()
                    && repositorio.listar(Manutencao.class).isEmpty(), "Exclusão das cinco entidades após remover vínculos");
        }
    }

    private static Motorista motorista(String cpf, String cnh) { return new Motorista(0, "Motorista", cpf, cnh, "D", "11999999999", true); }
    private static Veiculo veiculo(String placa) { return new Veiculo(0, placa, "Caminhão", 2026, new BigDecimal("15000.00"), "Disponível"); }
    private static Cliente cliente(String documento) { return new Cliente(0, "Cliente", documento, "11999999999", "Rua Central, 100"); }
    private static Viagem viagem(int motorista, int veiculo, int cliente, String status) { return new Viagem(0, motorista, veiculo, cliente, "São Paulo", "Joinville", DIA, null, new BigDecimal("1200.50"), status); }
    private static void verificar(boolean condicao, String mensagem) { verificacoes++; if (!condicao) throw new AssertionError(mensagem); }
    private static void esperar(Class<? extends Throwable> tipo, Runnable operacao) {
        verificacoes++;
        try { operacao.run(); }
        catch (Throwable ex) { if (tipo.isInstance(ex)) return; throw new AssertionError("Esperava " + tipo.getSimpleName(), ex); }
        throw new AssertionError("Esperava " + tipo.getSimpleName());
    }
    private static void esperarSql(int codigo, Runnable operacao) {
        verificacoes++;
        try { operacao.run(); }
        catch (PersistenciaException ex) {
            for (Throwable causa = ex; causa != null; causa = causa.getCause()) {
                if (causa instanceof SQLException sql && sql.getErrorCode() == codigo) return;
            }
            throw new AssertionError("Esperava erro MySQL " + codigo, ex);
        }
        throw new AssertionError("Esperava erro MySQL " + codigo);
    }
}
