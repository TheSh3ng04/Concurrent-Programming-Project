# Exercício 1 — Sending money using locks

## 1.1 Locks with native way

A ideia é representar o sistema como a composição paralela de quatro processos: dois participantes e duas trancas.

```text
PAB := lockA?.lockB?.sendAB!.unlockB?.unlockA!.PAB
PBA := lockB?.lockA?.sendBA!.unlockA?.unlockB!.PBA

LockA := lockA!.unlockA!.LockA
LockB := lockB!.unlockB!.LockB

(PAB | PBA | LockA | LockB) \ {lockA, lockB, unlockA, unlockB}
```
Modelámos dois participantes concorrentes que tentam transferir dinheiro um para o outro. 
Cada transferência exige a aquisição de duas trancas, mas as trancas são adquiridas por ordem inversa nos dois participantes. 
A composição paralela dos dois processos com as duas trancas, seguida de restrição das ações internas, produz um LTS com um estado bloqueado, mostrando o deadlock esperado.

## 1.2 Fixing deadlock

```text
PAB := lockA?.lockB?.sendAB!.unlockB?.unlockA!.PAB
PBA := lockA?.lockB?.sendBA!.unlockB?.unlockA!.PBA

LockA := lockA!.unlockA!.LockA
LockB := lockB!.unlockB!.LockB

(PAB | PBA | LockA | LockB) \ {lockA, lockB, unlockA, unlockB}
```

A correção consiste em impor uma ordem total sobre as trancas. Ambos os processos trancam A e depois o B, o que elimina a possibilidade de espera circular. 
Assim, o sistema corrigido é deadlock-free. Este mesmo não é weakly bisimilar ao sistema inicial, uma vez que o comportamento observável do sistema original inclui a possibilidade de bloqueio terminal.


## 1.3 Using locks in a naive way

```text
Zenitsu := lockZ?.lockH?.sendZH!.unlockH?.unlockZ!.Zenitsu
Hinata  := lockH?.lockE?.sendHE!.unlockE?.unlockH!.Hinata
Eren    := lockE?.lockZ?.sendEZ!.unlockZ?.unlockE!.Eren
        +  lockE?.lockH?.sendEH!.unlockH?.unlockE!.Eren

LockZ := lockZ!.unlockZ!.LockZ
LockH := lockH!.unlockH!.LockH
LockE := lockE!.unlockE!.LockE

(Zenitsu | Hinata | Eren | LockZ | LockH | LockE) \ {lockZ, lockH, lockE, unlockZ, unlockH, unlockE}
```

Neste exercício, modelamos o cenário da Figura 1 com três participantes e três locks, usando uma estratégia deliberadamente naive de aquisição de locks. 
O objetivo não é evitar o problema, mas sim preservar a possibilidade de deadlock. 
O participante Zenitsu só pode enviar para Hinata, enquanto Hinata pode enviar para Eren, e Eren pode enviar para ambos os outros participantes. 
Como cada processo pode adquirir locks numa ordem diferente, o sistema pode entrar em espera circular e bloquear a execução.

Cada participante executa primeiro a aquisição do seu lock e depois tenta adquirir o lock do destinatário antes de realizar o envio. 
Esta escolha cria a situação clássica de deadlock por espera circular, pois um processo pode ficar à espera de um lock que já foi adquirido por outro processo.


## 1.4 Generalização com value passing CCS

```text
P1 := send.P2 + recv.P1
P2 := send.P3 + recv.P2
P3 := send.P1 + recv.P3

System := P1 | P2 | P3
System
```

Este modelo generaliza o cenário anterior usando CCS com passagem de valores. A ideia principal é representar cada participante através do mesmo processo parametrizado P[i], onde o parâmetro i identifica o participante corrente. Assim, em vez de definirmos processos distintos para cada par origem-destino, usamos um único esquema comum para os três participantes, o que preserva a simetria do sistema. A vantagem da passagem de valores é que o destino da comunicação passa a ser um valor, em vez de termos de codificar manualmente cada canal possível.

A ação send?j permite ao participante escolher o identificador j do destinatário. Depois da escolha do destinatário, a ação recv!i representa o envio da identidade do participante atual, permitindo modelar a comunicação de forma abstrata e uniforme. A ação recv?k mantém o processo pronto para receber mensagens de qualquer participante, guardando o valor recebido na variável k.

Como os três participantes são instâncias do mesmo processo parametrizado, eles são equivalentes do ponto de vista estrutural e comportamental. Isto corresponde ao enunciado, que pede three equivalent participants. Além disso, o sistema pode ser facilmente estendido para mais participantes sem alterar a lógica principal: bastaria adicionar novas instâncias P[n] ao paralelismo. Desta forma, o modelo é compacto, simétrico e escalável.