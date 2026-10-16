package tests;

import controller.FrotaController;
import controller.RegraNegocioException;
import dao.ArquivoRepositorio;
import model.Cliente;
import model.Manutencao;
import model.Motorista;
import model.Veiculo;
import model.Viagem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Testes executáveis com Java 17, sem biblioteca ou banco de dados externo. */
public final class RegraNegocioTest {
    private static int verificacoes;
    private static final LocalDate DIA = LocalDate.of(2026, 10, 16);

    private RegraNegocioTest() { }

    public static void main(String[] args) {
        documentosCamposEUnicidade();
        viagensConflitosEDatas();
        manutencoesVinculosEHistorico();
        relatorioPorSaidaInclusivo();
        System.out.println("Regras de negócio: " + verificacoes + " verificações aprovadas.");
    }

    private static void documentosCamposEUnicidade() {
        try (FrotaController c = novoController()) {
            Motorista motorista = c.salvar(new Motorista(0, "  Ana Lima  ", "529.982.247-25",
                    "12345678901", "ab", "(11) 99999-0000", true));
            conferir(motorista.id() > 0 && motorista.nome().equals("Ana Lima")
                            && motorista.cpf().equals("52998224725") && motorista.categoriaCnh().equals("AB"),
                    "Motorista recebe ID e normalização de nome, documento e categoria.");
            rejeitar(() -> c.salvar(new Motorista(0, "Outro", "52998224725", "12345678902", "B", "123", true)),
                    "CPF duplicado");
            rejeitar(() -> c.salvar(new Motorista(0, "Outro", "11144477735", "12345678901", "B", "123", true)),
                    "CNH duplicada");
            rejeitar(() -> c.salvar(new Motorista(0, "Outro", "52998224724", "12345678902", "B", "123", true)),
                    "CPF com dígito inválido");
            rejeitar(() -> c.salvar(new Motorista(0, "Outro", "11111111111", "12345678902", "B", "123", true)),
                    "CPF com dígitos repetidos");
            rejeitar(() -> c.salvar(new Motorista(0, "Outro", "11144477735", "123", "B", "123", true)),
                    "CNH deve ter onze dígitos");
            rejeitar(() -> c.salvar(new Motorista(0, "Outro", "11144477735", "12345678902", "Z", "123", true)),
                    "Categoria de CNH inválida");
            rejeitar(() -> c.salvar(new Motorista(0, "Outro", "11144477735", "12345678902", "B", "", true)),
                    "Telefone obrigatório");
            rejeitar(() -> c.salvar(new Motorista(0, "a".repeat(101), "11144477735", "12345678902", "B", "123", true)),
                    "Limite do nome");
            conferir(c.listar(Motorista.class).size() == 1, "Falhas de validação não persistem motoristas.");

            Cliente cliente = c.salvar(new Cliente(0, "Empresa", "11.222.333/0001-81", "123", "Rua Central"));
            conferir(cliente.cpfCnpj().equals("11222333000181"), "CNPJ é normalizado e validado.");
            rejeitar(() -> c.salvar(new Cliente(0, "Empresa", "11222333000181", "123", "Rua Central")),
                    "CNPJ duplicado");
            rejeitar(() -> c.salvar(new Cliente(0, "Empresa", "11222333000180", "123", "Rua Central")),
                    "CNPJ com dígito inválido");
            rejeitar(() -> c.salvar(new Cliente(0, "Empresa", "00000000000000", "123", "Rua Central")),
                    "CNPJ com dígitos repetidos");
            rejeitar(() -> c.salvar(new Cliente(0, "Pessoa", "11144477735", "123", " ")),
                    "Endereço obrigatório");
            rejeitar(() -> c.salvar(new Cliente(0, "Pessoa", "11144477735", "123", "a".repeat(201))),
                    "Limite do endereço");

            Veiculo veiculo = c.salvar(new Veiculo(0, "abc-1234", "Caminhão", 2020, valor("1000"), "Disponível"));
            conferir(veiculo.placa().equals("ABC1234"), "Placa é normalizada.");
            Veiculo casasNormalizadas = c.salvar(new Veiculo(veiculo.id(), veiculo.placa(), veiculo.modelo(),
                    veiculo.ano(), valor("1000.0000"), veiculo.status()));
            conferir(casasNormalizadas.capacidadeKg().scale() == 2,
                    "Casas decimais sem valor são normalizadas antes de persistir nos dois modos.");
            rejeitar(() -> c.salvar(new Veiculo(0, "ABC1234", "Outro", 2021, valor("2000"), "Disponível")),
                    "Placa duplicada");
            rejeitar(() -> c.salvar(new Veiculo(0, "1234567", "Outro", 2021, valor("2000"), "Disponível")),
                    "Formato de placa inválido");
            rejeitar(() -> c.salvar(new Veiculo(0, "DEF1G23", "Outro", 2021, valor("0"), "Disponível")),
                    "Capacidade deve ser positiva");
            rejeitar(() -> c.salvar(new Veiculo(0, "DEF1G23", "Outro", 2021, valor("1.001"), "Disponível")),
                    "Capacidade com mais de duas casas");
            rejeitar(() -> c.salvar(new Veiculo(0, "DEF1G23", "Outro", 2021, valor("100000000"), "Disponível")),
                    "Limite DECIMAL(10,2)");
            rejeitar(() -> c.salvar(new Veiculo(0, "DEF1G23", "Outro", 1800, valor("1"), "Disponível")),
                    "Ano fora do intervalo");
            rejeitar(() -> c.salvar(new Veiculo(0, "DEF1G23", "Outro", 2021, valor("1"), "Ativo")),
                    "Status de veículo inválido");
            rejeitar(() -> c.salvar(motorista.comId(9999)), "Edição de registro inexistente");
            rejeitar(() -> c.salvar(cliente.comId(-1)), "Identificador negativo");
        }
    }

    private static void viagensConflitosEDatas() {
        try (FrotaController c = novoController()) {
            Base base = base(c);
            Viagem planejada = c.salvar(viagem(base, 0, DIA, null, "Planejada"));
            rejeitar(() -> c.salvar(viagem(base, 0, DIA, DIA.minusDays(1), "Planejada")),
                    "Chegada anterior à saída");
            rejeitar(() -> c.salvar(viagem(base, 0, DIA, null, "Concluída")),
                    "Conclusão exige chegada");
            rejeitar(() -> c.salvar(viagem(base, 0, null, null, "Planejada")), "Saída obrigatória");
            rejeitar(() -> c.salvar(new Viagem(0, base.motorista().id(), base.veiculo().id(),
                    base.cliente().id(), "Origem", "Destino", DIA, null, BigDecimal.ZERO, "Planejada")),
                    "Frete deve ser positivo");
            rejeitar(() -> c.salvar(new Viagem(0, 999, base.veiculo().id(), base.cliente().id(),
                    "Origem", "Destino", DIA, null, valor("100"), "Planejada")),
                    "Referência de motorista inexistente");

            c.salvar(situacao(base.veiculo(), "Em manutenção"));
            rejeitar(() -> c.salvar(viagem(base, planejada.id(), DIA, null, "Em andamento")),
                    "Veículo indisponível ao iniciar");
            c.salvar(situacao(base.veiculo(), "Disponível"));
            Viagem andamento = c.salvar(viagem(base, planejada.id(), DIA, null, "Em andamento"));
            conferir(c.listar(Veiculo.class).get(0).status().equals("Disponível"),
                    "Iniciar viagem preserva status manual do veículo.");
            c.salvar(situacao(base.veiculo(), "Em viagem"));
            c.salvar(viagem(base, andamento.id(), DIA, DIA.plusDays(1), "Em andamento"));
            conferir(c.listar(Viagem.class).size() == 1,
                    "Editar a própria viagem em andamento admite veículo marcado Em viagem.");

            Veiculo outroVeiculo = c.salvar(new Veiculo(0, "DEF1G23", "Outro", 2022, valor("1200"), "Disponível"));
            Base mesmoMotorista = new Base(base.motorista(), outroVeiculo, base.cliente());
            rejeitar(() -> c.salvar(viagem(mesmoMotorista, 0, DIA, null, "Em andamento")),
                    "Motorista não pode ter duas viagens em andamento");
            Motorista outroMotorista = c.salvar(new Motorista(0, "Bruno", "11144477735", "12345678902", "D", "123", true));
            c.salvar(situacao(base.veiculo(), "Disponível"));
            Base mesmoVeiculo = new Base(outroMotorista, base.veiculo(), base.cliente());
            rejeitar(() -> c.salvar(viagem(mesmoVeiculo, 0, DIA, null, "Em andamento")),
                    "Veículo não pode ter duas viagens em andamento");

            Viagem concluida = c.salvar(viagem(base, andamento.id(), DIA, DIA.plusDays(1), "Concluída"));
            c.salvar(new Motorista(base.motorista().id(), base.motorista().nome(), base.motorista().cpf(),
                    base.motorista().cnh(), base.motorista().categoriaCnh(), base.motorista().telefone(), false));
            c.salvar(new Viagem(concluida.id(), concluida.motoristaId(), concluida.veiculoId(), concluida.clienteId(),
                    "Origem corrigida", concluida.destino(), concluida.dataSaida(), concluida.dataChegada(),
                    concluida.valorFrete(), concluida.status()));
            conferir(c.listar(Viagem.class).get(0).origem().equals("Origem corrigida"),
                    "Motorista inativado posteriormente não impede correção de histórico.");
            rejeitar(() -> c.salvar(viagem(base, 0, DIA, null, "Planejada")),
                    "Motorista inativo não recebe nova viagem");
            rejeitar(() -> c.salvar(viagem(base, concluida.id(), DIA, null, "Em andamento")),
                    "Motorista inativo não reinicia viagem");
        }
    }

    private static void manutencoesVinculosEHistorico() {
        try (FrotaController c = novoController()) {
            Base base = base(c);
            Manutencao manutencao = c.salvar(new Manutencao(0, base.veiculo().id(), DIA,
                    "Preventiva", "Troca de óleo", BigDecimal.ZERO, 0));
            conferir(c.listar(Veiculo.class).get(0).status().equals("Disponível"),
                    "Manutenção não modifica status manual do veículo.");
            rejeitar(() -> c.excluir(Veiculo.class, base.veiculo().id()), "Veículo com manutenção vinculada");
            rejeitar(() -> c.salvar(new Manutencao(0, base.veiculo().id(), DIA, "Corretiva", "Reparo", valor("-1"), 0)),
                    "Custo negativo");
            rejeitar(() -> c.salvar(new Manutencao(0, base.veiculo().id(), DIA, "Corretiva", "Reparo", valor("1"), -1)),
                    "Quilometragem negativa");
            rejeitar(() -> c.salvar(new Manutencao(0, base.veiculo().id(), DIA, "Corretiva", "", valor("1"), 1)),
                    "Descrição obrigatória");
            rejeitar(() -> c.salvar(new Manutencao(0, base.veiculo().id(), DIA, "Corretiva", "a".repeat(256), valor("1"), 1)),
                    "Limite de descrição");
            c.excluir(Manutencao.class, manutencao.id());
            conferir(c.listar(Manutencao.class).isEmpty(), "Manutenção pode ser excluída.");

            Viagem viagem = c.salvar(viagem(base, 0, DIA, null, "Planejada"));
            rejeitar(() -> c.excluir(Motorista.class, base.motorista().id()), "Motorista com viagem vinculada");
            rejeitar(() -> c.excluir(Cliente.class, base.cliente().id()), "Cliente com viagem vinculada");
            rejeitar(() -> c.excluir(Veiculo.class, base.veiculo().id()), "Veículo com viagem vinculada");
            rejeitar(() -> c.excluir(Viagem.class, viagem.id()), "Histórico não pode ser apagado");
            c.cancelarViagem(viagem.id());
            c.cancelarViagem(viagem.id());
            Viagem cancelada = c.listar(Viagem.class).get(0);
            conferir(cancelada.status().equals("Cancelada") && cancelada.id() == viagem.id()
                            && cancelada.valorFrete().compareTo(viagem.valorFrete()) == 0,
                    "Cancelamento idempotente preserva o registro e seus dados.");
            rejeitar(() -> c.excluir(Motorista.class, base.motorista().id()),
                    "Viagem cancelada continua protegendo o vínculo histórico");
            rejeitar(() -> c.cancelarViagem(9999), "Cancelar viagem inexistente");

            Motorista semVinculo = c.salvar(new Motorista(0, "Bruno", "11144477735", "12345678902", "D", "123", true));
            c.excluir(Motorista.class, semVinculo.id());
            conferir(c.listar(Motorista.class).size() == 1, "Registro sem vínculos pode ser excluído.");
        }
    }

    private static void relatorioPorSaidaInclusivo() {
        try (FrotaController c = novoController()) {
            Base base = base(c);
            Cliente outro = c.salvar(new Cliente(0, "Outra pessoa", "11144477735", "123", "Outro endereço"));
            Viagem primeiro = c.salvar(viagem(base, 0, DIA, DIA.plusDays(8), "Concluída"));
            Viagem ultimo = c.salvar(viagem(base, 0, DIA.plusDays(2), null, "Planejada"));
            c.salvar(viagem(base, 0, DIA.minusDays(1), DIA.plusDays(1), "Concluída"));
            c.salvar(viagem(base, 0, DIA.plusDays(3), null, "Planejada"));
            Viagem cancelada = c.salvar(viagem(base, 0, DIA.plusDays(1), null, "Planejada"));
            c.cancelarViagem(cancelada.id());
            Viagem outroCliente = c.salvar(viagem(new Base(base.motorista(), base.veiculo(), outro),
                    0, DIA.plusDays(1), null, "Planejada"));

            List<Viagem> todas = c.relatorio(DIA, DIA.plusDays(2), null);
            conferir(todas.stream().map(Viagem::id).toList().equals(List.of(primeiro.id(), outroCliente.id(), ultimo.id())),
                    "Relatório usa saída inclusiva, ordena por saída e exclui canceladas.");
            List<Viagem> cliente = c.relatorio(DIA, DIA.plusDays(2), base.cliente().id());
            conferir(cliente.stream().map(Viagem::id).toList().equals(List.of(primeiro.id(), ultimo.id())),
                    "Relatório filtra o cliente selecionado.");
            conferir(c.relatorio(DIA, DIA, null).size() == 1, "Relatório aceita um único dia.");
            BigDecimal total = cliente.stream().map(Viagem::valorFrete).reduce(BigDecimal.ZERO, BigDecimal::add);
            conferir(total.compareTo(valor("250.00")) == 0, "Total do relatório usa valores decimais exatos.");
            rejeitar(() -> c.relatorio(DIA.plusDays(1), DIA, null), "Período invertido");
            rejeitar(() -> c.relatorio(null, DIA, null), "Período obrigatório");
            rejeitar(() -> c.relatorio(DIA, DIA, 9999), "Cliente do filtro inexistente");
        }
    }

    private static FrotaController novoController() {
        return new FrotaController(new ArquivoRepositorio());
    }

    private static Base base(FrotaController controller) {
        Motorista motorista = controller.salvar(new Motorista(0, "Ana", "52998224725", "12345678901", "D", "123", true));
        Veiculo veiculo = controller.salvar(new Veiculo(0, "ABC1234", "Caminhão", 2020, valor("1000"), "Disponível"));
        Cliente cliente = controller.salvar(new Cliente(0, "Empresa", "11222333000181", "123", "Rua Central"));
        return new Base(motorista, veiculo, cliente);
    }

    private static Viagem viagem(Base base, int id, LocalDate saida, LocalDate chegada, String status) {
        return new Viagem(id, base.motorista().id(), base.veiculo().id(), base.cliente().id(),
                "São Paulo", "Campinas", saida, chegada, valor("125.00"), status);
    }

    private static Veiculo situacao(Veiculo veiculo, String status) {
        return new Veiculo(veiculo.id(), veiculo.placa(), veiculo.modelo(), veiculo.ano(), veiculo.capacidadeKg(), status);
    }

    private static BigDecimal valor(String valor) {
        return new BigDecimal(valor);
    }

    private static void rejeitar(Runnable operacao, String motivo) {
        try {
            operacao.run();
        } catch (RegraNegocioException esperado) {
            verificacoes++;
            return;
        }
        throw new AssertionError("A operação deveria ser rejeitada: " + motivo);
    }

    private static void conferir(boolean resultado, String motivo) {
        if (!resultado) throw new AssertionError(motivo);
        verificacoes++;
    }

    private record Base(Motorista motorista, Veiculo veiculo, Cliente cliente) { }
}
