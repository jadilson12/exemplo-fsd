package br.faculdade.adk;

import module java.base;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

// Etapa de INGESTAO do RAG: ler documentos, quebrar em trechos, transformar em
// vetores e guardar. Roda uma vez, na subida da aplicacao.
@Configuration
public class BaseDeRegulamentos {

    private static final Logger log = LoggerFactory.getLogger(BaseDeRegulamentos.class);

    // SimpleVectorStore guarda os vetores em memoria. Serve para demonstracao;
    // em producao o mesmo codigo apontaria para PgVector, Redis, Qdrant etc.
    // — o tipo VectorStore nao muda.
    @Bean
    VectorStore vectorStore(EmbeddingModel embeddingModel,
                            @Value("classpath:regulamentos/*.md") Resource[] regulamentos) throws IOException {

        var store = SimpleVectorStore.builder(embeddingModel).build();

        // Quebra cada regulamento em trechos menores. Trecho grande gasta contexto
        // e dilui a busca; trecho pequeno demais perde o sentido do artigo.
        var splitter = TokenTextSplitter.builder()
                .withChunkSize(350)
                .withMinChunkSizeChars(100)
                .build();

        for (var regulamento : regulamentos) {
            var reader = new TextReader(regulamento);
            // Metadado de origem: e' o que permite CITAR a fonte na resposta.
            reader.getCustomMetadata().put("fonte", regulamento.getFilename());

            var trechos = splitter.apply(reader.get());
            store.add(trechos);

            log.info("Regulamento indexado: {} ({} trechos)", regulamento.getFilename(), trechos.size());
        }

        return store;
    }
}
