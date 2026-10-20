package dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import model.Cliente;
import model.Entidade;
import model.Manutencao;
import model.Motorista;
import model.Veiculo;
import model.Viagem;

/** MySQL via JDBC. O esquema deve ser criado pelos scripts de database/. */
public final class JdbcRepositorio implements Repositorio {
    private final Connection conexao;
    private boolean fechado;
    private boolean emTransacao;
    private boolean rollbackObrigatorio;
    private static final String NOME_TRAVA = "SHA2(CONCAT('gestao_frota:', DATABASE()), 256)";

    public JdbcRepositorio(String url, String usuario, String senha) {
        if (url == null || !url.startsWith("jdbc:mysql:"))
            throw new PersistenciaException("Configure uma URL JDBC do MySQL (jdbc:mysql://servidor:3306/gestao_frota).");
        Connection aberta = null;
        try {
            java.util.Properties propriedades = new java.util.Properties();
            if (usuario != null) propriedades.setProperty("user", usuario);
            if (senha != null) propriedades.setProperty("password", senha);
            propriedades.setProperty("connectTimeout", "10000");
            propriedades.setProperty("socketTimeout", "30000");
            propriedades.setProperty("useAffectedRows", "false");
            aberta = DriverManager.getConnection(url, propriedades);
            aberta.setAutoCommit(true);
            aberta.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            conexao = aberta;
        } catch (SQLException ex) {
            if (aberta != null) try { aberta.close(); } catch (SQLException ignorada) { /* Inicialização incompleta. */ }
            if ("08001".equals(ex.getSQLState()) && ex.getMessage() != null && ex.getMessage().contains("No suitable driver"))
                throw new PersistenciaException("Driver MySQL não encontrado. Execute scripts/baixar-driver.ps1 e inicie novamente.", ex);
            throw new PersistenciaException("Não foi possível conectar ao MySQL. Confira o serviço, o banco gestao_frota e as credenciais.", ex);
        }
    }

    @Override public synchronized <T extends Entidade> List<T> listar(Class<T> tipo) {
        verificarAberto();
        String tabela = tabela(tipo);
        try (PreparedStatement comando = conexao.prepareStatement("SELECT * FROM " + tabela + " ORDER BY id");
                ResultSet resultado = comando.executeQuery()) {
            List<T> lista = new ArrayList<>();
            while (resultado.next()) lista.add(tipo.cast(ler(tipo, resultado)));
            return List.copyOf(lista);
        } catch (SQLException ex) { throw falhaSql(ex); }
        catch (RuntimeException ex) { rollbackObrigatorio = true; throw ex; }
    }

    @Override public synchronized <T extends Entidade> T salvar(T entidade) {
        if (!emTransacao) return transacao(() -> salvar(entidade));
        verificarAberto();
        try {
        if (entidade == null) throw new PersistenciaException("Informe o registro a salvar.");
        if (entidade.id() < 0) throw new PersistenciaException("Identificador inválido.");
        String tabela = tabela(entidade.getClass());
        String[] colunas = colunas(entidade.getClass());
        String sql;
        if (entidade.id() == 0) {
            sql = "INSERT INTO " + tabela + " (" + String.join(",", colunas) + ") VALUES ("
                    + String.join(",", java.util.Collections.nCopies(colunas.length, "?")) + ")";
        } else {
            sql = "UPDATE " + tabela + " SET "
                    + String.join(",", java.util.Arrays.stream(colunas).map(c -> c + "=?").toList())
                    + " WHERE id=?";
        }
        try (PreparedStatement comando = conexao.prepareStatement(sql,
                entidade.id() == 0 ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS)) {
            vincular(comando, entidade);
            if (entidade.id() != 0) comando.setInt(colunas.length + 1, entidade.id());
            int alterados = comando.executeUpdate();
            if (entidade.id() != 0) {
                // Alguns clientes MySQL contam somente linhas modificadas, mesmo quando o ID existe.
                if (alterados == 0 && !existe(tabela, entidade.id()))
                    throw new PersistenciaException("O registro não existe mais. Atualize a tela.");
                return entidade;
            }
            try (ResultSet resultado = comando.getGeneratedKeys()) {
                if (alterados != 1 || !resultado.next() || resultado.getInt(1) <= 0)
                    throw new PersistenciaException("O MySQL não retornou o identificador do registro salvo.");
                @SuppressWarnings("unchecked") T salvo = (T) entidade.comId(resultado.getInt(1));
                return salvo;
            }
        } catch (SQLException ex) { throw falhaSql(ex); }
        } catch (RuntimeException ex) { rollbackObrigatorio = true; throw ex; }
    }

    private boolean existe(String tabela, int id) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement("SELECT id FROM " + tabela + " WHERE id=?")) {
            comando.setInt(1, id);
            try (ResultSet resultado = comando.executeQuery()) { return resultado.next(); }
        }
    }

    @Override public synchronized void excluir(Class<? extends Entidade> tipo, int id) {
        if (!emTransacao) { transacao(() -> { excluir(tipo, id); return null; }); return; }
        verificarAberto();
        try (PreparedStatement comando = conexao.prepareStatement("DELETE FROM " + tabela(tipo) + " WHERE id=?")) {
            comando.setInt(1, id);
            if (comando.executeUpdate() == 0) throw new PersistenciaException("O registro não existe mais. Atualize a tela.");
        } catch (SQLException ex) { throw falhaSql(ex); }
        catch (RuntimeException ex) { rollbackObrigatorio = true; throw ex; }
    }

    @Override public synchronized <T> T transacao(Callable<T> operacao) {
        verificarAberto();
        if (operacao == null) throw new PersistenciaException("Informe a operação da transação.");
        if (emTransacao) {
            try { return operacao.call(); }
            catch (Exception ex) { rollbackObrigatorio = true; throw converter(ex); }
        }
        emTransacao = true;
        rollbackObrigatorio = false;
        boolean travaObtida = false;
        try {
            conexao.setAutoCommit(false);
            // Serializa as validações e escritas das instâncias do aplicativo no mesmo banco.
            // Leituras seguintes em READ COMMITTED enxergam o commit da instância anterior.
            try (PreparedStatement trava = conexao.prepareStatement("SELECT GET_LOCK(" + NOME_TRAVA + ", 10)");
                    ResultSet resultado = trava.executeQuery()) {
                if (!resultado.next() || resultado.getInt(1) != 1)
                    throw new PersistenciaException("O banco está ocupado com outra operação. Tente novamente.");
                travaObtida = true;
            }
            T resultado = operacao.call();
            if (rollbackObrigatorio) throw new PersistenciaException("A transação foi cancelada por um erro anterior. Nenhuma alteração foi salva.");
            conexao.commit();
            return resultado;
        } catch (Exception ex) {
            rollback(ex);
            throw converter(ex);
        } catch (Error erro) {
            rollback(erro);
            throw erro;
        } finally {
            emTransacao = false;
            rollbackObrigatorio = false;
            // GET_LOCK pertence à sessão: commit/rollback não libera essa trava no MySQL.
            try {
                if (!conexao.isClosed()) {
                    if (travaObtida) {
                        try (PreparedStatement liberar = conexao.prepareStatement("SELECT RELEASE_LOCK(" + NOME_TRAVA + ")");
                                ResultSet resultado = liberar.executeQuery()) {
                            if (!resultado.next() || resultado.getInt(1) != 1)
                                throw new SQLException("Não foi possível liberar a trava da sessão MySQL.");
                        }
                    }
                    conexao.setAutoCommit(true);
                }
            }
            catch (SQLException ex) { fechado = true; try { conexao.close(); } catch (SQLException ignorada) { /* Conexão inutilizável. */ } }
        }
    }

    private void rollback(Throwable causa) {
        try { conexao.rollback(); }
        catch (SQLException ex) { causa.addSuppressed(ex); fechado = true; try { conexao.close(); } catch (SQLException ignorada) { /* Conexão inutilizável. */ } }
    }

    private RuntimeException converter(Exception ex) {
        if (ex instanceof SQLException sql) return falhaSql(sql);
        return ex instanceof RuntimeException re ? re : new PersistenciaException("Não foi possível concluir a transação.", ex);
    }

    private PersistenciaException falhaSql(SQLException ex) {
        if (emTransacao) rollbackObrigatorio = true;
        String mensagem = switch (ex.getErrorCode()) {
            case 1062 -> "Já existe registro com o mesmo CPF, CNH, CPF/CNPJ ou placa, ou uma viagem em andamento para esse motorista/veículo.";
            case 1451, 1452 -> "O registro está vinculado ao histórico de viagens/manutenções, ou o vínculo informado não existe. Preserve os registros relacionados.";
            case 1048, 1264, 1292, 1364, 1366, 1406, 3819 -> "Os dados não atendem aos limites do banco. Confira os campos obrigatórios, valores, datas e situações.";
            case 1049, 1054, 1146 -> "O esquema MySQL está ausente ou incompatível. Execute scripts/criar-banco.ps1 para preparar o banco gestao_frota.";
            case 1205, 1213, 3058 -> "O banco está ocupado com outra operação. Tente novamente.";
            default -> "Não foi possível acessar o MySQL. Confira a conexão e tente novamente.";
        };
        return new PersistenciaException(mensagem, ex);
    }

    /** Todos os identificadores SQL vêm desta lista fixa, nunca de entrada do usuário. */
    private static String tabela(Class<? extends Entidade> tipo) {
        if (tipo == Motorista.class) return "motorista";
        if (tipo == Veiculo.class) return "veiculo";
        if (tipo == Cliente.class) return "cliente";
        if (tipo == Viagem.class) return "viagem";
        if (tipo == Manutencao.class) return "manutencao";
        throw new PersistenciaException("Tipo de registro não suportado.");
    }
    private static String[] colunas(Class<? extends Entidade> tipo) {
        if (tipo == Motorista.class) return new String[] { "nome", "cpf", "cnh", "categoria_cnh", "telefone", "ativo" };
        if (tipo == Veiculo.class) return new String[] { "placa", "modelo", "ano", "capacidade_kg", "status" };
        if (tipo == Cliente.class) return new String[] { "nome", "cpf_cnpj", "telefone", "endereco" };
        if (tipo == Viagem.class) return new String[] { "motorista_id", "veiculo_id", "cliente_id", "origem", "destino", "data_saida", "data_chegada", "valor_frete", "status" };
        if (tipo == Manutencao.class) return new String[] { "veiculo_id", "data", "tipo", "descricao", "custo", "quilometragem" };
        throw new PersistenciaException("Tipo de registro não suportado.");
    }
    private static void vincular(PreparedStatement comando, Entidade entidade) throws SQLException {
        if (entidade instanceof Motorista m) {
            comando.setString(1, m.nome()); comando.setString(2, m.cpf()); comando.setString(3, m.cnh());
            comando.setString(4, m.categoriaCnh()); comando.setString(5, m.telefone()); comando.setBoolean(6, m.ativo());
        } else if (entidade instanceof Veiculo v) {
            comando.setString(1, v.placa()); comando.setString(2, v.modelo()); comando.setInt(3, v.ano());
            comando.setBigDecimal(4, v.capacidadeKg()); comando.setString(5, v.status());
        } else if (entidade instanceof Cliente c) {
            comando.setString(1, c.nome()); comando.setString(2, c.cpfCnpj()); comando.setString(3, c.telefone()); comando.setString(4, c.endereco());
        } else if (entidade instanceof Viagem v) {
            comando.setInt(1, v.motoristaId()); comando.setInt(2, v.veiculoId()); comando.setInt(3, v.clienteId());
            comando.setString(4, v.origem()); comando.setString(5, v.destino());
            data(comando, 6, v.dataSaida()); data(comando, 7, v.dataChegada());
            comando.setBigDecimal(8, v.valorFrete()); comando.setString(9, v.status());
        } else if (entidade instanceof Manutencao m) {
            comando.setInt(1, m.veiculoId()); data(comando, 2, m.data()); comando.setString(3, m.tipo());
            comando.setString(4, m.descricao()); comando.setBigDecimal(5, m.custo()); comando.setInt(6, m.quilometragem());
        }
    }
    private static void data(PreparedStatement comando, int indice, LocalDate data) throws SQLException {
        if (data == null) comando.setNull(indice, Types.DATE);
        else comando.setObject(indice, data);
    }
    private static Entidade ler(Class<? extends Entidade> tipo, ResultSet r) throws SQLException {
        int id = r.getInt("id");
        if (tipo == Motorista.class) return new Motorista(id, r.getString("nome"), r.getString("cpf"), r.getString("cnh"), r.getString("categoria_cnh"), r.getString("telefone"), r.getBoolean("ativo"));
        if (tipo == Veiculo.class) return new Veiculo(id, r.getString("placa"), r.getString("modelo"), r.getInt("ano"), r.getBigDecimal("capacidade_kg"), r.getString("status"));
        if (tipo == Cliente.class) return new Cliente(id, r.getString("nome"), r.getString("cpf_cnpj"), r.getString("telefone"), r.getString("endereco"));
        if (tipo == Viagem.class) return new Viagem(id, r.getInt("motorista_id"), r.getInt("veiculo_id"), r.getInt("cliente_id"), r.getString("origem"), r.getString("destino"), r.getObject("data_saida", LocalDate.class), r.getObject("data_chegada", LocalDate.class), r.getBigDecimal("valor_frete"), r.getString("status"));
        if (tipo == Manutencao.class) return new Manutencao(id, r.getInt("veiculo_id"), r.getObject("data", LocalDate.class), r.getString("tipo"), r.getString("descricao"), r.getBigDecimal("custo"), r.getInt("quilometragem"));
        throw new PersistenciaException("Tipo de registro não suportado.");
    }
    private void verificarAberto() { if (fechado) throw new PersistenciaException("A conexão MySQL já foi fechada."); }
    @Override public String descricao() { return "MySQL (JDBC)"; }
    @Override public synchronized void close() {
        if (emTransacao) throw new PersistenciaException("Aguarde o fim da transação antes de fechar os dados.");
        if (!fechado) {
            fechado = true;
            try { conexao.close(); }
            catch (SQLException ex) { throw new PersistenciaException("Não foi possível encerrar a conexão MySQL.", ex); }
        }
    }
}
