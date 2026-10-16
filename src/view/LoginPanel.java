package view;

import app.Configuracao;
import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

public final class LoginPanel extends JPanel {
    private final JTextField usuario = new JTextField(24);
    private final JPasswordField senha = new JPasswordField(24);
    private final JLabel erro = new JLabel(" ");

    public LoginPanel(Configuracao configuracao, Runnable entrar, Runnable fechar, boolean demo) {
        super(new GridLayout(1, 2));
        JPanel marca = new JPanel(new GridBagLayout()); marca.setBackground(Tema.MARINHO);
        JPanel texto = new JPanel(); texto.setOpaque(false); texto.setLayout(new BoxLayout(texto, BoxLayout.Y_AXIS));
        JLabel etiqueta = new JLabel("CONTROLE DA TRANSPORTADORA"); etiqueta.setForeground(new Color(128, 174, 236)); etiqueta.setFont(Tema.NORMAL.deriveFont(12f)); texto.add(etiqueta); texto.add(Box.createVerticalStrut(24));
        JLabel titulo = Tema.titulo("<html>Gestão de Frota<br><font color='#80B3FF'>e Fretes</font></html>", 40); titulo.setForeground(Color.WHITE); texto.add(titulo); texto.add(Box.createVerticalStrut(24));
        JLabel descricao = new JLabel("<html>Motoristas, veículos e viagens.<br>As informações da sua operação,<br>reunidas em um só lugar.</html>"); descricao.setFont(Tema.NORMAL.deriveFont(17f)); descricao.setForeground(new Color(191, 206, 226)); texto.add(descricao);
        texto.add(Box.createVerticalStrut(70)); JLabel rodape = new JLabel("JAVA SWING  /  GESTÃO DE FROTA"); rodape.setFont(Tema.NORMAL.deriveFont(11f)); rodape.setForeground(new Color(138, 157, 183)); texto.add(rodape);
        marca.add(texto); add(marca);
        JPanel area = new JPanel(new GridBagLayout()); area.setBackground(Color.WHITE);
        JPanel formulario = new JPanel(); formulario.setOpaque(false); formulario.setLayout(new BoxLayout(formulario, BoxLayout.Y_AXIS)); formulario.setBorder(BorderFactory.createEmptyBorder(40, 40, 40, 40));
        JLabel acesso = Tema.titulo("Acesso do administrador", 26); acesso.setAlignmentX(LEFT_ALIGNMENT); formulario.add(acesso); formulario.add(Box.createVerticalStrut(12));
        formulario.add(Tema.legenda("Informe seu usuário e sua senha.")); formulario.add(Box.createVerticalStrut(30));
        campo(formulario, "Usuário", usuario); formulario.add(Box.createVerticalStrut(16)); campo(formulario, "Senha", senha);
        usuario.setName("usuario"); senha.setName("senha"); senha.setEchoChar('●');
        erro.setForeground(new Color(174, 41, 46)); erro.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0)); formulario.add(erro);
        JPanel acoes = new JPanel(new GridLayout(1, 2, 12, 0)); acoes.setOpaque(false); acoes.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40)); acoes.setAlignmentX(LEFT_ALIGNMENT);
        JButton acessar = Tema.botao("Entrar", true), sair = Tema.botao("Sair", false); acoes.add(acessar); acoes.add(sair); formulario.add(acoes);
        if (configuracao.senhaPadrao() && configuracao.usuarioPadrao()) { formulario.add(Box.createVerticalStrut(22)); formulario.add(Tema.legenda("<html>Primeiro acesso: <b>administrador / admin123</b><br>Altere a credencial em config.properties.</html>")); }
        if (demo) { formulario.add(Box.createVerticalStrut(18)); formulario.add(Tema.legenda("Modo de demonstração · dados fictícios")); }
        acessar.addActionListener(e -> {
            char[] digitada = senha.getPassword();
            try {
                if (configuracao.autenticar(usuario.getText(), digitada)) { senha.setText(""); erro.setText(" "); entrar.run(); }
                else { erro.setText("Usuário ou senha inválidos"); senha.setText(""); senha.requestFocusInWindow(); }
            } finally { Arrays.fill(digitada, '\0'); }
        });
        senha.addActionListener(e -> acessar.doClick()); usuario.addActionListener(e -> senha.requestFocusInWindow()); sair.addActionListener(e -> fechar.run());
        area.add(formulario); add(area);
    }
    private void campo(JPanel formulario, String nome, JTextField campo) {
        JLabel label = new JLabel(nome); label.setLabelFor(campo); formulario.add(label); formulario.add(Box.createVerticalStrut(7));
        campo.setFont(Tema.NORMAL.deriveFont(15f)); campo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42)); campo.setPreferredSize(new Dimension(350, 42)); campo.setAlignmentX(LEFT_ALIGNMENT);
        campo.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Tema.LINHA), BorderFactory.createEmptyBorder(9, 11, 9, 11))); formulario.add(campo);
    }
    public void focar() { usuario.requestFocusInWindow(); }
}
