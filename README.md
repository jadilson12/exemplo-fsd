# Construir Sistemas Modernos na Era da IA

Material da palestra de 20/09/2026: slides e as aplicações de demonstração,
progredindo em três níveis sobre o mesmo caso de uso.

## Conteúdo


- [`nivel-01`](nivel-01) — RAG + tool calling numa única aplicação (Spring Boot + Spring AI).
- [`nivel-02`](nivel-02) — as mesmas tools expostas por um servidor MCP separado.
- [`nivel-03`](nivel-03) — agente com sessão e estado, usando Google ADK e TUI no terminal.

Cada nível é independente e tem seu próprio README com instruções de
configuração, execução e roteiro de demonstração.

## As três aplicações

Todos os níveis usam o mesmo assistente universitário: regulamentos fictícios,
o art. 12 com o prazo de **20 de março** e os requerimentos `2026-0001` a
`2026-0003`.

| Nível | O que acrescenta | Stack |
| --- | --- | --- |
| [`nivel-01`](nivel-01) | RAG + tool calling numa única aplicação | Spring Boot 4.1 + Spring AI 1.1 |
| [`nivel-02`](nivel-02) | As tools viram um servidor MCP separado | Spring Boot 4.1 + Spring AI 1.1 (cliente + servidor MCP) |
| [`nivel-03`](nivel-03) | Agente com sessão e estado, TUI no terminal | Spring Boot 4.1 + Spring AI 2.0 + Google ADK |

Os três cenários da demonstração aparecem em todos:

1. **Pergunta com evidência** — responde 20 de março citando o art. 12.
2. **Pergunta sem evidência** — admite a limitação em vez de inventar.
3. **Acesso negado** — o requerimento de outro aluno é barrado pelo código do
   serviço, nunca pelo prompt.

O que muda de um nível para o outro é *onde* cada peça mora:

```
nível 1   [ assistente: RAG + tools ]
                 │
nível 2   [ assistente: RAG ] ──MCP──► [ secretaria: tools ]
                 │
nível 3   [ agente ADK: decide buscar, decide chamar tool, mantém sessão ]
```

Provider: nos três, chat e embedding saem pelo protocolo OpenAI apontando para
**OpenRouter** ou **LiteLLM** (`OPENAI_BASE_URL`). Modelos usados nos testes:

```bash
MODELO=nvidia/nemotron-3.5-lightning:free                  # chat (precisa de tool calling)
MODELO_EMBEDDING=nvidia/llama-nemotron-embed-vl-1b-v2:free # embedding do RAG
```

Cada nível tem seu `mise.toml` com as versões e as tasks da demo; a chave fica
no `.env`, copiado de `.env.example` e fora do git.
