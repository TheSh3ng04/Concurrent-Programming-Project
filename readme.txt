How to compile and run
----------------------

1) Exercise 1 - CCS / PseuCo
----------------------------
Files:
- Exercise1/Exercise_1_1/exerc1_1.ccs
- Exercise1/Exercise_1_2/exerc1_2.ccs
- Exercise1/Exercise_1_3/exerc1_3.ccs
- Exercise1/Exercise_1_4/exerc1_4.ccs

These files can be opened and executed in the CCS/PseuCo tools used in class.
The corresponding LTS screenshots are included as PNG files in each subfolder.

2) Exercise 2 - Process simulator server
----------------------------------------
Main server folder:
- process-simulator/server

Requirements:
- Scala
- sbt

Run the server:
1. Open a terminal in:
   process-simulator/server
2. Execute:
   sbt run

The client is available in:
- process-simulator/client/index.html

To use the client:
1. Start the Scala server with sbt run.
2. Open client/index.html in a browser.
3. Edit the input program and send requests to the server.

Important files:
- src/main/scala/cp/serverSim/Main.scala
- src/main/scala/cp/serverSim/Routes.scala
- src/main/scala/cp/serverSim/ServerState.scala

Note:
- Do not include compiled output folders such as target/ in the submission.

3) Exercise 3 - Akka ticket office
----------------------------------
Files:
- Exercise3/TicketOffice.Scala
- Exercise3/TicketOfficeDelegationTest.scala

If the project uses sbt, compile and run it with the usual Scala/Akka workflow used in class.
The diagrams used in the report are in:
- Exercise3/Diagrams/actor_hierarchy_diagram.png
- Exercise3/Diagrams/sequence_diagram.png

Submission notes
-----------------
- The ZIP must include the PDF report and all source code.
- Use separate folders for different exercises.
- Do not include compiled files or build artifacts.
- The report must include a short section explaining whether AI was used and how it was used.

Recommended ZIP content
-----------------------
- Report.pdf
- Report.md
- Exercise1/
- Exercise2/
- Exercise3/
- process-simulator/
- this readme.txt

AI Usage
--------
AI was used only as a support tool for:
- understanding the exercise requirements and project structure;
- detecting and guiding fixes for bugs or inconsistencies in the implementation;
- generating this README text.

All final code, diagrams, and report content were reviewed and edited by the authors.

End of file