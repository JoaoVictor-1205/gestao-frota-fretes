package view;

import controller.FrotaController;
import model.Viagem;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class RelatorioPanel extends JPanel {
    private final FrotaController controller;
    private final Formulario filtros = new Formulario();
    private final DefaultTableModel modelo = Tema.modelo(new String[]{"Código", "Saída", "Cliente", "Destino", "Valor do frete"});
    private final JButton gerar = Tema.botao("Gerar relatório", true);
    private final JButton limpar = Tema.botao("Limpar filtros", false);
    private final JLabel quantidade = Tema.titulo("0 fretes", 19);
    private final JLabel total = Tema.titulo("R$ 0,00", 23);
    private final JLabel situacao = Tema.legenda("Informe o período e clique em Gerar relatório.");
    private boolean ocupado;
    private DadosTela dados;

    public RelatorioPanel(FrotaController controller) {
        super(new BorderLayout(0, 16)); this.controller = controller; setOpaque(false);
        JPanel cartao = Tema.cartao("Relatório de fretes por período");
        filtros.mascarado("inicio", "Data inicial (dd/mm/aaaa) *", Formatacao.data(LocalDate.now().withDayOfMonth(1)), Mascaras.Tipo.DATA)
            .mascarado("fim", "Data final (dd/mm/aaaa) *", Formatacao.data(LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth())), Mascaras.Tipo.DATA)
            .vinculo("cliente", "Cliente (opcional)"); filtros.limpar();
        JPanel conteudo = new JPanel(new BorderLayout()); conteudo.setOpaque(false); conteudo.add(filtros, BorderLayout.CENTER);
        JPanel acoes = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)); acoes.setOpaque(false); acoes.add(gerar); acoes.add(limpar);
        conteudo.add(acoes, BorderLayout.SOUTH); cartao.add(conteudo, BorderLayout.CENTER); add(cartao, BorderLayout.NORTH);
        JPanel registros = Tema.cartao("Fretes com saída no período");
        JTable tabela = new JTable(modelo); Tema.tabela(tabela);
        tabela.getColumnModel().getColumn(0).setMaxWidth(70);
        tabela.getColumnModel().getColumn(2).setPreferredWidth(240);
        JScrollPane rolagem = new JScrollPane(tabela); rolagem.setColumnHeaderView(tabela.getTableHeader()); registros.add(rolagem, BorderLayout.CENTER);
        JPanel resumo = new JPanel(new BorderLayout(0, 12)); resumo.setOpaque(false);
        JPanel valores = new JPanel(new GridLayout(1, 2, 20, 0)); valores.setOpaque(false);
        valores.add(metrica("QUANTIDADE DE FRETES", quantidade)); valores.add(metrica("VALOR DOS SERVIÇOS", total));
        resumo.add(valores, BorderLayout.CENTER);
        JPanel notas = new JPanel(new GridLayout(0, 1, 0, 5)); notas.setOpaque(false);
        notas.add(Tema.legenda("O período inclui as duas datas. Viagens canceladas não entram no relatório."));
        notas.add(Tema.legenda("Os valores representam serviços registrados, sem baixa de pagamentos ou cálculo de lucro."));
        notas.add(situacao); resumo.add(notas, BorderLayout.SOUTH);
        registros.add(resumo, BorderLayout.SOUTH); add(registros, BorderLayout.CENTER);
        gerar.addActionListener(e -> gerar()); limpar.addActionListener(e -> { filtros.limpar(); zerar(); });
    }
    private JPanel metrica(String titulo, JLabel valor) {
        JPanel p = new JPanel(new BorderLayout(0, 8)); p.setBackground(new Color(239, 245, 253)); p.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        p.add(Tema.legenda(titulo), BorderLayout.NORTH); p.add(valor, BorderLayout.CENTER); return p;
    }
    public void atualizar() {
        if (ocupado) return;
        zerar();
        Tarefas.executar(this, () -> DadosTela.carregar(controller), this::apresentarDados, this::ocupado);
    }
    public void apresentarDados(DadosTela d) { dados = d; filtros.opcoes("cliente", d.clientes().stream().map(c -> new Formulario.Opcao(c.id(), c.nome())).toList(), "Todos os clientes"); }
    private void gerar() {
        if (ocupado) return;
        final LocalDate inicio, fim; final Integer clienteId;
        try {
            inicio = filtros.data("inicio", false); fim = filtros.data("fim", false);
            int id; try { id = filtros.vinculo("cliente"); } catch (IllegalArgumentException e) { id = 0; }
            clienteId = id == 0 ? null : id;
        } catch (RuntimeException e) { Tarefas.erro(this, e); return; }
        Tarefas.executar(this, () -> new Resultado(DadosTela.carregar(controller), controller.relatorio(inicio, fim, clienteId)), resultado -> {
            dados = resultado.dados(); apresentarRelatorio(resultado.viagens());
        }, this::ocupado);
    }
    public void apresentarRelatorio(List<Viagem> viagens) {
        modelo.setRowCount(0); BigDecimal soma = BigDecimal.ZERO;
        for (Viagem v : viagens) {
            modelo.addRow(new Object[]{v.id(), v.dataSaida(), dados.cliente(v.clienteId()), v.destino(), v.valorFrete()}); soma = soma.add(v.valorFrete());
        }
        quantidade.setText(viagens.size() + " frete(s)"); total.setText(Formatacao.moeda(soma));
        situacao.setText(viagens.isEmpty() ? "Nenhum frete encontrado para o período e o cliente selecionados." : "Relatório gerado com sucesso.");
    }
    private void zerar() { modelo.setRowCount(0); quantidade.setText("0 fretes"); total.setText("R$ 0,00"); situacao.setText("Informe o período e clique em Gerar relatório."); }
    private void ocupado(boolean valor) { ocupado = valor; gerar.setEnabled(!valor); limpar.setEnabled(!valor); filtros.habilitar(!valor); }
    private record Resultado(DadosTela dados, List<Viagem> viagens) {}
}
