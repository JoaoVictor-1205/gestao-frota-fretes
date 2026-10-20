package view;

import javax.swing.JTextField;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/** Restringe caracteres e formata números ao sair do campo, preservando entradas inválidas. */
public final class CamposNumericos {
    private static final Locale BR = Locale.forLanguageTag("pt-BR");

    private CamposNumericos() {}

    public static void decimal(JTextField campo) {
        configurar(campo, true);
        campo.setToolTipText("Exemplos: 1.500,00 ou 1500.50; até duas casas decimais.");
    }

    public static void inteiro(JTextField campo) {
        configurar(campo, false);
        campo.setToolTipText("Exemplos: 85000 ou 85.000; somente números inteiros.");
    }

    private static void configurar(JTextField campo, boolean decimal) {
        ((AbstractDocument) campo.getDocument()).setDocumentFilter(new Filtro(decimal));
        campo.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent evento) {
                String texto = campo.getText();
                if (texto.isBlank()) return;
                try {
                    NumberFormat formato = NumberFormat.getNumberInstance(BR);
                    formato.setGroupingUsed(true);
                    formato.setMaximumIntegerDigits(Integer.MAX_VALUE);
                    if (decimal) {
                        formato.setMinimumFractionDigits(2);
                        formato.setMaximumFractionDigits(2);
                        formato.setRoundingMode(RoundingMode.UNNECESSARY);
                        campo.setText(formato.format(Formatacao.numero(texto, "Valor")));
                    } else {
                        formato.setMaximumFractionDigits(0);
                        campo.setText(formato.format(Formatacao.inteiro(texto, "Número")));
                    }
                } catch (IllegalArgumentException | ArithmeticException e) {
                    // O parser e o controller apresentarão o erro ao salvar, sem corrigir o valor.
                }
            }
        });
    }

    private static final class Filtro extends DocumentFilter {
        private final String caracteresPermitidos;

        private Filtro(boolean decimal) {
            caracteresPermitidos = decimal ? "[0-9.,\\-\\s]*" : "[0-9.\\-\\s]*";
        }

        @Override public void insertString(FilterBypass fb, int offset, String texto, AttributeSet atributos)
                throws BadLocationException {
            replace(fb, offset, 0, texto, atributos);
        }

        @Override public void replace(FilterBypass fb, int offset, int tamanho, String texto, AttributeSet atributos)
                throws BadLocationException {
            String atual = fb.getDocument().getText(0, fb.getDocument().getLength());
            String candidato = atual.substring(0, offset) + (texto == null ? "" : texto)
                    + atual.substring(offset + tamanho);
            if (candidato.matches(caracteresPermitidos)) super.replace(fb, offset, tamanho, texto, atributos);
        }
    }
}
