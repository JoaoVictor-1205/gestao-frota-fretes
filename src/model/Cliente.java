package model;

public record Cliente(int id, String nome, String cpfCnpj, String telefone,
                      String endereco) implements Entidade {
    @Override
    public Cliente comId(int id) {
        return new Cliente(id, nome, cpfCnpj, telefone, endereco);
    }
}
