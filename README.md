# Cargo Tracker with a shipment dashboard and a guarded AI assistant

This branch is the demo for **Adding AI to Enterprise Java Without Rewriting the Application** (TEC-4881) at JCON at IBM TechXchange 2026, by Ed Burns and Brian Benz.

It starts from [Eclipse Cargo Tracker](https://github.com/eclipse-ee4j/cargotracker), a Jakarta EE application. It adds three things:

- A shipment dashboard with a world map. Ships follow their voyages, and the page updates when handling events arrive.
- Document search over shipping procedures and port notices.
- An AI assistant that answers questions about shipments. Its answers are typed Java records, so the dashboard can light up the shipments it names.

The domain model does not change. The AI feature lives in its own package, `org.eclipse.cargotracker.ai`, behind one Java interface. The app works when the model is slow, wrong, or turned off.

The original Cargo Tracker README is in [CARGO-TRACKER.md](CARGO-TRACKER.md). The 2025 version of this demo is the tag `jcon-us-2025`.

## Run it with Docker Compose

You need Docker with Compose v2. A model endpoint is optional. Without one, the assistant stays off and the rest of the app works.

1. Copy the settings file and add your model endpoint:

   ```bash
   cp .env.example .env
   ```

   Set `AI_BASE_URL`, `AI_API_KEY`, and `AI_CHAT_MODEL`. The file `.env` is in `.gitignore`. Do not commit it.

2. Build and start:

   ```bash
   docker compose up --build
   ```

   The first build downloads Maven dependencies and Open Liberty features. It takes several minutes. Later builds are faster.

3. Open these pages:

   | Page | Address |
   | --- | --- |
   | Shipment dashboard | http://localhost:8080/cargo-tracker/dashboard/ |
   | Cargo Tracker | http://localhost:8080/cargo-tracker/ |
   | Traces in Jaeger | http://localhost:16686 |

If port 8080 or 16686 is in use, set `APP_PORT` or `JAEGER_PORT` in `.env`.

The sample data lives in an in-memory database. To start the demo again from the beginning, recreate the container:

```bash
docker compose up -d --force-recreate cargo-tracker
```

## What the dashboard shows

| Part | What it does |
| --- | --- |
| Map | Ports, ships, trains, and their lanes. Colors show the status. Rings mark cargo that needs attention. Warning signs mark port notices. |
| Tiles | Counts of shipments in transit, in port, booked, delivered, and needing attention. Select a tile to filter the map and the table. |
| Ask about shipments | Sends a question to the assistant. The answer shows the shipments it names, the sources it used, the model, the time, the tokens, and a link to the trace. |
| Needs attention | Cargo that is misdirected, misrouted, not routed, or late. |
| Live events | Handling events as they arrive from the existing server-sent events endpoint. |
| Live traffic | Reports the next planned handling event every 20 seconds, the way a port system would. **Next event** reports one now. |
| Play 4 days | Moves the ships along their schedules to show where they will be. |

The dashboard reads the existing data through a read-only REST endpoint, `GET /rest/dashboard`. Live updates come from the existing endpoint `GET /rest/cargo`.

## How the AI feature fits in

The assistant follows four rules from the talk:

1. **One Java interface per AI feature.** The rest of the app calls `CargoAssistant` and never sees a prompt.
2. **Typed results.** The model returns a `CargoAnswer` record. If the JSON does not fit the record, the call fails.
3. **The app works without the model.** Every call has a timeout and a fallback. A rate limit or server error gets one retry. A timeout does not.
4. **Configuration picks the model.** A new model is a configuration change and a test run.

The main classes are in `src/main/java/org/eclipse/cargotracker/ai`:

| Class | Job |
| --- | --- |
| `CargoAssistant` | The Java interface. LangChain4j creates the implementation. |
| `CargoAnswer` | The typed answer: text, tracking IDs, and sources. |
| `StaysOnTopic` | Input guard. Refuses questions that are not about cargo, and prompt injection. |
| `CargoQuestionService` | Checks every answer and turns any failure into a clear fallback. |
| `CargoTools` | Read-only tools. The assistant can look at cargo. It cannot book or change anything. |
| `Procedures` | Picks the procedure document for each kind of problem. The app decides, not the model. |
| `DocumentIndex` | Document search with an embedding model that runs inside the app. No vector database. |
| `ChatModels` | Builds the model client from settings. The app and the evaluation tests both use it. |
| `ModelCallTracing` | Adds an OpenTelemetry span with token counts for every model call. |

The dashboard code is in `src/main/java/org/eclipse/cargotracker/interfaces/dashboard` and `src/main/webapp/dashboard`. The sample fleet and the live traffic are in `src/main/java/org/eclipse/cargotracker/demo`.

## Model settings

Set these in `.env` for Docker Compose, or in `k8s/ai-config.yaml` for Kubernetes.

| Setting | Default | Meaning |
| --- | --- | --- |
| `AI_ENABLED` | `false` | Turns the assistant on or off. |
| `AI_BASE_URL` | `https://api.openai.com/v1` | Any OpenAI-compatible endpoint. |
| `AI_API_KEY` | none | The key. Keep it in `.env` or a Kubernetes Secret. |
| `AI_CHAT_MODEL` | `gpt-6.1-sol` | The model name, or the deployment name in Azure OpenAI. |
| `AI_API` | `responses` | `responses` for the Responses API, or `chat-completions`. |
| `AI_REASONING_EFFORT` | empty | `low`, `medium`, or `high` for reasoning models. |
| `AI_TIMEOUT_SECONDS` | `20` | How long to wait for one model call. |
| `DEMO_TRAFFIC_ENABLED` | `false` | Starts live traffic at startup. |
| `DEMO_TRAFFIC_INTERVAL_SECONDS` | `20` | Seconds between handling reports. |
| `DEMO_FLEET_ENABLED` | `true` | Loads the sample fleet. |

Examples of `AI_BASE_URL`:

| Service | `AI_BASE_URL` | `AI_API` |
| --- | --- | --- |
| Azure OpenAI in Microsoft Foundry | `https://YOUR-RESOURCE.services.ai.azure.com/openai/v1/` | `responses` |
| OpenAI | `https://api.openai.com/v1` | `responses` |
| GitHub Models | `https://models.github.ai/inference` | `chat-completions` |
| Ollama in this Compose file | `http://ollama:11434/v1` | `chat-completions` |

With `gpt-6.1-sol`, use the Responses API. That model does not accept tool calls through Chat Completions.

## Try the three demos

### 1. Ask the app

On the dashboard, select **Needs attention**, then **JKL567**, then **Off-topic test**. The first two answers light up shipments on the map and cite a tool and a procedure document. The third question never reaches the model. The input guard refuses it.

### 2. Measure it

Run the evaluation tests against the model. They call the model with 40 cargo questions and 20 off-topic questions:

```bash
# Linux and macOS: export the settings from .env first
set -a; . ./.env; set +a
EVAL_P95_MILLIS=15000 EVAL_MAX_TOKENS=3500 ./mvnw -Popenliberty -Dtest=CargoAssistantEvalTest test
```

```powershell
# Windows PowerShell
Get-Content .env | Where-Object { $_ -match '^[A-Z_]+=' } | ForEach-Object { $n, $v = $_ -split '=', 2; Set-Item "env:$n" $v }
$env:EVAL_P95_MILLIS = "15000"; $env:EVAL_MAX_TOKENS = "3500"
./mvnw -Popenliberty -Dtest=CargoAssistantEvalTest test
```

| Check | Pass rule |
| --- | --- |
| Finds the right cargo | 95% of the questions, which is 38 of 40 |
| Cites a real source | Every answer |
| Stays on topic | Every off-topic question is refused |
| Returns valid data | Every answer passes Bean Validation |
| Answers in time | 95th percentile under `EVAL_P95_MILLIS`, 4,000 by default |
| Costs what we expect | Tokens per answer under `EVAL_MAX_TOKENS`, 2,500 by default |

The defaults match the table in the talk. With `gpt-6.1-sol`, answers take 6 to 12 seconds and up to about 3,000 tokens, so the time and cost checks fail with the defaults. That is the point of the gates: a model change becomes a visible decision. Set `EVAL_LIMIT=10` to run fewer questions during a live demo.

Then open a trace from the **Open trace** link under an answer. You see the REST request, the document search, each model call with its tokens, and each tool call.

### 3. Break it

Each step changes configuration, not code. The image stays the same.

| Problem | Docker Compose | What you see |
| --- | --- | --- |
| The model is slow | `AI_TIMEOUT_SECONDS=1 docker compose up -d cargo-tracker` | Fallback: "The model did not answer in time." The map still works. |
| A bad key | Change `AI_API_KEY` in `.env`, then `docker compose up -d cargo-tracker` | Fallback: "The model endpoint rejected the API key." |
| A model name the endpoint does not know | Change `AI_CHAT_MODEL` in `.env`, then `docker compose up -d cargo-tracker` | Fallback with the reason. Change it back to roll back. |
| Turn the feature off | `AI_ENABLED=false docker compose up -d cargo-tracker` | The assistant shows as off. Everything else works. |

In PowerShell, set the variable first, for example `$env:AI_TIMEOUT_SECONDS = "1"`, then run `docker compose up -d cargo-tracker`. Remove it with `Remove-Item env:AI_TIMEOUT_SECONDS` and run the same command again to go back. Each change recreates the container, so the sample data starts over.

To make cargo go wrong through the existing handling API, report that SIN202 was unloaded in Rotterdam. Its route says Hamburg:

```bash
curl -X POST http://localhost:8080/cargo-tracker/rest/handling/reports \
  -H "Content-Type: application/json" \
  -d "{\"completionTime\":\"$(date -u '+%-m/%-d/%Y %-I:%M %p')\",\"trackingId\":\"SIN202\",\"eventType\":\"UNLOAD\",\"unLocode\":\"NLRTM\",\"voyageNumber\":\"AE210\"}"
```

```powershell
$when = (Get-Date).ToUniversalTime().ToString("M/d/yyyy h:mm tt", [Globalization.CultureInfo]::InvariantCulture)
Invoke-RestMethod -Method Post -Uri http://localhost:8080/cargo-tracker/rest/handling/reports -ContentType "application/json" `
  -Body (@{ completionTime = $when; trackingId = "SIN202"; eventType = "UNLOAD"; unLocode = "NLRTM"; voyageNumber = "AE210" } | ConvertTo-Json)
```

The dashboard shows SIN202 as misdirected within a few seconds. Ask the assistant about SIN202 to get the procedure.

## Deploy to Azure Kubernetes Service

The scripts create a resource group, a container registry, and a one-node AKS cluster. They build the image, push it, and apply the manifests in `k8s/`. They read the model settings from `.env`. The key goes into a Kubernetes Secret. It is never in the image or on a command line.

You need the Azure CLI, signed in with `az login`, plus Docker and kubectl.

```powershell
./deploy/aks/deploy.ps1
```

```bash
./deploy/aks/deploy.sh
```

Then open the app and the traces through port forwarding:

```bash
kubectl -n cargo-tracker port-forward svc/cargo-tracker 8080:80
kubectl -n cargo-tracker port-forward svc/jaeger 16686:16686
```

The app runs as one replica, because the sample keeps its data in an in-memory database.

To break it in Kubernetes, change one setting and restart. `deploy/aks/set-ai.ps1` does both:

```powershell
./deploy/aks/set-ai.ps1 timeout-seconds 1        # the model times out
./deploy/aks/set-ai.ps1 timeout-seconds 20       # back to normal
./deploy/aks/set-ai.ps1 enabled false            # turn the assistant off
```

To roll back a release, use `kubectl -n cargo-tracker rollout undo deployment/cargo-tracker`.

To rotate the key, run `./deploy/aks/rotate-key.ps1`. It asks for the new key, updates the Secret through standard input, and restarts the app. The image stays the same.

The cluster costs money while it runs. Delete everything with:

```bash
az group delete --name cargo-tracker-ai
```

## Run without Docker

You need JDK 21. Open Liberty runs in Maven:

```bash
export AI_ENABLED=true AI_BASE_URL=... AI_API_KEY=... AI_CHAT_MODEL=gpt-6.1-sol AI_REASONING_EFFORT=low
./mvnw -Popenliberty liberty:run
```

The offline tests do not need a model:

```bash
./mvnw -Popenliberty test
```

## Run with a local model

For a demo without internet access, Compose can start Ollama. Pull a model that supports tool calls, then point `.env` at it:

```bash
docker compose --profile local-model up -d
docker compose exec ollama ollama pull qwen3:4b
```

```
AI_BASE_URL=http://ollama:11434/v1
AI_API=chat-completions
AI_API_KEY=ollama
AI_CHAT_MODEL=qwen3:4b
AI_REASONING_EFFORT=
```

Small local models answer more slowly on a laptop, and their answers are less reliable. Run the evaluation tests before you trust one.

## Data and credits

- The sample fleet, voyages, port notices, and procedures are made up for the demo. They are not real shipping data.
- Land and lake outlines on the map come from [Natural Earth](https://www.naturalearthdata.com/), which is in the public domain.
- The sea lanes are hand-placed waypoints. They are a drawing aid, not a navigation chart.
- Eclipse Cargo Tracker is licensed under the terms in [LICENSE.md](LICENSE.md).
