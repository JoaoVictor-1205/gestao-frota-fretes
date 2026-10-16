package view;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class Tema {
    public static final Color FUNDO = new Color(242, 245, 249);
    public static final Color TEXTO = new Color(25, 40, 60);
    public static final Color SECUNDARIO = new Color(100, 116, 139);
    public static final Color AZUL = new Color(30, 92, 193);
    public static final Color LINHA = new Color(220, 227, 235);
    public static final Color MARINHO = new Color(18, 35, 58);
    public static final Font NORMAL = new Font("Segoe UI", Font.PLAIN, 13);
    private Tema() {}

    public static void instalar() {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); }
        catch (Exception ignored) { /* O visual padrão do Swing continua disponível. */ }
        for (String key : new String[]{"Label.font", "Button.font", "TextField.font", "PasswordField.font", "ComboBox.font", "Table.font", "TableHeader.font", "Menu.font", "MenuItem.font", "OptionPane.messageFont", "OptionPane.buttonFont"}) UIManager.put(key, NORMAL);
        UIManager.put("Panel.background", FUNDO);
        UIManager.put("Label.foreground", TEXTO);
        UIManager.put("TextField.background", Color.WHITE);
        UIManager.put("ComboBox.background", Color.WHITE);
        UIManager.put("Table.selectionBackground", new Color(222, 234, 253));
        UIManager.put("Table.selectionForeground", TEXTO);
        UIManager.put("Table.gridColor", LINHA);
        UIManager.put("Button.focus", new Color(170, 200, 244));
        UIManager.put("ScrollPane.border", BorderFactory.createLineBorder(LINHA));
    }

    public static JLabel titulo(String texto, int tamanho) {
        JLabel l = new JLabel(texto); l.setFont(NORMAL.deriveFont(Font.BOLD, tamanho)); return l;
    }

    public static JLabel legenda(String texto) {
        JLabel l = new JLabel(texto); l.setForeground(SECUNDARIO); return l;
    }

    public static JButton botao(String texto, boolean principal) {
        JButton b = new JButton(texto);
        b.setFocusPainted(true); b.setOpaque(true); b.setContentAreaFilled(true);
        b.setBackground(principal ? AZUL : Color.WHITE);
        b.setForeground(principal ? Color.WHITE : TEXTO);
        b.setBorder(new CompoundBorder(new LineBorder(principal ? AZUL : LINHA), new EmptyBorder(9, 16, 9, 16)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); return b;
    }

    public static JPanel cartao(String titulo) {
        JPanel p = new JPanel(new BorderLayout(0, 14)); p.setBackground(Color.WHITE);
        p.setBorder(new CompoundBorder(new LineBorder(LINHA), new EmptyBorder(18, 20, 18, 20)));
        if (titulo != null) p.add(Tema.titulo(titulo, 16), BorderLayout.NORTH);
        return p;
    }

    public static void tabela(JTable tabela) {
        tabela.setRowHeight(34); tabela.setShowVerticalLines(false); tabela.setIntercellSpacing(new Dimension(0, 1));
        tabela.setFillsViewportHeight(true); tabela.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabela.getTableHeader().setFont(NORMAL.deriveFont(Font.BOLD));
        tabela.getTableHeader().setBackground(new Color(245, 248, 252));
        tabela.getTableHeader().setPreferredSize(new Dimension(0, 36));
        tabela.setAutoCreateRowSorter(true);
        tabela.setDefaultEditor(Object.class, null);
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object valor, boolean selecionado, boolean foco, int linha, int coluna) {
                String cabecalho = t.getColumnName(coluna);
                Object texto = valor instanceof LocalDate data ? Formatacao.data(data)
                    : valor instanceof BigDecimal numero ? (cabecalho.startsWith("Capacidade") ? Formatacao.decimal(numero) : Formatacao.moeda(numero))
                    : valor instanceof Integer numero && cabecalho.equals("Km") ? Formatacao.decimal(BigDecimal.valueOf(numero)) : valor;
                super.getTableCellRendererComponent(t, texto, selecionado, foco, linha, coluna);
                setHorizontalAlignment(valor instanceof Number ? RIGHT : LEFT);
                setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8)); return this;
            }
        };
        tabela.setDefaultRenderer(Object.class, renderer); tabela.setDefaultRenderer(Number.class, renderer);
        tabela.setDefaultRenderer(LocalDate.class, renderer);
    }

    public static DefaultTableModel modelo(String[] colunas) {
        return new DefaultTableModel(colunas, 0) {
            @Override public boolean isCellEditable(int linha, int coluna) { return false; }
            @Override public Class<?> getColumnClass(int coluna) {
                String nome = getColumnName(coluna);
                if (nome.equals("Código") || nome.equals("Ano") || nome.equals("Km")) return Integer.class;
                if (nome.equals("Saída") || nome.equals("Data")) return LocalDate.class;
                if (nome.equals("Valor") || nome.equals("Valor do frete") || nome.equals("Custo") || nome.startsWith("Capacidade")) return BigDecimal.class;
                return String.class;
            }
        };
    }
}
