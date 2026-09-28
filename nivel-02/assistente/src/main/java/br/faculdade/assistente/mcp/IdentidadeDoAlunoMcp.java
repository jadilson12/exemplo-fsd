package br.faculdade.assistente.mcp;

import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.common.McpTransportContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.net.http.HttpRequest;

/**
 * Propaga a identidade do aluno para o servidor MCP no header `X-Matricula`.
 *
 * É o equivalente, no nível 2, ao ToolContext do nível 1: a matrícula viaja
 * pelo transporte, fora do alcance do modelo. Num sistema real este valor viria
 * do token da sessão autenticada; aqui vem de `demo.matricula`, no application.yml.
 */
@Configuration
public class IdentidadeDoAlunoMcp {

    @Bean
    McpSyncHttpClientRequestCustomizer identidadeDoAluno(@Value("${demo.matricula}") String matricula) {
        return (HttpRequest.Builder builder, String method, URI endpoint, String body,
                McpTransportContext context) -> builder.header("X-Matricula", matricula);
    }
}
