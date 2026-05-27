package cp.serverSim

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentLinkedQueue
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import scala.collection.JavaConverters._

class ServerState() {

  // Thread-safe request counter (Exercise 2.1)
  private val _counter = new AtomicInteger(0)

  // Lock-free structure storing execution results with timestamps (Exercise 2.2)
  // ConcurrentLinkedQueue is a lock-free, thread-safe FIFO queue (non-blocking CAS operations)
  private val results = new ConcurrentLinkedQueue[String]()

  // Exercise 2.4: @volatile ensures that writes by one thread are immediately
  // visible to all other threads, without using locks.
  // Without @volatile, the JVM may cache _simulationEnabled in a CPU register,
  // so worker threads might never see the updated value written by the main thread.
  @volatile private var _simulationEnabled: Boolean = true

  private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

  def incrementAndGetCounter(): Int = _counter.incrementAndGet()

  def counter: Int = _counter.get()

  def resetCounter(): Unit = {
    _counter.set(0)
    results.clear()
  }

  def addResult(msg: String): Unit = {
    val ts = LocalTime.now().format(formatter)
    results.add(s"[$ts] $msg")
  }

  def enableSimulation(): Unit  = { _simulationEnabled = true }
  def disableSimulation(): Unit = { _simulationEnabled = false }
  def isSimulationEnabled: Boolean = _simulationEnabled

  def getResults: List[String] = results.iterator().asScala.toList

def toHtml: String = {
  val resultsHtml =
    if (results.isEmpty) "<li>No results yet.</li>"
    else getResults.map(r => s"<li>$r</li>").mkString("\n")

  s"""
     |<p><b>counter:</b> $counter</p>
     |<p><b>simulation enabled:</b> $isSimulationEnabled</p>
     |<ul>
     |$resultsHtml
     |</ul>
     |""".stripMargin
}
}