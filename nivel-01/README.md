# Nível 1 — RAG + tool calling com Spring AI

Assistente universitário da palestra: regulamentos fictícios, art. 12 com o
prazo de **20 de março**. Só Spring AI na camada de IA.

| Arquivo | Conceito |
| --- | --- |
| `rag/BaseDeRegulamentos.java` | ingestão do RAG: ler → dividir → embeddings → vector store |
| `web/AssistenteController.java` | `ChatClient` + `QuestionAnswerAdvisor` |
| `tools/RequerimentoTools.java` | tool calling: o modelo propõe, a aplicação executa e autoriza |
| `resources/regulamentos/*.md` | a base de evidências |

## Configurar

```bash
cp .env.example .env    # preencha a chave do provider escolhido
```

O provider é trocado **só por variável de ambiente**, sem mexer no código:

```bash
PROVIDER=openai                               # OpenRouter / OpenAI / Ollama / LiteLLM
OPENAI_BASE_URL=https://openrouter.ai/api     # OpenAI: https://api.openai.com
OPENAI_API_KEY=sk-or-v1-...                   # Ollama: http://localhost:11434
MODELO=nvidia/nemotron-3.5-lightning:free     # precisa suportar tool calling
```

```bash
PROVIDER=google-genai                         # Gemini
GOOGLE_API_KEY=...                            # Google AI Studio
MODELO=gemini-3.8-flash
```

O embedding do RAG tem a chave própria `PROVIDER_EMBEDDING`: `openai`,
`google-genai` ou `transformers` (ONNX dentro da JVM, sem chave e sem rede).
Para falar com o Vertex AI em vez da Gemini Developer API, troque as duas
`api-key` do bloco `google.genai` do `application.yml` por `project-id` e
`location`.

## Rodar

```bash
mise trust && mise install
mise run checar-provider   # confere a chave: HTTP 200
mise run run               # sobe na porta 8080
```

## Demonstração

As respostas abaixo são as que saíram de verdade, com
`MODELO=nvidia/nemotron-3.5-lightning:free` e embedding
`nvidia/llama-nemotron-embed-vl-1b-v2:free`, ambos pelo OpenRouter. Trocando o
`PROVIDER` o conteúdo é o mesmo; o texto varia.

**1. Pergunta com evidência**

```bash
curl -s localhost:8080/perguntar -H 'Content-Type: application/json' \
  -d '{"matricula":"20260001","texto":"Qual é o prazo para solicitar aproveitamento de disciplina?"}'
```

```json
{"resposta":"De acordo com o Art. 12 do Regulamento de Aproveitamento de Disciplina (REG-APROV-2026), o pedido deve ser registrado até 20 de março, pelo portal do aluno...","fontes":["aproveitamento-de-disciplina.md"]}
```

**2. Pergunta sem evidência**

```bash
curl -s localhost:8080/perguntar -H 'Content-Type: application/json' \
  -d '{"matricula":"20260001","texto":"Qual é o valor da taxa de segunda via do diploma?"}'
```

```json
{"resposta":"Não foi possível encontrar a informação sobre o valor da taxa de segunda via do diploma nos trechos de regulamento fornecidos. Recomendo que você procure a Secretaria Acadêmica...","fontes":[]}
```

**3. Acesso negado** — `2026-0003` é de outro aluno:

```bash
curl -s localhost:8080/perguntar -H 'Content-Type: application/json' \
  -d '{"matricula":"20260001","texto":"Como está meu requerimento 2026-0003?"}'
```

```json
{"resposta":"Não foi possível consultar o requerimento 2026-0003. Segundo a consulta realizada, este protocolo não pertence ao aluno autenticado...","fontes":[]}
```

**4. Contraste** — `2026-0001` é do próprio aluno:

```bash
curl -s localhost:8080/perguntar -H 'Content-Type: application/json' \
  -d '{"matricula":"20260001","texto":"Como está meu requerimento 2026-0001?"}'
```

```json
{"resposta":"Tipo: Aproveitamento de disciplina. Situação: Em análise pelo colegiado...","fontes":[]}
```

No console aparece a tool sendo chamada:

```
Tool consultarRequerimento: protocolo=2026-0003 matricula=20260001
```

As mesmas perguntas estão nas tasks `mise run demo-evidencia`,
`demo-sem-fonte` e `demo-acesso-negado` (`mise run demo` roda as três, mas em
paralelo — para narrar, chame uma a uma).
