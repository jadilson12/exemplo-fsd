package br.faculdade.assistente;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Assistente universitario: exemplo minimo de RAG + Tool calling com Spring AI.
//
// Tres cenarios para a apresentacao:
//   1. Pergunta com evidencia   -> RAG responde e cita o artigo do regulamento.
//   2. Pergunta sem evidencia   -> assistente admite que nao encontrou fonte.
//   3. Requerimento de terceiro -> a TOOL nega o acesso; quem decide e' o codigo,
//                                  nao o modelo.
@SpringBootApplication
class AssistenteApplication {

    // O Spring Boot (plugin Maven e JarLauncher) procura por `public static void
    // main`: o main de instancia do Java 25 so vale no launcher `java`.
    public static void main(String[] args) {
        SpringApplication.run(AssistenteApplication.class, args);
    }
}
