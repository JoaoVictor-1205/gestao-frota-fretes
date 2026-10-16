package model;

public record Motorista(int id, String nome, String cpf, String cnh, String categoriaCnh,
                       String telefone, boolean ativo) implements Entidade {
    @Override
    public Motorista comId(int id) {
        return new Motorista(id, nome, cpf, cnh, categoriaCnh, telefone, ativo);
    }
}
