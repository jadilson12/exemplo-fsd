package br.faculdade.adk;

import module java.base;

import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// TUI do assistente. Além da conversa, mostra o TRACE de cada turno:
// qual tool o agente decidiu chamar, o que a tool devolveu e só então a resposta.
// É esse trace que torna o fluxo completo visível na apresentação.
//
// @ConditionalOnProperty exclui esta TUI quando assistente.interface=dev-ui:
// as duas disputariam o terminal/stdin ao mesmo tempo.
@Component
@ConditionalOnProperty(name = "assistente.interface", havingValue = "tui", matchIfMissing = true)
public class AssistenteTui implements CommandLineRunner {

    private static final AttributedStyle VERDE = AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN);
    private static final AttributedStyle CINZA = AttributedStyle.DEFAULT.foreground(AttributedStyle.BRIGHT);
    private static final AttributedStyle AMARELO = AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW);
    private static final AttributedStyle VERMELHO = AttributedStyle.DEFAULT.foreground(AttributedStyle.RED);

    private final InMemoryRunner runner;
    private final FerramentasAcademicas ferramentas;
    private final String matriculaInicial;

    private Terminal terminal;
    private Session sessao;
    private String matricula;
    private boolean mostrarTrace = true;

    public AssistenteTui(InMemoryRunner runner,
                         FerramentasAcademicas ferramentas,
                         @Value("${demo.matricula}") String matriculaInicial) {
        this.runner = runner;
        this.ferramentas = ferramentas;
        this.matriculaInicial = matriculaInicial;
    }

    @Override
    public void run(String... args) throws Exception {
        this.terminal = TerminalBuilder.builder().system(true).build();
        var leitor = LineReaderBuilder.builder().terminal(terminal).build();

        this.matricula = matriculaInicial;
        novaSessao();
        cabecalho();

        while (true) {
            String entrada;
            try {
                entrada = leitor.readLine(new AttributedString("\naluno > ", VERDE).toAnsi(terminal));
            } catch (UserInterruptException | EndOfFileException fim) {
                break;
            }

            if (entrada == null || entrada.isBlank()) {
                continue;
            }
            if (entrada.startsWith("/")) {
                if (!comando(entrada.trim())) {
                    break;
                }
                continue;
            }

            perguntar(entrada);
        }

        imprimir("Até logo.", CINZA);
        terminal.close();
    }

    // Um turno completo: envia a pergunta e vai imprimindo os eventos do ADK.
    private void perguntar(String pergunta) {
        var mensagem = Content.fromParts(Part.fromText(pergunta));

        try {
            runner.runAsync(sessao.userId(), sessao.id(), mensagem)
                    .blockingForEach(this::imprimirEvento);
        } catch (RuntimeException erro) {
            imprimir("Falha ao falar com o modelo: " + erro.getMessage(), VERMELHO);
        }
    }

    private void imprimirEvento(Event evento) {
        if (mostrarTrace) {
            // O agente decidiu usar uma ferramenta.
            evento.functionCalls().forEach(chamada ->
                    imprimir("  ↳ tool  %s(%s)".formatted(chamada.name().orElse("?"),
                            chamada.args().orElse(Map.of())), AMARELO));

            // O que a ferramenta devolveu — inclusive acesso_negado e sem_fonte.
            evento.functionResponses().forEach(resposta ->
                    imprimir("  ↳ saída %s -> %s".formatted(resposta.name().orElse("?"),
                            resumir(String.valueOf(resposta.response().orElse(Map.of())))), CINZA));
        }

        if (evento.finalResponse()) {
            var texto = evento.stringifyContent();
            if (!texto.isBlank()) {
                imprimir("\nassistente > " + texto, VERDE);
            }
        }
    }

    private boolean comando(String comando) {
        var partes = comando.split("\\s+", 2);
        switch (partes[0]) {
            case "/sair" -> {
                return false;
            }
            case "/ajuda" -> ajuda();
            case "/trace" -> {
                mostrarTrace = !mostrarTrace;
                imprimir("Trace " + (mostrarTrace ? "ligado" : "desligado"), CINZA);
            }
            case "/limpar" -> {
                // Nova sessao = memoria de conversa zerada, mesmo aluno.
                novaSessao();
                imprimir("Nova sessão iniciada (histórico apagado).", CINZA);
            }
            case "/matricula" -> {
                if (partes.length < 2) {
                    imprimir("Uso: /matricula 20269999", CINZA);
                } else {
                    // Troca o aluno "autenticado". Serve para mostrar que o mesmo
                    // protocolo muda de resultado conforme a sessao, nao conforme o texto.
                    matricula = partes[1].trim();
                    novaSessao();
                    imprimir("Autenticado como " + matricula + " (nova sessão).", CINZA);
                }
            }
            case "/estado" -> imprimir("Estado da sessão: " + new LinkedHashMap<>(sessao.state()), CINZA);
            // Os dois comandos abaixo chamam as tools direto, sem modelo nenhum.
            // Servem para mostrar que RAG e guardrail são código comum — e que
            // essa parte da demo funciona mesmo sem rede ou chave de API.
            case "/buscar" -> {
                if (partes.length < 2) {
                    imprimir("Uso: /buscar prazo de aproveitamento", CINZA);
                } else {
                    imprimir(resumir(String.valueOf(ferramentas.buscarRegulamento(partes[1]))), CINZA);
                }
            }
            case "/requerimentos" -> imprimir(String.valueOf(ferramentas.listar(matricula)), CINZA);
            case "/protocolo" -> {
                if (partes.length < 2) {
                    imprimir("Uso: /protocolo 2026-0003", CINZA);
                } else {
                    imprimir(String.valueOf(ferramentas.consultar(partes[1].trim(), matricula)), CINZA);
                }
            }
            default -> imprimir("Comando desconhecido. /ajuda mostra a lista.", CINZA);
        }
        return true;
    }

    private void novaSessao() {
        // O estado inicial carrega a identidade do aluno. As tools leem dali.
        Map<String, Object> estado = new ConcurrentHashMap<>(Map.of("matricula", matricula));
        this.sessao = runner.sessionService()
                .createSession(AgenteAcademico.APP, "aluno-" + matricula, estado, null)
                .blockingGet();
    }

    private void cabecalho() {
        imprimir("""
                ┌────────────────────────────────────────────────────────────┐
                │ Assistente acadêmico — Spring + Google ADK                 │
                │ modelo via Spring AI (OpenRouter/LiteLLM)                  │
                └────────────────────────────────────────────────────────────┘""", VERDE);
        imprimir("Aluno autenticado: " + matricula + "   |   /ajuda para os comandos", CINZA);
    }

    private void ajuda() {
        imprimir("""
                /ajuda              esta lista
                /trace              liga/desliga o trace de tools
                /limpar             nova sessão (apaga o histórico da conversa)
                /matricula <nº>     troca o aluno autenticado
                /estado             mostra o estado da sessão
                /buscar <assunto>   RAG direto, sem modelo
                /requerimentos      tool direta: lista os requerimentos do aluno
                /protocolo <nº>     tool direta: consulta um protocolo
                /sair               encerra

                Perguntas da demonstração:
                  Qual é o prazo para solicitar aproveitamento de disciplina?
                  Qual é o valor da taxa de segunda via do diploma?
                  Quais requerimentos eu tenho abertos?
                  Como está o requerimento 2026-0003?""", CINZA);
    }

    private String resumir(String texto) {
        return texto.length() <= 160 ? texto : texto.substring(0, 157) + "...";
    }

    private void imprimir(String texto, AttributedStyle estilo) {
        terminal.writer().println(new AttributedString(texto, estilo).toAnsi(terminal));
        terminal.flush();
    }
}
