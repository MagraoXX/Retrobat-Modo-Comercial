# Ei Chat Open Source Rebuild — Arquitetura v1

## Regra principal
A linha Teste 24/25 permanece intacta. Esta reconstrução vive em `tv-assistant-next` e será instalada apenas quando gerar um APK funcional.

## Experiência
- Visual novo, mais limpo e mais bonito que a versão antiga.
- Conversa principal inspirada no fluxo do ChatGPT/LibreChat, adaptada para TV e controle remoto.
- Sidebar recolhível, histórico, composer central, respostas sem caixas pesadas.
- Overlay compacto sobre YouTube/Netflix/jogos e outros apps.
- Modo de voz com aparência de chamada real, estados discretos e sem piscar o microfone.

## Regras preservadas
- Wake phrases: `Ei Chat`, `Hey Chat`, `Chat`.
- `KEYCODE_ASSIST 219` continua suportado.
- Conversa contínua.
- Interrupção natural (barge-in): o usuário pode falar enquanto a IA responde e a resposta é interrompida.
- `encerrar chamada` / `finalizar chamada` encerram a sessão.
- Back no overlay fecha apenas o overlay.
- Main app mantém saída por duplo Back.
- Comandos locais da TV continuam antes da IA quando aplicável.
- Overlay funciona sobre outros apps.
- Histórico e contexto local persistente.
- Sem login obrigatório do usuário.

## Componentes open source priorizados
### UI
- LibreChat (MIT) como referência/base visual e comportamental.
- Implementação Android TV própria para foco por D-pad e desempenho.

### Voz em tempo real
- Pipecat (BSD-2-Clause) como primeira opção de orquestração de turnos, interrupção e streaming.
- LiveKit Agents (Apache-2.0) fica como alternativa de transporte/realtime se precisarmos de uma camada de chamada/WebRTC mais forte.

### Áudio local Android
- sherpa-onnx (Apache-2.0): VAD, ASR/STT, TTS e keyword spotting em Android.
- O wake word não será mais implementado com loop de Android SpeechRecognizer.

### Wake word
- Preferência inicial: keyword spotting local via sherpa-onnx com modelo próprio para `Ei Chat`/`Chat`.
- openWakeWord pode ser usado para protótipo/treino, mas modelos pré-treinados incluídos possuem licença não comercial; não será dependência obrigatória da distribuição.

### TTS / voz
- Interface de TTS desacoplada.
- Kokoro é o primeiro candidato para voz mais natural.
- Fallback local leve via TTS do sherpa-onnx/Piper se o hardware não suportar a voz mais pesada.

### LLM
- Camada desacoplada do app. O aplicativo não exigirá conta do ChatGPT.
- Deve aceitar backend local/open-source e comandos locais sem rede.

## Máquina de estados da voz
`IDLE -> WAKE_DETECTED -> LISTENING -> THINKING -> SPEAKING -> LISTENING`

Regras:
- apenas um capturador de microfone por vez;
- timeout de silêncio volta a IDLE/WAIT sem recriar recognizer em loop;
- durante SPEAKING, VAD continua observando barge-in sem abrir/fechar sessão visual;
- barge-in cancela TTS imediatamente e muda para LISTENING;
- erros de ASR aplicam backoff, nunca retry agressivo;
- indicador de microfone reflete estado lógico da sessão, não criação/destruição de recognizer.

## Fases
1. Scaffold Android TV novo e tema visual.
2. Engine de áudio contínuo + VAD + KWS sem SpeechRecognizer.
3. STT streaming.
4. TTS natural e barge-in.
5. Orquestração de conversa/LLM.
6. Overlay e comandos de TV.
7. Persistência/histórico.
8. Build, instalação na TCL e captura real da tela.
9. Testes de latência, wake word, silêncio, interrupção e estabilidade.
