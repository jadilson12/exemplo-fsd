package br.faculdade.assistente.tools;

import module java.base;

import br.faculdade.assistente.tools.RequerimentoService.Requerimento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

// TOOL CALLING: o modelo nao executa nada. Ele apenas PROPOE a chamada
// ("quero consultar_requerimento com protocolo=2026-0003") e o Spring AI
// executa este metodo Java, devolvendo o resultado para o modelo redigir a
// resposta final.
//
// A descricao da anotacao @Tool e' o que o modelo le para decidir quando
// chamar. Descricao ruim = tool chamada na hora errada.
@Component
public class RequerimentoTools {

    private static final Logger log = LoggerFactory.getLogger(RequerimentoTools.class);

    private final RequerimentoService requerimentos;

    RequerimentoTools(RequerimentoService requerimentos) {
        this.requerimentos = requerimentos;
    }

    @Tool(description = "Consulta a situação de um requerimento acadêmico do aluno autenticado, a partir do número de protocolo.")
    String consultarRequerimento(
            @ToolParam(description = "Número do protocolo, no formato 2026-0001") String protocolo,
            ToolContext toolContext) {

        var matriculaAutenticada = (String) toolContext.getContext().get("matricula");

        return switch (requerimentos.buscar(protocolo)) {
            case null ->
                    "Protocolo %s não encontrado.".formatted(protocolo);

            // GUARDRAIL na ferramenta: quem nega o acesso e' esta guarda,
            // nao o modelo. Autorizacao nunca fica a cargo do prompt.
            case Requerimento r when !r.matriculaDono().equals(matriculaAutenticada) ->
                    """
                    ACESSO NEGADO: o protocolo %s não pertence ao aluno autenticado. \
                    Oriente o aluno a procurar a secretaria.""".formatted(protocolo);

            case Requerimento(var numero, _, var tipo, var situacao) ->
                    "Protocolo %s | Tipo: %s | Situação: %s".formatted(numero, tipo, situacao);
        };
    }

    // Usado pelo controller para montar o ToolContext da chamada.
    public static Map<String, Object> contextoDoAluno(String matricula) {
        return Map.of("matricula", matricula);
    }
}
