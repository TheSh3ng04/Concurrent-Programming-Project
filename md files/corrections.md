## Exercício 2.1 — Contador de Pedidos Thread-Safe

### Problema Identificado

O contador original no ficheiro `ServerState.scala` era declarado como uma variável mutável simples:

```scala
var counter = 0
```

A operação `counter += 1` em `Routes.scala` **não é atómica** — é compilada internamente em três passos separados:

1. **Leitura** do valor atual de `counter`
2. **Incremento** do valor lido
3. **Escrita** do novo valor

Quando dois pedidos chegam ao servidor em simultâneo (em threads diferentes), ambos podem executar o passo 1 ao mesmo tempo, lendo o mesmo valor. Cada thread incrementa e escreve de volta o mesmo resultado, perdendo efetivamente um dos incrementos. Este problema chama-se **race condition** (condição de corrida).

**Exemplo de programa que demonstra a falha:**

Enviar ao servidor, ao mesmo tempo, um grande número de pedidos em paralelo (por exemplo, usando vários blocos no cliente). O contador final será inferior ao número real de pedidos recebidos.

### Solução Implementada

Substituição do `var counter` por um `AtomicInteger` da biblioteca `java.util.concurrent.atomic`:

```scala
import java.util.concurrent.atomic.AtomicInteger

private val _counter = new AtomicInteger(0)
def incrementAndGetCounter(): Int = _counter.incrementAndGet()
def counter: Int = _counter.get()
def resetCounter(): Unit = _counter.set(0)
```

O método `incrementAndGet()` executa a operação de leitura-incremento-escrita como uma **única instrução atómica** ao nível do hardware (instrução CAS — *Compare-And-Set*), garantindo que dois threads nunca produzem o mesmo número de pedido, independentemente da ordem de execução.

Esta abordagem é **lock-free**: não utiliza `synchronized`, evitando o custo de aquisição de monitores e eliminando o risco de deadlock nesta operação.

---

## Alteração em `Routes.scala` — Contador thread-safe

### O que foi alterado

Em dois sítios do ficheiro `Routes.scala`, substituímos o acesso direto à variável `counter` por chamadas aos novos métodos de `ServerState`:

```scala
//antes
state.counter += 1
val cnt = state.counter
//depois
cnt = state.incrementAndGetCounter()
```
```scala
//antes
state.counter = 0
//depois
state.resetCounter()` 
```
### Porquê

A variável `counter` é partilhada entre várias threads. Ler e incrementar em dois passos separados cria uma **race condition**: duas threads podem ler o mesmo valor antes de qualquer delas escrever, produzindo contagens duplicadas. O método `incrementAndGetCounter()` usa `AtomicInteger.incrementAndGet()`, que garante que incremento e leitura acontecem como operação atómica.

### Como verificar

1. Arrancar o servidor com `sbt run` na pasta `process-simulator/server/`.
2. Abrir o cliente em `process-simulator/client/index.html` num browser.
3. Submeter várias tarefas em paralelo (usar o botão de exemplo ou enviar várias requisições rápidas).
4. Confirmar que os IDs de tarefa no output são **todos diferentes e sequenciais**, sem repetições nem saltos.

Alternativamente, via `curl` em PowerShell:

```powershell
# enviar 5 tarefas seguidas
1..5 | ForEach-Object {
  Start-Job { curl -s http://localhost:8080/run -d '{"program":"print 1"}' }
}
Get-Job | Wait-Job | Receive-Job
```

Os campos `id` na resposta devem ser 1, 2, 3, 4, 5 — nunca repetidos.

---


### Alteração em `ServerState.scala`

No ficheiro `ServerState.scala`, o contador partilhado foi substituído por um `AtomicInteger`. Em vez de usar uma variável inteira normal, o servidor passou a usar uma estrutura própria para acessos concorrentes.

Foram definidos três métodos principais: `incrementAndGetCounter()`, para incrementar e obter o valor de forma atómica; `counter`, para leitura segura; e `resetCounter()`, para reiniciar o valor. Assim, o estado do servidor fica preparado para múltiplos pedidos em paralelo sem race conditions.


---

### Alteração em `ServerState.scala`

Nesta fase, o estado interno do servidor foi estendido para guardar os resultados das instruções `print` com timestamp. Para isso, foi usada uma `ConcurrentLinkedQueue[String]`, que é uma estrutura thread-safe adequada para acessos concorrentes sem locks explícitos.

Foi também criado o método `addResult`, que adiciona cada resultado com a hora atual, e o método `toHtml` passou a mostrar não só o contador mas também a lista de resultados. Além disso, a operação de reset limpa tanto o contador como os resultados guardados.

---


### Suporte à cláusula `after`

Foi implementado um mecanismo simples de dependências entre comandos, permitindo expressões como `b after a` e cadeias do tipo `a; b after a; c after b`. A execução é feita de forma sequencial no servidor, verificando para cada comando se a dependência indicada já foi satisfeita no contexto do mesmo pedido.

Durante os testes, confirmou-se que cadeias válidas de dependência são executadas pela ordem esperada. Verificou-se também que, ao enviar múltiplos comandos através do parâmetro `cmd` da URL, os separadores `;` devem ser codificados corretamente (`%3B`) para que toda a sequência seja interpretada pelo servidor.


---

### Variável `volatile`

Foi adicionada uma variável partilhada de controlo do servidor, usada como sinalizador global para permitir ou bloquear a execução de novas simulações. Como este valor pode ser alterado por um pedido HTTP e lido por uma thread de execução do servidor, a variável foi declarada com `@volatile`.

O uso de `@volatile` garante visibilidade entre threads, isto é, uma alteração feita por uma thread torna-se observável pelas restantes sem necessidade de usar locks para este caso simples. Desta forma, a implementação demonstra um cenário típico em que `volatile` é apropriado: sinalização e coordenação leve entre threads.