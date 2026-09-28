package br.faculdade.adk;

import com.google.adk.agents.BaseAgent;
import com.google.adk.web.AdkWebServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Nível 3: o agente do Google ADK rodando dentro de uma aplicação Spring.
 *
 * Divisão de papéis:
 *   Spring AI -> acesso ao modelo (OpenRouter/LiteLLM) e ao vector store do RAG;
 *   Google ADK -> o agente em si: instrução, tools, sessão, estado e runner;
 *   JLine     -> a TUI que mostra o fluxo completo no terminal.
 *
 * A interface é escolhida por {@code assistente.interface} (application.yml,
 * lido de {@code INTERFACE} no .env): "tui" (padrão) abre o terminal; "dev-ui"
 * entrega o mesmo agente para a Dev UI web do Google ADK.
 */
@SpringBootApplication
public class AssistenteAdkApplication {

    public static void main(String[] args) {
        try (var contexto = SpringApplication.run(AssistenteAdkApplication.class, args)) {
            var interfaceEscolhida = contexto.getEnvironment().getProperty("assistente.interface", "tui");

            if ("dev-ui".equalsIgnoreCase(interfaceEscolhida)) {
                // application.yml trava spring.main.web-application-type=none para a
                // TUI, mas essa mesma propriedade é lida pelo SpringApplication que o
                // AdkWebServer cria por baixo dos panos — sem isso ele nunca sobe o Tomcat.
                System.setProperty("spring.main.web-application-type", "servlet");
                AdkWebServer.start(contexto.getBean(BaseAgent.class));
            }
        }
    }
}
