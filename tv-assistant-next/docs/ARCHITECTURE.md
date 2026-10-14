# Ei Chat Next — Arquitetura v2

## Regra principal
Teste 24 e Teste 25 são legado descartável. O desenvolvimento ativo vive em `tv-assistant-next`. Não existe obrigação de preservar assinatura, login, dados ou compatibilidade binária com a geração antiga.

## Experiência
- Visual novo, mais limpo e mais bonito que a versão antiga.
- Conversa principal inspirada no fluxo do ChatGPT/LibreChat, adaptada para TV e controle remoto.
- Sidebar recolhível, histórico, composer central e respostas sem caixas pesadas.
- Overlay compacto sobre YouTube, streaming, jogos e outros apps quando o Android permitir.
- Modo de voz com aparência de chamada real, estados discretos e sem piscar o microfone.

## Regras preservadas
- Wake phrases: `Ei Chat`, `Hey Chat`, `Chat`.
- `KEYCODE_ASSIST 219` continua suportado.
- Conversa contínua.
- Interrupção natural (barge-in): falar durante a resposta interrompe o TTS e volta imediatamente para escuta.
- `encerrar chamada` / `finalizar chamada` encerram a sessão.
- Back no overlay fecha apenas o overlay.
- Main app mantém saída por duplo Back.
- Comandos locais da TV continuam antes da IA quando aplicável.
- Histórico e contexto local persistente.
- Sem login obrigatório.

## Pipeline de áudio
Há um único capturador `AudioRecord`, compartilhado pelos módulos:
`ContinuousAudioEngine -> AudioBus -> WakeWordEngine / VAD / SpeechToTextEngine -> AssistantRouter -> TextToSpeechEngine`

Nenhum módulo de wake word ou STT pode abrir um segundo microfone por conta própria.

## Componentes open source priorizados
### UI
- LibreChat (MIT) como referência visual/comportamental.
- Implementação Android TV própria para D-pad e desempenho.

### Voz em tempo real
- Pipecat como primeira opção de orquestração de turnos, interrupção e streaming.
- LiveKit Agents fica como alternativa se precisarmos de transporte/WebRTC mais forte.

### Áudio local Android
- sherpa-onnx para VAD e ASR/STT local no Android.
- O `android.speech.SpeechRecognizer` não faz parte da nova fundação.

### Wake word
- Para português, sherpa-onnx KWS não será a fundação principal porque os modelos públicos disponíveis não cobrem bem `Ei Chat`.
- O plano é usar o pipeline openWakeWord para treinar um modelo ONNX próprio em português para `Ei Chat`/`Hey Chat`/`Chat`.
- O runtime Android deverá consumir PCM do `AudioBus`; ele não deve criar outro `AudioRecord`.
- Modelos prontos com licença não comercial não serão dependência obrigatória do produto.

### STT
- Primeiro candidato: sherpa-onnx com modelo multilíngue compatível com português.
- O modelo será baixável/substituível para não amarrar o APK a um único ASR.

### TTS / voz
- Interface de TTS desacoplada.
- Kokoro é o primeiro candidato para voz mais natural.
- Fallback local leve via sherpa-onnx/Piper se necessário.

### LLM
- Camada desacoplada. O aplicativo não exige conta ChatGPT.
- Deve aceitar backend local/open-source e comandos locais sem rede.

### Visão / contexto da tela
- `ScreenContextProvider` concentra screenshot, AssistContent e AssistStructure quando o Android fornecer.
- Jogos: puzzle, mapa, objetivo, menu, itens, erros e tutorial visíveis.
- Filmes, séries e vídeos: cena, legenda, menus e contexto visual.
- HDMI/console só será tratado quando o sistema realmente expuser pixels/contexto ao app.

## Máquina de estados da voz
`IDLE -> WAKE_DETECTED -> LISTENING -> THINKING -> SPEAKING -> LISTENING`

Regras:
- apenas um capturador de microfone;
- silêncio não recria recognizer em loop;
- durante `SPEAKING`, VAD continua observando barge-in pelo mesmo fluxo de PCM;
- barge-in cancela TTS imediatamente;
- erros aplicam backoff, nunca retry agressivo;
- indicador de microfone representa estado lógico da sessão.

## Fases
1. Scaffold Android TV novo e tema visual. ✅
2. Captura contínua com `AudioRecord`, sem SpeechRecognizer. ✅ base compilada
3. AudioBus + interfaces modulares. Em andamento
4. VAD + STT local multilíngue.
5. Wake word próprio `Ei Chat`.
6. TTS natural + barge-in.
7. Orquestração de conversa/LLM.
8. Overlay e comandos de TV.
9. Visão de jogos/filmes/séries/vídeos.
10. Persistência/histórico.
11. Instalação na TCL, screenshots reais e testes de estabilidade/latência.
