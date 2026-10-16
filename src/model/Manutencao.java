package model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Manutencao(int id, int veiculoId, LocalDate data, String tipo, String descricao,
                        BigDecimal custo, int quilometragem) implements Entidade {
    @Override
    public Manutencao comId(int id) {
        return new Manutencao(id, veiculoId, data, tipo, descricao, custo, quilometragem);
    }
}
