package tests;

import app.Configuracao;
import app.DadosDemonstracao;
import controller.FrotaController;
import dao.ArquivoRepositorio;
import model.*;
import view.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;

/** Testa os painéis na EDT e renderiza as oito telas sem exigir um display. */
public final class InterfaceTest {
    private static int verificacoes;
    private InterfaceTest() {}
    public static void main(String[] args) throws Exception {
        Tema.instalar(); testarFormatacao();
        Path saida = Path.of(".validation", "telas"); Files.createDirectories(saida);
        try (FrotaController controller = new FrotaController(new ArquivoRepositorio())) {
            DadosDemonstracao.carregar(controller);
            DadosTela dados = DadosTela.carregar(controller);
            PainelPrincipal principal = edt(() -> new PainelPrincipal(controller, () -> {}, true));
            edt(() -> {
                AtomicBoolean entrou = new AtomicBoolean();
                Configuracao config = new Configuracao();
                LoginPanel login = new LoginPanel(config, () -> entrou.set(true), () -> {}, true);
                verificar(componentes(login, JPasswordField.class).size() == 1, "Senha usa JPasswordField");
                if (config.senhaPadrao() && config.usuarioPadrao()) {
                    ((JTextField) campo(login, "usuario")).setText("administrador");
                    ((JPasswordField) campo(login, "senha")).setText("incorreta"); botao(login, "Entrar").doClick();
                    verificar(!entrou.get() && componentes(login, JLabel.class).stream().anyMatch(l -> l.getText().equals("Usuário ou senha inválidos")), "Credencial inválida mostra mensagem prevista");
                    ((JPasswordField) campo(login, "senha")).setText("admin123"); botao(login, "Entrar").doClick();
                    verificar(entrou.get(), "Login válido aciona menu");
                    verificar(((JPasswordField) campo(login, "senha")).getPassword().length == 0, "Login limpa senha");
                }
                renderizar(login, 1360, 900, saida.resolve("01-login.png"));
                int numero = 2;
                for (String nome : PainelPrincipal.TELAS) {
                    JPanel painel = principal.tela(nome);
                    if (painel instanceof CadastroPanel<?> cadastro) cadastro.apresentarDados(dados);
                    else if (painel instanceof MenuPanel menu) menu.apresentarDados(dados);
                    else if (painel instanceof RelatorioPanel relatorio) {
                        relatorio.apresentarDados(dados);
                        relatorio.apresentarRelatorio(controller.relatorio(LocalDate.now().withDayOfMonth(1), LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()), null));
                    }
                    principal.exibirSemCarregar(nome);
                    renderizar(principal, 1360, 900, saida.resolve(String.format("%02d-%s.png", numero++, Formatacao.busca(nome))));
                    verificar(painel.isVisible(), "Navegação exibe " + nome);
                    if (painel instanceof CadastroPanel<?> cadastro) {
                        JTable tabela = componentes(cadastro, JTable.class).get(0);
                        verificar(tabela.getHeight() >= 120, "Tabela visível em " + nome);
                        verificar(tabela.getRowCount() > 0, "Dados carregados em " + nome);
                        tabela.setRowSelectionInterval(0, 0);
                        verificar(botao(cadastro, nome.equals("Viagens / Fretes") ? "Cancelar viagem" : "Excluir").isEnabled(), "Seleção habilita ação em " + nome);
                        JTextField busca = (JTextField) campo(cadastro, "busca"); busca.setText("nenhum-resultadovalido");
                        verificar(tabela.getRowCount() == 0, "Busca filtra em " + nome);
                        verificar(!botao(cadastro, nome.equals("Viagens / Fretes") ? "Cancelar viagem" : "Excluir").isEnabled(), "Filtro limpa ID selecionado em " + nome);
                        busca.setText("");
                        tabela.setRowSelectionInterval(0, 0); botao(cadastro, "Novo").doClick();
                        verificar(tabela.getSelectedRow() == -1, "Novo retira seleção em " + nome);
                    }
                }
                principal.exibirSemCarregar("Viagens / Fretes");
                renderizar(principal, 1180, 820, saida.resolve("09-viagens-compacta.png"));
                verificar(componentes(principal.tela("Viagens / Fretes"), JTable.class).get(0).getHeight() >= 80, "Tabela cabe na janela mínima");
                JPanel manutencao = principal.tela("Manutenção");
                JComboBox<?> filtroVeiculo = componentes(manutencao, JComboBox.class).stream().filter(c -> "Filtrar por veículo".equals(c.getAccessibleContext().getAccessibleName())).findFirst().orElseThrow();
                filtroVeiculo.setSelectedIndex(2);
                verificar(componentes(manutencao, JTable.class).get(0).getRowCount() == 0, "Filtro de manutenção restringe veículo sem serviços");
                filtroVeiculo.setSelectedIndex(1);
                verificar(componentes(manutencao, JTable.class).get(0).getRowCount() == 1, "Filtro de manutenção recupera histórico do veículo");
                JPanel viagens = principal.tela("Viagens / Fretes");
                ((JTextField) campo(viagens, "busca")).setText(Formatacao.data(dados.viagens().get(0).dataSaida()));
                verificar(componentes(viagens, JTable.class).get(0).getRowCount() == 1, "Busca aceita data no formato exibido");
                ((JTextField) campo(viagens, "busca")).setText("");
                var modeloOrdenacao = Tema.modelo(new String[]{"Código", "Saída", "Valor"});
                modeloOrdenacao.addRow(new Object[]{10, LocalDate.of(2026, 1, 2), new BigDecimal("900")});
                modeloOrdenacao.addRow(new Object[]{2, LocalDate.of(2025, 12, 31), new BigDecimal("1200")});
                JTable ordenacao = new JTable(modeloOrdenacao); Tema.tabela(ordenacao);
                ordenacao.getRowSorter().toggleSortOrder(0);
                verificar(ordenacao.getValueAt(0, 0).equals(2), "Código ordena numericamente");
                ordenacao.getRowSorter().toggleSortOrder(1);
                verificar(ordenacao.getValueAt(0, 0).equals(2), "Data ordena cronologicamente");
                ordenacao.getRowSorter().toggleSortOrder(2);
                verificar(ordenacao.getValueAt(0, 0).equals(10), "Valor ordena numericamente");
                return null;
            });
            // Salva pela ação real do botão, aguarda SwingWorker e confere controller e tabela.
            JPanel clientes = principal.tela("Clientes");
            int antes = controller.listar(Cliente.class).size();
            edt(() -> {
                botao(clientes, "Novo").doClick();
                texto(clientes, "nome", "Cliente cadastrado pela tela"); texto(clientes, "documento", "123.456.789-09");
                texto(clientes, "telefone", "11999998888"); texto(clientes, "endereco", "Rua de teste, 10"); botao(clientes, "Salvar").doClick(); return null;
            });
            aguardar(() -> controller.listar(Cliente.class).size() == antes + 1 && edt(() -> componentes(clientes, JTable.class).get(0).getRowCount()) == antes + 1);
            verificar(controller.listar(Cliente.class).stream().anyMatch(c -> c.nome().equals("Cliente cadastrado pela tela") && c.cpfCnpj().equals("12345678909")), "Salvar pela View persiste e normaliza documento");
            edt(() -> {
                JTable tabela = componentes(clientes, JTable.class).get(0); tabela.setRowSelectionInterval(tabela.getRowCount() - 1, tabela.getRowCount() - 1);
                texto(clientes, "nome", "Cliente atualizado pela tela"); botao(clientes, "Salvar").doClick(); return null;
            });
            aguardar(() -> controller.listar(Cliente.class).stream().anyMatch(c -> c.nome().equals("Cliente atualizado pela tela")) && edt(() -> botao(clientes, "Salvar").isEnabled()));
            verificar(controller.listar(Cliente.class).size() == antes + 1, "Editar pela View atualiza sem inserir outro registro");
        }
        System.out.println("InterfaceTest: " + verificacoes + " verificações passaram; oito telas renderizadas em .validation/telas/ (headless).");
    }
    private static void testarFormatacao() {
        verificar(Formatacao.numero("1.500,00", "Valor").compareTo(new BigDecimal("1500")) == 0, "Moeda pt-BR");
        verificar(Formatacao.numero("16.000", "Capacidade").compareTo(new BigDecimal("16000")) == 0, "Milhar pt-BR");
        verificar(Formatacao.numero("1500.50", "Valor").compareTo(new BigDecimal("1500.50")) == 0, "Decimal com ponto");
        verificar(Formatacao.inteiro("85.000", "Km") == 85000, "Quilometragem pt-BR");
        verificar(Formatacao.data("29/02/2024", false).equals(LocalDate.of(2024, 2, 29)), "Data bissexta");
        esperar(() -> Formatacao.data("29/02/2025", false)); esperar(() -> Formatacao.numero("1.500,001", "Valor"));
        esperar(() -> Formatacao.inteiro("85,5", "Km")); verificar(Formatacao.data("", true) == null, "Chegada opcional");
        verificar(Formatacao.documento("11222333000181").equals("11.222.333/0001-81"), "Máscara CNPJ");
        verificar(Formatacao.busca("João — 529.982").equals("joao529982"), "Busca ignora pontuação e acentos");
    }
    private static void esperar(Runnable acao) { verificacoes++; try { acao.run(); } catch (IllegalArgumentException esperada) { return; } throw new AssertionError("Entrada inválida aceita"); }
    private static void verificar(boolean ok, String mensagem) { verificacoes++; if (!ok) throw new AssertionError(mensagem); }
    private static <T> T edt(Callable<T> acao) throws Exception { FutureTask<T> tarefa = new FutureTask<>(acao); SwingUtilities.invokeAndWait(tarefa); return tarefa.get(); }
    private static <T extends Component> List<T> componentes(Component raiz, Class<T> tipo) {
        List<T> resultado = new ArrayList<>(); if (tipo.isInstance(raiz)) resultado.add(tipo.cast(raiz));
        if (raiz instanceof Container c) for (Component filho : c.getComponents()) resultado.addAll(componentes(filho, tipo)); return resultado;
    }
    private static Component campo(Component raiz, String nome) { return componentes(raiz, JComponent.class).stream().filter(c -> nome.equals(c.getName())).findFirst().orElseThrow(); }
    private static JButton botao(Component raiz, String texto) { return componentes(raiz, JButton.class).stream().filter(b -> b.getText().equals(texto)).findFirst().orElseThrow(); }
    private static void texto(Component raiz, String nome, String valor) { ((JTextField) campo(raiz, nome)).setText(valor); }
    private static void renderizar(JPanel painel, int largura, int altura, Path destino) throws Exception {
        painel.setSize(largura, altura); layout(painel);
        BufferedImage imagem = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB); Graphics2D g = imagem.createGraphics();
        painel.printAll(g); g.dispose(); ImageIO.write(imagem, "png", destino.toFile());
    }
    private static void layout(Container pai) { pai.doLayout(); for (Component c : pai.getComponents()) if (c instanceof Container filho) layout(filho); }
    private static void aguardar(Callable<Boolean> concluido) throws Exception {
        long limite = System.nanoTime() + 10_000_000_000L;
        while (!concluido.call()) { if (System.nanoTime() > limite) throw new AssertionError("Tempo excedido na operação SwingWorker"); Thread.sleep(30); }
    }
}
