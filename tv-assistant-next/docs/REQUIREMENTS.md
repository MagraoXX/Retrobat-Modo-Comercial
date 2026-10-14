# Ei Chat Next — Requisitos obrigatórios

## Geração antiga
- Teste 24 e Teste 25 são legado e podem ser descartados.
- O Next não precisa preservar assinatura, login ou compatibilidade com eles.

## Voz
- Wake words: `Ei Chat`, `Hey Chat`, `Chat`.
- Conversa contínua e natural.
- Barge-in: falar durante a resposta interrompe a voz da IA e volta a ouvir imediatamente.
- `encerrar chamada` / `finalizar chamada` encerram a sessão.
- Sem loop agressivo de abrir/fechar microfone.
- Apenas um capturador de áudio por vez.
- Indicador visual representa o estado lógico da chamada, não a criação do recognizer.
- Voz natural; evitar voz feminina robotizada do TTS padrão.
- `KEYCODE_ASSIST 219` abre/inicia o assistente.

## Interface
- Projeto visual novo; não reutilizar a aparência feia da geração antiga.
- Conversa em tela cheia com visual limpo no estilo ChatGPT/LibreChat, adaptado à TV.
- Histórico local, composer, sidebar recolhível e foco correto por D-pad.
- Overlay compacto sobre outros apps, inspirado na fluidez do Google Assistant, com identidade própria.
- Main app: duplo Back para sair.
- Overlay: um Back fecha só overlay/sessão.
- Sem login obrigatório.

## TV e apps
- Comandos locais devem ser tentados antes da IA quando aplicável.
- Abrir apps, controlar volume/mute/mídia e demais comandos já definidos anteriormente.
- Overlay deve funcionar sobre YouTube, streaming e demais apps quando permitido pelo Android.
- Inicialização/retorno deve ser robusto após reboot/standby conforme permitido pelo sistema.

## Jogos, filmes, séries e vídeos
- Quando Android/Google TV fornecer screenshot/AssistContent/AssistStructure, o Ei Chat deve entender a tela.
- Jogos: explicar puzzle, mapa, objetivo, menu, item, erro, tutorial e estratégia visível.
- Filmes/séries/vídeos: explicar cena, legenda, menu, contexto visual e responder perguntas sobre o que está na tela.
- Nunca prometer captura de HDMI/console quando o sistema não expuser a imagem ao app.
- Se a imagem não puder ser capturada, informar a limitação e usar contexto disponível.

## Pesquisa e mídia
- Pesquisa atual na web quando a pergunta exigir informação recente.
- Reconhecimento de música por áudio ambiente da TV/soundbar, separado de metadados.
- Não chamar esse reconhecimento de Shazam se não usar o serviço Shazam.

## Arquitetura aberta
- Priorizar componentes open source/gratuitos e intercambiáveis.
- UI: LibreChat como referência visual/comportamental; implementação Android TV própria.
- Realtime: Pipecat como primeira opção; LiveKit Agents como alternativa.
- Áudio local: sherpa-onnx para VAD/KWS/ASR/TTS quando viável.
- TTS natural desacoplado; Kokoro como primeiro candidato, com fallback leve.
- LLM desacoplado e sem exigir conta ChatGPT.
- Componentes devem ser substituíveis sem refazer o aplicativo inteiro.

## Critério para instalar na TCL
- Não instalar build que não compile.
- Primeiro APK funcional deve abrir, navegar por D-pad e não iniciar loops de microfone.
- Depois validar wake word, voz, barge-in, overlay, comandos e visão em etapas.
- Capturas apresentadas como reais só podem vir da TV em execução, nunca de mockup.
