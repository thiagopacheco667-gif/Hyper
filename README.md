# Dynamic Island 2.0 — Poco X7 Pro (HyperOS)

Central interativa de atividades e multitarefa no topo da tela, em Kotlin + Jetpack Compose.

## Estado atual: Etapa 1 — Fundação

- Ilha permanente sobre todos os apps, centrada no furo real da câmera (`DisplayCutout`).
- Três estados com animação de mola: compacto, aviso curto (peek) e painel expandido.
- Painel expandido com relógio e bateria reais (nível, carregamento, bateria baixa).
- Aviso automático ao conectar o carregador ou quando a bateria fica baixa.
- Tela de configuração com status das permissões e atalhos para os ajustes do HyperOS.
- Preferência de ligar/desligar a ilha salva com DataStore.

## Arquitetura

| Pacote | Responsabilidade |
| --- | --- |
| `service` | `IslandAccessibilityService`: mantém a ilha viva (religado pelo sistema após reiniciar). |
| `overlay` | Janela `TYPE_ACCESSIBILITY_OVERLAY`, cálculo do recorte, ciclo de vida do Compose fora de Activity. |
| `island` | Máquina de estados e UI Compose da ilha. |
| `system` | Bateria e permissões/atalhos do HyperOS. |
| `data` | Preferências persistentes. |
| `ui` | Tela de configuração. |

Por que acessibilidade: é a única camada pública do Android que fica **acima da barra de status**; uma sobreposição comum (`TYPE_APPLICATION_OVERLAY`) fica por baixo dela e perde os toques na área da câmera. O serviço não lê o conteúdo da tela (`canRetrieveWindowContent=false`).

Desempenho: a janela tem sempre o tamanho da ilha; cresce antes da animação e só encolhe no fim, sem redimensionar a cada quadro. Animações infinitas só existem durante o carregamento. A bateria usa eventos raros e só escuta atualizações contínuas com o painel aberto.

## Gerar o APK

Cada push em `main` roda o GitHub Actions, que compila e publica o APK em **Releases** (`DynamicIsland-N.apk`).
Para compilar localmente: Android Studio (JDK 17) → abrir a pasta → `./gradlew assembleDebug`.

Os APKs usam uma chave de desenvolvimento fixa (`keystore/`), então cada build instala por cima da anterior sem perder as permissões. Ela será trocada por uma chave secreta antes do APK final.

## Instalar e testar no Poco X7 Pro

1. Baixe o APK mais recente em Releases e instale (permita instalar de fontes desconhecidas).
2. Abra o app. Em **Serviço da ilha**, toque em *Abrir Acessibilidade* → Apps baixados → Dynamic Island 2.0 → ativar.
   - Se estiver bloqueado: Informações do app → ⋮ → *Permitir configurações restritas*, e tente de novo.
3. Conceda **Bateria sem restrições**, **Inicialização automática** e **Outras permissões → janelas pop-up em segundo plano**.
4. Trave o app nos recentes (segure o card → cadeado).

Roteiro de teste da Etapa 1:
- A cápsula aparece ao redor da câmera em qualquer app (WhatsApp, Chrome, YouTube em retrato).
- Toque: abre o painel com hora e bateria. Toque de novo, toque fora ou espere 8 s: recolhe.
- Segure: abre esta tela de configuração.
- Conecte o carregador: a cápsula se alarga com "Carregando" e a porcentagem por 3 s.
- Gire para paisagem: a ilha some; volte para retrato: reaparece.
- Puxe a barra de notificações e use os botões do sistema: nada deve ficar bloqueado fora da área da cápsula.
- Reinicie o celular: a ilha deve voltar sozinha.
