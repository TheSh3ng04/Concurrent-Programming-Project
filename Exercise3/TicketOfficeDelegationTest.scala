import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.event.Logging

object TicketOfficeDelegationTest extends App {

    case class ToSell(n: Int)
    case class Buy(n: Int)
    case class ChildSaleFailed(remaining: Int)
    case object Bye


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

        case Bye =>
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

            case Bye =>
                log.info(s"Closing ticket office. Final stock = $stock.")
                context.stop(self)
        }
    }

    val sys = ActorSystem("TicketSys")
    val ticketOffice = sys.actorOf(Props[SellerActor], "mainoffice")

    ticketOffice ! ToSell(200)
    ticketOffice ! Buy(30)
    ticketOffice ! Buy(80)
    ticketOffice ! Bye

    Thread.sleep(3000)
    sys.terminate()
}