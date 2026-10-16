package dao;

import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import model.Cliente;
import model.Entidade;
import model.Manutencao;
import model.Motorista;
import model.Veiculo;
import model.Viagem;

/**
 * Armazenamento local para executar sem servidor de banco de dados.
 * A trava acompanha toda a sessão e uma troca atômica publica cada transação.
 */
public final class ArquivoRepositorio implements Repositorio {
    private static final List<Class<? extends Entidade>> TIPOS = List.of(
            Motorista.class, Veiculo.class, Cliente.class, Viagem.class, Manutencao.class);
    private static final Set<String> VEICULO_STATUS = Set.of("Disponível", "Em viagem", "Em manutenção");
    private static final Set<String> VIAGEM_STATUS = Set.of("Planejada", "Em andamento", "Concluída", "Cancelada");
    private static final Set<String> CNH_CATEGORIAS = Set.of("A", "B", "AB", "C", "D", "E", "AC", "AD", "AE");
    private final Path arquivo;
    private FileChannel canalTrava;
    private FileLock trava;
    private Estado estado = new Estado();
    private boolean fechado;
    private boolean emTransacao;
    private boolean rollbackObrigatorio;

    /** Instância descartável, sem escrita em disco. */
    public ArquivoRepositorio() { arquivo = null; }

    public ArquivoRepositorio(Path arquivo) {
        if (arquivo == null) throw new PersistenciaException("Informe o caminho do arquivo de dados.");
        this.arquivo = arquivo.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.arquivo.getParent());
            Path caminhoTrava = this.arquivo.resolveSibling(this.arquivo.getFileName() + ".lock");
            canalTrava = FileChannel.open(caminhoTrava, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            try { trava = canalTrava.tryLock(); }
            catch (OverlappingFileLockException ex) { throw arquivoEmUso(ex); }
            if (trava == null) throw arquivoEmUso(null);
            if (Files.exists(this.arquivo)) carregar();
        } catch (IOException | RuntimeException ex) {
            liberarTrava();
            if (ex instanceof PersistenciaException pe) throw pe;
            throw new PersistenciaException("Não foi possível abrir os dados locais. Verifique o arquivo e as permissões.", ex);
        }
    }

    private PersistenciaException arquivoEmUso(Throwable causa) {
        return new PersistenciaException("O arquivo de dados já está em uso. Feche a outra instância do sistema antes de continuar.", causa);
    }

    private void carregar() {
        try (ObjectInputStream entrada = new ObjectInputStream(Files.newInputStream(arquivo))) {
            entrada.setObjectInputFilter(ArquivoRepositorio::filtrar);
            Object dados = entrada.readObject();
            if (!(dados instanceof Estado carregado) || carregado.versao != 1)
                throw new IOException("Formato de dados local desconhecido.");
            estado = carregado;
            validarEstado();
        } catch (IOException | ClassNotFoundException | RuntimeException ex) {
            throw new PersistenciaException("O arquivo de dados local está inválido ou incompatível. Preserve-o e restaure uma cópia válida.", ex);
        }
    }

    private static ObjectInputFilter.Status filtrar(ObjectInputFilter.FilterInfo info) {
        if (info.depth() > 30 || info.references() > 1_000_000 || info.streamBytes() > 50_000_000
                || info.arrayLength() > 1_000_000) return ObjectInputFilter.Status.REJECTED;
        Class<?> tipo = info.serialClass();
        if (tipo == null) return ObjectInputFilter.Status.UNDECIDED;
        if (tipo.isArray()) {
            Class<?> componente = tipo.getComponentType();
            return componente.isPrimitive() || componente == Map.Entry.class
                    ? ObjectInputFilter.Status.ALLOWED : ObjectInputFilter.Status.REJECTED;
        }
        boolean permitido = TIPOS.contains(tipo) || tipo == Estado.class || tipo == LinkedHashMap.class
                || tipo == java.util.HashMap.class || tipo == String.class || tipo == Integer.class
                || tipo == Number.class || tipo == BigDecimal.class || tipo == BigInteger.class
                || tipo == LocalDate.class || tipo.getName().equals("java.time.Ser");
        return permitido ? ObjectInputFilter.Status.ALLOWED : ObjectInputFilter.Status.REJECTED;
    }

    @Override public synchronized <T extends Entidade> List<T> listar(Class<T> tipo) {
        verificarAberto();
        List<T> lista = new ArrayList<>();
        tabela(tipo).values().forEach(entidade -> lista.add(tipo.cast(entidade)));
        return List.copyOf(lista);
    }

    @Override public synchronized <T extends Entidade> T salvar(T entidade) {
        if (!emTransacao) return transacao(() -> salvar(entidade));
        verificarAberto();
        try {
            if (entidade == null) throw new PersistenciaException("Informe o registro a salvar.");
            Class<? extends Entidade> tipo = entidade.getClass();
            LinkedHashMap<Integer, Entidade> tabela = tabela(tipo);
            int id = entidade.id();
            if (id < 0) throw new PersistenciaException("Identificador inválido.");
            if (id > 0 && !tabela.containsKey(id)) throw new PersistenciaException("O registro não existe mais. Atualize a tela.");
            if (id == 0) {
                int ultimo = estado.sequencias.get(tipo.getName());
                if (ultimo == Integer.MAX_VALUE) throw new PersistenciaException("Limite de registros atingido.");
                id = ultimo + 1;
                estado.sequencias.put(tipo.getName(), id);
            }
            @SuppressWarnings("unchecked") T salvo = (T) entidade.comId(id);
            tabela.put(id, salvo);
            validarEstado();
            return salvo;
        } catch (RuntimeException ex) { rollbackObrigatorio = true; throw ex; }
    }

    @Override public synchronized void excluir(Class<? extends Entidade> tipo, int id) {
        if (!emTransacao) { transacao(() -> { excluir(tipo, id); return null; }); return; }
        verificarAberto();
        try {
            if (tabela(tipo).remove(id) == null) throw new PersistenciaException("O registro não existe mais. Atualize a tela.");
            validarEstado();
        } catch (RuntimeException ex) { rollbackObrigatorio = true; throw ex; }
    }

    @Override public synchronized <T> T transacao(Callable<T> operacao) {
        verificarAberto();
        if (operacao == null) throw new PersistenciaException("Informe a operação da transação.");
        if (emTransacao) {
            try { return operacao.call(); }
            catch (Exception ex) { rollbackObrigatorio = true; throw converter(ex); }
        }
        Estado anterior = estado.copia();
        emTransacao = true;
        rollbackObrigatorio = false;
        try {
            T resultado = operacao.call();
            if (rollbackObrigatorio) throw new PersistenciaException("A transação foi cancelada por um erro anterior. Nenhuma alteração foi salva.");
            validarEstado();
            persistir();
            return resultado;
        } catch (Exception ex) {
            estado = anterior;
            throw converter(ex);
        } catch (Error erro) {
            estado = anterior;
            throw erro;
        } finally { emTransacao = false; rollbackObrigatorio = false; }
    }

    private RuntimeException converter(Exception ex) {
        return ex instanceof RuntimeException re ? re : new PersistenciaException("Não foi possível concluir a transação.", ex);
    }

    private void persistir() {
        if (arquivo == null) return;
        Path temporario = null;
        try {
            temporario = Files.createTempFile(arquivo.getParent(), arquivo.getFileName() + ".", ".tmp");
            try (FileChannel canal = FileChannel.open(temporario, StandardOpenOption.WRITE);
                    ObjectOutputStream saida = new ObjectOutputStream(Channels.newOutputStream(canal))) {
                saida.writeObject(estado);
                saida.flush();
                canal.force(true);
            }
            Files.move(temporario, arquivo, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new PersistenciaException("Não foi possível gravar os dados locais. A transação foi desfeita; verifique espaço e permissões.", ex);
        } finally {
            if (temporario != null) try { Files.deleteIfExists(temporario); } catch (IOException ignorada) { /* Não apaga dados anteriores. */ }
        }
    }

    private LinkedHashMap<Integer, Entidade> tabela(Class<? extends Entidade> tipo) {
        if (!TIPOS.contains(tipo)) throw new PersistenciaException("Tipo de registro não suportado.");
        return estado.tabelas.get(tipo.getName());
    }

    private void validarEstado() {
        if (estado.tabelas == null || estado.sequencias == null || estado.tabelas.size() != TIPOS.size()
                || estado.sequencias.size() != TIPOS.size()) throw new PersistenciaException("Estrutura de dados local inválida.");
        for (Class<? extends Entidade> tipo : TIPOS) {
            Map<Integer, Entidade> tabela = estado.tabelas.get(tipo.getName());
            Integer sequencia = estado.sequencias.get(tipo.getName());
            if (tabela == null || sequencia == null || sequencia < 0) throw new PersistenciaException("Estrutura de dados local inválida.");
            for (Map.Entry<Integer, Entidade> item : tabela.entrySet()) {
                Entidade entidade = item.getValue();
                if (entidade == null || entidade.getClass() != tipo || entidade.id() <= 0
                        || !item.getKey().equals(entidade.id()) || entidade.id() > sequencia)
                    throw new PersistenciaException("Identificador inválido nos dados locais.");
                validarEntidade(entidade);
            }
        }
    }

    private void validarEntidade(Entidade entidade) {
        if (entidade instanceof Motorista m) {
            texto(m.nome(), 100, "Nome do motorista"); digitos(m.cpf(), 11, "CPF"); digitos(m.cnh(), 11, "CNH");
            if (m.categoriaCnh() == null || !CNH_CATEGORIAS.contains(m.categoriaCnh())) falha("Categoria de CNH inválida.");
            texto(m.telefone(), 20, "Telefone");
            for (Entidade outro : tabela(Motorista.class).values()) if (outro.id() != m.id()) {
                Motorista existente = (Motorista) outro;
                if (existente.cpf().equals(m.cpf())) falha("Já existe motorista com esse CPF.");
                if (existente.cnh().equals(m.cnh())) falha("Já existe motorista com essa CNH.");
            }
        } else if (entidade instanceof Veiculo v) {
            texto(v.placa(), 7, "Placa");
            if (!v.placa().matches("[A-Z]{3}[0-9][A-Z0-9][0-9]{2}")) falha("Placa inválida.");
            texto(v.modelo(), 100, "Modelo");
            if (v.ano() < 1900 || v.ano() > 2100) falha("Ano deve estar entre 1900 e 2100.");
            valor(v.capacidadeKg(), true, "Capacidade");
            if (v.status() == null || !VEICULO_STATUS.contains(v.status())) falha("Situação do veículo inválida.");
            for (Entidade outro : tabela(Veiculo.class).values())
                if (outro.id() != v.id() && ((Veiculo) outro).placa().equals(v.placa())) falha("Já existe veículo com essa placa.");
        } else if (entidade instanceof Cliente c) {
            texto(c.nome(), 100, "Nome do cliente");
            if (c.cpfCnpj() == null || !c.cpfCnpj().matches("(?:[0-9]{11}|[0-9]{14})")) falha("CPF/CNPJ deve conter 11 ou 14 dígitos.");
            texto(c.telefone(), 20, "Telefone"); texto(c.endereco(), 200, "Endereço");
            for (Entidade outro : tabela(Cliente.class).values())
                if (outro.id() != c.id() && ((Cliente) outro).cpfCnpj().equals(c.cpfCnpj())) falha("Já existe cliente com esse CPF/CNPJ.");
        } else if (entidade instanceof Viagem v) {
            referencia(Motorista.class, v.motoristaId()); referencia(Veiculo.class, v.veiculoId()); referencia(Cliente.class, v.clienteId());
            texto(v.origem(), 100, "Origem"); texto(v.destino(), 100, "Destino");
            if (v.dataSaida() == null) falha("Informe a data de saída.");
            if (v.dataChegada() != null && v.dataChegada().isBefore(v.dataSaida())) falha("A chegada não pode ser anterior à saída.");
            valor(v.valorFrete(), true, "Valor do frete");
            if (v.status() == null || !VIAGEM_STATUS.contains(v.status())) falha("Situação da viagem inválida.");
            if ("Concluída".equals(v.status()) && v.dataChegada() == null) falha("Informe a chegada para concluir a viagem.");
            if ("Em andamento".equals(v.status())) for (Entidade outro : tabela(Viagem.class).values()) {
                Viagem existente = (Viagem) outro;
                if (existente.id() != v.id() && "Em andamento".equals(existente.status())
                        && (existente.veiculoId() == v.veiculoId() || existente.motoristaId() == v.motoristaId()))
                    falha("Veículo ou motorista já possui viagem em andamento.");
            }
        } else if (entidade instanceof Manutencao m) {
            referencia(Veiculo.class, m.veiculoId());
            if (m.data() == null) falha("Informe a data da manutenção.");
            if (m.tipo() == null || !Set.of("Preventiva", "Corretiva").contains(m.tipo())) falha("Tipo de manutenção inválido.");
            texto(m.descricao(), 255, "Descrição"); valor(m.custo(), false, "Custo");
            if (m.quilometragem() < 0) falha("Quilometragem não pode ser negativa.");
        }
    }

    private void referencia(Class<? extends Entidade> tipo, int id) {
        if (!tabela(tipo).containsKey(id)) falha("O registro possui vínculo ausente ou está sendo usado no histórico. Verifique as viagens e manutenções.");
    }
    private static void texto(String valor, int maximo, String campo) {
        if (valor == null || valor.isBlank() || valor.length() > maximo) falha(campo + " é obrigatório e aceita até " + maximo + " caracteres.");
    }
    private static void digitos(String valor, int tamanho, String campo) {
        if (valor == null || !valor.matches("[0-9]{" + tamanho + "}")) falha(campo + " deve conter " + tamanho + " dígitos.");
    }
    private static void valor(BigDecimal valor, boolean positivo, String campo) {
        if (valor == null || valor.stripTrailingZeros().scale() > 2 || valor.abs().compareTo(new BigDecimal("99999999.99")) > 0
                || (positivo ? valor.signum() <= 0 : valor.signum() < 0))
            falha(campo + " inválido. Use até 99.999.999,99 e duas casas decimais.");
    }
    private static void falha(String mensagem) { throw new PersistenciaException(mensagem); }
    private void verificarAberto() { if (fechado) throw new PersistenciaException("O armazenamento já foi fechado."); }

    @Override public String descricao() { return arquivo == null ? "Em memória (dados temporários)" : "Arquivo local: " + arquivo; }

    @Override public synchronized void close() {
        if (emTransacao) throw new PersistenciaException("Aguarde o fim da transação antes de fechar os dados.");
        if (!fechado) { fechado = true; liberarTrava(); }
    }
    private void liberarTrava() {
        if (trava != null) try { trava.release(); } catch (IOException ignorada) { /* Canal também será fechado. */ }
        if (canalTrava != null) try { canalTrava.close(); } catch (IOException ignorada) { /* Fechamento sem checked exception. */ }
    }

    private static final class Estado implements Serializable {
        private static final long serialVersionUID = 1L;
        private final int versao = 1;
        private final LinkedHashMap<String, LinkedHashMap<Integer, Entidade>> tabelas = new LinkedHashMap<>();
        private final LinkedHashMap<String, Integer> sequencias = new LinkedHashMap<>();
        private Estado() {
            for (Class<?> tipo : TIPOS) { tabelas.put(tipo.getName(), new LinkedHashMap<>()); sequencias.put(tipo.getName(), 0); }
        }
        private Estado copia() {
            Estado copia = new Estado();
            tabelas.forEach((tipo, registros) -> copia.tabelas.put(tipo, new LinkedHashMap<>(registros)));
            copia.sequencias.putAll(sequencias);
            return copia;
        }
    }
}
