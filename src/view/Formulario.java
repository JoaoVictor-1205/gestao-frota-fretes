package view;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.List;

public final class Formulario extends JPanel {
    public record Opcao(int id, String texto) { @Override public String toString() { return texto; } }
    private final Map<String, JComponent> campos = new LinkedHashMap<>();
    private final Map<String, Object> padroes = new HashMap<>();
    private int posicao;

    public Formulario() { super(new GridBagLayout()); setOpaque(false); }
    private void adicionar(String chave, String titulo, JComponent componente) {
        campos.put(chave, componente); componente.setName(chave);
        componente.setPreferredSize(new Dimension(160, 34));
        componente.setMinimumSize(new Dimension(70, 34));
        componente.getAccessibleContext().setAccessibleName(titulo);
        JLabel label = new JLabel(titulo); label.setLabelFor(componente);
        JPanel grupo = new JPanel(new BorderLayout(0, 6)); grupo.setOpaque(false);
        grupo.add(label, BorderLayout.NORTH); grupo.add(componente, BorderLayout.CENTER);
        GridBagConstraints c = new GridBagConstraints(); c.gridx = posicao % 4; c.gridy = posicao / 4;
        c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.insets = new Insets(0, 0, 13, c.gridx == 3 ? 0 : 15);
        add(grupo, c); posicao++;
    }
    public Formulario texto(String chave, String titulo, String padrao) {
        JTextField texto = new JTextField(); texto.setBorder(new CompoundBorder(new LineBorder(Tema.LINHA), new EmptyBorder(6, 9, 6, 9)));
        adicionar(chave, titulo, texto); padroes.put(chave, padrao); return this;
    }
    public Formulario mascarado(String chave, String titulo, String padrao, Mascaras.Tipo tipo) {
        texto(chave, titulo, padrao);
        Mascaras.aplicar((JTextField) campos.get(chave), tipo);
        return this;
    }
    public Formulario decimal(String chave, String titulo) {
        texto(chave, titulo, "");
        CamposNumericos.decimal((JTextField) campos.get(chave));
        return this;
    }
    public Formulario numeroInteiro(String chave, String titulo) {
        texto(chave, titulo, "");
        CamposNumericos.inteiro((JTextField) campos.get(chave));
        return this;
    }
    public Formulario escolha(String chave, String titulo, String... opcoes) {
        JComboBox<String> combo = new JComboBox<>(opcoes); adicionar(chave, titulo, combo);
        if (opcoes.length > 0) padroes.put(chave, opcoes[0]); return this;
    }
    public Formulario vinculo(String chave, String titulo) { adicionar(chave, titulo, new JComboBox<Opcao>()); return this; }
    public String texto(String chave) { return ((JTextField) campos.get(chave)).getText().trim(); }
    public String escolha(String chave) { return Objects.toString(((JComboBox<?>) campos.get(chave)).getSelectedItem(), ""); }
    public int vinculo(String chave) {
        Object item = ((JComboBox<?>) campos.get(chave)).getSelectedItem();
        if (!(item instanceof Opcao opcao) || opcao.id() == 0) throw new IllegalArgumentException("Selecione " + campos.get(chave).getAccessibleContext().getAccessibleName().replace(" *", "").toLowerCase(Locale.ROOT) + ".");
        return opcao.id();
    }
    public void preencher(String chave, Object valor) {
        JComponent campo = campos.get(chave);
        if (campo instanceof JTextField texto) Mascaras.preencher(texto, valor == null ? "" : valor.toString());
        else if (campo instanceof JComboBox<?> combo) {
            if (valor instanceof Integer id) {
                for (int i = 0; i < combo.getItemCount(); i++) if (combo.getItemAt(i) instanceof Opcao opcao && opcao.id() == id) { combo.setSelectedIndex(i); return; }
                combo.setSelectedIndex(combo.getItemCount() > 0 ? 0 : -1);
            } else combo.setSelectedItem(valor);
        }
    }
    @SuppressWarnings("unchecked")
    public void opcoes(String chave, List<Opcao> valores) {
        opcoes(chave, valores, "Selecione...");
    }
    @SuppressWarnings("unchecked")
    public void opcoes(String chave, List<Opcao> valores, String primeiraOpcao) {
        JComboBox<Opcao> combo = (JComboBox<Opcao>) campos.get(chave);
        Object selecionado = combo.getSelectedItem();
        combo.removeAllItems(); combo.addItem(new Opcao(0, primeiraOpcao)); valores.forEach(combo::addItem);
        if (selecionado instanceof Opcao opcao) preencher(chave, opcao.id());
    }
    public void limpar() {
        campos.forEach((chave, campo) -> {
            if (campo instanceof JTextField texto) Mascaras.preencher(texto, Objects.toString(padroes.get(chave), ""));
            else if (campo instanceof JComboBox<?> combo) combo.setSelectedIndex(combo.getItemCount() > 0 ? 0 : -1);
        });
    }
    public void habilitar(boolean habilitado) { campos.values().forEach(c -> c.setEnabled(habilitado)); }
    public LocalDate data(String chave, boolean opcional) { return Formatacao.data(texto(chave), opcional); }
    public BigDecimal numero(String chave, String titulo) { return Formatacao.numero(texto(chave), titulo); }
    public int inteiro(String chave, String titulo) { return Formatacao.inteiro(texto(chave), titulo); }
}
