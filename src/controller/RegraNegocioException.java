package controller;

/** Erro de entrada ou regra de negócio apresentado ao usuário pelo formulário. */
public class RegraNegocioException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}
