package br.faculdade.mcp;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

/**
 * A mesma regra de negócio do nível 1 — agora morando no serviço da secretaria,
 * e não dentro do assistente. É essa separação que o MCP torna natural.
 */
@Service
public class RequerimentoService {

    public record Requerimento(String protocolo, String matriculaDono, String tipo, String situacao) {
    }

    private static final Map<String, Requerimento> BASE = List.of(
            new Requerimento("2026-0001", "20260001", "Aproveitamento de disciplina", "Em análise pelo colegiado"),
            new Requerimento("2026-0002", "20260001", "Trancamento total", "Deferido em 05/04/2026"),
            new Requerimento("2026-0003", "20269999", "Aproveitamento de disciplina", "Indeferido em 02/04/2026")
    ).stream().collect(Collectors.toMap(Requerimento::protocolo, r -> r));

    public Optional<Requerimento> buscar(String protocolo) {
        return Optional.ofNullable(BASE.get(protocolo));
    }

    public List<Requerimento> listarDoAluno(String matricula) {
        return BASE.values().stream()
                .filter(r -> r.matriculaDono().equals(matricula))
                .toList();
    }
}
