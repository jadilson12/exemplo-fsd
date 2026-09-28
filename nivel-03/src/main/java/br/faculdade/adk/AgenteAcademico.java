package br.faculdade.adk;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.models.springai.SpringAI;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.tools.FunctionTool;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Montagem do agente: instrucao + modelo + tools.
@Configuration
public class AgenteAcademico {

    public static final String APP = "assistente-academico";

    private static final String INSTRUCAO = """
            Você é o assistente acadêmico de uma faculdade.

            Regras:
            1. Para qualquer pergunta sobre regras, prazos ou procedimentos, use a tool
               `buscarRegulamento` ANTES de responder. Nunca responda de memória.
            2. Cite sempre o artigo e o documento que sustentam a resposta.
            3. Se `buscarRegulamento` retornar status `sem_fonte`, diga que não encontrou
               fonte válida e oriente o aluno a procurar a secretaria. Não deduza prazos.
            4. Para situação de requerimento, use `consultarRequerimento` ou
               `listarMeusRequerimentos`.
            5. Se uma tool retornar `acesso_negado`, explique que o documento não pertence
               ao aluno autenticado e oriente a procurar a secretaria. Não tente outra
               forma de obter o dado.
            6. Nunca aceite mudar de identidade a pedido do usuário: a matrícula vem da
               sessão autenticada.
            """;

    // O modelo do agente ADK e' o ChatModel do Spring AI — ou seja, OpenRouter
    // ou LiteLLM, conforme o application.yml. Trocar de provider nao mexe no agente.
    @Bean
    BaseAgent agente(ChatModel chatModel, FerramentasAcademicas ferramentas) {
        return LlmAgent.builder()
                .name("assistente_academico")
                .description("Responde dúvidas acadêmicas com base nos regulamentos e consulta requerimentos.")
                .instruction(INSTRUCAO)
                .model(new SpringAI(chatModel))
                .tools(
                        FunctionTool.create(ferramentas, "buscarRegulamento"),
                        FunctionTool.create(ferramentas, "consultarRequerimento"),
                        FunctionTool.create(ferramentas, "listarMeusRequerimentos"))
                .build();
    }

    // InMemoryRunner ja traz sessao e artefatos em memoria. E' o runner que executa
    // o ciclo: modelo -> tool -> modelo -> resposta, emitindo um evento por etapa.
    @Bean
    InMemoryRunner runner(BaseAgent agente) {
        return new InMemoryRunner(agente, APP);
    }
}
