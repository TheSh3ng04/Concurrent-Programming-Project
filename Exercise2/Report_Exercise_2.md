# Exercício 2 — Simulação de processos dependentes num servidor

## 2.1 Contador de pedidos thread-safe

Na versão inicial do servidor, o contador de pedidos era atualizado através de uma variável partilhada não sincronizada. Num contexto concorrente, esta abordagem podia originar condições de corrida, uma vez que duas ou mais threads podiam ler o mesmo valor antigo, incrementando localmente e escrever depois o mesmo resultado final, tendo alguma possibilidade de perder atualizações.

Para evidenciar este problema,  enviar vários pedidos quase em simultâneo. Nessa situação, o valor final do contador pode ficar abaixo do número real de pedidos recebidos, e assim, mostra que o estado interno do servidor não é consistente.

Para resolver esta situação, fizemos com que o contador seja substituído por uma estrutura thread-safe baseada em operações atómicas, evitando bloqueios pesados e garante que cada incremento é executado de forma indivisível, mesmo quando vários clientes fazem pedidos ao mesmo tempo.

## 2.2 Thread pool e estrutura partilhada lock-free

Depois da correção do contador, implementamos um thread pool com um número fixo de threads para processar as instruções pendentes. Esta abordagem permite limitar o paralelismo, reutilizar workers já criados e manter pequeno o código executado no momento em que o pedido HTTP é recebido.

Os resultados das instruções `print` passaram a ser guardados numa estrutura partilhada thread-safe, juntamente com uma marca temporal. Assim, o estado do servidor pode ser consultado a qualquer momento através do pedido de `status`, preservando o histórico das execuções concorrentes.

Uma implementação não thread-safe desta estrutura poderia falhar quando várias threads tentassem acrescentar resultados ao mesmo tempo. Nesse caso, algumas mensagens poderiam perder-se, surgir em ordem inconsistente, ou até causar corrupção do estado interno; com a solução adotada, os resultados são registados de forma robusta mesmo com múltiplos pedidos concorrentes.

## 2.3 Dependências entre instruções com `after`

Nesta parte, implementamos um suporte completo à cláusula `after`, permitindo que uma instrução seja executada só depois de todas as instruções dependentes terem terminado. Assim, desta forma, o servidor consegue respeitar relações de precedência sempre que existem várias tarefas concorrentes no sistema.

A interpretação das instruções considera os três componentes definidos: a operação `print`, um atraso opcional com `@`, e uma lista opcional de dependências introduzida por `after`. Assim, o servidor não apenas executa instruções em paralelo, mas também coordena corretamente a ordem de execução sempre que existem restrições entre elas.

## 2.4 Funcionalidade adicional com `volatile`

Acrescentamos também uma funcionalidade didática de ativação e desativação da simulação no servidor. Quando o modo de simulação está desativado, novos pedidos deixam de ser executados e recusa o ato de registo no estado interno do servidor.

Esta funcionalidade foi implementada com uma variável anotada com `@volatile`, pois o seu valor é lido e escrito por múltiplas threads. Sem `volatile`, uma thread poderia continuar a observar um valor antigo e, por isso, aceitar pedidos mesmo depois de outra thread já ter desativado o servidor.

Com `volatile`, garante-se visibilidade imediata da atualização entre threads. Assim, quando o servidor é desativado, os workers passam rapidamente a observar esse novo estado e os pedidos seguintes são recusados de forma consistente.

## Exemplo experimental

Para validar o comportamento do servidor em condições reais de utilização, realizamos vários testes manuais através de pedidos HTTP. Estes exemplos permitem observar o funcionamento do contador, o registo dos resultados, a execução concorrente das instruções e as dependências definidas no programa de entrada.  

Nos testes seguintes, confirma-se também que o estado interno do servidor é corretamente atualizado e que as respostas devolvidas correspondem ao comportamento esperado.

#### Exemplo 1 — Execução simples

Foi submetido um único pedido ao servidor com uma instrução simples. No estado observado depois da execução, o contador deverá passar a 1 e a lista de resultados deverá incluir o registo correspondente. Este teste confirma o funcionamento básico do contador thread-safe e do registo de resultados no servidor.

```powershell
PS C:\Users\black> curl.exe "http://localhost:8080/reset"
State reset!
PS C:\Users\black> curl.exe "http://localhost:8080/enable"
Simulation enabled
PS C:\Users\black> curl.exe "http://localhost:8080/run-simulation?cmd=print%20%22a%22"
[1] Request accepted from 127.0.0.1
PS C:\Users\black> Start-Sleep -Seconds 2
PS C:\Users\black> curl.exe "http://localhost:8080/status"

<p><b>counter:</b> 1</p>
<p><b>simulation enabled:</b> true</p>
<ul>
<li>[19:23:09.433] [1] Received request from 127.0.0.1: print "a" (instruction 1)</li>
</ul>
```

---

#### Exemplo 2 — Vários pedidos concorrentes

Foram enviados vários pedidos quase ao mesmo tempo para o servidor. No estado final, o contador deverá refletir o número total de pedidos aceites e os resultados deverão ficar registados sem perdas. Este teste ilustra que o contador e a estrutura partilhada suportam concorrência de forma correta.

```powershell
PS C:\Users\black> curl.exe "http://localhost:8080/reset"
State reset!
PS C:\Users\black> curl.exe "http://localhost:8080/enable"
Simulation enabled
PS C:\Users\black> 1..5 | ForEach-Object {
>>   $i = $_
>>   Start-Job -ScriptBlock {
>>     param($n)
>>     curl.exe "http://localhost:8080/run-simulation?cmd=print%20%22job$n%22"
>>   } -ArgumentList $i
>> }

Id     Name            PSJobTypeName   State         HasMoreData     Location             Command
--     ----            -------------   -----         -----------     --------             -------
17     Job17           BackgroundJob   Running       True            localhost            ...
19     Job19           BackgroundJob   Running       True            localhost            ...
21     Job21           BackgroundJob   Running       True            localhost            ...
23     Job23           BackgroundJob   Running       True            localhost            ...
25     Job25           BackgroundJob   Running       True            localhost            ...


PS C:\Users\black> Start-Sleep -Seconds 4
PS C:\Users\black> curl.exe "http://localhost:8080/status"

<p><b>counter:</b> 5</p>
<p><b>simulation enabled:</b> true</p>
<ul>
<li>[19:23:59.193] [1] Received request from 127.0.0.1: print "job1" (instruction 1)</li>
<li>[19:23:59.316] [2] Received request from 127.0.0.1: print "job2" (instruction 1)</li>
<li>[19:23:59.441] [3] Received request from 127.0.0.1: print "job3" (instruction 1)</li>
<li>[19:23:59.551] [4] Received request from 127.0.0.1: print "job4" (instruction 1)</li>
<li>[19:23:59.661] [5] Received request from 127.0.0.1: print "job5" (instruction 1)</li>
</ul>
```
---

#### Exemplo 3 — Pedido com várias instruções independentes

Foi executado um único pedido contendo várias instruções sem dependências entre si. Como não existe cláusula `after`, as instruções podem ser executadas assim que houver threads disponíveis. Este teste mostra que o servidor consegue tratar paralelismo interno dentro do mesmo pedido.

```powershell
PS C:\Users\black> curl.exe "http://localhost:8080/reset"
State reset!
PS C:\Users\black> curl.exe "http://localhost:8080/enable"
Simulation enabled
PS C:\Users\black> curl.exe "http://localhost:8080/run-simulation?cmd=print%20%22A%22%20%40%202%3Bprint%20%22B%22%20%40%201%3Bprint%20%22C%22"
[1] Request accepted from 127.0.0.1
PS C:\Users\black> Start-Sleep -Seconds 4
PS C:\Users\black> curl.exe "http://localhost:8080/status"

<p><b>counter:</b> 1</p>
<p><b>simulation enabled:</b> true</p>
<ul>
<li>[19:06:52.301] [1] Received request from 127.0.0.1: print "C" (instruction 3)</li>
<li>[19:06:53.308] [1] Received request from 127.0.0.1: print "B" (instruction 2)</li>
<li>[19:06:54.315] [1] Received request from 127.0.0.1: print "A" (instruction 1)</li>
</ul>
```

---

#### Exemplo 4 — Dependência linear com `after`

Foi executado um programa em que a segunda instrução depende da primeira. No resultado observado, `B` só deverá ser concluída depois de `A`, respeitando a dependência declarada. Este teste confirma o funcionamento básico da cláusula `after` em dependências lineares.

```powershell
PS C:\Users\black> curl.exe "http://localhost:8080/reset"
State reset!
PS C:\Users\black> curl.exe "http://localhost:8080/enable"
Simulation enabled
PS C:\Users\black> curl.exe "http://localhost:8080/run-simulation?cmd=print%20%22A%22%20%40%202%3Bprint%20%22B%22%20after%201"
[1] Request accepted from 127.0.0.1
PS C:\Users\black> Start-Sleep -Seconds 5
PS C:\Users\black> curl.exe "http://localhost:8080/status"

<p><b>counter:</b> 1</p>
<p><b>simulation enabled:</b> true</p>
<ul>
<li>[19:07:57.038] [1] Received request from 127.0.0.1: print "A" (instruction 1)</li>
<li>[19:07:57.038] [1] Received request from 127.0.0.1: print "B" (instruction 2)</li>
</ul>
```

---

#### Exemplo 5 — Dependência linear com atraso visível

Foi testado um caso em que a segunda instrução depende da primeira e possui também o seu próprio atraso. Assim, o instante de conclusão de `B` deverá surgir claramente depois da conclusão de `A`, o que permite observar de forma mais nítida o efeito combinado de `after` com `@`. Este é um dos testes mais claros para demonstrar que a dependência foi implementada corretamente.

```powershell
PS C:\Users\black> curl.exe "http://localhost:8080/reset"
State reset!
PS C:\Users\black> curl.exe "http://localhost:8080/enable"
Simulation enabled
PS C:\Users\black> curl.exe "http://localhost:8080/run-simulation?cmd=print%20%22A%22%20%40%202%3Bprint%20%22B%22%20%40%203%20after%201"
[1] Request accepted from 127.0.0.1
PS C:\Users\black> Start-Sleep -Seconds 7
PS C:\Users\black> curl.exe "http://localhost:8080/status"

<p><b>counter:</b> 1</p>
<p><b>simulation enabled:</b> true</p>
<ul>
<li>[19:09:09.199] [1] Received request from 127.0.0.1: print "A" (instruction 1)</li>
<li>[19:09:12.205] [1] Received request from 127.0.0.1: print "B" (instruction 2)</li>
</ul>
```

---

#### Exemplo 7 — Dependência de junção

Foi considerado um cenário em que uma instrução final depende da conclusão de duas instruções anteriores. Neste caso, `C` só deverá começar depois de `A` e `B` terminarem. O teste ilustra corretamente uma sincronização com múltiplos predecessores.

```powershell
PS C:\Users\black> curl.exe "http://localhost:8080/reset"
State reset!
PS C:\Users\black> curl.exe "http://localhost:8080/enable"
Simulation enabled
PS C:\Users\black> curl.exe "http://localhost:8080/run-simulation?cmd=print%20%22A%22%20%40%202%3Bprint%20%22B%22%20%40%203%3Bprint%20%22C%22%20after%201%2C2"
[1] Request accepted from 127.0.0.1
PS C:\Users\black> Start-Sleep -Seconds 7
PS C:\Users\black> curl.exe "http://localhost:8080/status"

<p><b>counter:</b> 1</p>
<p><b>simulation enabled:</b> true</p>
<ul>
<li>[19:17:59.239] [1] Received request from 127.0.0.1: print "A" (instruction 1)</li>
<li>[19:18:00.237] [1] Received request from 127.0.0.1: print "B" (instruction 2)</li>
<li>[19:18:00.237] [1] Received request from 127.0.0.1: print "C" (instruction 3)</li>
</ul>
```

---

#### Exemplo 9 — Desativação da simulação

Foi testada a funcionalidade adicional de desativação da simulação no servidor. Depois de desativado, um novo pedido não deverá ser executado normalmente, e o estado interno deverá refletir essa recusa. Este exemplo serve para ilustrar a utilização de uma variável partilhada com `@volatile`.

```powershell
PS C:\Users\black> curl.exe "http://localhost:8080/reset"
State reset!
PS C:\Users\black> curl.exe "http://localhost:8080/disable"
Simulation disabled
PS C:\Users\black> curl.exe "http://localhost:8080/run-simulation?cmd=print%20%22blocked%22"
[1] Request accepted from 127.0.0.1
PS C:\Users\black> Start-Sleep -Seconds 2
PS C:\Users\black> curl.exe "http://localhost:8080/status"

<p><b>counter:</b> 1</p>
<p><b>simulation enabled:</b> false</p>
<ul>
<li>[19:20:09.747] [1] Simulation refused for 127.0.0.1: disabled flag</li>
</ul>
```

---

#### Exemplo 10 — Reativação da simulação

Depois de reativado o servidor, um novo pedido deverá voltar a ser aceite e executado normalmente. Este teste complementa o exemplo anterior e mostra que a flag partilhada é observada corretamente pelas diferentes threads do sistema.

```powershell
PS C:\Users\black> curl.exe "http://localhost:8080/reset"
State reset!
PS C:\Users\black> curl.exe "http://localhost:8080/disable"
Simulation disabled
PS C:\Users\black> curl.exe "http://localhost:8080/enable"
Simulation enabled
PS C:\Users\black> curl.exe "http://localhost:8080/run-simulation?cmd=print%20%22ok-again%22"
[1] Request accepted from 127.0.0.1
PS C:\Users\black> Start-Sleep -Seconds 2
PS C:\Users\black> curl.exe "http://localhost:8080/status"

<p><b>counter:</b> 1</p>
<p><b>simulation enabled:</b> true</p>
<ul>
<li>[19:20:52.218] [1] Received request from 127.0.0.1: print "ok-again" (instruction 1)</li>
</ul>
```