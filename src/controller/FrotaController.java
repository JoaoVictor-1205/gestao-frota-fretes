package controller;

import dao.Repositorio;
import model.Cliente;
import model.Entidade;
import model.Manutencao;
import model.Motorista;
import model.Veiculo;
import model.Viagem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Regras compartilhadas pela interface, pelo modo local e pelo modo PostgreSQL. */
public final class FrotaController implements AutoCloseable {
    public static final List<String> SITUACOES_VEICULO =
            List.of("Disponível", "Em viagem", "Em manutenção");
    public static final List<String> SITUACOES_VIAGEM =
            List.of("Planejada", "Em andamento", "Concluída", "Cancelada");
    public static final List<String> TIPOS_MANUTENCAO = List.of("Preventiva", "Corretiva");
    public static final List<String> CATEGORIAS_CNH =
            List.of("A", "B", "AB", "C", "D", "E", "AC", "AD", "AE");

    private static final Set<Class<? extends Entidade>> TIPOS = Set.of(
            Motorista.class, Veiculo.class, Cliente.class, Viagem.class, Manutencao.class);
    private static final BigDecimal LIMITE_DECIMAL = new BigDecimal("99999999.99");
    private final Repositorio repositorio;

    public FrotaController(Repositorio repositorio) {
        this.repositorio = Objects.requireNonNull(repositorio, "Repositório obrigatório.");
    }

    public <T extends Entidade> List<T> listar(Class<T> tipo) {
        validarTipo(tipo);
        return repositorio.listar(tipo).stream().sorted(Comparator.comparingInt(Entidade::id)).toList();
    }

    public <T extends Entidade> T salvar(T entidade) {
        exigir(entidade != null, "Informe os dados que deseja salvar.");
        validarTipo(entidade.getClass());
        exigir(entidade.id() >= 0, "O identificador do registro é inválido.");
        return repositorio.transacao(() -> {
            if (entidade.id() > 0) {
                buscar(entidade.getClass(), entidade.id());
            }
            Entidade normalizada = normalizar(entidade);
            validar(normalizada);
            @SuppressWarnings("unchecked")
            T salva = (T) repositorio.salvar(normalizada);
            return salva;
        });
    }

    public void excluir(Class<? extends Entidade> tipo, int id) {
        validarTipo(tipo);
        repositorio.transacao(() -> {
            buscar(tipo, id);
            if (tipo == Viagem.class) {
                throw new RegraNegocioException(
                        "As viagens preservam o histórico. Use Cancelar viagem para cancelar este registro.");
            }
            boolean temViagem = listar(Viagem.class).stream().anyMatch(v ->
                    (tipo == Motorista.class && v.motoristaId() == id)
                    || (tipo == Veiculo.class && v.veiculoId() == id)
                    || (tipo == Cliente.class && v.clienteId() == id));
            exigir(!temViagem, "Este registro possui viagem vinculada e não pode ser excluído.");
            if (tipo == Veiculo.class) {
                exigir(listar(Manutencao.class).stream().noneMatch(m -> m.veiculoId() == id),
                        "Este veículo possui manutenção vinculada e não pode ser excluído.");
            }
            repositorio.excluir(tipo, id);
            return null;
        });
    }

    public void cancelarViagem(int id) {
        repositorio.transacao(() -> {
            Viagem viagem = buscar(Viagem.class, id);
            if (!"Cancelada".equals(viagem.status())) {
                repositorio.salvar(new Viagem(viagem.id(), viagem.motoristaId(), viagem.veiculoId(),
                        viagem.clienteId(), viagem.origem(), viagem.destino(), viagem.dataSaida(),
                        viagem.dataChegada(), viagem.valorFrete(), "Cancelada"));
            }
            return null;
        });
    }

    /** O intervalo considera a data de saída e inclui os dois dias informados. */
    public List<Viagem> relatorio(LocalDate inicio, LocalDate fim, Integer clienteId) {
        exigir(inicio != null && fim != null, "Informe a data inicial e a data final do relatório.");
        exigir(!fim.isBefore(inicio), "A data final não pode ser anterior à data inicial.");
        if (clienteId != null) {
            buscar(Cliente.class, clienteId);
        }
        return listar(Viagem.class).stream()
                .filter(v -> !"Cancelada".equals(v.status()))
                .filter(v -> !v.dataSaida().isBefore(inicio) && !v.dataSaida().isAfter(fim))
                .filter(v -> clienteId == null || v.clienteId() == clienteId)
                .sorted(Comparator.comparing(Viagem::dataSaida).thenComparingInt(Viagem::id))
                .toList();
    }

    public String descricaoPersistencia() {
        return repositorio.descricao();
    }

    @Override
    public void close() {
        repositorio.close();
    }

    private Entidade normalizar(Entidade entidade) {
        if (entidade instanceof Motorista m) {
            return new Motorista(m.id(), texto(m.nome()), documento(m.cpf()), documento(m.cnh()),
                    texto(m.categoriaCnh()).toUpperCase(Locale.ROOT), texto(m.telefone()), m.ativo());
        }
        if (entidade instanceof Veiculo v) {
            return new Veiculo(v.id(), texto(v.placa()).toUpperCase(Locale.ROOT).replaceAll("[\\s-]", ""),
                    texto(v.modelo()), v.ano(), quantia(v.capacidadeKg()), texto(v.status()));
        }
        if (entidade instanceof Cliente c) {
            return new Cliente(c.id(), texto(c.nome()), documento(c.cpfCnpj()), texto(c.telefone()),
                    texto(c.endereco()));
        }
        if (entidade instanceof Viagem v) {
            return new Viagem(v.id(), v.motoristaId(), v.veiculoId(), v.clienteId(), texto(v.origem()),
                    texto(v.destino()), v.dataSaida(), v.dataChegada(), quantia(v.valorFrete()), texto(v.status()));
        }
        if (entidade instanceof Manutencao m) {
            return new Manutencao(m.id(), m.veiculoId(), m.data(), texto(m.tipo()), texto(m.descricao()),
                    quantia(m.custo()), m.quilometragem());
        }
        throw new RegraNegocioException("Tipo de registro não suportado.");
    }

    private void validar(Entidade entidade) {
        if (entidade instanceof Motorista m) {
            obrigatorio(m.nome(), "Nome", 100);
            exigir(cpfValido(m.cpf()), "Informe um CPF válido para o motorista.");
            exigir(m.cnh().matches("[0-9]{11}"), "A CNH deve conter 11 dígitos.");
            exigir(CATEGORIAS_CNH.contains(m.categoriaCnh()), "Selecione uma categoria de CNH válida.");
            obrigatorio(m.telefone(), "Telefone", 20);
            for (Motorista outro : listar(Motorista.class)) {
                if (outro.id() != m.id()) {
                    exigir(!outro.cpf().equals(m.cpf()), "Já existe um motorista com este CPF.");
                    exigir(!outro.cnh().equals(m.cnh()), "Já existe um motorista com esta CNH.");
                }
            }
        } else if (entidade instanceof Veiculo v) {
            exigir(v.placa().matches("[A-Z]{3}[0-9][A-Z0-9][0-9]{2}"),
                    "Informe uma placa válida no padrão ABC1234 ou ABC1D23.");
            obrigatorio(v.modelo(), "Modelo", 100);
            exigir(v.ano() >= 1900 && v.ano() <= 2100, "O ano deve estar entre 1900 e 2100.");
            decimal(v.capacidadeKg(), "Capacidade", true);
            exigir(SITUACOES_VEICULO.contains(v.status()), "Selecione uma situação válida para o veículo.");
            exigir(listar(Veiculo.class).stream().noneMatch(outro ->
                    outro.id() != v.id() && outro.placa().equals(v.placa())),
                    "Já existe um veículo com esta placa.");
        } else if (entidade instanceof Cliente c) {
            obrigatorio(c.nome(), "Nome", 100);
            exigir(cpfValido(c.cpfCnpj()) || cnpjValido(c.cpfCnpj()), "Informe um CPF ou CNPJ válido.");
            obrigatorio(c.telefone(), "Telefone", 20);
            obrigatorio(c.endereco(), "Endereço", 200);
            exigir(listar(Cliente.class).stream().noneMatch(outro ->
                    outro.id() != c.id() && outro.cpfCnpj().equals(c.cpfCnpj())),
                    "Já existe um cliente com este CPF/CNPJ.");
        } else if (entidade instanceof Viagem v) {
            validarViagem(v);
        } else if (entidade instanceof Manutencao m) {
            buscar(Veiculo.class, m.veiculoId());
            exigir(m.data() != null, "Informe a data da manutenção.");
            exigir(TIPOS_MANUTENCAO.contains(m.tipo()), "Selecione um tipo de manutenção válido.");
            obrigatorio(m.descricao(), "Descrição", 255);
            decimal(m.custo(), "Custo", false);
            exigir(m.quilometragem() >= 0, "A quilometragem não pode ser negativa.");
        }
    }

    private void validarViagem(Viagem v) {
        Motorista motorista = buscar(Motorista.class, v.motoristaId());
        Veiculo veiculo = buscar(Veiculo.class, v.veiculoId());
        buscar(Cliente.class, v.clienteId());
        obrigatorio(v.origem(), "Origem", 100);
        obrigatorio(v.destino(), "Destino", 100);
        exigir(v.dataSaida() != null, "Informe a data de saída.");
        exigir(v.dataChegada() == null || !v.dataChegada().isBefore(v.dataSaida()),
                "A data de chegada não pode ser anterior à data de saída.");
        decimal(v.valorFrete(), "Valor do frete", true);
        exigir(SITUACOES_VIAGEM.contains(v.status()), "Selecione uma situação válida para a viagem.");
        exigir(!"Concluída".equals(v.status()) || v.dataChegada() != null,
                "Informe a data de chegada para concluir a viagem.");

        Viagem anterior = v.id() == 0 ? null : buscar(Viagem.class, v.id());
        // Uma inativação posterior não impede corrigir o histórico do mesmo motorista.
        boolean exigeAtivo = anterior == null || anterior.motoristaId() != v.motoristaId()
                || "Planejada".equals(v.status()) || "Em andamento".equals(v.status());
        exigir(!exigeAtivo || motorista.ativo(), "O motorista selecionado está inativo.");

        if ("Em andamento".equals(v.status())) {
            boolean iniciandoOuTrocandoVeiculo = anterior == null
                    || !"Em andamento".equals(anterior.status()) || anterior.veiculoId() != v.veiculoId();
            exigir(!iniciandoOuTrocandoVeiculo || "Disponível".equals(veiculo.status()),
                    "O veículo precisa estar disponível para iniciar a viagem.");
            for (Viagem outra : listar(Viagem.class)) {
                if (outra.id() != v.id() && "Em andamento".equals(outra.status())) {
                    exigir(outra.motoristaId() != v.motoristaId(),
                            "O motorista já possui outra viagem em andamento.");
                    exigir(outra.veiculoId() != v.veiculoId(),
                            "O veículo já possui outra viagem em andamento.");
                }
            }
        }
    }

    private <T extends Entidade> T buscar(Class<T> tipo, int id) {
        exigir(id > 0, "Selecione um registro válido de " + nomeTipo(tipo) + ".");
        return listar(tipo).stream().filter(e -> e.id() == id).findFirst()
                .orElseThrow(() -> new RegraNegocioException(
                        "O registro de " + nomeTipo(tipo) + " não foi encontrado. Atualize a tela."));
    }

    private static void validarTipo(Class<?> tipo) {
        exigir(tipo != null && TIPOS.contains(tipo), "Tipo de registro não suportado.");
    }

    private static String nomeTipo(Class<?> tipo) {
        if (tipo == Motorista.class) return "motorista";
        if (tipo == Veiculo.class) return "veículo";
        if (tipo == Cliente.class) return "cliente";
        if (tipo == Viagem.class) return "viagem";
        if (tipo == Manutencao.class) return "manutenção";
        return "registro";
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private static String documento(String valor) {
        return texto(valor).replaceAll("[.\\-/\\s]", "");
    }

    private static BigDecimal quantia(BigDecimal valor) {
        if (valor == null) return null;
        BigDecimal reduzido = valor.stripTrailingZeros();
        // Retira apenas casas sem valor: nunca arredonda uma entrada inválida.
        if (reduzido.scale() <= 2 && reduzido.abs().compareTo(LIMITE_DECIMAL) <= 0) {
            return reduzido.setScale(2);
        }
        return valor;
    }

    private static void obrigatorio(String valor, String campo, int tamanhoMaximo) {
        exigir(!valor.isBlank(), "O campo " + campo + " é obrigatório.");
        exigir(valor.length() <= tamanhoMaximo,
                "O campo " + campo + " deve ter no máximo " + tamanhoMaximo + " caracteres.");
        exigir(valor.indexOf('\0') < 0, "O campo " + campo + " contém um caractere inválido.");
    }

    private static void decimal(BigDecimal valor, String campo, boolean positivo) {
        exigir(valor != null, "O campo " + campo + " é obrigatório.");
        exigir(positivo ? valor.signum() > 0 : valor.signum() >= 0,
                "O campo " + campo + (positivo ? " deve ser maior que zero." : " não pode ser negativo."));
        exigir(valor.stripTrailingZeros().scale() <= 2,
                "O campo " + campo + " deve ter no máximo duas casas decimais.");
        exigir(valor.compareTo(LIMITE_DECIMAL) <= 0,
                "O campo " + campo + " deve ser no máximo 99.999.999,99.");
    }

    private static boolean cpfValido(String cpf) {
        if (!cpf.matches("[0-9]{11}") || cpf.chars().distinct().count() == 1) return false;
        return digito(cpf, 9, 10) == cpf.charAt(9) - '0'
                && digito(cpf, 10, 11) == cpf.charAt(10) - '0';
    }

    private static int digito(String valor, int quantidade, int pesoInicial) {
        int soma = 0;
        for (int i = 0; i < quantidade; i++) soma += (valor.charAt(i) - '0') * (pesoInicial - i);
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    private static boolean cnpjValido(String cnpj) {
        if (!cnpj.matches("[0-9]{14}") || cnpj.chars().distinct().count() == 1) return false;
        int[] pesos = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        for (int quantidade = 12; quantidade <= 13; quantidade++) {
            int soma = 0;
            for (int i = 0; i < quantidade; i++) {
                soma += (cnpj.charAt(i) - '0') * pesos[i + 13 - quantidade];
            }
            int resto = soma % 11;
            if ((resto < 2 ? 0 : 11 - resto) != cnpj.charAt(quantidade) - '0') return false;
        }
        return true;
    }

    private static void exigir(boolean condicao, String mensagem) {
        if (!condicao) throw new RegraNegocioException(mensagem);
    }
}
