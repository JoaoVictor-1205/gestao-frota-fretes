package view;

import app.Configuracao;
import controller.FrotaController;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public final class JanelaPrincipal extends JFrame {
    private final FrotaController controller;
    private final Configuracao configuracao;
    private final boolean demo;
    private boolean fechada;

    public JanelaPrincipal(FrotaController controller, Configuracao configuracao, boolean demo) {
        super("Gestão de Frota e Fretes"); this.controller = controller; this.configuracao = configuracao; this.demo = demo;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE); setMinimumSize(new Dimension(1180, 820)); setSize(1360, 900); setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent e) { fechar(); } }); mostrarLogin();
    }
    private void mostrarLogin() {
        setJMenuBar(null); LoginPanel login = new LoginPanel(configuracao, this::entrar, this::fechar, demo);
        setContentPane(login); revalidate(); repaint(); SwingUtilities.invokeLater(login::focar);
    }
    private void entrar() {
        PainelPrincipal principal = new PainelPrincipal(controller, this::mostrarLogin, demo);
        setContentPane(principal); setJMenuBar(menu(principal)); revalidate(); repaint(); principal.exibir("Menu principal");
    }
    private JMenuBar menu(PainelPrincipal principal) {
        JMenuBar barra = new JMenuBar();
        JMenu cadastros = new JMenu("Cadastros");
        for (String tela : new String[]{"Motoristas", "Veículos", "Clientes"}) item(cadastros, tela, () -> principal.exibir(tela));
        barra.add(cadastros);
        JMenu viagens = new JMenu("Viagens"); item(viagens, "Viagens / Fretes", () -> principal.exibir("Viagens / Fretes")); barra.add(viagens);
        JMenu manutencao = new JMenu("Manutenção"); item(manutencao, "Histórico de manutenção", () -> principal.exibir("Manutenção")); barra.add(manutencao);
        JMenu relatorios = new JMenu("Relatórios"); item(relatorios, "Relatório de fretes", () -> principal.exibir("Relatório de fretes")); barra.add(relatorios);
        JMenu sessao = new JMenu("Sessão"); item(sessao, "Menu principal", () -> principal.exibir("Menu principal")); item(sessao, "Sair", this::mostrarLogin); barra.add(sessao); return barra;
    }
    private void item(JMenu menu, String texto, Runnable acao) { JMenuItem item = new JMenuItem(texto); item.addActionListener(e -> acao.run()); menu.add(item); }
    private void fechar() {
        if (fechada) return;
        try { controller.close(); fechada = true; dispose(); }
        catch (RuntimeException e) { Tarefas.erro(this, e); }
    }
}
