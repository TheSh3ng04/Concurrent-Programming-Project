package cp.serverSim

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentLinkedQueue
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import scala.collection.JavaConverters._

class ServerState() {

  //thread-safe counter for the number of received requests.
  private val _counter = new AtomicInteger(0)

  //lock-free queue storing execution results with timestamps.
  private val results = new ConcurrentLinkedQueue[String]()

  //flag used to enable/disable simulation across threads.
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