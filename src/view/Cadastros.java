package view;

import controller.FrotaController;
import model.*;
import java.time.LocalDate;

public final class Cadastros {
    private Cadastros() {}
    public static CadastroPanel<Motorista> motoristas(FrotaController controller) {
        return new CadastroPanel<>(controller, Motorista.class, "Cadastro de motoristas", new String[]{"Código", "Nome", "CPF", "Categoria CNH", "Telefone", "Situação"}) {
            {
                formulario.texto("nome", "Nome *", "").texto("cpf", "CPF *", "").texto("cnh", "CNH (11 dígitos) *", "")
                    .escolha("categoria", "Categoria da CNH *", "A", "B", "AB", "C", "D", "E", "AC", "AD", "AE")
                    .texto("telefone", "Telefone *", "").escolha("ativo", "Situação *", "Ativo", "Inativo");
                formulario.limpar();
            }
            protected Motorista ler() { return new Motorista(selecionado, formulario.texto("nome"), formulario.texto("cpf"), formulario.texto("cnh"), formulario.escolha("categoria"), formulario.texto("telefone"), formulario.escolha("ativo").equals("Ativo")); }
            protected void preencher(Motorista m) {
                formulario.preencher("nome", m.nome()); formulario.preencher("cpf", Formatacao.documento(m.cpf())); formulario.preencher("cnh", m.cnh());
                formulario.preencher("categoria", m.categoriaCnh()); formulario.preencher("telefone", Formatacao.telefone(m.telefone())); formulario.preencher("ativo", m.ativo() ? "Ativo" : "Inativo");
            }
            protected Object[] linha(Motorista m) { return new Object[]{m.id(), m.nome(), Formatacao.documento(m.cpf()), m.categoriaCnh(), Formatacao.telefone(m.telefone()), m.ativo() ? "Ativo" : "Inativo"}; }
        };
    }
    public static CadastroPanel<Veiculo> veiculos(FrotaController controller) {
        return new CadastroPanel<>(controller, Veiculo.class, "Cadastro de veículos", new String[]{"Código", "Placa", "Modelo", "Ano", "Capacidade (kg)", "Situação"}) {
            {
                formulario.texto("placa", "Placa *", "").texto("modelo", "Modelo *", "").texto("ano", "Ano *", String.valueOf(LocalDate.now().getYear()))
                    .texto("capacidade", "Capacidade (kg) *", "").escolha("status", "Situação *", "Disponível", "Em viagem", "Em manutenção"); formulario.limpar();
            }
            protected Veiculo ler() { return new Veiculo(selecionado, formulario.texto("placa"), formulario.texto("modelo"), formulario.inteiro("ano", "Ano"), formulario.numero("capacidade", "Capacidade"), formulario.escolha("status")); }
            protected void preencher(Veiculo v) {
                formulario.preencher("placa", Formatacao.placa(v.placa())); formulario.preencher("modelo", v.modelo()); formulario.preencher("ano", v.ano());
                formulario.preencher("capacidade", Formatacao.decimal(v.capacidadeKg())); formulario.preencher("status", v.status());
            }
            protected Object[] linha(Veiculo v) { return new Object[]{v.id(), Formatacao.placa(v.placa()), v.modelo(), v.ano(), v.capacidadeKg(), v.status()}; }
        };
    }
    public static CadastroPanel<Cliente> clientes(FrotaController controller) {
        return new CadastroPanel<>(controller, Cliente.class, "Cadastro de clientes", new String[]{"Código", "Nome / razão social", "CPF / CNPJ", "Telefone", "Endereço"}) {
            {
                formulario.texto("nome", "Nome / razão social *", "").texto("documento", "CPF / CNPJ *", "").texto("telefone", "Telefone *", "").texto("endereco", "Endereço *", ""); formulario.limpar();
            }
            protected Cliente ler() { return new Cliente(selecionado, formulario.texto("nome"), formulario.texto("documento"), formulario.texto("telefone"), formulario.texto("endereco")); }
            protected void preencher(Cliente c) { formulario.preencher("nome", c.nome()); formulario.preencher("documento", Formatacao.documento(c.cpfCnpj())); formulario.preencher("telefone", Formatacao.telefone(c.telefone())); formulario.preencher("endereco", c.endereco()); }
            protected Object[] linha(Cliente c) { return new Object[]{c.id(), c.nome(), Formatacao.documento(c.cpfCnpj()), Formatacao.telefone(c.telefone()), c.endereco()}; }
        };
    }
    public static CadastroPanel<Viagem> viagens(FrotaController controller) {
        return new CadastroPanel<>(controller, Viagem.class, "Lançamento de viagens / fretes", new String[]{"Código", "Cliente", "Saída", "Destino", "Valor", "Situação"}) {
            {
                formulario.vinculo("cliente", "Cliente *").vinculo("motorista", "Motorista *").vinculo("veiculo", "Veículo *")
                    .escolha("status", "Situação *", "Planejada", "Em andamento", "Concluída", "Cancelada")
                    .texto("origem", "Origem *", "").texto("destino", "Destino *", "")
                    .texto("saida", "Saída (dd/mm/aaaa) *", Formatacao.data(LocalDate.now())).texto("chegada", "Chegada (dd/mm/aaaa)", "")
                    .texto("valor", "Valor do frete (R$) *", ""); formulario.limpar();
            }
            protected void vinculos(DadosTela d) {
                formulario.opcoes("cliente", d.clientes().stream().map(c -> new Formulario.Opcao(c.id(), c.nome())).toList());
                // Inativos continuam visíveis para a edição do histórico; o controller restringe novas atribuições.
                formulario.opcoes("motorista", d.motoristas().stream().map(m -> new Formulario.Opcao(m.id(), m.nome() + (m.ativo() ? "" : " (inativo)"))).toList());
                formulario.opcoes("veiculo", d.veiculos().stream().map(v -> new Formulario.Opcao(v.id(), Formatacao.placa(v.placa()) + " — " + v.modelo() + " (" + v.status() + ")")).toList());
            }
            protected Viagem ler() { return new Viagem(selecionado, formulario.vinculo("motorista"), formulario.vinculo("veiculo"), formulario.vinculo("cliente"), formulario.texto("origem"), formulario.texto("destino"), formulario.data("saida", false), formulario.data("chegada", true), formulario.numero("valor", "Valor do frete"), formulario.escolha("status")); }
            protected void preencher(Viagem v) {
                formulario.preencher("cliente", v.clienteId()); formulario.preencher("motorista", v.motoristaId()); formulario.preencher("veiculo", v.veiculoId());
                formulario.preencher("origem", v.origem()); formulario.preencher("destino", v.destino()); formulario.preencher("saida", Formatacao.data(v.dataSaida()));
                formulario.preencher("chegada", Formatacao.data(v.dataChegada())); formulario.preencher("valor", Formatacao.decimal(v.valorFrete())); formulario.preencher("status", v.status());
            }
            protected Object[] linha(Viagem v) { return new Object[]{v.id(), dados.cliente(v.clienteId()), v.dataSaida(), v.destino(), v.valorFrete(), v.status()}; }
        };
    }
    public static CadastroPanel<Manutencao> manutencoes(FrotaController controller) {
        return new CadastroPanel<>(controller, Manutencao.class, "Histórico de manutenção", new String[]{"Código", "Veículo", "Data", "Tipo", "Km", "Custo"}) {
            {
                formulario.vinculo("veiculo", "Veículo *").texto("data", "Serviço (dd/mm/aaaa) *", Formatacao.data(LocalDate.now()))
                    .escolha("tipo", "Tipo *", "Preventiva", "Corretiva").texto("km", "Quilometragem (km) *", "")
                    .texto("custo", "Custo (R$) *", "").texto("descricao", "Descrição do serviço *", ""); formulario.limpar();
            }
            protected void vinculos(DadosTela d) { formulario.opcoes("veiculo", d.veiculos().stream().map(v -> new Formulario.Opcao(v.id(), Formatacao.placa(v.placa()) + " — " + v.modelo())).toList()); }
            protected Manutencao ler() { return new Manutencao(selecionado, formulario.vinculo("veiculo"), formulario.data("data", false), formulario.escolha("tipo"), formulario.texto("descricao"), formulario.numero("custo", "Custo"), formulario.inteiro("km", "Quilometragem")); }
            protected void preencher(Manutencao m) {
                formulario.preencher("veiculo", m.veiculoId()); formulario.preencher("data", Formatacao.data(m.data())); formulario.preencher("tipo", m.tipo());
                formulario.preencher("km", m.quilometragem()); formulario.preencher("custo", Formatacao.decimal(m.custo())); formulario.preencher("descricao", m.descricao());
            }
            protected Object[] linha(Manutencao m) { return new Object[]{m.id(), dados.veiculo(m.veiculoId()), m.data(), m.tipo(), m.quilometragem(), m.custo()}; }
        };
    }
}
