package tests;

import dao.ArquivoRepositorio;
import dao.JdbcRepositorio;
import dao.PersistenciaException;
import dao.Repositorio;
import java.io.ObjectOutputStream;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;
import model.Cliente;
import model.Manutencao;
import model.Motorista;
import model.Veiculo;
import model.Viagem;

/** Testes locais executáveis com Java 17, sem bibliotecas externas ou banco instalado. */
public final class PersistenciaTest {
    private static int verificacoes;
    private static final LocalDate DIA = LocalDate.of(2026, 10, 16);
    private PersistenciaTest() { }

    public static void main(String[] argumentos) throws Exception {
        testarMemoria();
        testarTransacoes();
        testarArquivo();
        testarJdbcComDriverSimulado();
        System.out.println("PersistenciaTest: " + verificacoes + " verificações passaram (arquivo/memória e contrato JDBC com driver simulado).");
    }

    private static void testarMemoria() {
        try (Repositorio repositorio = new ArquivoRepositorio()) {
            Motorista m = repositorio.salvar(motorista("12345678901", "12345678901"));
            Veiculo v = repositorio.salvar(veiculo("ABC1D23"));
            Cliente c = repositorio.salvar(cliente("12345678901"));
            Viagem viagem = repositorio.salvar(viagem(m.id(), v.id(), c.id(), "Planejada"));
            Manutencao manutencao = repositorio.salvar(new Manutencao(0, v.id(), DIA, "Preventiva", "Troca de óleo", new BigDecimal("0.00"), 45000));
            verificar(m.id() == 1 && v.id() == 1 && c.id() == 1 && viagem.id() == 1 && manutencao.id() == 1, "IDs independentes por tabela");
            Motorista editado = repositorio.salvar(new Motorista(m.id(), "Nome alterado", m.cpf(), m.cnh(), m.categoriaCnh(), m.telefone(), false));
            verificar(repositorio.listar(Motorista.class).equals(List.of(editado)), "Edição sem duplicar registro");
            esperar(UnsupportedOperationException.class, () -> repositorio.listar(Motorista.class).clear());
            esperar(PersistenciaException.class, () -> repositorio.salvar(motorista(m.cpf(), "99999999999")));
            esperar(PersistenciaException.class, () -> repositorio.salvar(motorista("99999999999", m.cnh())));
            esperar(PersistenciaException.class, () -> repositorio.salvar(veiculo(v.placa())));
            esperar(PersistenciaException.class, () -> repositorio.salvar(cliente(c.cpfCnpj())));
            esperar(PersistenciaException.class, () -> repositorio.excluir(Motorista.class, m.id()));
            esperar(PersistenciaException.class, () -> repositorio.excluir(Veiculo.class, v.id()));
            esperar(PersistenciaException.class, () -> repositorio.excluir(Cliente.class, c.id()));
            verificar(repositorio.listar(Motorista.class).size() == 1, "Exclusão vinculada não altera estado");
            esperar(PersistenciaException.class, () -> repositorio.salvar(viagem(999, v.id(), c.id(), "Planejada")));
            esperar(PersistenciaException.class, () -> repositorio.salvar(viagem(m.id(), v.id(), c.id(), "Concluída")));
            Viagem emAndamento = repositorio.salvar(new Viagem(viagem.id(), m.id(), v.id(), c.id(), "Origem", "Destino", DIA, null, new BigDecimal("1200.50"), "Em andamento"));
            esperar(PersistenciaException.class, () -> repositorio.salvar(viagem(m.id(), v.id(), c.id(), "Em andamento")));
            verificar(repositorio.listar(Viagem.class).equals(List.of(emAndamento)), "Conflito em andamento preserva viagem original");
            repositorio.excluir(Viagem.class, viagem.id());
            repositorio.excluir(Manutencao.class, manutencao.id());
            repositorio.excluir(Motorista.class, m.id());
            repositorio.excluir(Veiculo.class, v.id());
            repositorio.excluir(Cliente.class, c.id());
            verificar(repositorio.listar(Viagem.class).isEmpty() && repositorio.listar(Veiculo.class).isEmpty(), "Exclusão permitida após remover vínculos");
            Veiculo zeros = repositorio.salvar(new Veiculo(0, "DEF4G56", "Modelo", 2026, new BigDecimal("1200.0000"), "Disponível"));
            verificar(zeros.id() == 2, "Zeros decimais excedentes não alteram precisão real");
            esperar(PersistenciaException.class, () -> repositorio.salvar(new Veiculo(0, "GHI7J89", "Modelo", 2026, new BigDecimal("1200.001"), "Disponível")));
            esperar(PersistenciaException.class, () -> repositorio.salvar(new Veiculo(0, "GHI7J89", "Modelo", 2026, new BigDecimal("100000000.00"), "Disponível")));
        }
        Repositorio fechado = new ArquivoRepositorio();
        fechado.close(); fechado.close();
        esperar(PersistenciaException.class, () -> fechado.listar(Cliente.class));
    }

    private static void testarTransacoes() {
        try (Repositorio repositorio = new ArquivoRepositorio()) {
            esperar(IllegalArgumentException.class, () -> repositorio.transacao(() -> {
                repositorio.salvar(cliente("12345678901"));
                throw new IllegalArgumentException("falha de negócio simulada");
            }));
            verificar(repositorio.listar(Cliente.class).isEmpty(), "Rollback de inclusão");
            Cliente c = repositorio.salvar(cliente("12345678901"));
            verificar(c.id() == 1, "Rollback restaura sequência de identificadores");
            esperar(PersistenciaException.class, () -> repositorio.transacao(() -> {
                repositorio.salvar(cliente("99999999999"));
                try { repositorio.transacao(() -> { throw new IllegalStateException("falha aninhada"); }); }
                catch (IllegalStateException esperada) { /* Mesmo capturada, marca rollback obrigatório. */ }
                return null;
            }));
            verificar(repositorio.listar(Cliente.class).equals(List.of(c)), "Transação aninhada falha integralmente");
            esperar(PersistenciaException.class, () -> repositorio.transacao(() -> {
                repositorio.salvar(cliente("99999999999"));
                try { repositorio.salvar(cliente(c.cpfCnpj())); }
                catch (PersistenciaException esperada) { /* Falha de constraint não pode ser ignorada. */ }
                return null;
            }));
            verificar(repositorio.listar(Cliente.class).equals(List.of(c)), "Erro capturado também impede commit parcial");
            esperar(IllegalStateException.class, () -> repositorio.transacao(() -> {
                repositorio.excluir(Cliente.class, c.id());
                throw new IllegalStateException("falha após exclusão");
            }));
            verificar(repositorio.listar(Cliente.class).equals(List.of(c)), "Rollback de exclusão");
        }
    }

    private static void testarArquivo() throws Exception {
        Path pasta = Files.createTempDirectory("frota-persistencia-test-");
        try {
            Path arquivo = pasta.resolve("frota.dat");
            Motorista m; Veiculo v; Cliente c; Viagem viagem; Manutencao manutencao;
            try (Repositorio repositorio = new ArquivoRepositorio(arquivo)) {
                m = repositorio.salvar(motorista("12345678901", "12345678901"));
                v = repositorio.salvar(veiculo("ABC1D23")); c = repositorio.salvar(cliente("12345678901"));
                viagem = repositorio.salvar(viagem(m.id(), v.id(), c.id(), "Planejada"));
                manutencao = repositorio.salvar(new Manutencao(0, v.id(), DIA, "Corretiva", "Reparo", new BigDecimal("320.00"), 1000));
                esperar(PersistenciaException.class, () -> new ArquivoRepositorio(arquivo));
                verificar(repositorio.listar(Cliente.class).equals(List.of(c)), "Tentativa concorrente mantém dados da sessão original");
            }
            try (Repositorio reaberto = new ArquivoRepositorio(arquivo)) {
                verificar(reaberto.listar(Motorista.class).equals(List.of(m)), "Reabre motoristas");
                verificar(reaberto.listar(Veiculo.class).equals(List.of(v)), "Reabre veículos e BigDecimal");
                verificar(reaberto.listar(Cliente.class).equals(List.of(c)), "Reabre clientes");
                verificar(reaberto.listar(Viagem.class).equals(List.of(viagem)), "Reabre viagens, LocalDate e chegada nula");
                verificar(reaberto.listar(Manutencao.class).equals(List.of(manutencao)), "Reabre manutenções");
                byte[] antes = Files.readAllBytes(arquivo);
                esperar(IllegalStateException.class, () -> reaberto.transacao(() -> {
                    reaberto.salvar(cliente("99999999999")); throw new IllegalStateException("rollback");
                }));
                verificar(Arrays.equals(antes, Files.readAllBytes(arquivo)), "Rollback não publica arquivo intermediário");
                Path backup = pasta.resolve("anterior.dat");
                Files.move(arquivo, backup); Files.createDirectory(arquivo); Files.writeString(arquivo.resolve("bloqueio.txt"), "impede troca por arquivo");
                esperar(PersistenciaException.class, () -> reaberto.salvar(cliente("99999999999")));
                verificar(reaberto.listar(Cliente.class).equals(List.of(c)), "Falha ao gravar também restaura memória");
                Files.delete(arquivo.resolve("bloqueio.txt")); Files.delete(arquivo); Files.move(backup, arquivo);
                Cliente segundo = reaberto.salvar(cliente("99999999999"));
                verificar(segundo.id() == 2, "Falha ao gravar também restaura sequência");
            }
            Path corrompido = pasta.resolve("corrompido.dat");
            byte[] invalido = new byte[] { 0, 1, 2, 3, 4 };
            Files.write(corrompido, invalido);
            esperar(PersistenciaException.class, () -> new ArquivoRepositorio(corrompido));
            esperar(PersistenciaException.class, () -> new ArquivoRepositorio(corrompido));
            verificar(Arrays.equals(invalido, Files.readAllBytes(corrompido)), "Falha de leitura preserva arquivo e libera trava");
            Path proibido = pasta.resolve("classe-proibida.dat");
            try (ObjectOutputStream saida = new ObjectOutputStream(Files.newOutputStream(proibido))) { saida.writeObject(new Date()); }
            byte[] antes = Files.readAllBytes(proibido);
            esperar(PersistenciaException.class, () -> new ArquivoRepositorio(proibido));
            verificar(Arrays.equals(antes, Files.readAllBytes(proibido)), "Filtro rejeita classes fora da lista sem apagar arquivo");
            try (var arquivos = Files.list(pasta)) { verificar(arquivos.noneMatch(p -> p.toString().endsWith(".tmp")), "Arquivos temporários removidos após falha"); }
        } finally {
            try (var caminhos = Files.walk(pasta)) {
                for (Path caminho : caminhos.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(caminho);
            }
        }
    }

    /** Valida protocolo JDBC/transações; não substitui integração com MySQL real. */
    private static void testarJdbcComDriverSimulado() throws Exception {
        DriverSimulado driver = new DriverSimulado();
        DriverManager.registerDriver(driver);
        try (Repositorio repositorio = new JdbcRepositorio(DriverSimulado.URL, "usuario", "senha")) {
            Motorista salvo = repositorio.salvar(new Motorista(0, "O'Connor; DROP TABLE motorista", "12345678901", "12345678901", "D", "11999999999", true));
            verificar(salvo.id() == 42, "JDBC retorna ID gerado pelo MySQL");
            Chamada insert = driver.chamadas.stream().filter(c -> c.sql.startsWith("INSERT")).findFirst().orElseThrow();
            verificar(!insert.sql.contains("O'Connor") && insert.parametros.get(1).equals(salvo.nome()), "JDBC parametriza conteúdo de texto");
            verificar(insert.chavesGeradas && !insert.sql.contains("RETURNING"), "Inclusão solicita chaves geradas pelo JDBC");
            verificar(driver.commits == 1 && driver.rollbacks == 0 && driver.autoCommit, "JDBC confirma e restaura autoCommit");
            verificar(driver.isolamento == Connection.TRANSACTION_READ_COMMITTED, "Isolamento JDBC explícito para validações após trava");
            verificar(driver.propriedades.getProperty("connectTimeout").equals("10000")
                    && driver.propriedades.getProperty("socketTimeout").equals("30000"), "Timeouts MySQL são expressos em milissegundos");
            verificar(driver.chamadas.stream().anyMatch(c -> c.sql.contains("GET_LOCK")), "Transação usa trava entre instâncias");
            verificar(driver.chamadas.stream().anyMatch(c -> c.sql.contains("RELEASE_LOCK")), "Commit libera trava da conexão MySQL");
            repositorio.salvar(salvo);
            Chamada update = driver.chamadas.stream().filter(c -> c.sql.startsWith("UPDATE")).findFirst().orElseThrow();
            verificar(update.parametros.get(7).equals(42) && update.sql.endsWith("WHERE id=?"), "Edição JDBC vincula ID no WHERE");
            driver.mutacaoSemMudanca = true;
            verificar(repositorio.salvar(salvo).equals(salvo), "Edição sem alterações aceita registro existente");
            verificar(driver.chamadas.stream().anyMatch(c -> c.sql.startsWith("SELECT id FROM motorista WHERE id=?")), "Zero alterações exige conferir a existência do registro");
            driver.registroExiste = false;
            esperar(PersistenciaException.class, () -> repositorio.salvar(salvo));
            driver.mutacaoSemMudanca = false;
            driver.registroExiste = true;
            int commits = driver.commits;
            int rollbacks = driver.rollbacks;
            esperar(IllegalStateException.class, () -> repositorio.transacao(() -> { repositorio.excluir(Motorista.class, 42); throw new IllegalStateException("falha simulada"); }));
            verificar(driver.rollbacks == rollbacks + 1 && driver.commits == commits && driver.autoCommit, "JDBC desfaz transação que falhou");
            driver.falharMutacao = true;
            esperar(PersistenciaException.class, () -> repositorio.salvar(motorista("12345678901", "12345678901")));
            verificar(driver.rollbacks == rollbacks + 2 && driver.commits == commits, "Violação SQL MySQL causa rollback");
            esperar(PersistenciaException.class, () -> repositorio.transacao(() -> {
                repositorio.salvar(motorista("99999999999", "99999999999"));
                try { repositorio.salvar(null); } catch (PersistenciaException esperada) { /* Callback tentou ignorar falha. */ }
                return null;
            }));
            verificar(driver.rollbacks == rollbacks + 3 && driver.commits == commits, "JDBC também impede commit parcial após erro capturado");
            long travasAntes = driver.chamadas.stream().filter(c -> c.sql.contains("GET_LOCK")).count();
            repositorio.transacao(() -> repositorio.transacao(() -> null));
            verificar(driver.chamadas.stream().filter(c -> c.sql.contains("GET_LOCK")).count() == travasAntes + 1,
                    "Transações aninhadas adquirem a trava uma única vez");
            verificar(driver.chamadas.stream().filter(c -> c.sql.contains("GET_LOCK")).count()
                    == driver.chamadas.stream().filter(c -> c.sql.contains("RELEASE_LOCK")).count(), "Commit e rollback liberam todas as travas adquiridas");
        } finally { DriverManager.deregisterDriver(driver); }
        verificar(driver.fechado, "JDBC encerra conexão");
        esperar(PersistenciaException.class, () -> new JdbcRepositorio("jdbc:postgresql://localhost/frota", "", ""));
    }

    private static Motorista motorista(String cpf, String cnh) { return new Motorista(0, "Motorista", cpf, cnh, "D", "11999999999", true); }
    private static Veiculo veiculo(String placa) { return new Veiculo(0, placa, "Caminhão", 2026, new BigDecimal("15000.00"), "Disponível"); }
    private static Cliente cliente(String documento) { return new Cliente(0, "Cliente", documento, "11999999999", "Rua Central, 100"); }
    private static Viagem viagem(int motorista, int veiculo, int cliente, String status) { return new Viagem(0, motorista, veiculo, cliente, "Origem", "Destino", DIA, null, new BigDecimal("1200.50"), status); }
    private static void verificar(boolean condicao, String mensagem) { verificacoes++; if (!condicao) throw new AssertionError(mensagem); }
    private static void esperar(Class<? extends Throwable> tipo, Runnable operacao) {
        verificacoes++;
        try { operacao.run(); }
        catch (Throwable ex) { if (tipo.isInstance(ex)) return; throw new AssertionError("Esperava " + tipo.getSimpleName() + ", recebeu " + ex, ex); }
        throw new AssertionError("Esperava " + tipo.getSimpleName());
    }

    private static final class Chamada {
        private final String sql;
        private final boolean chavesGeradas;
        private final Map<Integer, Object> parametros = new LinkedHashMap<>();
        private Chamada(String sql, boolean chavesGeradas) { this.sql = sql; this.chavesGeradas = chavesGeradas; }
    }
    private static final class DriverSimulado implements Driver {
        private static final String URL = "jdbc:mysql://teste-local-simulado/gestao_frota";
        private final List<Chamada> chamadas = new ArrayList<>();
        private Properties propriedades;
        private int commits, rollbacks, isolamento;
        private boolean fechado, autoCommit = true, falharMutacao, mutacaoSemMudanca, registroExiste = true;
        @Override public Connection connect(String url, Properties propriedades) {
            if (!acceptsURL(url)) return null;
            this.propriedades = propriedades;
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Connection.class }, (proxy, metodo, args) -> {
                return switch (metodo.getName()) {
                    case "setAutoCommit" -> { autoCommit = (boolean) args[0]; yield null; }
                    case "setTransactionIsolation" -> { isolamento = (int) args[0]; yield null; }
                    case "getAutoCommit" -> autoCommit;
                    case "commit" -> { commits++; yield null; }
                    case "rollback" -> { rollbacks++; yield null; }
                    case "close" -> { fechado = true; yield null; }
                    case "isClosed" -> fechado;
                    case "prepareStatement" -> preparar((String) args[0], args.length > 1 && args[1].equals(Statement.RETURN_GENERATED_KEYS));
                    default -> throw new UnsupportedOperationException(metodo.getName());
                };
            });
        }
        private PreparedStatement preparar(String sql, boolean chavesGeradas) {
            Chamada chamada = new Chamada(sql, chavesGeradas); chamadas.add(chamada);
            return (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { PreparedStatement.class }, (proxy, metodo, args) -> {
                if (metodo.getName().startsWith("set")) { chamada.parametros.put((Integer) args[0], args[1]); return null; }
                return switch (metodo.getName()) {
                    case "executeQuery" -> resultado(sql.contains("GET_LOCK") || sql.contains("RELEASE_LOCK") ? 1 : 42,
                            !sql.startsWith("SELECT id FROM") || registroExiste);
                    case "getGeneratedKeys" -> resultado(42, true);
                    case "executeUpdate" -> {
                        if (falharMutacao && (sql.startsWith("INSERT") || sql.startsWith("UPDATE"))) { falharMutacao = false; throw new SQLException("duplicidade simulada", "23000", 1062); }
                        yield sql.startsWith("UPDATE") && mutacaoSemMudanca ? 0 : 1;
                    }
                    case "execute" -> true;
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException(metodo.getName());
                };
            });
        }
        private ResultSet resultado(int valor, boolean existe) {
            boolean[] lido = { false };
            return (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { ResultSet.class }, (proxy, metodo, args) -> switch (metodo.getName()) {
                case "next" -> { boolean proximo = existe && !lido[0]; lido[0] = true; yield proximo; }
                case "getInt" -> valor;
                case "wasNull" -> false;
                case "close" -> null;
                default -> throw new UnsupportedOperationException(metodo.getName());
            });
        }
        @Override public boolean acceptsURL(String url) { return URL.equals(url); }
        @Override public DriverPropertyInfo[] getPropertyInfo(String url, Properties propriedades) { return new DriverPropertyInfo[0]; }
        @Override public int getMajorVersion() { return 1; }
        @Override public int getMinorVersion() { return 0; }
        @Override public boolean jdbcCompliant() { return false; }
        @Override public Logger getParentLogger() { return Logger.getGlobal(); }
    }
}
