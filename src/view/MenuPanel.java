package view;

import controller.FrotaController;
import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.function.Consumer;

public final class MenuPanel extends JPanel {
    private final FrotaController controller;
    private final JLabel motoristas = Tema.titulo("—", 28);
    private final JLabel veiculos = Tema.titulo("—", 28);
    private final JLabel viagens = Tema.titulo("—", 28);
    private final JLabel valor = Tema.titulo("—", 28);
    private boolean ocupado;

    public MenuPanel(FrotaController controller, Consumer<String> navegar) {
        super(new BorderLayout(0, 22)); this.controller = controller; setOpaque(false);
        JPanel metricas = new JPanel(new GridLayout(1, 4, 14, 0)); metricas.setOpaque(false);
        metricas.add(metrica("MOTORISTAS ATIVOS", motoristas)); metricas.add(metrica("VEÍCULOS DISPONÍVEIS", veiculos));
        metricas.add(metrica("VIAGENS EM ANDAMENTO", viagens)); metricas.add(metrica("FRETES NO MÊS", valor)); add(metricas, BorderLayout.NORTH);
        JPanel acessos = Tema.cartao("O que você deseja fazer?");
        JPanel grade = new JPanel(new GridLayout(3, 2, 16, 16)); grade.setOpaque(false);
        grade.add(atalho("01", "Motoristas", "Cadastre profissionais e acompanhe sua situação.", navegar));
        grade.add(atalho("02", "Veículos", "Mantenha os dados e a disponibilidade da frota.", navegar));
        grade.add(atalho("03", "Clientes", "Organize os contratantes dos seus fretes.", navegar));
        grade.add(atalho("04", "Viagens / Fretes", "Registre rotas, datas e valores de transporte.", navegar));
        grade.add(atalho("05", "Manutenção", "Consulte serviços, custos e quilometragem.", navegar));
        grade.add(atalho("06", "Relatório de fretes", "Consulte a quantidade e o valor por período.", navegar));
        acessos.add(grade, BorderLayout.CENTER); add(acessos, BorderLayout.CENTER);
        add(Tema.legenda("Comece pelos cadastros de motoristas, veículos e clientes; depois registre as viagens."), BorderLayout.SOUTH);
    }
    private JPanel metrica(String titulo, JLabel numero) {
        JPanel p = Tema.cartao(null); p.add(Tema.legenda(titulo), BorderLayout.NORTH); p.add(numero, BorderLayout.CENTER); return p;
    }
    private JPanel atalho(String numero, String nome, String descricao, Consumer<String> navegar) {
        JPanel p = new JPanel(new BorderLayout(0, 10)); p.setBackground(new Color(247, 249, 253)); p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Tema.LINHA), BorderFactory.createEmptyBorder(18, 20, 18, 20)));
        JPanel topo = new JPanel(new BorderLayout()); topo.setOpaque(false); JLabel n = Tema.titulo(numero, 15); n.setForeground(Tema.AZUL); topo.add(n, BorderLayout.WEST);
        JButton abrir = Tema.botao("Abrir →", false); abrir.addActionListener(e -> navegar.accept(nome)); topo.add(abrir, BorderLayout.EAST);
        p.add(topo, BorderLayout.NORTH); JPanel texto = new JPanel(new GridLayout(2, 1, 0, 7)); texto.setOpaque(false);
        texto.add(Tema.titulo(nome, 19)); texto.add(Tema.legenda("<html>" + descricao + "</html>")); p.add(texto, BorderLayout.CENTER); return p;
    }
    public void atualizar() { if (!ocupado) Tarefas.executar(this, () -> DadosTela.carregar(controller), this::apresentarDados, v -> ocupado = v); }
    public void apresentarDados(DadosTela dados) {
        motoristas.setText(String.valueOf(dados.motoristas().stream().filter(m -> m.ativo()).count()));
        veiculos.setText(String.valueOf(dados.veiculos().stream().filter(v -> v.status().equals("Disponível")).count()));
        viagens.setText(String.valueOf(dados.viagens().stream().filter(v -> v.status().equals("Em andamento")).count()));
        LocalDate inicio = LocalDate.now().withDayOfMonth(1), fim = inicio.plusMonths(1).minusDays(1);
        valor.setFont(Tema.NORMAL.deriveFont(Font.BOLD, 21));
        valor.setText(Formatacao.moeda(dados.viagens().stream().filter(v -> !v.status().equals("Cancelada") && !v.dataSaida().isBefore(inicio) && !v.dataSaida().isAfter(fim)).map(v -> v.valorFrete()).reduce(BigDecimal.ZERO, BigDecimal::add)));
    }
}
