package view;

import controller.FrotaController;
import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PainelPrincipal extends JPanel {
    public static final String[] TELAS = {"Menu principal", "Motoristas", "Veículos", "Clientes", "Viagens / Fretes", "Manutenção", "Relatório de fretes"};
    private final CardLayout layout = new CardLayout();
    private final JPanel conteudo = new JPanel(layout);
    private final JLabel titulo = Tema.titulo("Menu principal", 29);
    private final JLabel descricao = Tema.legenda("Uma visão organizada da sua operação de transporte.");
    private final Map<String, JPanel> telas = new LinkedHashMap<>();
    private final Map<String, JButton> navegacao = new LinkedHashMap<>();

    public PainelPrincipal(FrotaController controller, Runnable sair, boolean demo) {
        super(new BorderLayout());
        JPanel lateral = new JPanel(new BorderLayout(0, 25)); lateral.setBackground(Tema.MARINHO);
        lateral.setBorder(BorderFactory.createEmptyBorder(27, 18, 23, 18)); lateral.setPreferredSize(new Dimension(228, 0));
        JLabel marca = Tema.titulo("<html>GESTÃO DE<br>FROTA <font color='#6FA9FF'>&amp; FRETES</font></html>", 21); marca.setForeground(Color.WHITE); marca.setBorder(BorderFactory.createEmptyBorder(0, 7, 18, 0));
        lateral.add(marca, BorderLayout.NORTH);
        JPanel links = new JPanel(); links.setOpaque(false); links.setLayout(new BoxLayout(links, BoxLayout.Y_AXIS));
        JLabel secao = new JLabel("ÁREA DE TRABALHO"); secao.setFont(Tema.NORMAL.deriveFont(10f)); secao.setForeground(new Color(150, 170, 197)); links.add(secao); links.add(Box.createVerticalStrut(15));
        for (String nome : TELAS) {
            JButton botao = new JButton(nome); botao.setFont(Tema.NORMAL); botao.setHorizontalAlignment(SwingConstants.LEFT);
            botao.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 8)); botao.setAlignmentX(LEFT_ALIGNMENT); botao.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
            botao.setForeground(new Color(204, 216, 233)); botao.setBackground(Tema.MARINHO); botao.setOpaque(true); botao.setFocusPainted(true);
            botao.addActionListener(e -> exibir(nome)); navegacao.put(nome, botao); links.add(botao); links.add(Box.createVerticalStrut(5));
        }
        links.add(Box.createVerticalGlue()); lateral.add(links, BorderLayout.CENTER);
        JPanel sessao = new JPanel(new GridLayout(0, 1, 0, 9)); sessao.setOpaque(false);
        JLabel usuario = new JLabel("●  Administrador"); usuario.setForeground(new Color(187, 213, 241)); sessao.add(usuario);
        JButton logout = Tema.botao("Sair da sessão", false); logout.addActionListener(e -> sair.run()); sessao.add(logout); lateral.add(sessao, BorderLayout.SOUTH); add(lateral, BorderLayout.WEST);
        JPanel area = new JPanel(new BorderLayout(0, 20)); area.setBorder(BorderFactory.createEmptyBorder(26, 26, 18, 26));
        JPanel cabecalho = new JPanel(new BorderLayout()); cabecalho.setOpaque(false);
        JPanel textos = new JPanel(new BorderLayout(0, 8)); textos.setOpaque(false); textos.add(titulo, BorderLayout.NORTH); textos.add(descricao, BorderLayout.CENTER);
        cabecalho.add(textos, BorderLayout.CENTER); JLabel versao = Tema.legenda(demo ? "DADOS DE DEMONSTRAÇÃO" : "Gestão da transportadora"); cabecalho.add(versao, BorderLayout.EAST); area.add(cabecalho, BorderLayout.NORTH);
        telas.put("Menu principal", new MenuPanel(controller, this::exibir));
        telas.put("Motoristas", Cadastros.motoristas(controller)); telas.put("Veículos", Cadastros.veiculos(controller));
        telas.put("Clientes", Cadastros.clientes(controller)); telas.put("Viagens / Fretes", Cadastros.viagens(controller));
        telas.put("Manutenção", Cadastros.manutencoes(controller)); telas.put("Relatório de fretes", new RelatorioPanel(controller));
        telas.forEach((nome, painel) -> conteudo.add(painel, nome)); conteudo.setOpaque(false); area.add(conteudo, BorderLayout.CENTER);
        JLabel modo = Tema.legenda(controller.descricaoPersistencia()); modo.setFont(Tema.NORMAL.deriveFont(11f)); area.add(modo, BorderLayout.SOUTH); add(area, BorderLayout.CENTER);
        exibirSemCarregar("Menu principal");
    }
    public void exibir(String nome) {
        exibirSemCarregar(nome);
        JPanel painel = telas.get(nome);
        if (painel instanceof CadastroPanel<?> cadastro) cadastro.atualizar();
        else if (painel instanceof RelatorioPanel relatorio) relatorio.atualizar();
        else if (painel instanceof MenuPanel menu) menu.atualizar();
    }
    public void exibirSemCarregar(String nome) {
        if (!telas.containsKey(nome)) throw new IllegalArgumentException("Tela desconhecida: " + nome);
        layout.show(conteudo, nome); titulo.setText(nome);
        descricao.setText(switch (nome) {
            case "Motoristas" -> "Profissionais, documentos e situação dos motoristas.";
            case "Veículos" -> "Dados e disponibilidade dos veículos da sua frota.";
            case "Clientes" -> "Contratantes dos serviços de transporte.";
            case "Viagens / Fretes" -> "Rotas, responsáveis e valores dos seus serviços.";
            case "Manutenção" -> "Serviços realizados e histórico de cada veículo.";
            case "Relatório de fretes" -> "Consulte os serviços registrados pela data de saída.";
            default -> "Uma visão organizada da sua operação de transporte.";
        });
        navegacao.forEach((tela, botao) -> { botao.setBackground(tela.equals(nome) ? new Color(39, 68, 108) : Tema.MARINHO); botao.setForeground(tela.equals(nome) ? Color.WHITE : new Color(204, 216, 233)); });
    }
    public JPanel tela(String nome) { return telas.get(nome); }
}
