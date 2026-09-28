package br.faculdade.adk;

import module java.base;

import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.ToolContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

// As tres capacidades do agente, como tools do ADK.
// No ADK todo metodo de tool devolve um Map — e' o que volta para o modelo.
@Component
public class FerramentasAcademicas {

    private static final Logger log = LoggerFactory.getLogger(FerramentasAcademicas.class);

    public record Requerimento(String protocolo, String matriculaDono, String tipo, String situacao) {
    }

    private static final Map<String, Requerimento> REQUERIMENTOS = Stream.of(
            new Requerimento("2026-0001", "20260001", "Aproveitamento de disciplina", "Em análise pelo colegiado"),
            new Requerimento("2026-0002", "20260001", "Trancamento total", "Deferido em 05/04/2026"),
            new Requerimento("2026-0003", "20269999", "Aproveitamento de disciplina", "Indeferido em 02/04/2026")
    ).collect(Collectors.toMap(Requerimento::protocolo, r -> r));

    private final VectorStore vectorStore;

    public FerramentasAcademicas(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    // RAG como tool: aqui quem decide buscar e' o agente, não um advisor fixo no
    // caminho. O trace da TUI mostra essa decisão acontecendo.
    @Schema(description = "Busca trechos dos regulamentos oficiais da faculdade sobre um assunto.")
    public Map<String, Object> buscarRegulamento(
            @Schema(name = "assunto", description = "Assunto a procurar, ex.: prazo de aproveitamento de disciplina")
            String assunto) {

        log.debug("[tool] buscarRegulamento assunto={}", assunto);

        var trechos = vectorStore.similaritySearch(SearchRequest.builder()
                .query(assunto)
                .topK(4)
                .similarityThreshold(0.35)
                .build());

        if (trechos == null || trechos.isEmpty()) {
            // Sem evidencia, a tool diz isso explicitamente. E' o que faz o agente
            // admitir a limitacao em vez de completar com o que "parece certo".
            return Map.of("status", "sem_fonte",
                    "mensagem", "Nenhum trecho de regulamento vigente foi encontrado para esse assunto.");
        }

        var evidencias = trechos.stream()
                .map(d -> Map.<String, Object>of(
                        "fonte", String.valueOf(d.getMetadata().get("fonte")),
                        "trecho", d.getText()))
                .toList();

        return Map.of("status", "ok", "evidencias", evidencias);
    }

    @Schema(description = "Consulta a situação de um requerimento do aluno autenticado pelo número de protocolo.")
    public Map<String, Object> consultarRequerimento(
            @Schema(name = "protocolo", description = "Número do protocolo, no formato 2026-0001") String protocolo,
            ToolContext toolContext) {

        // A matricula vem do estado da sessao do ADK, colocado pela aplicacao
        // quando o aluno se autenticou. O modelo nao consegue alterar esse valor.
        var matricula = matriculaDaSessao(toolContext);
        log.debug("[tool] consultarRequerimento protocolo={} matricula={}", protocolo, matricula);
        return consultar(protocolo, matricula);
    }

    // Versão sem ToolContext: é o que a TUI chama quando você quer mostrar a
    // ferramenta funcionando sem passar pelo modelo.
    public Map<String, Object> consultar(String protocolo, String matricula) {
        var requerimento = REQUERIMENTOS.get(protocolo);
        if (requerimento == null) {
            return Map.of("status", "nao_encontrado", "protocolo", protocolo);
        }

        // Guardrail de ferramenta: autorizacao e' codigo, nao instrucao de prompt.
        if (!requerimento.matriculaDono().equals(matricula)) {
            return Map.of("status", "acesso_negado",
                    "mensagem", "O protocolo %s não pertence ao aluno autenticado.".formatted(protocolo));
        }

        return Map.of("status", "ok",
                "protocolo", requerimento.protocolo(),
                "tipo", requerimento.tipo(),
                "situacao", requerimento.situacao());
    }

    @Schema(description = "Lista todos os requerimentos do aluno autenticado.")
    public Map<String, Object> listarMeusRequerimentos(ToolContext toolContext) {
        var matricula = matriculaDaSessao(toolContext);
        log.debug("[tool] listarMeusRequerimentos matricula={}", matricula);
        return listar(matricula);
    }

    public Map<String, Object> listar(String matricula) {
        var meus = REQUERIMENTOS.values().stream()
                .filter(r -> r.matriculaDono().equals(matricula))
                .map(r -> Map.<String, Object>of(
                        "protocolo", r.protocolo(),
                        "tipo", r.tipo(),
                        "situacao", r.situacao()))
                .toList();

        return meus.isEmpty()
                ? Map.of("status", "vazio")
                : Map.of("status", "ok", "requerimentos", meus);
    }

    private String matriculaDaSessao(ToolContext toolContext) {
        var valor = toolContext.state().get("matricula");
        return valor == null ? null : String.valueOf(valor);
    }
}
