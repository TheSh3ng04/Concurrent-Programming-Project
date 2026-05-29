# Exercise 3

## 3.1 Ticket office with Actors

A ideia é completar o template fornecido e implementar um ator de bilheteira. Este ator deve poder receber mensagens `ToSell(n)` para aumentar o número de bilhetes disponíveis e mensagens `Buy(n)` para vender bilhetes. Se o pedido for maior do que o stock disponível, a compra deve falhar e deve ser registada uma mensagem de erro.

```scala
import akka.actor.{Actor, ActorSystem, Props}
import akka.event.Logging

object TicketOfficeTest extends App {
    case class ToSell(n:Int)
    case class Buy(n: Int)
    case object Bye

    class SellerActor extends Actor {
        val log = Logging(context.system, this)
        var stock: Int = 0

        def receive: Actor.Receive = {
            case ToSell(n) =>
                stock += n
                log.info(s"Added $n tickets. Stock is now $stock.")

            case Buy(n) =>
                if (n <= stock) {
                    stock-= n
                    log.info(s"Sold $n tickets. Stocks is now $stock.")
                } else {
                    log.error(s"Purchase failed: request $n, avaliable $stock.")
                }

            case "Bye" =>
                log.info(s"Closing ticket office. Final stock = $stock.")
                context.stop(self)
        }
    }

    val sys = ActorSystem("TicketSys")
    val ticketOffice = sys.actorOf(Props[SellerActor], "mainoffice")

    ticketOffice ! ToSell(2000)
    for (_ <- 0 until 101) ticketOffice ! Buy(20)
    println("Tried to buy many 2020 tickets.")
    ticketOffice ! "Bye"

    Thread.sleep(3000)
    sys.terminate()
}
```

Neste excerto de código, foi implementado um `SellerActor` que mantém o stock interno de bilhetes. Quando recebe `ToSell(n)`, aumenta o stock; quando recebe `Buy(n)`, verifica se existem bilhetes suficientes. Se existirem, reduz o stock; caso contrário, regista uma mensagem de erro e mantém o estado inalterado.

Esta solução segue o modelo de atores porque o stock pertence a um único ator e só é modificado através de mensagens. Assim, evita-se partilha direta de memória e não é necessário usar sincronização explícita.


## 3.2 Tickets office  with a delegation funtionality

### Código Scala

```scala
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.event.Logging

object TicketOfficeDelegationTest extends App {

    case class ToSell(n: Int)
    case class Buy(n: Int)
    case class ChildSaleFailed(remaining: Int)
    case object "Bye"


    class ChildSellerActor(parent: ActorRef, var stock: Int) extends Actor {
        val log = Logging(context.system, this)

        def receive: Actor.Receive = {
            case Buy(n) =>
                if (n <= stock) {
                    stock -= n
                    log.info(s"[Child] Sold $n tickets. Remaining: $stock.")
                } else {
                    log.error(s"[Child] Purchase failed: requested $n, available $stock.")
                    parent ! ChildSaleFailed(stock)
                    context.stop(self)
                }

        case "Bye" =>
            log.info(s"[Child] Closing. Returning $stock tickets to parent.")
            parent ! ChildSaleFailed(stock)
            context.stop(self)
        }
    }

    class SellerActor extends Actor {
        val log = Logging(context.system, this)
        var stock: Int = 0
        val threshold = 100
        val childChunk = 50

        def receive: Actor.Receive = {
            case ToSell(n) =>
                stock += n
                log.info(s"Added $n tickets. Stock is now $stock.")
                if (stock > threshold) {
                    val childStock = math.min(childChunk, stock)
                    stock -= childStock
                    val child = context.actorOf(Props(new ChildSellerActor(self, childStock)))
                    log.info(s"Created child ${child.path.name} with $childStock tickets. Parent stock: $stock.")
            }

            case Buy(n) =>
                if (n <= stock) {
                    stock -= n
                    log.info(s"Sold $n tickets. Stock is now $stock.")
                } else {
                    log.error(s"Purchase failed at parent: requested $n, available $stock.")
                }

            case ChildSaleFailed(remaining) =>
                stock += remaining
                log.info(s"Child returned $remaining tickets. Stock is now $stock.")

            case "Bye" =>
                log.info(s"Closing ticket office. Final stock = $stock.")
                context.stop(self)
        }
    }

    val sys = ActorSystem("TicketSys")
    val ticketOffice = sys.actorOf(Props[SellerActor], "mainoffice")

    ticketOffice ! ToSell(200)
    ticketOffice ! Buy(30)
    ticketOffice ! Buy(70)
    ticketOffice ! "Bye"

    Thread.sleep(3000)
    sys.terminate()
}
```

Aqui temos um sistema de bilheteira com atores em Akka. O ator principal (SellerActor) mantém o stock de bilhetes e responde às mensagens de `ToSell(n)` e `Buy(n)`. Quando ele recebe `ToSell(n)`, atualiza o seu stock interno. Se a mensagem recebida for `Buy(n)`, este tenta vender os  tickets, aceitando o pedido se existir quantidade suficiente .  

Para além do ator principal, foi criamos o `ChildSellerActor`, para suportar a funcionalidade de delegação. Quando o stock do ator principal ultrapassa um determinado quantidade, este cria um filho e transfere uma parte fixa de *tickets*. Desta forma, o sistema passa a ter uma estrutura hierárquica, com um ator pai responsável pela gestão global e (um ou mais) filhos responsáveis por parte do trabalho.  

O `ChildSellerActor` recebe também pedidos de compra sempre que consegue satisfazer o pedido e reduz o seu stock. Caso contrário, este envia uma mensagem `ChildSaleFailed(remaining)` ao pai, devolvendo a quantidade de *tickets* que ainda lhe restava. Esta tal mensagem permite ao ator principal recuperar o stock e manter o estado consistente do sistema.  

A implementação foi testada com uma sequência de mensagens que inclui a criação de stock, algumas compras e o encerramento do sistema com Bye. Este teste permite observar o comportamento de uma venda e/ou o comportamento de uma falha e devolução de stock, por parte do ator filho.  

### Actor Hierarchy

[actor_hierarchy_diagram.png]

O diagrama da hierarquia de atores mostra a organização estrutural do sistema em tempo de execução. No topo encontra-se o *ActorSystem*, que contém o ator principal *SellerActor*. Sempre que o stock disponível ultrapassa o limiar definido, este ator cria um novo *ChildSellerActor*, que fica subordinado ao pai na hierarquia.  

Esta estrutura é importante porque reflete o modelo de criação e a posse de atores no Akka. Um ator criado com `context.actorOf` torna-se filho do ator que o criou, o que significa que a sua existência está associada ao pai. Assim desta forma, o diagrama evidencia a relação entre o ator principal e os seus filhos, tal como foi discutido nas aulas teóricas sobre hierarquia e lifecycle de atores.  

### Sequence diagram

[sequence_diagram.png]

O diagrama de sequência descreve uma execução possível do sistema e mostra a ordem em que as mensagens são trocadas. 

O primeiro ato mostra o processo da criação de um filho *(destacado em verde)*, enviando o `ToSell(200)`, que chega ao SellerActor. Este atualiza o seu stock interno e, como o stock ultrapassa o limiar definido, cria um ChildSellerActor com 50 *tickets*.

No ato seguinte representa uma venda normal, sem criação de filho *(destacado a cor-de-rosa)*, o cliente envia `Buy(30)`, que é tratado pelo SellerActor, e reduz o stock disponível. 

De seguida, com criação de filho *(destacado a cor-de-laranja)*. É enviado `Buy(70)` para o *ChildSellerActor*, verifica o seu stock local e, por sua vez, é detetado *tickets* suficientes para satisfazer o pedido.
Perante essa situação, o *ChildSellerActor* executa a sua verificação interna e envia ao ator pai a mensagem `ChildSaleFailed(50)`, indicando que tem 50 *tickets*. O *SellerActor* recebe essa mensagem e reintegra esse stock no seu estado interno. 

Por fim, o ato Bye *(destacado a cor preta)*, o cliente envia Bye, e termina a execução do sistema.

Este diagrama de sequência é útil porque mostra claramente a delegação entre atores e a forma como a falha de venda é tratada.

Neste sistema, o filho comunica o resultado ao pai, que recupera o stock remanescente, daí a comunicação por mensagens e não por memória partilhada.