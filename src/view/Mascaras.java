package view;

import java.util.Locale;
import javax.swing.JTextField;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

/** Máscaras sem placeholders: campos vazios continuam vazios, inclusive datas opcionais. */
public final class Mascaras {
    public enum Tipo {
        CPF(11, "000.000.000-00"),
        CPF_CNPJ(14, "000.000.000-00 ou 00.000.000/0000-00"),
        TELEFONE(11, "(00) 0000-0000 ou (00) 00000-0000"),
        CNH(11, "11 dígitos"),
        PLACA(7, "ABC-1234 ou ABC1D23"),
        DATA(8, "dd/mm/aaaa"),
        ANO(4, "aaaa");

        private final int limite;
        private final String exemplo;
        Tipo(int limite, String exemplo) { this.limite = limite; this.exemplo = exemplo; }
    }

    private Mascaras() { }

    public static void aplicar(JTextField campo, Tipo tipo) {
        ((AbstractDocument) campo.getDocument()).setDocumentFilter(new Filtro(campo, tipo));
        campo.setToolTipText(tipo.exemplo);
        preencher(campo, campo.getText());
    }

    /** Preserva valores já salvos fora da máscara, sem reaproveitar o campo do registro anterior. */
    public static void preencher(JTextField campo, String valor) {
        AbstractDocument documento = (AbstractDocument) campo.getDocument();
        DocumentFilter filtro = documento.getDocumentFilter();
        if (!(filtro instanceof Filtro mascara)) { campo.setText(valor); return; }
        String dados = mascara.extrair(valor);
        String exibido = mascara.permitida(valor) && mascara.cabe(dados) ? mascara.formatar(dados) : valor;
        documento.setDocumentFilter(null);
        try { campo.setText(exibido); }
        finally { documento.setDocumentFilter(filtro); }
    }

    private static final class Filtro extends DocumentFilter {
        private final JTextField campo;
        private final Tipo tipo;

        private Filtro(JTextField campo, Tipo tipo) { this.campo = campo; this.tipo = tipo; }

        @Override public void insertString(FilterBypass fb, int offset, String texto, AttributeSet atributos) throws BadLocationException {
            replace(fb, offset, 0, texto, atributos);
        }

        @Override public void remove(FilterBypass fb, int offset, int tamanho) throws BadLocationException {
            replace(fb, offset, tamanho, "", null);
        }

        @Override public void replace(FilterBypass fb, int offset, int tamanho, String texto, AttributeSet atributos) throws BadLocationException {
            String entrada = texto == null ? "" : texto;
            // Rejeita a alteração inteira em vez de transformar uma colagem inválida em outro documento.
            if (!permitida(entrada)) return;
            String anterior = fb.getDocument().getText(0, fb.getDocument().getLength());
            String dados = extrair(anterior);
            String novos = extrair(entrada);
            int inicio = extrair(anterior.substring(0, offset)).length();
            int fim = extrair(anterior.substring(0, offset + tamanho)).length();

            // Permite corrigir valores legados sem truncar código de país ou ramal.
            if (entrada.isEmpty() && tamanho > 0 && (!permitida(anterior) || !cabe(dados))) {
                fb.replace(offset, tamanho, "", atributos);
                campo.setCaretPosition(offset);
                return;
            }

            // Backspace/Delete sobre a pontuação também remove o dígito vizinho.
            if (entrada.isEmpty() && tamanho > 0 && inicio == fim) {
                if (campo.getCaretPosition() > offset && inicio > 0) inicio--;
                else if (fim < dados.length()) fim++;
            }
            String atualizado = dados.substring(0, inicio) + novos + dados.substring(fim);
            if (atualizado.length() > tipo.limite) return;
            if (!novos.isEmpty() && !cabe(atualizado)) return;
            String formatado = formatar(atualizado);
            int posicao = posicao(formatado, inicio + novos.length());
            fb.replace(0, anterior.length(), formatado, atributos);
            // Mantém o cursor junto à edição, mesmo quando muda a máscara de CPF para CNPJ.
            campo.setCaretPosition(posicao);
        }

        private boolean permitida(String entrada) {
            if (tipo == Tipo.DATA) {
                String compacta = entrada.replaceAll("\\s", "");
                if (!compacta.matches("[0-9/]*")) return false;
                // Uma data inteira com separadores precisa estar na ordem exibida pelo campo.
                return !compacta.contains("/") || extrair(compacta).length() != 8
                        || compacta.matches("[0-9]{2}/[0-9]{2}/[0-9]{4}");
            }
            return entrada.matches(tipo == Tipo.PLACA ? "[a-zA-Z0-9\\s-]*" : "[0-9\\s.()/\\-]*");
        }

        private String extrair(String texto) {
            return (tipo == Tipo.PLACA ? texto.replaceAll("[^a-zA-Z0-9]", "").toUpperCase(Locale.ROOT)
                    : texto.replaceAll("[^0-9]", ""));
        }

        private boolean cabe(String dados) {
            return dados.length() <= tipo.limite && (tipo != Tipo.PLACA
                    || dados.matches("[A-Z]{0,3}|[A-Z]{3}[0-9](?:[A-Z0-9][0-9]{0,2})?"));
        }

        private String formatar(String dados) {
            String padrao = switch (tipo) {
                case CPF -> "###.###.###-##";
                case CPF_CNPJ -> dados.length() <= 11 ? "###.###.###-##" : "##.###.###/####-##";
                case TELEFONE -> dados.length() <= 10 ? "(##) ####-####" : "(##) #####-####";
                case CNH -> "###########";
                case PLACA -> dados.matches("[A-Z]{3}[0-9]{1,4}") ? "###-####" : "#######";
                case DATA -> "##/##/####";
                case ANO -> "####";
            };
            StringBuilder resultado = new StringBuilder();
            int indice = 0;
            for (int i = 0; i < padrao.length() && indice < dados.length(); i++) {
                char simbolo = padrao.charAt(i);
                resultado.append(simbolo == '#' ? dados.charAt(indice++) : simbolo);
            }
            return resultado.toString();
        }

        private int posicao(String formatado, int quantidade) {
            if (quantidade == 0) return 0;
            int lidos = 0;
            for (int i = 0; i < formatado.length(); i++) {
                char c = formatado.charAt(i);
                if (c >= '0' && c <= '9' || tipo == Tipo.PLACA && c >= 'A' && c <= 'Z') lidos++;
                if (lidos == quantidade) return i + 1;
            }
            return formatado.length();
        }
    }
}
