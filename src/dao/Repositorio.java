package dao;

import java.util.List;
import java.util.concurrent.Callable;
import model.Entidade;

/** Contrato comum às persistências local e PostgreSQL. */
public interface Repositorio extends AutoCloseable {
    <T extends Entidade> List<T> listar(Class<T> tipo);
    <T extends Entidade> T salvar(T entidade);
    void excluir(Class<? extends Entidade> tipo, int id);
    <T> T transacao(Callable<T> operacao);
    String descricao();
    @Override void close();
}
