package br.faculdade.assistente.tools;
import module java.base;
import org.springframework.stereotype.Service;

// Regra de negocio da faculdade, sem nenhuma dependencia de IA.
// Na vida real seria o servico/repositorio que a aplicacao Spring ja tem.
@Service
class RequerimentoService {

    record Requerimento(String protocolo, String matriculaDono, String tipo, String situacao) {
    }

    private static final Map<String, Requerimento> BASE = Stream.of(
            new Requerimento("2026-0001", "20260001", "Aproveitamento de disciplina", "Em análise pelo colegiado"),
            new Requerimento("2026-0002", "20260001", "Trancamento total", "Deferido em 05/04/2026"),
            new Requerimento("2026-0003", "20269999", "Aproveitamento de disciplina", "Indeferido em 02/04/2026")
    ).collect(Collectors.toUnmodifiableMap(Requerimento::protocolo, Function.identity()));

    Requerimento buscar(String protocolo) {
        return BASE.get(protocolo);
    }
}
