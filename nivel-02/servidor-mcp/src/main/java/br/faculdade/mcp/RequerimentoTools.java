package br.faculdade.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Tools publicadas via MCP.
 *
 * A identidade do aluno NÃO é um parâmetro da tool: ela chega no header HTTP
 * `X-Matricula`, colocado pelo cliente MCP a partir da sessão autenticada.
 * Se fosse parâmetro, o modelo poderia preencher com qualquer matrícula —
 * é exatamente o ataque que a demo do nível 2 mostra.
 */
@Component
public class RequerimentoTools {

    private static final Logger log = LoggerFactory.getLogger(RequerimentoTools.class);

    private final RequerimentoService requerimentos;

    public RequerimentoTools(RequerimentoService requerimentos) {
        this.requerimentos = requerimentos;
    }

    @Tool(description = "Consulta a situação de um requerimento acadêmico pelo número de protocolo.")
    public String consultarRequerimento(
            @ToolParam(description = "Número do protocolo, no formato 2026-0001") String protocolo) {

        String matricula = matriculaAutenticada();

        if (matricula == null) {
            return "ACESSO NEGADO: requisição sem identificação do aluno.";
        }

        return requerimentos.buscar(protocolo)
                .map(req -> {
                    // Autorização é código do serviço, não instrução de prompt.
                    if (!req.matriculaDono().equals(matricula)) {
                        return "ACESSO NEGADO: o protocolo %s não pertence ao aluno autenticado. "
                                .formatted(protocolo) + "Oriente o aluno a procurar a secretaria.";
                    }
                    return "Protocolo %s | Tipo: %s | Situação: %s"
                            .formatted(req.protocolo(), req.tipo(), req.situacao());
                })
                .orElse("Protocolo %s não encontrado.".formatted(protocolo));
    }

    @Tool(description = "Lista todos os requerimentos do aluno autenticado, com protocolo, tipo e situação.")
    public String listarMeusRequerimentos() {
        String matricula = matriculaAutenticada();

        if (matricula == null) {
            return "ACESSO NEGADO: requisição sem identificação do aluno.";
        }

        var lista = requerimentos.listarDoAluno(matricula);
        if (lista.isEmpty()) {
            return "Nenhum requerimento encontrado para o aluno autenticado.";
        }
        return lista.stream()
                .map(r -> "Protocolo %s | %s | %s".formatted(r.protocolo(), r.tipo(), r.situacao()))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    /** Lê o header da requisição MCP em andamento. */
    private String matriculaAutenticada() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servlet) {
            return servlet.getRequest().getHeader("X-Matricula");
        }
        return null;
    }
}
