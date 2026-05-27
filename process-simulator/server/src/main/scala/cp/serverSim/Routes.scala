package cp.serverSim

import cats.effect.IO
import org.http4s._
import org.http4s.dsl.io._
import org.slf4j.LoggerFactory

import java.util.concurrent.{ConcurrentHashMap, Executors}
import scala.util.matching.Regex
import scala.util.Try

object Routes {
  private val logger = LoggerFactory.getLogger(getClass)
  private val state = new ServerState()

  // Fixed-size worker pool required by the assignment
  private val pool = Executors.newFixedThreadPool(4)

  case class Instruction(
    index: Int,
    raw: String,
    message: String,
    delaySeconds: Int,
    deps: Set[Int]
  )

  val routes: IO[HttpRoutes[IO]] = IO {
    HttpRoutes.of[IO] {

      case GET -> Root / "status" =>
        Ok(state.toHtml)
          .map(addCORSHeaders)
          .map(_.withContentType(org.http4s.headers.`Content-Type`(MediaType.text.html)))

      case GET -> Root / "reset" =>
        state.resetCounter()
        Ok("State reset!").map(addCORSHeaders)

      case GET -> Root / "enable" =>
        state.enableSimulation()
        Ok("Simulation enabled").map(addCORSHeaders)

      case GET -> Root / "disable" =>
        state.disableSimulation()
        Ok("Simulation disabled").map(addCORSHeaders)

      case req @ GET -> Root / "run-simulation" =>
        val cmdOpt = req.uri.query.params.get("cmd")
        val userIp = req.remoteAddr.map(_.toString).getOrElse("unknown")

        cmdOpt match {
          case Some(cmdBlock) =>
            val cnt = state.incrementAndGetCounter()
            submitProcess(cnt, cmdBlock, userIp)
            Ok(s"[$cnt] Request accepted from $userIp")
              .map(addCORSHeaders)

          case None =>
            BadRequest("Command not provided. Use /run-simulation?cmd=")
              .map(addCORSHeaders)
        }
    }
  }

  private def submitProcess(cnt: Int, cmdBlock: String, userIp: String): Unit = {
    pool.submit(new Runnable {
      override def run(): Unit = runProcess(cnt, cmdBlock, userIp)
    })
  }

  // This implementation avoids the broken "single pass foreach" behavior.
  // It repeatedly schedules newly-ready instructions until all are done.
  // Each ready instruction runs in the fixed thread pool.
  private def runProcess(cnt: Int, cmdBlock: String, userIp: String): Unit = {
    if (!state.isSimulationEnabled) {
      val output = s"""[$cnt] Simulation refused for $userIp: disabled flag"""
      state.addResult(output)
      logger.info(output)
      return
    }

    val instructions = parseInstructions(cmdBlock)

    logger.info(s"Starting request [$cnt] from $userIp with ${instructions.size} instruction(s)")

    if (instructions.isEmpty) {
      val output = s"""[$cnt] No valid instructions found"""
      state.addResult(output)
      logger.info(output)
      return
    }

    val completed = ConcurrentHashMap.newKeySet[Int]()
    val scheduled = ConcurrentHashMap.newKeySet[Int]()
    val allIndices = instructions.map(_.index).toSet

    // reject references to missing instruction indices
    val invalidDeps = instructions.flatMap(i => i.deps.filterNot(allIndices.contains).map(d => (i.index, d)))
    if (invalidDeps.nonEmpty) {
      invalidDeps.foreach { case (instIdx, depIdx) =>
        val output = s"""[$cnt] Invalid dependency: instruction $instIdx depends on missing instruction $depIdx"""
        state.addResult(output)
        logger.info(output)
      }
      return
    }

    def scheduleReadyInstructions(): Unit = {
      instructions.foreach { inst =>
        val isDone = completed.contains(inst.index)
        val isScheduled = scheduled.contains(inst.index)
        val depsReady = inst.deps.forall(completed.contains)

        if (!isDone && !isScheduled && depsReady) {
          scheduled.add(inst.index)
          pool.submit(new Runnable {
            override def run(): Unit = executeInstruction(inst)
          })
        }
      }
    }

    def executeInstruction(inst: Instruction): Unit = {
      if (!state.isSimulationEnabled) {
        val output = s"""[$cnt] Simulation stopped before instruction ${inst.index}"""
        state.addResult(output)
        logger.info(output)
        completed.add(inst.index)
        scheduleReadyInstructions()
        return
      }

      if (inst.delaySeconds > 0) {
        Thread.sleep(inst.delaySeconds.toLong * 1000L)
      }

      val output = s"""[$cnt] Received request from $userIp: print "${inst.message}" (instruction ${inst.index})"""
      state.addResult(output)
      logger.info(output)
      completed.add(inst.index)

      // After completing one instruction, try to schedule newly unlocked ones
      scheduleReadyInstructions()
    }

    // initial wave: instructions with empty deps
    scheduleReadyInstructions()
  }

  private def parseInstructions(cmdBlock: String): List[Instruction] = {
    cmdBlock
      .split(";")
      .map(_.trim)
      .filter(_.nonEmpty)
      .toList
      .zipWithIndex
      .flatMap { case (raw, idx) => parseInstruction(raw, idx + 1) }
  }

  private def parseInstruction(raw: String, index: Int): Option[Instruction] = {
    val afterSplit = raw.split("""\s+after\s+""", 2)
    val beforeAfter = afterSplit(0).trim

    val deps: Set[Int] =
      if (afterSplit.length > 1) {
        afterSplit(1)
          .split(",")
          .map(_.trim)
          .filter(_.nonEmpty)
          .flatMap(s => Try(s.toInt).toOption)
          .toSet
      } else Set.empty[Int]

    val delayPattern: Regex = """^(.*?)(?:\s*@\s*(\d+))?$""".r

    beforeAfter match {
      case delayPattern(printPart, delayStr) =>
        val delay = Option(delayStr).flatMap(s => Try(s.toInt).toOption).getOrElse(0)
        val message = extractPrintableMessage(printPart.trim)
        Some(Instruction(index, raw, message, delay, deps))
      case _ => None
    }
  }

  private def extractPrintableMessage(text: String): String = {
    val quoted: Regex = """^print\s+"(.*)"$""".r
    val plain: Regex = """^print\s+(.+)$""".r

    text match {
      case quoted(msg) => msg
      case plain(msg)  => msg.trim
      case other       => other.trim
    }
  }

  def addCORSHeaders(response: Response[IO]): Response[IO] = {
    response.putHeaders(
      "Access-Control-Allow-Origin" -> "*",
      "Access-Control-Allow-Methods" -> "GET, POST, PUT, DELETE, OPTIONS",
      "Access-Control-Allow-Headers" -> "Content-Type, Authorization",
      "Access-Control-Allow-Credentials" -> "true"
    )
  }
}