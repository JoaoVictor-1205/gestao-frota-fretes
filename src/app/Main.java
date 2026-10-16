package app;

import controller.FrotaController;
import dao.ArquivoRepositorio;
import dao.JdbcRepositorio;
import dao.Repositorio;
import view.JanelaPrincipal;
import view.Tema;
import javax.swing.*;
import java.nio.file.Path;
import java.util.Arrays;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        Tema.instalar();
        boolean demo = Arrays.asList(args).contains("--demo");
        // A abertura da conexão e do arquivo acontece fora da thread de eventos do Swing.
        try {
            Configuracao configuracao = new Configuracao();
            Repositorio repositorio = demo ? new ArquivoRepositorio(Path.of("data/demo.dat")) : switch (configuracao.modo()) {
                case "arquivo" -> new ArquivoRepositorio(configuracao.arquivo());
                case "memoria" -> new ArquivoRepositorio();
                case "postgres" -> new JdbcRepositorio(configuracao.dbUrl(), configuracao.dbUsuario(), configuracao.dbSenha());
                default -> throw new IllegalArgumentException("Modo inválido. Use arquivo, memoria ou postgres.");
            };
            FrotaController controller = new FrotaController(repositorio);
            try {
                if (demo) DadosDemonstracao.carregar(controller);
                SwingUtilities.invokeLater(() -> {
                    try { new JanelaPrincipal(controller, configuracao, demo).setVisible(true); }
                    catch (RuntimeException e) { controller.close(); TarefasInicializacao.erro(e); }
                });
            } catch (RuntimeException e) {
                controller.close();
                throw e;
            }
        } catch (RuntimeException e) {
            SwingUtilities.invokeLater(() -> TarefasInicializacao.erro(e));
        }
    }

    private static final class TarefasInicializacao {
        static void erro(RuntimeException e) {
            JOptionPane.showMessageDialog(null, "Não foi possível iniciar o sistema.\n\n" + e.getMessage()
                + "\n\nConfira config.properties e as instruções do README.", "Erro de inicialização", JOptionPane.ERROR_MESSAGE);
        }
    }
}
