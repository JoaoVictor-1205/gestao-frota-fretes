package view;

import controller.FrotaController;
import model.*;
import java.util.List;

public record DadosTela(List<Motorista> motoristas, List<Veiculo> veiculos, List<Cliente> clientes,
                        List<Viagem> viagens, List<Manutencao> manutencoes) {
    public static DadosTela carregar(FrotaController c) {
        return new DadosTela(c.listar(Motorista.class), c.listar(Veiculo.class), c.listar(Cliente.class), c.listar(Viagem.class), c.listar(Manutencao.class));
    }
    public String motorista(int id) { return motoristas.stream().filter(m -> m.id() == id).findFirst().map(Motorista::nome).orElse("Motorista " + id); }
    public String veiculo(int id) { return veiculos.stream().filter(v -> v.id() == id).findFirst().map(v -> Formatacao.placa(v.placa())).orElse("Veículo " + id); }
    public String cliente(int id) { return clientes.stream().filter(c -> c.id() == id).findFirst().map(Cliente::nome).orElse("Cliente " + id); }
}
