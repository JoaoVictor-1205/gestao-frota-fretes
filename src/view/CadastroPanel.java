package view;

import controller.FrotaController;
import model.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.List;
import java.util.ArrayList;

/** Formulário reutilizado pelas cinco entidades; ações de persistência sempre fora da EDT. */
public abstract class CadastroPanel<T extends Entidade> extends JPanel {
    protected final FrotaController controller;
    protected final Formulario formulario = new Formulario();
    protected final JTable tabela;
    protected final JLabel mensagem = Tema.legenda("Campos com * são obrigatórios. Selecione uma linha para editar.");
    protected DadosTela dados;
    protected int selecionado;
    private final Class<T> tipo;
    private final DefaultTableModel modelo;
    private final JButton novo = Tema.botao("Novo", false);
    private final JButton salvar = Tema.botao("Salvar", true);
    private final JButton excluir;
    private final JButton limpar = Tema.botao("Limpar", false);
    private final JButton atualizar = Tema.botao("Atualizar lista", false);
    private final JTextField busca = new JTextField(23);
    private final JLabel contador = Tema.legenda("0 registros");
    private final JLabel codigo = Tema.legenda("Novo registro · código gerado ao salvar");
    private List<T> registros = List.of();
    private boolean ocupado;
    private boolean carregando;
    private final JComboBox<Formulario.Opcao> filtroVeiculo;

    protected CadastroPanel(FrotaController controller, Class<T> tipo, String titulo, String[] colunas) {
        super(new BorderLayout(0, 16)); this.controller = controller; this.tipo = tipo;
        setOpaque(false);
        excluir = Tema.botao(tipo == Viagem.class ? "Cancelar viagem" : "Excluir", false);
        excluir.setForeground(new Color(164, 45, 48));
        JPanel cartaoFormulario = Tema.cartao(titulo);
        JPanel centro = new JPanel(new BorderLayout(0, 7)); centro.setOpaque(false);
        centro.add(codigo, BorderLayout.NORTH); centro.add(formulario, BorderLayout.CENTER);
        JPanel rodape = new JPanel(new BorderLayout()); rodape.setOpaque(false);
        JPanel acoes = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)); acoes.setOpaque(false);
        acoes.add(novo); acoes.add(salvar); acoes.add(excluir); acoes.add(limpar);
        rodape.add(acoes, BorderLayout.NORTH);
        mensagem.setBorder(BorderFactory.createEmptyBorder(11, 0, 0, 0)); rodape.add(mensagem, BorderLayout.SOUTH);
        centro.add(rodape, BorderLayout.SOUTH); cartaoFormulario.add(centro, BorderLayout.CENTER);
        add(cartaoFormulario, BorderLayout.NORTH);

        JPanel lista = Tema.cartao("Registros cadastrados");
        JPanel conteudoLista = new JPanel(new BorderLayout(0, 12)); conteudoLista.setOpaque(false);
        JPanel ferramentas = new JPanel(new BorderLayout(12, 0)); ferramentas.setOpaque(false);
        JPanel filtros = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0)); filtros.setOpaque(false);
        JLabel labelBusca = Tema.legenda("Buscar:"); labelBusca.setLabelFor(busca);
        filtros.add(labelBusca); busca.setPreferredSize(new Dimension(220, 32));
        busca.setName("busca"); busca.getAccessibleContext().setAccessibleName("Buscar registros"); filtros.add(busca);
        if (tipo == Manutencao.class) {
            filtroVeiculo = new JComboBox<>(); filtroVeiculo.setPreferredSize(new Dimension(190, 32));
            filtroVeiculo.getAccessibleContext().setAccessibleName("Filtrar por veículo"); filtros.add(filtroVeiculo);
            filtroVeiculo.addActionListener(e -> filtrar());
        } else filtroVeiculo = null;
        ferramentas.add(filtros, BorderLayout.CENTER); ferramentas.add(atualizar, BorderLayout.EAST);
        conteudoLista.add(ferramentas, BorderLayout.NORTH);
        modelo = Tema.modelo(colunas);
        tabela = new JTable(modelo); Tema.tabela(tabela);
        JScrollPane rolagem = new JScrollPane(tabela); rolagem.setColumnHeaderView(tabela.getTableHeader()); rolagem.setPreferredSize(new Dimension(500, 210));
        tabela.getColumnModel().getColumn(0).setMaxWidth(68);
        for (int i = 1; i < tabela.getColumnCount(); i++) {
            String coluna = tabela.getColumnName(i);
            tabela.getColumnModel().getColumn(i).setPreferredWidth(coluna.contains("Nome") || coluna.equals("Cliente") || coluna.equals("Modelo") || coluna.equals("Endereço") ? 210 : 125);
        }
        conteudoLista.add(rolagem, BorderLayout.CENTER); conteudoLista.add(contador, BorderLayout.SOUTH);
        lista.add(conteudoLista, BorderLayout.CENTER); add(lista, BorderLayout.CENTER);
        tabela.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting() && !carregando) selecionar(); });
        busca.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filtrar(); }
            public void removeUpdate(DocumentEvent e) { filtrar(); }
            public void changedUpdate(DocumentEvent e) { filtrar(); }
        });
        novo.addActionListener(e -> limpar()); limpar.addActionListener(e -> limpar());
        salvar.addActionListener(e -> salvar()); excluir.addActionListener(e -> excluir());
        atualizar.addActionListener(e -> atualizar()); atualizarBotoes();
    }

    protected abstract T ler();
    protected abstract void preencher(T entidade);
    protected abstract Object[] linha(T entidade);
    protected void vinculos(DadosTela novosDados) {}

    public void atualizar() {
        if (ocupado) return;
        Tarefas.executar(this, () -> DadosTela.carregar(controller), this::apresentarDados, this::ocupado);
    }

    /** Também permite verificar renderização dos painéis sem abrir janelas. */
    public void apresentarDados(DadosTela novosDados) {
        dados = novosDados; carregando = true;
        try {
            vinculos(dados);
            if (filtroVeiculo != null) {
                Object anterior = filtroVeiculo.getSelectedItem(); filtroVeiculo.removeAllItems();
                filtroVeiculo.addItem(new Formulario.Opcao(0, "Todos os veículos"));
                dados.veiculos().forEach(v -> filtroVeiculo.addItem(new Formulario.Opcao(v.id(), Formatacao.placa(v.placa()))));
                if (anterior instanceof Formulario.Opcao opcao) for (int i = 0; i < filtroVeiculo.getItemCount(); i++) if (filtroVeiculo.getItemAt(i).id() == opcao.id()) filtroVeiculo.setSelectedIndex(i);
            }
            List<? extends Entidade> lista = tipo == Motorista.class ? dados.motoristas() : tipo == Veiculo.class ? dados.veiculos() : tipo == Cliente.class ? dados.clientes() : tipo == Viagem.class ? dados.viagens() : dados.manutencoes();
            registros = new ArrayList<>(); for (Entidade entidade : lista) registros.add(tipo.cast(entidade));
            tabela.clearSelection(); modelo.setRowCount(0); registros.forEach(t -> modelo.addRow(linha(t)));
            limpar(); filtrar();
        } finally { carregando = false; }
    }

    private void selecionar() {
        int linha = tabela.getSelectedRow();
        if (linha < 0) { if (selecionado != 0) limpar(); atualizarBotoes(); return; }
        T entidade = registros.get(tabela.convertRowIndexToModel(linha)); selecionado = entidade.id();
        preencher(entidade); codigo.setText("Editando registro #" + selecionado);
        mensagem.setText("Altere os campos e clique em Salvar para atualizar o registro."); atualizarBotoes();
    }

    protected void limpar() {
        selecionado = 0; tabela.clearSelection(); formulario.limpar();
        codigo.setText("Novo registro · código gerado ao salvar");
        mensagem.setText("Campos com * são obrigatórios. Selecione uma linha para editar."); atualizarBotoes();
    }

    private void salvar() {
        if (ocupado) return;
        final T entidade;
        try { entidade = ler(); } catch (RuntimeException e) { Tarefas.erro(this, e); return; }
        if (entidade instanceof Viagem viagem && viagem.status().equals("Cancelada")
                && !Tarefas.confirmar(this, "Salvar esta viagem como Cancelada? O registro será mantido no histórico.")) return;
        Tarefas.executar(this, () -> { controller.salvar(entidade); return DadosTela.carregar(controller); }, novos -> {
            apresentarDados(novos); mensagem.setText("Registro salvo com sucesso.");
        }, this::ocupado);
    }

    private void excluir() {
        if (ocupado || selecionado == 0) return;
        boolean viagem = tipo == Viagem.class;
        if (!Tarefas.confirmar(this, viagem ? "Cancelar a viagem #" + selecionado + "? O registro será mantido no histórico." : "Excluir o registro #" + selecionado + "? Esta operação não poderá ser desfeita.")) return;
        int id = selecionado;
        Tarefas.executar(this, () -> {
            if (viagem) controller.cancelarViagem(id); else controller.excluir(tipo, id);
            return DadosTela.carregar(controller);
        }, novos -> { apresentarDados(novos); mensagem.setText(viagem ? "Viagem cancelada. Histórico preservado." : "Registro excluído com sucesso."); }, this::ocupado);
    }

    private void ocupado(boolean valor) {
        ocupado = valor; formulario.habilitar(!valor); tabela.setEnabled(!valor); busca.setEnabled(!valor);
        if (filtroVeiculo != null) filtroVeiculo.setEnabled(!valor);
        atualizarBotoes();
    }
    private void atualizarBotoes() {
        novo.setEnabled(!ocupado); salvar.setEnabled(!ocupado); limpar.setEnabled(!ocupado);
        excluir.setEnabled(!ocupado && selecionado > 0); atualizar.setEnabled(!ocupado);
    }

    private void filtrar() {
        if (tabela == null) return;
        String termo = Formatacao.busca(busca.getText());
        int veiculoId = filtroVeiculo != null && filtroVeiculo.getSelectedItem() instanceof Formulario.Opcao opcao ? opcao.id() : 0;
        @SuppressWarnings("unchecked") TableRowSorter<DefaultTableModel> sorter = (TableRowSorter<DefaultTableModel>) tabela.getRowSorter();
        sorter.setRowFilter(new RowFilter<>() {
            @Override public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                if (veiculoId > 0 && entry.getIdentifier() < registros.size() && registros.get(entry.getIdentifier()) instanceof Manutencao m && m.veiculoId() != veiculoId) return false;
                if (termo.isEmpty()) return true;
                for (int i = 0; i < entry.getValueCount(); i++) {
                    Object valor = entry.getValue(i);
                    String exibido = valor instanceof java.time.LocalDate data ? Formatacao.data(data)
                        : valor instanceof java.math.BigDecimal numero ? Formatacao.decimal(numero) + " " + Formatacao.moeda(numero) : entry.getStringValue(i);
                    if (Formatacao.busca(exibido).contains(termo)) return true;
                }
                return false;
            }
        });
        contador.setText(tabela.getRowCount() + " registro(s)" + (tabela.getRowCount() == 0 ? " · Nenhum resultado para os filtros atuais." : " · Selecione uma linha para editar."));
    }
}
