package app;

import controller.FrotaController;
import model.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Dados fictícios criados apenas com --demo, em arquivo separado dos dados reais. */
public final class DadosDemonstracao {
    private DadosDemonstracao() {}

    public static void carregar(FrotaController c) {
        if (!c.listar(Motorista.class).isEmpty() || !c.listar(Veiculo.class).isEmpty()
                || !c.listar(Cliente.class).isEmpty()) return;
        Motorista joao = c.salvar(new Motorista(0, "João Silva", "52998224725", "12345678901", "E", "11990001122", true));
        c.salvar(new Motorista(0, "Ana Costa", "11144477735", "23456789012", "D", "11990003344", true));
        Veiculo cargo = c.salvar(new Veiculo(0, "ABC1D23", "Ford Cargo 2429", 2021, new BigDecimal("16000"), "Disponível"));
        c.salvar(new Veiculo(0, "DEF4G56", "Iveco Tector", 2020, new BigDecimal("12000"), "Disponível"));
        Cliente mercado = c.salvar(new Cliente(0, "Mercado Central Ltda.", "11222333000181", "11990000010", "Rua das Flores, 100 — São Paulo/SP"));
        Cliente horizonte = c.salvar(new Cliente(0, "Comercial Horizonte", "11444777000161", "11990000020", "Av. Brasil, 250 — Campinas/SP"));
        LocalDate inicio = LocalDate.now().withDayOfMonth(1);
        c.salvar(new Viagem(0, joao.id(), cargo.id(), mercado.id(), "São Paulo/SP", "Campinas/SP", inicio, inicio.plusDays(1), new BigDecimal("1500"), "Concluída"));
        c.salvar(new Viagem(0, joao.id(), cargo.id(), horizonte.id(), "São Paulo/SP", "Santos/SP", inicio.plusDays(4), inicio.plusDays(5), new BigDecimal("2100"), "Concluída"));
        c.salvar(new Viagem(0, joao.id(), cargo.id(), mercado.id(), "São Paulo/SP", "Sorocaba/SP", inicio.plusDays(16), null, new BigDecimal("1800"), "Planejada"));
        c.salvar(new Manutencao(0, cargo.id(), inicio.minusDays(8), "Preventiva", "Troca de óleo e filtros.", new BigDecimal("750"), 85000));
    }
}
