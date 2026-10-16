package model;

import java.io.Serializable;

/** Contrato comum dos registros persistidos no arquivo local e no PostgreSQL. */
public interface Entidade extends Serializable {
    int id();

    Entidade comId(int id);
}
