# laya-local demo

Runs `typesafe-ai-java` against a self-hosted
[laya-serve](https://pypi.org/project/laya-serve/) instance instead of the
hosted TypeSafe API. laya-serve speaks the Jev wire contract, so the SDK code
is identical — only the base URL changes.

## 1. Start laya-serve

Fake backend (no weights, instant):

```bash
python3 -m venv ~/laya-serve-venv
source ~/laya-serve-venv/bin/activate
pip install laya-serve
laya-serve
```

Server listens on `localhost:8000`. Check `curl localhost:8000/healthz`.

Real Laya weights (optional, downloads a ~421M-param checkpoint on first run):

```bash
pip install 'laya-serve[inference]'
LAYA_SERVE_BACKEND=laya LAYA_SERVE_DEVICE=cpu laya-serve
```

## 2. Run the demo

From this directory:

```bash
mvn -q compile exec:java
```

Expected output (values differ with the real backend):

```
Laya local demo - base url: http://localhost:8000

Models known to laya-serve:
  - laya-english
  - jev-latest
  ...

Evaluating a support message with three typed questions:
model          : laya-english
is_urgent      : 0.91  (yes above 0.7? true)
department     : billing  confidence 0.88
frustration    : 1.05  (Frustrated)
usage          : input=... output=...

Same client code, same question types, local weights.
Point baseUrl at https://api.typesafe.ai and it goes hosted.
```

## 3. Switching hosted vs local

```java
// local
TypeSafeClient client = TypeSafeClient.laya("http://localhost:8000", "local-key");

// hosted
TypeSafeClient client = TypeSafeClient.builder(System.getenv("TYPESAFE_API_KEY")).build();

// or by environment, no code change:
//   TYPESAFE_API_KEY=<your key>  TYPESAFE_BASE_URL=http://localhost:8000
TypeSafeClient client = TypeSafeClient.fromEnv();
```
