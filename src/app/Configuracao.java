package app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Properties;

public final class Configuracao {
    private final Properties valores = new Properties();

    public Configuracao() {
        Path arquivo = Path.of("config.properties");
        if (Files.exists(arquivo)) {
            try (var reader = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) {
                valores.load(reader);
            } catch (IOException e) {
                throw new IllegalStateException("Não foi possível ler config.properties.", e);
            }
        }
    }

    private String valor(String chave, String variavel, String padrao) {
        String ambiente = System.getenv(variavel);
        return ambiente != null ? ambiente : valores.getProperty(chave, padrao);
    }

    public String modo() { return valor("app.modo", "FROTA_MODO", "arquivo").trim().toLowerCase(Locale.ROOT); }
    public Path arquivo() { return Path.of(valor("app.arquivo", "FROTA_ARQUIVO", "data/frota.dat")); }
    public String dbUrl() { return valor("db.url", "FROTA_DB_URL", "jdbc:postgresql://localhost:5432/gestao_frota"); }
    public String dbUsuario() { return valor("db.usuario", "FROTA_DB_USUARIO", "postgres"); }
    public String dbSenha() { return valor("db.senha", "FROTA_DB_SENHA", ""); }
    public boolean senhaPadrao() { return valor("admin.senha", "FROTA_ADMIN_SENHA", "admin123").equals("admin123"); }
    public boolean usuarioPadrao() { return valor("admin.usuario", "FROTA_ADMIN_USUARIO", "administrador").equals("administrador"); }

    public boolean autenticar(String usuario, char[] senha) {
        String esperado = valor("admin.usuario", "FROTA_ADMIN_USUARIO", "administrador");
        byte[] recebida = new String(senha).getBytes(StandardCharsets.UTF_8);
        byte[] correta = valor("admin.senha", "FROTA_ADMIN_SENHA", "admin123").getBytes(StandardCharsets.UTF_8);
        try {
            return esperado.equals(usuario.trim()) && MessageDigest.isEqual(correta, recebida);
        } finally {
            java.util.Arrays.fill(recebida, (byte) 0);
            java.util.Arrays.fill(correta, (byte) 0);
        }
    }
}
