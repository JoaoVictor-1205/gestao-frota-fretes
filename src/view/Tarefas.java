package view;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public final class Tarefas {
    private Tarefas() {}
    public static <T> void executar(JComponent origem, Callable<T> acao, Consumer<T> sucesso, Consumer<Boolean> ocupacao) {
        ocupacao.accept(true);
        origem.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return acao.call(); }
            @Override protected void done() {
                origem.setCursor(Cursor.getDefaultCursor()); ocupacao.accept(false);
                try { sucesso.accept(get()); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                catch (ExecutionException e) { erro(origem, e.getCause()); }
                catch (RuntimeException e) { erro(origem, e); }
            }
        }.execute();
    }
    public static void erro(Component origem, Throwable erro) {
        String mensagem = erro.getMessage();
        JOptionPane.showMessageDialog(origem, mensagem == null ? "Não foi possível concluir a operação." : mensagem, "Confira os dados", JOptionPane.WARNING_MESSAGE);
    }
    public static boolean confirmar(Component origem, String texto) {
        return JOptionPane.showConfirmDialog(origem, texto, "Confirmar operação", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }
}
