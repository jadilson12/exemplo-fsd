package br.faculdade.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Servidor MCP da secretaria acadêmica.
 *
 * Ponto da apresentação: este processo NÃO conversa com modelo nenhum.
 * Ele só publica capacidades (tools) num protocolo padrão. Qualquer host de IA
 * — o assistente Spring do nível 2, o Claude Code, um agente Python — consome
 * as mesmas tools sem código de integração específico.
 */
@SpringBootApplication
public class ServidorMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServidorMcpApplication.class, args);
    }

    @Bean
    ToolCallbackProvider ferramentasDaSecretaria(RequerimentoTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
