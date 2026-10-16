package view;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Locale;

public final class Formatacao {
    private static final Locale BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private Formatacao() {}
    public static String data(LocalDate data) { return data == null ? "" : data.format(DATA); }
    public static LocalDate data(String texto, boolean opcional) {
        if (opcional && texto.isBlank()) return null;
        try { return LocalDate.parse(texto.trim(), DATA); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Informe uma data válida no formato dd/mm/aaaa."); }
    }
    public static String moeda(BigDecimal numero) { return NumberFormat.getCurrencyInstance(BR).format(numero); }
    public static String decimal(BigDecimal numero) { return NumberFormat.getNumberInstance(BR).format(numero); }
    public static BigDecimal numero(String texto, String campo) {
        String valor = texto.trim();
        if (valor.matches("-?\\d{1,3}(\\.\\d{3})+(,\\d{1,2})?")) valor = valor.replace(".", "").replace(',', '.');
        else if (valor.matches("-?\\d+(,\\d{1,2})?")) valor = valor.replace(',', '.');
        else if (!valor.matches("-?\\d+\\.\\d{1,2}")) throw new IllegalArgumentException(campo + ": informe um número, por exemplo 1.500,00.");
        try { return new BigDecimal(valor); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(campo + ": número inválido."); }
    }
    public static int inteiro(String texto, String campo) {
        if (!texto.trim().matches("-?(\\d+|\\d{1,3}(\\.\\d{3})+)")) throw new IllegalArgumentException(campo + ": informe um número inteiro.");
        try { return Integer.parseInt(texto.trim().replace(".", "")); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(campo + ": número fora do limite permitido."); }
    }
    public static String documento(String valor) {
        if (valor.length() == 11) return valor.replaceFirst("(\\d{3})(\\d{3})(\\d{3})(\\d{2})", "$1.$2.$3-$4");
        if (valor.length() == 14) return valor.replaceFirst("(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})", "$1.$2.$3/$4-$5");
        return valor;
    }
    public static String telefone(String valor) {
        String digitos = valor.replaceAll("\\D", "");
        if (digitos.length() == 11) return digitos.replaceFirst("(\\d{2})(\\d{5})(\\d{4})", "($1) $2-$3");
        if (digitos.length() == 10) return digitos.replaceFirst("(\\d{2})(\\d{4})(\\d{4})", "($1) $2-$3");
        return valor;
    }
    public static String placa(String valor) { return valor.matches("[A-Z]{3}\\d{4}") ? valor.substring(0, 3) + "-" + valor.substring(3) : valor; }
    public static String busca(String valor) { return Normalizer.normalize(valor, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replaceAll("[^\\p{L}\\p{N}]", "").toLowerCase(Locale.ROOT); }
}
