package dao;

/** Erro de armazenamento que pode ser apresentado pela interface. */
public class PersistenciaException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PersistenciaException(String mensagem) { super(mensagem); }
    public PersistenciaException(String mensagem, Throwable causa) { super(mensagem, causa); }
}
