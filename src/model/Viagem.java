package model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Viagem(int id, int motoristaId, int veiculoId, int clienteId, String origem,
                     String destino, LocalDate dataSaida, LocalDate dataChegada,
                     BigDecimal valorFrete, String status) implements Entidade {
    @Override
    public Viagem comId(int id) {
        return new Viagem(id, motoristaId, veiculoId, clienteId, origem, destino,
                dataSaida, dataChegada, valorFrete, status);
    }
}
