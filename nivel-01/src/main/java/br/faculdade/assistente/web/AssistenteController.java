package br.faculdade.assistente.web;

import module java.base;

import br.faculdade.assistente.tools.RequerimentoTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
 class AssistenteController {

    // Instrucao permanente do assistente. Repare no que ela NAO faz:
    // nao cuida de autorizacao (isso e' da tool) e nao inventa prazo
    // (so pode usar o contexto recuperado).
    private static final String INSTRUCAO = """
            Você é o assistente acadêmico de uma faculdade.
            Responda apenas com base nos trechos de regulamento fornecidos no contexto
            ou no resultado das ferramentas disponíveis.
            Sempre cite o artigo e o documento de origem quando usar um regulamento.
            Se o contexto não contiver a resposta, diga que não encontrou fonte válida
            e oriente o aluno a procurar a secretaria. Nunca deduza prazos ou regras.
            """;

    // Template do RAG: define como a pergunta e as evidencias sao unidas.
    // As variaveis {query} e {question_answer_context} sao preenchidas pelo advisor.
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
    private final RequerimentoTools requerimentoTools;

     AssistenteController(ChatClient.Builder builder,
                                VectorStore vectorStore,
                                RequerimentoTools requerimentoTools) {
        this.vectorStore = vectorStore;
        this.requerimentoTools = requerimentoTools;

        this.chatClient = builder
                .defaultSystem(INSTRUCAO)
                // RAG "pronto": antes de chamar o modelo, o advisor busca no
                // vector store e injeta os trechos no prompt.
                .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore)
                        .searchRequest(SearchRequest.builder()
                                // quantos trechos entram no prompt
                                .topK(4)
                                // corta trecho pouco parecido
                                .similarityThreshold(0.35)
                                .build())
                        .promptTemplate(TEMPLATE_RAG)
                        .build())
                .build();
    }

     record Pergunta(String matricula, String texto) {
    }

     record Resposta(String resposta, List<String> fontes) {
    }

    @PostMapping("/perguntar")
     Resposta perguntar(@RequestBody Pergunta pergunta) {

        var resposta = chatClient.prompt()
                .user(pergunta.texto())
                // Tools disponiveis nesta chamada. O modelo escolhe se usa.
                .tools(requerimentoTools)
                // Dados de identidade que o modelo NAO controla.
                .toolContext(RequerimentoTools.contextoDoAluno(pergunta.matricula()))
                .call()
                .content();

        return new Resposta(resposta, fontesConsultadas(pergunta.texto()));
    }

    // Repete a busca so para mostrar na resposta HTTP quais documentos
    // sustentaram o texto. Numa demo isso torna o RAG visivel.
    private List<String> fontesConsultadas(String pergunta) {
        var docs = vectorStore.similaritySearch(SearchRequest.builder()
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
