package tests;

import app.DadosDemonstracao;
import controller.FrotaController;
import dao.ArquivoRepositorio;
import view.Mascaras;
import view.CamposNumericos;
import view.Cadastros;
import view.CadastroPanel;
import view.DadosTela;
import view.Formatacao;
import view.Formulario;
import view.RelatorioPanel;
import javax.swing.JButton;
import javax.swing.JTextField;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.FutureTask;

/** Exercita as edicoes reais dos documentos Swing, sem precisar de uma janela. */
public final class MascarasTest {
    private static int verificacoes;

    private MascarasTest() { }

    public static void main(String[] args) throws Exception {
        FutureTask<Void> tarefa = new FutureTask<>(() -> {
            documentos();
            telefones();
            demaisCampos();
            edicaoESelecao();
            limparERecarregar();
            camposNumericos();
            integracaoFormularios();
            return null;
        });
        SwingUtilities.invokeAndWait(tarefa);
        tarefa.get();
        System.out.println("MascarasTest: " + verificacoes + " verificacoes passaram na EDT.");
    }

    private static void documentos() throws BadLocationException {
        JTextField cpf = campo(Mascaras.Tipo.CPF);
        digitar(cpf, "52998224725");
        texto(cpf, "529.982.247-25", "CPF digitado recebe pontuacao");
        rejeitarInsercao(cpf, "6", "CPF cheio rejeita digito excedente");
        colar(cpf, "111.444.777-35");
        texto(cpf, "111.444.777-35", "CPF aceita documento ja formatado");
        rejeitarSubstituicao(cpf, "529982247250", "CPF longo nao e truncado");
        rejeitarSubstituicao(cpf, "529a98224725", "CPF nao descarta letras silenciosamente");

        JTextField documento = campo(Mascaras.Tipo.CPF_CNPJ);
        digitar(documento, "11222333000");
        texto(documento, "112.223.330-00", "Onze digitos usam a mascara de CPF");
        digitar(documento, "181");
        texto(documento, "11.222.333/0001-81", "Ao continuar a digitacao o campo passa a CNPJ");
        rejeitarInsercao(documento, "9", "CNPJ cheio rejeita digito excedente");
        colar(documento, " 11.222.333/0001-81 ");
        texto(documento, "11.222.333/0001-81", "CNPJ aceita pontuacao e espacos ao colar");
        documento.getDocument().remove(documento.getDocument().getLength() - 4, 4);
        texto(documento, "112.223.330-00", "Apagar os ultimos digitos de CNPJ recupera CPF");
        rejeitarSubstituicao(documento, "112223330001811", "CNPJ longo nao substitui valor atual");
    }

    private static void telefones() throws BadLocationException {
        JTextField telefone = campo(Mascaras.Tipo.TELEFONE);
        digitar(telefone, "1133334444");
        texto(telefone, "(11) 3333-4444", "Telefone fixo recebe mascara de dez digitos");
        colar(telefone, "11999998888");
        texto(telefone, "(11) 99999-8888", "Celular recebe mascara de onze digitos");
        colar(telefone, " (11) 99999-8888 ");
        texto(telefone, "(11) 99999-8888", "Telefone formatado pode ser colado");
        telefone.getDocument().remove(telefone.getDocument().getLength() - 1, 1);
        texto(telefone, "(11) 9999-9888", "Apagar digito de celular adapta a mascara para fixo");
        digitar(telefone, "8");
        texto(telefone, "(11) 99999-8888", "Acrescentar o decimo primeiro digito adapta para celular");
        rejeitarInsercao(telefone, "7", "Telefone cheio rejeita excesso");
        rejeitarSubstituicao(telefone, "(11) nove", "Telefone rejeita letras");
        Mascaras.preencher(telefone, "+55 47 99999-9999");
        texto(telefone, "+55 47 99999-9999", "Telefone legado preserva codigo do pais");
        telefone.setCaretPosition(telefone.getDocument().getLength());
        telefone.getDocument().remove(telefone.getDocument().getLength() - 1, 1);
        texto(telefone, "+55 47 99999-999", "Telefone legado pode ser corrigido com Backspace sem truncamento");
    }

    private static void demaisCampos() throws BadLocationException {
        JTextField cnh = campo(Mascaras.Tipo.CNH);
        digitar(cnh, "01234567890");
        texto(cnh, "01234567890", "CNH preserva zero inicial e usa onze digitos");
        rejeitarInsercao(cnh, "1", "CNH cheia rejeita excesso");
        rejeitarSubstituicao(cnh, "0123456789x", "CNH rejeita letras");

        JTextField placa = campo(Mascaras.Tipo.PLACA);
        digitar(placa, "abc1234");
        texto(placa, "ABC-1234", "Placa antiga recebe maiusculas e hifen");
        colar(placa, "abc-1234");
        texto(placa, "ABC-1234", "Placa antiga aceita hifen existente");
        colar(placa, "abc1d23");
        texto(placa, "ABC1D23", "Placa Mercosul recebe maiusculas");
        rejeitarInsercao(placa, "4", "Placa cheia rejeita excesso");
        rejeitarSubstituicao(placa, "123ABCD", "Placa rejeita ordem invalida de letras e digitos");

        JTextField data = campo(Mascaras.Tipo.DATA);
        digitar(data, "29102026");
        texto(data, "29/10/2026", "Data digitada recebe separadores");
        colar(data, "01/02/2024");
        texto(data, "01/02/2024", "Data aceita formato ja preenchido");
        rejeitarInsercao(data, "1", "Data cheia rejeita excesso");
        rejeitarSubstituicao(data, "01/fev/2024", "Data rejeita letras");
        rejeitarSubstituicao(data, "2001-10-20", "Data ISO nao e reinterpretada silenciosamente como outra data");
        rejeitarSubstituicao(data, "2001/10/20", "Data com ano primeiro nao e reordenada como dia primeiro");

        JTextField ano = campo(Mascaras.Tipo.ANO);
        digitar(ano, "2026");
        texto(ano, "2026", "Ano aceita quatro digitos");
        rejeitarInsercao(ano, "1", "Ano cheio rejeita excesso");
        rejeitarSubstituicao(ano, "20a6", "Ano rejeita letras");
    }

    private static void edicaoESelecao() throws BadLocationException {
        JTextField cpf = campo(Mascaras.Tipo.CPF);
        colar(cpf, "52998224725");
        cpf.select(4, 7);
        cpf.replaceSelection("444");
        texto(cpf, "529.444.247-25", "Substituir selecao no meio preserva os demais digitos");
        verificar(cpf.getCaretPosition() >= 7 && cpf.getCaretPosition() <= 8,
                "Caret permanece junto do trecho substituido, permitindo continuar a edicao");

        // O cursor depois do ponto representa Backspace sobre a pontuacao.
        cpf.setCaretPosition(4);
        cpf.getDocument().remove(3, 1);
        verificar(digitos(cpf).equals("5244424725"),
                "Backspace sobre ponto apaga o digito anterior em vez de prender o cursor");
        verificar(cpf.getCaretPosition() <= 4, "Backspace nao move o caret para o fim do campo");

        colar(cpf, "52998224725");
        ((AbstractDocument) cpf.getDocument()).replace(4, 3, "111", null);
        texto(cpf, "529.111.247-25", "Document.replace respeita a selecao entre separadores");
        ((AbstractDocument) cpf.getDocument()).replace(0, cpf.getDocument().getLength(), "", null);
        texto(cpf, "", "Document.replace consegue limpar todo o campo");
        cpf.getDocument().insertString(0, "1", null);
        texto(cpf, "1", "Campo vazio nao inclui espacos ou preenchimento obrigatorio");
    }

    private static void limparERecarregar() throws BadLocationException {
        Mascaras.Tipo[] tipos = Mascaras.Tipo.values();
        String[] exemplos = {"52998224725", "11222333000181", "11999998888", "01234567890", "ABC1D23", "29102026", "2026"};
        for (Mascaras.Tipo tipo : tipos) {
            JTextField campo = campo(tipo);
            String exemplo = switch (tipo) {
                case CPF -> exemplos[0];
                case CPF_CNPJ -> exemplos[1];
                case TELEFONE -> exemplos[2];
                case CNH -> exemplos[3];
                case PLACA -> exemplos[4];
                case DATA -> exemplos[5];
                case ANO -> exemplos[6];
            };
            campo.setText(exemplo);
            verificar(!campo.getText().isEmpty(), tipo + " permite carregar valor de cadastro");
            campo.setText("");
            texto(campo, "", tipo + " permite limpar formulario com setText");
            digitar(campo, exemplo);
            verificar(!campo.getText().isEmpty(), tipo + " permite preencher novamente");
            campo.selectAll();
            campo.replaceSelection("");
            texto(campo, "", tipo + " permite excluir selecao completa");
        }
    }

    private static void camposNumericos() {
        JTextField decimal = new JTextField();
        CamposNumericos.decimal(decimal);
        decimal.setText("1500.50");
        texto(decimal, "1500.50", "Decimal permite digitar antes de formatar");
        perderFoco(decimal);
        texto(decimal, "1.500,50", "Decimal recebe formato brasileiro ao perder foco");
        decimal.setText("16.000");
        perderFoco(decimal);
        texto(decimal, "16.000,00", "Decimal reconhece ponto de milhar");
        decimal.setText("1.500,00");
        perderFoco(decimal);
        texto(decimal, "1.500,00", "Decimal ja formatado permanece igual");
        decimal.setText("1.500,001");
        perderFoco(decimal);
        texto(decimal, "1.500,001", "Decimal invalido permanece para validacao sem arredondamento");
        decimal.setText("");
        perderFoco(decimal);
        texto(decimal, "", "Decimal vazio permanece vazio");

        JTextField inteiro = new JTextField();
        CamposNumericos.inteiro(inteiro);
        inteiro.setText("85000");
        texto(inteiro, "85000", "Quilometragem mantem digitacao ate perder foco");
        perderFoco(inteiro);
        texto(inteiro, "85.000", "Quilometragem recebe separador de milhar");
        inteiro.setText("abc");
        texto(inteiro, "85.000", "Quilometragem rejeita letras sem apagar valor atual");
    }

    private static void perderFoco(JTextField campo) {
        FocusEvent evento = new FocusEvent(campo, FocusEvent.FOCUS_LOST);
        for (FocusListener listener : campo.getFocusListeners()) listener.focusLost(evento);
    }

    private static void integracaoFormularios() {
        try (FrotaController controller = new FrotaController(new ArquivoRepositorio())) {
            DadosDemonstracao.carregar(controller);
            DadosTela dados = DadosTela.carregar(controller);

            CadastroPanel<?> motoristas = Cadastros.motoristas(controller);
            selecionarPrimeiro(motoristas, dados);
            texto(campo(motoristas, "cpf"), Formatacao.documento(dados.motoristas().get(0).cpf()), "Selecionar motorista carrega CPF mascarado");
            texto(campo(motoristas, "cnh"), dados.motoristas().get(0).cnh(), "Selecionar motorista carrega CNH");
            texto(campo(motoristas, "telefone"), Formatacao.telefone(dados.motoristas().get(0).telefone()), "Selecionar motorista carrega telefone mascarado");
            botao(motoristas, "Novo").doClick();
            texto(campo(motoristas, "cpf"), "", "Novo motorista limpa CPF");
            texto(campo(motoristas, "telefone"), "", "Novo motorista limpa telefone");

            CadastroPanel<?> clientes = Cadastros.clientes(controller);
            selecionarPrimeiro(clientes, dados);
            texto(campo(clientes, "documento"), Formatacao.documento(dados.clientes().get(0).cpfCnpj()), "Selecionar cliente carrega CNPJ mascarado");
            Formulario cliente = componentes(clientes, Formulario.class).get(0);
            cliente.preencher("documento", "52998224725");
            texto(campo(clientes, "documento"), "529.982.247-25", "Formulario.preencher adapta documento do cliente para CPF");
            botao(clientes, "Limpar").doClick();
            texto(campo(clientes, "documento"), "", "Limpar cliente limpa documento");

            model.Cliente legado = controller.salvar(new model.Cliente(0, "Cliente internacional", "52998224725", "+55 47 99999-9999", "Rua internacional, 10"));
            selecionarPrimeiro(clientes, DadosTela.carregar(controller));
            JTable tabelaClientes = componentes(clientes, JTable.class).get(0);
            for (int linha = 0; linha < tabelaClientes.getRowCount(); linha++) {
                if (((Number) tabelaClientes.getValueAt(linha, 0)).intValue() == legado.id()) tabelaClientes.setRowSelectionInterval(linha, linha);
            }
            texto(campo(clientes, "telefone"), legado.telefone(), "Selecionar telefone internacional preserva valor persistido, sem telefone anterior");
            campo(clientes, "nome").setText("Cliente internacional atualizado");
            texto(campo(clientes, "telefone"), legado.telefone(), "Editar somente nome preserva telefone internacional");
            botao(clientes, "Novo").doClick();
            texto(campo(clientes, "telefone"), "", "Novo limpa telefone legado fora da mascara");

            CadastroPanel<?> veiculos = Cadastros.veiculos(controller);
            selecionarPrimeiro(veiculos, dados);
            texto(campo(veiculos, "placa"), Formatacao.placa(dados.veiculos().get(0).placa()), "Selecionar veiculo carrega placa Mercosul");
            Formulario veiculo = componentes(veiculos, Formulario.class).get(0);
            veiculo.preencher("placa", "abc1234");
            texto(campo(veiculos, "placa"), "ABC-1234", "Formulario.preencher aplica placa antiga");
            botao(veiculos, "Novo").doClick();
            texto(campo(veiculos, "placa"), "", "Novo veiculo limpa placa");
            texto(campo(veiculos, "ano"), String.valueOf(LocalDate.now().getYear()), "Novo veiculo restaura ano padrao");

            CadastroPanel<?> viagens = Cadastros.viagens(controller);
            selecionarPrimeiro(viagens, dados);
            texto(campo(viagens, "saida"), Formatacao.data(dados.viagens().get(0).dataSaida()), "Selecionar viagem carrega data de saida");
            Formulario viagem = componentes(viagens, Formulario.class).get(0);
            viagem.preencher("chegada", "29102026");
            texto(campo(viagens, "chegada"), "29/10/2026", "Chegada opcional recebe mascara quando preenchida");
            verificar(viagem.data("chegada", true).equals(LocalDate.of(2026, 10, 29)), "Chegada mascarada continua sendo uma data valida");
            botao(viagens, "Novo").doClick();
            texto(campo(viagens, "saida"), Formatacao.data(LocalDate.now()), "Nova viagem restaura saida de hoje");
            texto(campo(viagens, "chegada"), "", "Nova viagem mantem chegada opcional vazia");
            verificar(viagem.data("chegada", true) == null, "Chegada vazia pode ser salva como null");
            viagem.preencher("chegada", null);
            texto(campo(viagens, "chegada"), "", "Carregar chegada null preserva campo vazio");
            viagem.preencher("chegada", "31022026");
            texto(campo(viagens, "chegada"), "31/02/2026", "Mascara de data permite concluir a digitacao antes de validar calendario");
            try {
                viagem.data("chegada", true);
                throw new AssertionError("Data inexistente passou pela validacao do formulario");
            } catch (IllegalArgumentException esperada) { verificacoes++; }

            CadastroPanel<?> manutencoes = Cadastros.manutencoes(controller);
            selecionarPrimeiro(manutencoes, dados);
            texto(campo(manutencoes, "data"), Formatacao.data(dados.manutencoes().get(0).data()), "Selecionar manutencao carrega data mascarada");
            perderFoco(campo(manutencoes, "km"));
            texto(campo(manutencoes, "km"), "85.000", "Quilometragem do formulario recebe milhar ao perder foco");
            botao(manutencoes, "Novo").doClick();
            texto(campo(manutencoes, "data"), Formatacao.data(LocalDate.now()), "Nova manutencao restaura data de hoje");
            texto(campo(manutencoes, "km"), "", "Nova manutencao limpa quilometragem");

            RelatorioPanel relatorio = new RelatorioPanel(controller);
            Formulario filtros = componentes(relatorio, Formulario.class).get(0);
            filtros.preencher("inicio", "01102026");
            filtros.preencher("fim", "31102026");
            texto(campo(relatorio, "inicio"), "01/10/2026", "Data inicial do relatorio recebe mascara");
            texto(campo(relatorio, "fim"), "31/10/2026", "Data final do relatorio recebe mascara");
            verificar(filtros.data("fim", false).equals(LocalDate.of(2026, 10, 31)), "Filtro mascarado produz data correta");
            botao(relatorio, "Limpar filtros").doClick();
            texto(campo(relatorio, "inicio"), Formatacao.data(LocalDate.now().withDayOfMonth(1)), "Limpar filtros restaura inicio do mes");
            texto(campo(relatorio, "fim"), Formatacao.data(LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth())), "Limpar filtros restaura fim do mes");
        }
    }

    private static void selecionarPrimeiro(CadastroPanel<?> painel, DadosTela dados) {
        painel.apresentarDados(dados);
        componentes(painel, JTable.class).get(0).setRowSelectionInterval(0, 0);
    }

    private static JTextField campo(Component raiz, String nome) {
        return componentes(raiz, JTextField.class).stream().filter(c -> nome.equals(c.getName())).findFirst().orElseThrow();
    }

    private static JButton botao(Component raiz, String texto) {
        return componentes(raiz, JButton.class).stream().filter(b -> texto.equals(b.getText())).findFirst().orElseThrow();
    }

    private static <T extends Component> List<T> componentes(Component raiz, Class<T> tipo) {
        List<T> resultado = new ArrayList<>();
        if (tipo.isInstance(raiz)) resultado.add(tipo.cast(raiz));
        if (raiz instanceof Container pai) for (Component filho : pai.getComponents()) resultado.addAll(componentes(filho, tipo));
        return resultado;
    }

    private static JTextField campo(Mascaras.Tipo tipo) {
        JTextField campo = new JTextField();
        Mascaras.aplicar(campo, tipo);
        return campo;
    }

    private static void digitar(JTextField campo, String valor) throws BadLocationException {
        for (int i = 0; i < valor.length(); i++) {
            campo.getDocument().insertString(campo.getDocument().getLength(), valor.substring(i, i + 1), null);
        }
    }

    private static void colar(JTextField campo, String valor) throws BadLocationException {
        ((AbstractDocument) campo.getDocument()).replace(0, campo.getDocument().getLength(), valor, null);
    }

    private static void rejeitarInsercao(JTextField campo, String valor, String mensagem) throws BadLocationException {
        String antes = campo.getText();
        campo.getDocument().insertString(campo.getDocument().getLength(), valor, null);
        texto(campo, antes, mensagem);
    }

    private static void rejeitarSubstituicao(JTextField campo, String valor, String mensagem) throws BadLocationException {
        String antes = campo.getText();
        colar(campo, valor);
        texto(campo, antes, mensagem);
    }

    private static String digitos(JTextField campo) { return campo.getText().replaceAll("\\D", ""); }

    private static void texto(JTextField campo, String esperado, String mensagem) {
        verificar(campo.getText().equals(esperado), mensagem + ": esperado [" + esperado + "] e recebido [" + campo.getText() + "]");
    }

    private static void verificar(boolean condicao, String mensagem) {
        verificacoes++;
        if (!condicao) throw new AssertionError(mensagem);
    }
}
