import akka.actor.{Actor, ActorSystem, Props}
import akka.event.Logging

case class ToSell(n: Int)
case class Buy(n: Int)

class SellerActor extends Actor {
  val log = Logging(context.system, this)
  var stock = 0

  def receive: Receive = {
    case ToSell(n) =>
      stock += n
      log.info(s"Added $n tickets, stock=$stock")

    case Buy(n) =>
      if (n <= stock) {
        stock -= n
        log.info(s"Sold $n tickets, stock=$stock")
      } else {
        log.error(s"Not enough tickets: requested $n, available $stock")
      }
  }
}

object TicketOfficeTest extends App {
  val sys = ActorSystem("TicketSys")
  val ticketOffice = sys.actorOf(Props[SellerActor], "mainoffice")

  ticketOffice ! ToSell(2000)
  for (_ <- 0 until 101) ticketOffice ! Buy(20)

  Thread.sleep(3000)
  sys.terminate()
}