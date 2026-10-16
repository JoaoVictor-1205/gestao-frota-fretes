package model;

import java.math.BigDecimal;

public record Veiculo(int id, String placa, String modelo, int ano, BigDecimal capacidadeKg,
                      String status) implements Entidade {
    @Override
    public Veiculo comId(int id) {
        return new Veiculo(id, placa, modelo, ano, capacidadeKg, status);
    }
}
