package br.faculdade.assistente.web;

import java.util.Arrays;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Nível 2: o RAG continua local (os regulamentos são da própria aplicação),
 * mas as TOOLS vêm de fora, descobertas no servidor MCP da secretaria.
 *
 * Diferença em relação ao nível 1: nenhuma classe de tool existe aqui.
 * O assistente recebe as ferramentas prontas, com nome, descrição e schema,
 * pelo protocolo.
 */
@RestController
public class AssistenteController {

    private static final String INSTRUCAO = """
            Você é o assistente acadêmico de uma faculdade.
            Responda apenas com base nos trechos de regulamento fornecidos no contexto
            ou no resultado das ferramentas disponíveis.
            Sempre cite o artigo e o documento de origem quando usar um regulamento.
            Se o contexto não contiver a resposta, diga que não encontrou fonte válida
            e oriente o aluno a procurar a secretaria. Nunca deduza prazos ou regras.
            Nunca invente número de protocolo nem matrícula.
            """;

    private static final PromptTemplate TEMPLATE_RAG = new PromptTemplate("""
            {query}

            Trechos de regulamento recuperados da base oficial:
            ---------------------
            {question_answer_context}
            ---------------------
            Se os trechos acima não responderem à pergunta, diga que não encontrou fonte válida.
            """);

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final ToolCallback[] ferramentasMcp;

    public AssistenteController(ChatClient.Builder builder,
                                VectorStore vectorStore,
                                ToolCallbackProvider ferramentasMcp) {
        this.vectorStore = vectorStore;
        // O starter do cliente MCP entrega as tools do servidor já convertidas
        // em ToolCallback do Spring AI.
        this.ferramentasMcp = ferramentasMcp.getToolCallbacks();

        this.chatClient = builder
                .defaultSystem(INSTRUCAO)
                .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore)
                        .searchRequest(SearchRequest.builder()
                                .topK(4)
                                .similarityThreshold(0.35)
                                .build())
                        .promptTemplate(TEMPLATE_RAG)
                        .build())
                .defaultToolCallbacks(this.ferramentasMcp)
                .build();
    }

    public record Pergunta(String texto) {
    }

    public record Resposta(String resposta, List<String> fontes) {
    }

    @PostMapping("/perguntar")
    public Resposta perguntar(@RequestBody Pergunta pergunta) {
        String resposta = chatClient.prompt()
                .user(pergunta.texto())
                .call()
                .content();

        return new Resposta(resposta, fontesConsultadas(pergunta.texto()));
    }

    /**
     * Mostra o que o assistente descobriu no servidor MCP. Bom para projetar:
     * essa lista não está no código do assistente, veio do protocolo.
     */
    @GetMapping("/ferramentas")
    public List<String> ferramentas() {
        return Arrays.stream(ferramentasMcp)
                .map(t -> t.getToolDefinition().name() + " — " + t.getToolDefinition().description())
                .toList();
    }

    /**
     * Chama uma tool MCP direto, sem passar pelo modelo. Serve para mostrar na
     * apresentação que a ferramenta (e o guardrail dela) existe e funciona
     * independentemente da IA — e não custa token nenhum.
     */
    @GetMapping("/tool/{nome}")
    public String chamarTool(@PathVariable String nome,
                             @RequestParam(required = false, defaultValue = "{}") String argumentos) {
        return Arrays.stream(ferramentasMcp)
                .filter(t -> t.getToolDefinition().name().equals(nome))
                .findFirst()
                .map(t -> t.call(argumentos))
                .orElse("Tool não encontrada: " + nome);
    }

    private List<String> fontesConsultadas(String pergunta) {
        List<Document> docs = vectorStore.similaritySearch(SearchRequest.builder()
                .query(pergunta)
                .topK(4)
                .similarityThreshold(0.35)
                .build());

        return docs == null ? List.of()
                : docs.stream()
                    .map(d -> String.valueOf(d.getMetadata().get("fonte")))
                    .distinct()
                    .toList();
    }
}
